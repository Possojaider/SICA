package com.sica;

import com.sica.domain.Session;
import com.sica.domain.AccessPolicy;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store.*;
import com.sica.application.Sica;
import javafx.stage.Window;
import java.sql.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    private final String database = "sica_ui_" + UUID.randomUUID().toString().replace("-", "");
    private String serverUrl, databaseUrl, dbUser, dbPassword;
    private final String appUser = "sica_ui_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private final String appPassword = com.sica.domain.CredentialCodes.generate();
    private final String adminEmail = "admin@ui.sica.local";
    private final String adminPassword = "Ui-Admin-seguro-2026";
    private Configuration configuration;
    private final List<ShellController> controllers = new ArrayList<>();
    private final List<BackgroundTasks> workers = new ArrayList<>();

    @BeforeAll void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(15, TimeUnit.SECONDS));
        tasks = new BackgroundTasks();
        workers.add(tasks);
        dbPassword = System.getenv("SICA_TEST_DB_PASSWORD");
        assertNotNull(dbPassword, "Ejecuta la prueba gráfica mediante scripts/test-mysql.");
        dbUser = System.getenv().getOrDefault("SICA_TEST_DB_USER", "root");
        serverUrl = System.getenv().getOrDefault("SICA_TEST_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/?connectionTimeZone=America/Bogota");
        int separator = serverUrl.indexOf('?');
        databaseUrl = serverUrl.substring(0, separator < 0 ? serverUrl.length() : separator) + database +
                (separator < 0 ? "" : serverUrl.substring(separator));
        try (var connection = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
             var statement = connection.createStatement()) {
            for (String resource : List.of("/db/schema.sql", "/db/data.sql")) {
                try (var input = getClass().getResourceAsStream(resource)) {
                    String script = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
                            .replace("sica_acme", database);
                    for (String query : script.split(";")) if (!query.isBlank()) statement.execute(query);
                }
            }
        }
        try (var connection = DriverManager.getConnection(serverUrl, dbUser, dbPassword)) {
            try (var create = connection.prepareStatement("CREATE USER '" + appUser + "'@'%' IDENTIFIED BY ?")) {
                create.setString(1, appPassword);
                create.executeUpdate();
            }
            try (var input = getClass().getResourceAsStream("/db/privileges.sql");
                 var statement = connection.createStatement()) {
                String grants = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
                        .replace("ON sica_acme.", "ON " + database + ".")
                        .replace("'sica'@'%'", "'" + appUser + "'@'%'");
                for (String query : grants.split(";")) if (!query.isBlank()) statement.execute(query);
            }
        }
        configuration = new Configuration(databaseUrl, appUser, appPassword, java.time.Clock.system(java.time.ZoneId.of("America/Bogota")));
        sica = ApplicationFactory.create(configuration);
        sica.bootstrap(adminEmail, adminPassword);
        session = sica.login(adminEmail, adminPassword);
    }

    @AfterAll void closeJavaFx() throws Exception {
        fx(() -> {
            for (ShellController controller : controllers) controller.close();
            return null;
        });
        for (BackgroundTasks worker : workers) worker.close();
        if (session != null) sica.logout(session);
        Platform.exit();
        if (serverUrl != null && dbPassword != null) {
            try (var connection = DriverManager.getConnection(serverUrl, dbUser, dbPassword);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database);
                statement.execute("DROP USER IF EXISTS '" + appUser + "'@'%'");
            }
        }
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
        fx(() -> {
            ((TextField) login.lookup("#email")).setText(adminEmail);
            ((PasswordField) login.lookup("#password")).setText(adminPassword);
            ((Button) login.lookup("#submit")).fire();
            return null;
        });
        await(() -> authenticated.get() != null);
        sica.logout(authenticated.get());

        Parent workspace = fx(() -> {
            shell = new ShellController(sica, session, tasks, () -> {});
            controllers.add(shell);
            return load("/fxml/shell.fxml", shell);
        });
        await(() -> fx(() -> !workspace.lookup("#loading").isVisible()));
        snapshot(workspace, "dashboard");
        for (String name : List.of("Notificaciones", "Usuarios", "Empresas", "Personas", "Registro de visitas",
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


    @Test void allFourRolesSeeTheirAuthorizedNavigation() throws Exception {
        long company = sica.saveCompany(session, null, new Company("UI-ROLES", "Empresa de prueba de roles", true));
        for (String role : List.of("SUPER", "SUPERVISOR", "GUARDA", "FUNCIONARIO")) {
            String email = "ui-" + role.toLowerCase(Locale.ROOT) + "@acme.co";
            sica.saveUser(session, null, new UserForm(email, role, "Ui-Usuario-seguro-2026", true, role,
                    role.equals("FUNCIONARIO") ? company : null));
            Session roleSession = sica.login(email, "Ui-Usuario-seguro-2026");
            ShellController controller = new ShellController(sica, roleSession, tasks, () -> {});
            Parent view = fx(() -> {
                controllers.add(controller);
                return load("/fxml/shell.fxml", controller);
            });
            await(() -> fx(() -> !view.lookup("#loading").isVisible()));
            Set<String> names = fx(() -> view.lookup("#navigation").lookupAll(".button").stream()
                    .map(Button.class::cast).map(Button::getText).collect(java.util.stream.Collectors.toSet()));
            assertTrue(names.containsAll(List.of("Panel principal", "Notificaciones", "Personas", "Registro de visitas")));
            assertEquals(role.equals("SUPER"), names.contains("Usuarios"));
            assertEquals(role.equals("SUPER"), names.contains("Empresas"));
            assertEquals(!role.equals("FUNCIONARIO"), names.contains("Entradas y salidas"));
            assertEquals(Set.of("SUPER", "FUNCIONARIO").contains(role), names.contains("Aprobar solicitudes"));
            assertEquals(!role.equals("GUARDA"), names.contains("Reportes"));
            fx(() -> { controller.close(); return null; });
            sica.logout(roleSession);
        }
    }

    @Test void twoDesktopInstancesCompleteNotificationApprovalAndQrEntryThroughTheirControls() throws Exception {
        long company = sica.saveCompany(session, null, new Company("UI-FLUJO", "Empresa del flujo QR", true));
        String document = "UI-" + UUID.randomUUID().toString().substring(0, 8);
        long person = sica.savePerson(session, null,
                new PersonForm(document, "Invitada del flujo QR", "INVITADO", company, "", "HABILITADO"));
        sica.saveUser(session, null, new UserForm("flujo-guarda@acme.co", "Guarda", "Ui-Guarda-seguro-2026", true, "GUARDA", null));
        sica.saveUser(session, null, new UserForm("flujo-funcionario@acme.co", "Funcionario", "Ui-Funcionario-seguro-2026", true, "FUNCIONARIO", company));
        Session guard = sica.login("flujo-guarda@acme.co", "Ui-Guarda-seguro-2026");
        Sica anotherApplication = ApplicationFactory.create(configuration);
        Session official = anotherApplication.login("flujo-funcionario@acme.co", "Ui-Funcionario-seguro-2026");
        BackgroundTasks officialWorker = new BackgroundTasks();
        workers.add(officialWorker);
        ShellController guardController = new ShellController(sica, guard, tasks, () -> {});
        ShellController officialController = new ShellController(anotherApplication, official, officialWorker, () -> {});
        Parent guardView = fx(() -> {
            controllers.add(guardController);
            return load("/fxml/shell.fxml", guardController);
        });
        Parent officialView = fx(() -> {
            controllers.add(officialController);
            return load("/fxml/shell.fxml", officialController);
        });
        awaitIdle(guardView);
        awaitIdle(officialView);
        navigate(officialView, "Notificaciones");
        navigate(guardView, "Registro de visitas");
        fx(() -> { button(guardView, "Registrar visita").fire(); return null; });
        await(() -> fx(() -> form() != null));
        fx(() -> {
            Parent form = form();
            selectChoice(form, "#person", " · " + person);
            selectChoice(form, "#host", " · " + company);
            selectChoice(form, "#type", "NO_ANUNCIADA");
            ((TextField) form.lookup("#start")).setText(sica.currentTime().minusMinutes(1).withSecond(0).withNano(0).toString());
            ((TextField) form.lookup("#end")).setText(sica.currentTime().plusHours(1).withSecond(0).withNano(0).toString());
            ((Button) form.lookup("#form-save")).fire();
            return null;
        });
        await(() -> fx(() -> form() == null));
        await(() -> fx(() -> rows(officialView).stream().anyMatch(row -> row.get(3).equals("SOLICITUD"))));
        long visit = fx(() -> Long.parseLong(rows(officialView).getFirst().get(1)));
        snapshot(officialView, "notificaciones");
        fx(() -> {
            table(officialView).getSelectionModel().selectFirst();
            button(officialView, "Marcar como leída").fire();
            return null;
        });
        await(() -> fx(() -> rows(officialView).stream().anyMatch(row -> row.get(5).equals("LEIDA"))));
        navigate(officialView, "Aprobar solicitudes");
        fx(() -> {
            table(officialView).getSelectionModel().selectFirst();
            button(officialView, "Aprobar").fire();
            return null;
        });
        await(() -> fx(() -> form() != null));
        fx(() -> {
            Parent form = form();
            ((Button) form.lookup("#form-save")).fire();
            return null;
        });
        await(() -> fx(() -> !((Label) form().lookup("#form-error")).getText().equals("Guardando…")));
        assertNotNull(fx(UiRenderingTest::form));
        fx(() -> {
            ((TextArea) form().lookup("#reason")).setText("Reunión programada. Identidad comprobada por el anfitrión.");
            ((Button) form().lookup("#form-save")).fire();
            return null;
        });
        await(() -> fx(() -> form() == null));
        await(() -> fx(() -> rows(guardView).stream().anyMatch(row ->
                row.getFirst().equals(Long.toString(visit)) && row.get(6).equals("APROBADA"))));
        assertThrows(SicaException.class, () -> sica.issueCredential(guard, visit));
        assertThrows(SicaException.class, () -> anotherApplication.checkIn(official, visit));
        // Cambia a visitas para conservar visible la aprobación resuelta.
        navigate(officialView, "Registro de visitas");
        fx(() -> { table(officialView).getSelectionModel().selectFirst(); return null; });
        Platform.runLater(() -> button(officialView, "Emitir o renovar QR").fire());
        await(() -> fx(() -> dialog("Emitir credencial temporal") != null));
        fx(() -> {
            DialogPane confirmation = dialog("Emitir credencial temporal");
            ((Button) confirmation.lookupButton(ButtonType.OK)).fire();
            return null;
        });
        await(() -> fx(() -> dialog("Credencial temporal de visita") != null));
        String code = fx(() -> ((TextField) dialog("Credencial temporal de visita").lookup("#code")).getText());
        assertTrue(code.startsWith("SICA1:"));
        snapshot(fx(() -> dialog("Credencial temporal de visita")), "credencial");
        fx(() -> {
            ((Button) dialog("Credencial temporal de visita").lookupButton(ButtonType.CLOSE)).fire();
            return null;
        });
        navigate(guardView, "Entradas y salidas");
        fx(() -> { button(guardView, "Ingresar con QR").fire(); return null; });
        await(() -> fx(() -> form() != null));
        fx(() -> {
            ((TextField) form().lookup("#code")).setText(code);
            ((Button) form().lookup("#form-save")).fire();
            return null;
        });
        await(() -> fx(() -> form() == null));
        await(() -> fx(() -> rows(guardView).stream().anyMatch(row ->
                row.getFirst().equals(Long.toString(visit)) && row.get(6).equals("DENTRO"))));
        assertThrows(SicaException.class, () -> sica.checkInWithCredential(guard, code));
        assertTrue(sica.report(guard, Report.NOTIFICACIONES, "aprobada", null, null).rows().stream()
                .anyMatch(row -> row.get(1).equals(Long.toString(visit))));
        snapshot(guardView, "ingreso-qr");
        fx(() -> { guardController.close(); officialController.close(); return null; });
        sica.logout(guard);
        anotherApplication.logout(official);
    }

    private static Button button(Parent root, String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getText().equals(text)).findFirst().orElseThrow();
    }

    private static DialogPane dialog(String title) {
        return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> title.equals(pane.getHeaderText())).findFirst().orElse(null);
    }

    private static Parent form() {
        return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(root -> "sica-form".equals(root.getId())).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static void selectChoice(Parent root, String selector, String suffix) {
        ComboBox<String> choice = (ComboBox<String>) root.lookup(selector);
        choice.setValue(choice.getItems().stream().filter(value -> value.endsWith(suffix)).findFirst().orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private static TableView<List<String>> table(Parent root) {
        return (TableView<List<String>>) root.lookup("#table");
    }

    private static List<List<String>> rows(Parent root) {
        return List.copyOf(table(root).getItems());
    }

    private static void awaitIdle(Parent root) throws Exception {
        await(() -> fx(() -> !root.lookup("#loading").isVisible()));
    }

    private static void navigate(Parent root, String name) throws Exception {
        awaitIdle(root);
        fx(() -> { button(root, name).fire(); return null; });
        awaitIdle(root);
        assertEquals(name, fx(() -> ((Label) root.lookup("#heading")).getText()));
        assertTrue(fx(() -> button(root, name).getStyleClass().contains("selected")));
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
        assertTrue(width >= 400 && height >= 300);
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!condition.call()) {
            if (System.nanoTime() >= deadline) fail("La operación JavaFX no terminó a tiempo");
            Thread.sleep(50);
        }
    }
}
