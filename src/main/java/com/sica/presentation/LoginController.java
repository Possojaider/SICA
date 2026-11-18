package com.sica.presentation;

import com.sica.domain.Session;

import com.sica.domain.port.in.SicaUseCases;
import com.sica.domain.SicaException;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

public final class LoginController {
    private final SicaUseCases sica;
    private final BackgroundTasks tasks;
    private final Consumer<Session> onLogin;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private Button submit;
    @FXML private ProgressIndicator loading;
    @FXML private Label message;

    public LoginController(SicaUseCases sica, BackgroundTasks tasks, Consumer<Session> onLogin) {
        this.sica = sica;
        this.tasks = tasks;
        this.onLogin = onLogin;
    }

    @FXML
    private void login() {
        if (submit.isDisabled()) return;
        if (email.getText().isBlank() || password.getText().isBlank()) {
            message.setText("Completa el correo y la contraseña.");
            return;
        }
        String loginEmail = email.getText();
        String secret = password.getText();
        submit.setDisable(true);
        loading.setManaged(true);
        loading.setVisible(true);
        message.setText("");
        tasks.submit(() -> sica.login(loginEmail, secret), session -> {
            password.clear();
            onLogin.accept(session);
        }, failure -> {
            submit.setDisable(false);
            loading.setVisible(false);
            loading.setManaged(false);
            password.clear();
            if (!(failure instanceof SicaException)) LoggerFactory.getLogger(getClass()).error("Fallo de inicio de sesión", failure);
            message.setText(failure instanceof SicaException ? failure.getMessage() : "No se pudo iniciar sesión.");
        });
    }
}
