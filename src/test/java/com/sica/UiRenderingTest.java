package com.sica;

import com.sica.domain.Session;
import com.sica.infrastructure.*;
import com.sica.presentation.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SICA_UI_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UiRenderingTest {
    private BackgroundTasks tasks;
    private ShellController shell;
    private com.sica.application.Sica sica;
    private Session session;

    @BeforeAll void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(15, TimeUnit.SECONDS));
        tasks = new BackgroundTasks();
        sica = ApplicationFactory.create(Configuration.environment());
        var env = LocalEnvironment.load();
        session = sica.login(env.get("SICA_ADMIN_EMAIL"), env.get("SICA_ADMIN_PASSWORD"));
    }

    @AfterAll void closeJavaFx() throws Exception {
        fx(() -> {
            if (shell != null) shell.close();
            return null;
        });
        if (tasks != null) tasks.close();
        if (session != null) sica.logout(session);
        Platform.exit();
    }

    @Test void loginAndAllModuleViewsLoadRenderAndQueryRealMySql() throws Exception {
        AtomicReference<Session> authenticated = new AtomicReference<>();
        Parent login = fx(() -> load("/fxml/login.fxml", new LoginController(sica, tasks, authenticated::set)));
        fx(() -> {
            Button submit = (Button) login.lookup("#submit");
            submit.fire();
            assertEquals("Completa el correo y la contraseña.", ((Label) login.lookup("#message")).getText());
            return null;
        });
        snapshot(login, "login");
        var env = LocalEnvironment.load();
        fx(() -> {
            ((TextField) login.lookup("#email")).setText(env.get("SICA_ADMIN_EMAIL"));
            ((PasswordField) login.lookup("#password")).setText(env.get("SICA_ADMIN_PASSWORD"));
            ((Button) login.lookup("#submit")).fire();
            return null;
        });
        await(() -> authenticated.get() != null);
        sica.logout(authenticated.get());

        Parent workspace = fx(() -> {
            shell = new ShellController(sica, session, tasks, () -> {});
            return load("/fxml/shell.fxml", shell);
        });
        await(() -> fx(() -> !workspace.lookup("#loading").isVisible()));
        snapshot(workspace, "dashboard");
        for (String name : List.of("Usuarios", "Empresas", "Personas", "Registro de visitas",
                "Entradas y salidas", "Solicitudes pendientes", "Aprobar solicitudes", "Incidentes", "Auditoría", "Reportes")) {
            fx(() -> {
                Button button = workspace.lookup("#navigation").lookupAll(".button").stream()
                        .map(Button.class::cast).filter(candidate -> candidate.getText().equals(name)).findFirst().orElseThrow();
                button.fire();
                return null;
            });
            await(() -> fx(() -> !workspace.lookup("#loading").isVisible()));
            fx(() -> {
                assertEquals(name, ((Label) workspace.lookup("#heading")).getText());
                assertFalse(workspace.lookup("#status").getStyleClass().contains("error"),
                        ((Label) workspace.lookup("#status")).getText());
                assertFalse(((TableView<?>) workspace.lookup("#table")).getColumns().isEmpty());
                return null;
            });
        }
        snapshot(workspace, "reportes");
    }

    private Parent load(String resource, Object controller) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        loader.setControllerFactory(type -> controller);
        Parent root = loader.load();
        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());
        root.applyCss();
        root.layout();
        return root;
    }

    private void snapshot(Parent root, String name) throws Exception {
        WritableImage rendered = fx(() -> {
            root.applyCss();
            root.layout();
            return root.snapshot(null, null);
        });
        int width = (int) rendered.getWidth();
        int height = (int) rendered.getHeight();
        assertTrue(width >= 1000 && height >= 700);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        rendered.getPixelReader().getPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        Path directory = Path.of("target", "ui");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }

    private static <T> T fx(Callable<T> operation) throws Exception {
        FutureTask<T> future = new FutureTask<>(operation);
        Platform.runLater(future);
        return future.get(15, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.call()) {
            if (System.nanoTime() >= deadline) fail("La operación JavaFX no terminó a tiempo");
            Thread.sleep(50);
        }
    }
}
