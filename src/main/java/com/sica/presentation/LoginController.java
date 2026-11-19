package com.sica.presentation;

import com.sica.domain.Session;
import com.sica.domain.SicaException;
import com.sica.domain.port.in.SicaUseCases;
import com.sica.domain.port.out.SavedLoginStore;
import javafx.animation.*;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

public final class LoginController implements AutoCloseable {
    private record Result(Session session, String warning) {}
    private final SicaUseCases sica;
    private final BackgroundTasks tasks;
    private final SavedLoginStore savedLogins;
    private final Consumer<Session> onLogin;
    private final Consumer<String> warning;
    private boolean saved;
    private boolean restoring;
    private boolean closed;
    private boolean entrancePlayed;
    private long edits;
    private Animation entrance;
    private Animation ambient;
    @FXML private VBox loginCard;
    @FXML private StackPane hero;
    @FXML private TextField email, visiblePassword;
    @FXML private PasswordField password;
    @FXML private HBox passwordFrame;
    @FXML private Button submit, visibilityToggle, forget;
    @FXML private CheckBox remember;
    @FXML private ProgressIndicator loading;
    @FXML private Label message, storageHint, welcome;
    @FXML private Circle ambientRing;

    public LoginController(SicaUseCases sica, BackgroundTasks tasks, Consumer<Session> onLogin) {
        this(sica, tasks, SavedLoginStore.unavailable(), onLogin, text -> {});
    }

    public LoginController(SicaUseCases sica, BackgroundTasks tasks, SavedLoginStore savedLogins,
                           Consumer<Session> onLogin, Consumer<String> warning) {
        this.sica = sica;
        this.tasks = tasks;
        this.savedLogins = savedLogins;
        this.onLogin = onLogin;
        this.warning = warning;
    }

    @FXML
    private void initialize() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty());
        hero.setClip(clip);
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        email.textProperty().addListener((observable, before, after) -> edits++);
        password.textProperty().addListener((observable, before, after) -> edits++);
        password.focusedProperty().addListener((observable, before, after) -> updatePasswordFocus());
        visiblePassword.focusedProperty().addListener((observable, before, after) -> updatePasswordFocus());
        loginCard.sceneProperty().addListener((observable, before, scene) -> attachAnimation(scene));
        remember.setDisable(!savedLogins.available());
        storageHint.setText(savedLogins.available()
                ? "Los datos se guardan en el llavero de este Mac."
                : "El guardado de contraseñas está disponible en macOS.");
        if (savedLogins.available()) restore();
    }

    private void restore() {
        restoring = true;
        remember.setDisable(true);
        long initialEdits = edits;
        tasks.submit(savedLogins::load, result -> {
            if (closed) return;
            restoring = false;
            remember.setDisable(submit.isDisabled());
            saved = result.isPresent();
            showForget(saved);
            if (edits == initialEdits) result.ifPresent(login -> {
                email.setText(login.email());
                password.setText(login.password());
                remember.setSelected(true);
                welcome.setText("Bienvenido de nuevo");
            });
        }, failure -> {
            if (closed) return;
            restoring = false;
            remember.setDisable(submit.isDisabled());
            showForget(true);
            feedback("No se pudieron recuperar los datos guardados. Puedes ingresar tu cuenta.", false);
            technical(failure, "No se pudieron recuperar las credenciales locales");
        });
    }

    @FXML
    private void login() {
        if (submit.isDisabled()) return;
        if (email.getText().isBlank() || password.getText().isBlank()) {
            feedback("Completa el correo y la contraseña.", false);
            return;
        }
        if (!email.getText().strip().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            feedback("Introduce un correo electrónico válido.", false);
            email.requestFocus();
            return;
        }
        String loginEmail = email.getText().strip();
        String secret = password.getText();
        boolean keep = remember.isSelected();
        busy(true, "Verificando acceso…");
        feedback("", false);
        tasks.submit(() -> {
            Session session = sica.login(loginEmail, secret);
            String storageWarning = "";
            if (savedLogins.available()) {
                try {
                    if (keep) savedLogins.save(new SavedLoginStore.Login(loginEmail, secret));
                    else savedLogins.clear();
                } catch (RuntimeException failure) {
                    technical(failure, "No se pudo actualizar la cuenta recordada");
                    storageWarning = keep
                            ? "La sesión se inició. No se pudieron guardar los datos en el llavero."
                            : "La sesión se inició. No se pudieron eliminar los datos previamente guardados.";
                }
            }
            return new Result(session, storageWarning);
        }, result -> {
            if (closed) {
                tasks.submit(() -> { sica.logout(result.session()); return null; }, ignored -> {}, failure -> technical(failure, "No se pudo cerrar la sesión descartada"));
                return;
            }
            password.clear();
            visiblePassword.setVisible(false);
            visiblePassword.setManaged(false);
            password.setVisible(true);
            password.setManaged(true);
            submit.setText("Acceso verificado");
            onLogin.accept(result.session());
            if (!result.warning().isEmpty()) warning.accept(result.warning());
        }, failure -> {
            if (closed) return;
            busy(false, "Iniciar sesión");
            password.clear();
            technical(failure, "Fallo de inicio de sesión");
            feedback(failure instanceof SicaException ? failure.getMessage() : "No se pudo iniciar sesión.", false);
            (visiblePassword.isVisible() ? visiblePassword : password).requestFocus();
        });
    }

    @FXML
    private void togglePassword() {
        boolean reveal = !visiblePassword.isVisible();
        visiblePassword.setVisible(reveal);
        visiblePassword.setManaged(reveal);
        password.setVisible(!reveal);
        password.setManaged(!reveal);
        visibilityToggle.setText(reveal ? "Ocultar" : "Mostrar");
        TextField active = reveal ? visiblePassword : password;
        active.requestFocus();
        active.positionCaret(active.getLength());
    }

    @FXML
    private void rememberChanged() {
        if (!remember.isSelected() && saved && !restoring) forget(false);
    }

    @FXML
    private void forgetCredentials() {
        forget(true);
    }

    private void forget(boolean clearFields) {
        if (submit.isDisabled() || restoring) return;
        busy(true, "Olvidando datos…");
        tasks.submit(() -> { savedLogins.clear(); return null; }, ignored -> {
            if (closed) return;
            saved = false;
            remember.setSelected(false);
            showForget(false);
            if (clearFields) {
                email.clear();
                password.clear();
                welcome.setText("Inicia sesión");
                email.requestFocus();
            }
            busy(false, "Iniciar sesión");
            feedback("Datos guardados eliminados.", true);
        }, failure -> {
            if (closed) return;
            busy(false, "Iniciar sesión");
            remember.setSelected(saved);
            feedback("No se pudieron olvidar los datos. Revisa la autorización del llavero.", false);
            technical(failure, "No se pudieron olvidar las credenciales");
        });
    }

    private void busy(boolean busy, String label) {
        submit.setDisable(busy);
        submit.setText(label);
        loading.setVisible(busy);
        loading.setManaged(busy);
        email.setDisable(busy);
        passwordFrame.setDisable(busy);
        remember.setDisable(busy || restoring || !savedLogins.available());
        forget.setDisable(busy);
    }

    private void showForget(boolean visible) {
        forget.setVisible(visible);
        forget.setManaged(visible);
    }

    private void feedback(String text, boolean success) {
        message.setText(text);
        message.getStyleClass().removeAll("error", "success");
        message.getStyleClass().add(success ? "success" : "error");
        if (!text.isEmpty() && !success && loginCard.getScene() != null && loginCard.getScene().getWindow() != null) {
            Timeline shake = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(passwordFrame.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(65), new KeyValue(passwordFrame.translateXProperty(), -4)),
                    new KeyFrame(Duration.millis(130), new KeyValue(passwordFrame.translateXProperty(), 4)),
                    new KeyFrame(Duration.millis(210), new KeyValue(passwordFrame.translateXProperty(), 0)));
            shake.play();
        }
    }

    private void updatePasswordFocus() {
        passwordFrame.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"),
                password.isFocused() || visiblePassword.isFocused());
    }

    private void attachAnimation(Scene scene) {
        if (scene == null) return;
        scene.windowProperty().addListener((observable, before, window) -> {
            if (window == null) return;
            window.showingProperty().addListener((property, wasShowing, showing) -> {
                if (showing) animateEntrance();
            });
            if (window.isShowing()) animateEntrance();
        });
        if (scene.getWindow() != null && scene.getWindow().isShowing()) animateEntrance();
    }

    private void animateEntrance() {
        if (closed || entrancePlayed) return;
        entrancePlayed = true;
        FadeTransition fade = new FadeTransition(Duration.millis(420), loginCard);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(420), loginCard);
        rise.setFromY(14);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);
        entrance = new ParallelTransition(fade, rise);
        entrance.play();
        FadeTransition pulse = new FadeTransition(Duration.seconds(3.8), ambientRing);
        pulse.setFromValue(0.2);
        pulse.setToValue(0.55);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        ambient = pulse;
        ambient.play();
    }

    private static void technical(Throwable failure, String context) {
        if (!(failure instanceof SicaException)) LoggerFactory.getLogger(LoginController.class).warn(context, failure);
    }

    @Override
    public void close() {
        closed = true;
        if (entrance != null) entrance.stop();
        if (ambient != null) ambient.stop();
        password.clear();
    }
}
