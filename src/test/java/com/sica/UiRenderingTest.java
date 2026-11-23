package com.sica;

import com.sica.domain.Session;
import com.sica.domain.AccessPolicy;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store.*;
import com.sica.domain.port.out.SavedLoginStore;
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
    private final List<LoginController> loginControllers = new ArrayList<>();
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
        if ("true".equals(System.getenv("SICA_TUTORIAL_CAPTURE"))) DemoDataInstaller.install(configuration);
    }

    @AfterAll void closeJavaFx() throws Exception {
        fx(() -> {
            for (LoginController controller : loginControllers) controller.close();
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
        if ("true".equals(System.getenv("SICA_TUTORIAL_CAPTURE"))) {
            Parent cleanLogin = fx(() -> load("/fxml/login.fxml", new LoginController(sica, tasks, authenticated::set)));
            snapshot(cleanLogin, "tutorial-login");
        }
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
            if ("true".equals(System.getenv("SICA_TUTORIAL_CAPTURE"))) {
                snapshot(workspace, "modulo-" + name.toLowerCase(Locale.ROOT).replace(' ', '-'));
                captureTutorialForm(workspace, name);
            }
        }
        snapshot(workspace, "reportes");
    }


    private void captureTutorialForm(Parent view, String name) throws Exception {
        String action = switch (name) {
            case "Usuarios", "Empresas", "Personas" -> "Nuevo";
            case "Registro de visitas" -> "Registrar visita";
            case "Incidentes" -> "Reportar incidente";
            case "Aprobar solicitudes" -> "Aprobar";
            default -> null;
        };
        if (action == null) return;
        fx(() -> {
            if (name.equals("Aprobar solicitudes")) table(view).getSelectionModel().selectFirst();
            button(view, action).fire();
            return null;
        });
        await(() -> fx(() -> form() != null));
        fx(() -> {
            switch (name) {
                case "Empresas" -> { text(form(), "nit", "900123456-1"); text(form(), "name", "Empresa del tutorial"); text(form(), "contact", "Área de recepción"); }
                case "Usuarios" -> { text(form(), "email", "funcionario@empresa.example"); text(form(), "name", "Funcionario de ejemplo"); selectChoice(form(), "#role", "FUNCIONARIO"); tutorialChoice("company", "Acme Logística"); }
                case "Personas" -> { text(form(), "doc", "DEMO-1001"); text(form(), "name", "Visitante de ejemplo"); tutorialChoice("company", "Acme Logística"); }
                case "Registro de visitas" -> tutorialChoice("person", "ACME-I-001");
                case "Incidentes" -> { text(form(), "visit", "2"); text(form(), "description", "Documento deteriorado. Identidad verificada por recepción."); }
                case "Aprobar solicitudes" -> text(form(), "reason", "Reunión confirmada con el anfitrión. Documento verificado.");
                default -> { }
            }
            return null;
        });
        snapshot(fx(UiRenderingTest::form), "formulario-" + name.toLowerCase(Locale.ROOT).replace(' ', '-'));
        if (name.equals("Registro de visitas")) {
            fx(() -> { selectChoice(form(), "#type", "NO_ANUNCIADA"); return null; });
            snapshot(fx(UiRenderingTest::form), "formulario-no-anunciada");
            fx(() -> { selectChoice(form(), "#type", "SIN_CARNET"); tutorialChoice("person", "ACME-T-001"); return null; });
            snapshot(fx(UiRenderingTest::form), "formulario-sin-carnet");
        }
        fx(() -> { button(form(), "Cancelar").fire(); return null; });
        await(() -> fx(() -> form() == null));
    }

    @SuppressWarnings("unchecked")
    private static void tutorialChoice(String id, String contains) {
        ComboBox<String> combo = (ComboBox<String>) form().lookup("#" + id);
        combo.setValue(combo.getItems().stream().filter(value -> value.contains(contains)).findFirst().orElseThrow());
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
            assertNotNull(fx(() -> view.lookup("#helpButton")), "Todos los roles deben tener acceso al tutorial");
            assertEquals(role.equals("SUPER"), names.contains("Usuarios"));
            assertEquals(role.equals("SUPER"), names.contains("Empresas"));
            assertEquals(!role.equals("FUNCIONARIO"), names.contains("Entradas y salidas"));
            assertEquals(Set.of("SUPER", "FUNCIONARIO").contains(role), names.contains("Aprobar solicitudes"));
            assertEquals(!role.equals("GUARDA"), names.contains("Reportes"));
            if ("true".equals(System.getenv("SICA_TUTORIAL_CAPTURE"))) snapshot(view, "rol-" + role.toLowerCase(Locale.ROOT));
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


    @Test void permissionChangesRefreshNavigationEvenWhenTheCurrentPageIsRevoked() throws Exception {
        String email = "revocacion@ui.sica.local";
        sica.saveUser(session, null, new UserForm(email, "Supervisor", "Ui-Supervisor-seguro-2026", true, "SUPERVISOR", null));
        Session supervisor = sica.login(email, "Ui-Supervisor-seguro-2026");
        BackgroundTasks worker = new BackgroundTasks();
        workers.add(worker);
        ShellController controller = new ShellController(sica, supervisor, worker, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml", controller); });
        try {
            navigate(view, "Incidentes");
            changeSupervisorPermission("INCIDENTES", false);
            fx(() -> { button(view, "Actualizar").fire(); return null; });
            awaitIdle(view);
            assertFalse(fx(() -> view.lookup("#status").getStyleClass().contains("error")),
                    fx(() -> ((Label) view.lookup("#status")).getText()));
            assertFalse(fx(() -> view.lookup("#navigation").lookupAll(".button").stream()
                    .map(Button.class::cast).anyMatch(b -> b.getText().equals("Incidentes"))));
            changeSupervisorPermission("VISITAS_LEER", false);
            fx(() -> { button(view, "Actualizar").fire(); return null; });
            awaitIdle(view);
            assertFalse(fx(() -> view.lookup("#status").getStyleClass().contains("error")));
            assertEquals("Notificaciones", fx(() -> ((Label) view.lookup("#heading")).getText()));
            assertFalse(fx(() -> view.lookup("#navigation").lookupAll(".button").stream()
                    .map(Button.class::cast).anyMatch(b -> b.getText().equals("Panel principal"))));
        } finally {
            fx(() -> { controller.close(); return null; });
            changeSupervisorPermission("INCIDENTES", true);
            changeSupervisorPermission("VISITAS_LEER", true);
            sica.logout(supervisor);
        }
    }

    @Test void changingReportWhileAQueryIsRunningEventuallyLoadsTheSelectedReport() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean block = new java.util.concurrent.atomic.AtomicBoolean();
        var delayed = (com.sica.domain.port.in.SicaUseCases) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {com.sica.domain.port.in.SicaUseCases.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("report") && block.compareAndSet(true, false)) {
                        started.countDown();
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Consulta de prueba bloqueada");
                    }
                    try { return method.invoke(sica, arguments); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        BackgroundTasks worker = new BackgroundTasks();
        workers.add(worker);
        ShellController controller = new ShellController(delayed, session, worker, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml", controller); });
        try {
            navigate(view, "Reportes");
            block.set(true);
            fx(() -> { button(view, "Actualizar").fire(); return null; });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            fx(() -> {
                @SuppressWarnings("unchecked") ComboBox<String> reports = (ComboBox<String>) view.lookup("#reportType");
                reports.setValue("Incidentes registrados");
                return null;
            });
            release.countDown();
            awaitIdle(view);
            assertTrue(fx(() -> table(view).getColumns().stream().anyMatch(column -> column.getText().equals("Descripcion"))));
            assertEquals("Incidentes registrados", fx(() -> ((ComboBox<?>) view.lookup("#reportType")).getValue()));
        } finally {
            release.countDown();
            fx(() -> { controller.close(); return null; });
        }
    }

    @Test void asynchronousPhotoKeepsItsImageAndCloseButtonInsideTheDialog() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long company = sica.saveCompany(session, null, new Company("PHOTO-" + suffix, "Empresa fotografía", true));
        String document = "PHOTO-" + suffix;
        sica.savePerson(session, null, new PersonForm(document, "Fotografía de prueba", "INVITADO", company,
                "https://sica-photo-test.invalid/image.png", "HABILITADO"));
        ShellController controller = new ShellController(sica, session, tasks, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml", controller); });
        try {
            navigate(view, "Personas");
            selectRowContaining(view, document);
            fx(() -> { button(view, "Ver fotografía").fire(); return null; });
            await(() -> fx(() -> dialog("Fotografía de identificación") != null));
            fx(() -> {
                DialogPane pane = dialog("Fotografía de identificación");
                javafx.scene.image.ImageView image = (javafx.scene.image.ImageView) pane.lookup("#identity-photo");
                assertNotNull(image);
                assertTrue(image.isPreserveRatio());
                assertTrue(image.getParent().getLayoutBounds().getHeight() >= 400);
                var close = pane.lookupButton(ButtonType.OK);
                var bounds = close.localToScene(close.getBoundsInLocal());
                assertTrue(bounds.getMaxY() <= pane.getScene().getHeight(), "El botón debe permanecer dentro de la ventana");
                ((Button) close).fire();
                return null;
            });
        } finally {
            fx(() -> { controller.close(); return null; });
        }
    }

    private void changeSupervisorPermission(String permission, boolean grant) throws SQLException {
        String sql = grant
                ? "INSERT IGNORE INTO rol_permisos SELECT r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='SUPERVISOR' AND p.codigo=?"
                : "DELETE FROM rol_permisos WHERE rol_id=(SELECT id FROM roles WHERE codigo='SUPERVISOR') AND permiso_id=(SELECT id FROM permisos WHERE codigo=?)";
        try (var connection = DriverManager.getConnection(databaseUrl, dbUser, dbPassword);
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, permission);
            statement.executeUpdate();
        }
    }

    @Test void companiesPeopleAndUsersCanBeCreatedEditedAndArchivedThroughForms() throws Exception {
        ShellController controller = new ShellController(sica, session, tasks, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml", controller); });
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try {
            navigate(view, "Empresas");
            fx(() -> { button(view, "Nuevo").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> {
                text(form(), "nit", "CRUD-" + suffix);
                text(form(), "name", "Empresa formularios " + suffix);
                text(form(), "contact", "Contacto original");
                button(form(), "Guardar").fire();
                return null;
            });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            long company = selectRowContaining(view, "CRUD-" + suffix);
            fx(() -> { button(view, "Editar").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> { text(form(), "contact", "Contacto actualizado"); button(form(), "Guardar").fire(); return null; });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            assertTrue(fx(() -> rows(view).stream().anyMatch(row -> row.contains("Contacto actualizado"))));

            navigate(view, "Personas");
            fx(() -> { button(view, "Nuevo").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> {
                text(form(), "doc", "CRUD-" + suffix);
                text(form(), "name", "Persona original " + suffix);
                selectChoice(form(), "#company", " · " + company);
                button(form(), "Guardar").fire();
                return null;
            });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            selectRowContaining(view, "CRUD-" + suffix);
            fx(() -> { button(view, "Editar").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> {
                text(form(), "name", "Persona editada " + suffix);
                selectChoice(form(), "#state", "RESTRINGIDO");
                button(form(), "Guardar").fire();
                return null;
            });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            assertTrue(fx(() -> rows(view).stream().anyMatch(row -> row.contains("Persona editada " + suffix) && row.contains("RESTRINGIDO"))));
            selectRowContaining(view, "CRUD-" + suffix);
            archiveSelected(view);
            assertTrue(fx(() -> rows(view).stream().anyMatch(row -> row.contains("CRUD-" + suffix) && row.contains("BAJA"))));
            selectRowContaining(view, "CRUD-" + suffix);
            fx(() -> { button(view,"Editar").fire(); return null; });
            await(() -> fx(() -> form()!=null));
            assertEquals("BAJA",fx(() -> ((ComboBox<?>)form().lookup("#state")).getValue()));
            fx(() -> { text(form(),"name","Persona de baja editada"); button(form(),"Guardar").fire(); return null; });
            await(() -> fx(() -> form()==null));
            awaitIdle(view);
            assertTrue(fx(() -> rows(view).stream().anyMatch(row -> row.contains("CRUD-" + suffix) && row.contains("BAJA"))));

            navigate(view, "Usuarios");
            String email = "crud-" + suffix + "@ui.sica.local";
            fx(() -> { button(view, "Nuevo").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> {
                text(form(), "email", email);
                text(form(), "name", "Usuario formularios");
                text(form(), "password", "Ui-Crud-seguro-2026");
                button(form(), "Guardar").fire();
                return null;
            });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            selectRowContaining(view, email);
            fx(() -> { button(view, "Editar").fire(); return null; });
            await(() -> fx(() -> form() != null));
            fx(() -> { text(form(), "name", "Usuario editado"); button(form(), "Guardar").fire(); return null; });
            await(() -> fx(() -> form() == null));
            awaitIdle(view);
            Session created = sica.login(email, "Ui-Crud-seguro-2026");
            sica.logout(created);
            selectRowContaining(view, email);
            archiveSelected(view);
            assertThrows(SicaException.class, () -> sica.login(email, "Ui-Crud-seguro-2026"));
            navigate(view, "Empresas");
            selectRowContaining(view, "CRUD-" + suffix);
            archiveSelected(view);
            assertFalse(sica.companies(session).stream().anyMatch(option -> option.id() == company));
        } finally {
            fx(() -> { controller.close(); return null; });
        }
    }

    private static void text(Parent root, String id, String value) {
        ((TextInputControl) root.lookup("#" + id)).setText(value);
    }

    private static long selectRowContaining(Parent view, String value) throws Exception {
        awaitIdle(view);
        return fx(() -> {
            List<String> row = rows(view).stream().filter(candidate -> candidate.contains(value)).findFirst().orElseThrow();
            table(view).getSelectionModel().select(row);
            return Long.parseLong(row.getFirst());
        });
    }

    private static void archiveSelected(Parent view) throws Exception {
        Platform.runLater(() -> button(view, "Dar de baja").fire());
        await(() -> fx(() -> dialog("Dar de baja") != null));
        fx(() -> { ((Button) dialog("Dar de baja").lookupButton(ButtonType.OK)).fire(); return null; });
        awaitIdle(view);
        assertFalse(fx(() -> view.lookup("#status").getStyleClass().contains("error")));
    }

    @Test void informationButtonPlaysSeeksPausesAndDisposesTheEmbeddedTutorial() throws Exception {
        ShellController controller = new ShellController(sica, session, tasks, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml", controller); });
        try {
            awaitIdle(view);
            fx(() -> { ((Button) view.lookup("#helpButton")).fire(); return null; });
            await(() -> fx(() -> tutorialRoot() != null));
            Parent tutorial = fx(UiRenderingTest::tutorialRoot);
            await(() -> fx(() -> {
                var player = ((javafx.scene.media.MediaView) tutorial.lookup("#tutorialVideo")).getMediaPlayer();
                return player != null && (player.getStatus() == javafx.scene.media.MediaPlayer.Status.READY || player.getError() != null);
            }));
            var player = fx(() -> ((javafx.scene.media.MediaView) tutorial.lookup("#tutorialVideo")).getMediaPlayer());
            assertNull(fx(player::getError));
            assertTrue(fx(() -> player.getTotalDuration().toSeconds()) > 880);
            assertEquals(25, fx(() -> ((ListView<?>) tutorial.lookup("#chapters")).getItems().size()));
            fx(() -> {
                ((Slider) tutorial.lookup("#volume")).setValue(0);
                ((Button) tutorial.lookup("#playPause")).fire();
                return null;
            });
            await(() -> fx(() -> player.getCurrentTime().toSeconds() > .5));
            assertTrue(fx(() -> player.getMedia().getTracks().stream().anyMatch(javafx.scene.media.VideoTrack.class::isInstance)));
            fx(() -> { ((ListView<?>) tutorial.lookup("#chapters")).getSelectionModel().select(13); return null; });
            await(() -> fx(() -> player.getCurrentTime().toSeconds() >= 458));
            fx(() -> { ((Button) tutorial.lookup("#playPause")).fire(); return null; });
            await(() -> fx(() -> player.getStatus() == javafx.scene.media.MediaPlayer.Status.PAUSED));
            double paused = fx(() -> player.getCurrentTime().toSeconds());
            Thread.sleep(300);
            assertEquals(paused, fx(() -> player.getCurrentTime().toSeconds()), .15);
            fx(() -> {
                @SuppressWarnings("unchecked") ComboBox<Double> rates = (ComboBox<Double>) tutorial.lookup("#speed");
                rates.setValue(1.5);
                return null;
            });
            assertEquals(1.5, fx(player::getRate));
            snapshot(tutorial, "videotutorial");
            fx(() -> { ((Button) view.lookup("#helpButton")).fire(); return null; });
            assertEquals(1, fx(() -> Window.getWindows().stream().filter(window -> window.getScene().lookup("#tutorialVideo") != null).count()));
            fx(() -> { ((Button) tutorial.lookup("#tutorialClose")).fire(); return null; });
            await(() -> fx(() -> player.getStatus() == javafx.scene.media.MediaPlayer.Status.DISPOSED));
            fx(() -> { ((Button) view.lookup("#helpButton")).fire(); return null; });
            await(() -> fx(() -> tutorialRoot() != null));
            fx(() -> { controller.close(); return null; });
            assertNull(fx(UiRenderingTest::tutorialRoot));
        } finally {
            fx(() -> { controller.close(); return null; });
        }
    }

    private static Parent tutorialRoot() {
        return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(root -> root.lookup("#tutorialVideo") != null).findFirst().orElse(null);
    }

    @Test void aBlockedKeychainReadCannotDelayManualAuthentication() throws Exception {
        MemoryLogins memory = new MemoryLogins();
        memory.readStarted = new CountDownLatch(1);
        memory.releaseRead = new CountDownLatch(1);
        AtomicReference<Session> authenticated = new AtomicReference<>();
        Parent view = loginView(memory, authenticated, ignored -> {});
        assertTrue(memory.readStarted.await(5, TimeUnit.SECONDS));
        long started = System.nanoTime();
        try {
            fx(() -> { fillLogin(view, adminEmail, adminPassword); ((Button) view.lookup("#submit")).fire(); return null; });
            await(() -> authenticated.get() != null);
            assertTrue(System.nanoTime()-started < TimeUnit.SECONDS.toNanos(3), "El acceso manual no debe esperar la lectura del llavero");
        } finally {
            memory.releaseRead.countDown();
            if (authenticated.get() != null) sica.logout(authenticated.get());
        }
    }

    @Test void editingFormsKeepTheExistingInactiveCompanySelected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0,8);
        long company = sica.saveCompany(session, null, new Company("INACT-"+suffix,"Empresa inactiva de edición",true));
        String document = "INACT-"+suffix;
        sica.savePerson(session, null, new PersonForm(document,"Trabajador con empresa inactiva","TRABAJADOR",company,"","HABILITADO"));
        String email = "inact-"+suffix+"@ui.sica.local";
        sica.saveUser(session,null,new UserForm(email,"Funcionario con empresa inactiva","Ui-Usuario-seguro-2026",true,"FUNCIONARIO",company));
        sica.archive(session,Entity.EMPRESAS,company);
        ShellController controller = new ShellController(sica, session, tasks, () -> {});
        Parent view = fx(() -> { controllers.add(controller); return load("/fxml/shell.fxml",controller); });
        try {
            for (String module : List.of("Personas","Usuarios")) {
                navigate(view,module);
                selectRowContaining(view,module.equals("Personas")?document:email);
                fx(() -> { button(view,"Editar").fire(); return null; });
                await(() -> fx(() -> form()!=null));
                assertEquals("Empresa inactiva · "+company,fx(() -> ((ComboBox<?>) form().lookup("#company")).getValue()));
                fx(() -> { text(form(),"name","Datos corregidos sin traslado"); button(form(),"Guardar").fire(); return null; });
                await(() -> fx(() -> form()==null));
                awaitIdle(view);
            }
            Data people = sica.report(session,Report.PERSONAS,document,null,null);
            assertEquals(Long.toString(company),people.rows().getFirst().get(4));
            Data users = sica.report(session,Report.USUARIOS,email,null,null);
            assertEquals(Long.toString(company),users.rows().getFirst().get(5));
        } finally { fx(() -> { controller.close(); return null; }); }
    }

    @Test void keychainWriteTimeoutStillOpensExactlyOneAuthenticatedSession() throws Exception {
        CountDownLatch release=new CountDownLatch(1);
        var logins=new SavedLoginStore() {
            public boolean available(){return true;}
            public Optional<Login> load(){return Optional.empty();}
            public void save(Login login){
                boolean done=false;
                while(!done) try {done=release.await(10,TimeUnit.SECONDS);} catch(InterruptedException interrupted) { done=release.getCount()==0; }
            }
            public void clear(){}
        };
        AtomicReference<Session> authenticated=new AtomicReference<>();
        AtomicReference<String> warning=new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger callbacks=new java.util.concurrent.atomic.AtomicInteger();
        Parent view=fx(()->{
            var controller=new LoginController(sica,tasks,logins,result->{authenticated.set(result);callbacks.incrementAndGet();},warning::set);
            loginControllers.add(controller);return load("/fxml/login.fxml",controller);
        });
        try {
            await(()->fx(()->!view.lookup("#remember").isDisabled()));
            fx(()->{fillLogin(view,adminEmail,adminPassword);((CheckBox)view.lookup("#remember")).setSelected(true);((Button)view.lookup("#submit")).fire();return null;});
            await(()->authenticated.get()!=null && warning.get()!=null);
            assertTrue(warning.get().contains("confirmar el guardado"));
            release.countDown();
            Thread.sleep(250);
            assertEquals(1,callbacks.get());
        } finally {release.countDown();if(authenticated.get()!=null)sica.logout(authenticated.get());}
    }

    private static final class MemoryLogins implements SavedLoginStore {
        private volatile Login stored;
        private boolean failSave;
        private CountDownLatch readStarted;
        private CountDownLatch releaseRead;
        private int saves;

        public boolean available() { return true; }
        public Optional<Login> load() {
            if (readStarted != null) {
                readStarted.countDown();
                try {
                    if (!releaseRead.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Prueba de lectura bloqueada.");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
            return Optional.ofNullable(stored);
        }
        public void save(Login login) {
            if (failSave) throw new SicaException("Llavero bloqueado en prueba.");
            saves++;
            stored = login;
        }
        public void clear() { stored = null; }
    }

    private Parent loginView(MemoryLogins memory, AtomicReference<Session> authenticated, java.util.function.Consumer<String> warning) throws Exception {
        return fx(() -> {
            LoginController controller = new LoginController(sica, tasks, memory, authenticated::set, warning);
            loginControllers.add(controller);
            return load("/fxml/login.fxml", controller);
        });
    }

    private static void fillLogin(Parent view, String email, String password) {
        ((TextField) view.lookup("#email")).setText(email);
        ((PasswordField) view.lookup("#password")).setText(password);
    }

    @Test void rememberedCredentialsAutofillCanBeRevealedAndCanBeForgotten() throws Exception {
        MemoryLogins memory = new MemoryLogins();
        AtomicReference<Session> authenticated = new AtomicReference<>();
        Parent first = loginView(memory, authenticated, warning -> fail(warning));
        await(() -> fx(() -> !first.lookup("#remember").isDisabled()));
        fx(() -> {
            fillLogin(first, adminEmail, adminPassword);
            ((CheckBox) first.lookup("#remember")).setSelected(true);
            ((Button) first.lookup("#submit")).fire();
            return null;
        });
        await(() -> authenticated.get() != null);
        assertEquals(new SavedLoginStore.Login(adminEmail, adminPassword), memory.stored);
        assertEquals(1, memory.saves);
        sica.logout(authenticated.get());

        Parent second = loginView(memory, new AtomicReference<>(), warning -> fail(warning));
        await(() -> fx(() -> ((CheckBox) second.lookup("#remember")).isSelected()));
        fx(() -> {
            assertEquals(adminEmail, ((TextField) second.lookup("#email")).getText());
            assertEquals(adminPassword, ((PasswordField) second.lookup("#password")).getText());
            ((Button) second.lookup("#visibilityToggle")).fire();
            assertTrue(second.lookup("#visiblePassword").isVisible());
            assertEquals(adminPassword, ((TextField) second.lookup("#visiblePassword")).getText());
            ((Button) second.lookup("#visibilityToggle")).fire();
            assertFalse(second.lookup("#visiblePassword").isVisible());
            return null;
        });
        snapshot(second, "login-recordado");
        fx(() -> { ((CheckBox) second.lookup("#remember")).fire(); return null; });
        await(() -> fx(() -> !second.lookup("#submit").isDisabled()) && memory.stored == null);
        fx(() -> {
            assertEquals(adminEmail, ((TextField) second.lookup("#email")).getText());
            assertFalse(((CheckBox) second.lookup("#remember")).isSelected());
            return null;
        });
        memory.stored = new SavedLoginStore.Login(adminEmail, adminPassword);
        Parent third = loginView(memory, new AtomicReference<>(), warning -> fail(warning));
        await(() -> fx(() -> third.lookup("#forget").isVisible()));
        fx(() -> { ((Button) third.lookup("#forget")).fire(); return null; });
        await(() -> fx(() -> !third.lookup("#submit").isDisabled()) && memory.stored == null);
        fx(() -> {
            assertEquals("", ((TextField) third.lookup("#email")).getText());
            assertEquals("", ((PasswordField) third.lookup("#password")).getText());
            return null;
        });
    }

    @Test void rejectedLoginNeverSavesTheEnteredPassword() throws Exception {
        MemoryLogins memory = new MemoryLogins();
        Parent view = loginView(memory, new AtomicReference<>(), warning -> fail(warning));
        await(() -> fx(() -> !view.lookup("#remember").isDisabled()));
        fx(() -> {
            fillLogin(view, adminEmail, "Contraseña-incorrecta-2026");
            ((CheckBox) view.lookup("#remember")).setSelected(true);
            ((Button) view.lookup("#submit")).fire();
            return null;
        });
        await(() -> fx(() -> !view.lookup("#submit").isDisabled()));
        assertNull(memory.stored);
        assertEquals(0, memory.saves);
        fx(() -> {
            assertTrue(((Label) view.lookup("#message")).getText().contains("Credenciales incorrectas"));
            assertTrue(((PasswordField) view.lookup("#password")).getText().isEmpty());
            return null;
        });
    }

    @Test void deniedCredentialStorageStillAllowsAnAuthenticatedSessionAndShowsAWarning() throws Exception {
        MemoryLogins memory = new MemoryLogins();
        memory.failSave = true;
        AtomicReference<Session> authenticated = new AtomicReference<>();
        AtomicReference<String> warning = new AtomicReference<>();
        Parent view = loginView(memory, authenticated, warning::set);
        await(() -> fx(() -> !view.lookup("#remember").isDisabled()));
        fx(() -> {
            fillLogin(view, adminEmail, adminPassword);
            ((CheckBox) view.lookup("#remember")).setSelected(true);
            ((Button) view.lookup("#submit")).fire();
            return null;
        });
        await(() -> warning.get() != null);
        assertNotNull(authenticated.get());
        assertTrue(warning.get().contains("No se pudo confirmar el guardado"));
        assertNull(memory.stored);
        sica.logout(authenticated.get());
    }

    @Test void aLateCredentialReadNeverOverwritesWhatTheUserHasAlreadyTyped() throws Exception {
        MemoryLogins memory = new MemoryLogins();
        memory.stored = new SavedLoginStore.Login(adminEmail, adminPassword);
        memory.readStarted = new CountDownLatch(1);
        memory.releaseRead = new CountDownLatch(1);
        Parent view = loginView(memory, new AtomicReference<>(), warning -> fail(warning));
        assertTrue(memory.readStarted.await(10, TimeUnit.SECONDS));
        try {
            fx(() -> { fillLogin(view, "escrito@acme.local", "Escrito-manualmente-2026"); return null; });
        } finally {
            memory.releaseRead.countDown();
        }
        await(() -> fx(() -> !view.lookup("#remember").isDisabled()));
        fx(() -> {
            assertEquals("escrito@acme.local", ((TextField) view.lookup("#email")).getText());
            assertEquals("Escrito-manualmente-2026", ((PasswordField) view.lookup("#password")).getText());
            assertFalse(((CheckBox) view.lookup("#remember")).isSelected());
            return null;
        });
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
        assertTrue(width >= 400 && height >= (root instanceof DialogPane ? 200 : 300));
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
