package com.sica;

import com.sica.domain.Session;

import com.sica.application.*;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import com.sica.infrastructure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SICA_TEST_DB_PASSWORD", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySqlIntegrationTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("America/Bogota"));
    private final String database = "sica_it_" + UUID.randomUUID().toString().replace("-", "");
    private String rootUrl;
    private String url;
    private String user;
    private String password;
    private Sica sica;
    private Session admin;
    private Store store;

    @BeforeAll void install() throws Exception {
        rootUrl = System.getenv().getOrDefault("SICA_TEST_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/?connectionTimeZone=America/Bogota&connectTimeout=5000&socketTimeout=15000");
        user = System.getenv().getOrDefault("SICA_TEST_DB_USER", "root");
        password = System.getenv("SICA_TEST_DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(rootUrl, user, password);
             Statement statement = connection.createStatement()) {
            for (String resource : List.of("/db/schema.sql", "/db/data.sql")) {
                try (var input = getClass().getResourceAsStream(resource)) {
                    assertNotNull(input);
                    String script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("sica_acme", database);
                    for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
                }
            }
        }
        int separator = rootUrl.indexOf('?');
        url = rootUrl.substring(0, separator < 0 ? rootUrl.length() : separator) + database +
                (separator < 0 ? "" : rootUrl.substring(separator));
        store = new JdbcStore(url, user, password);
        sica = new Sica(store, new BcryptHasher(), clock);
        sica.bootstrap("admin@acme.co", "Admin-test-12345");
        admin = sica.login("admin@acme.co", "Admin-test-12345");
    }

    @AfterAll void cleanup() throws Exception {
        if (rootUrl == null) return;
        try (Connection connection = DriverManager.getConnection(rootUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
        }
    }

    private record Fixture(long company, long person) {}

    private Fixture fixture(String type) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long company = sica.saveCompany(admin, null, new Company("NIT-" + suffix, "Empresa " + suffix, true));
        long person = sica.savePerson(admin, null,
                new PersonForm("DOC-" + suffix, "Persona " + suffix, type, company, "", "HABILITADO"));
        return new Fixture(company, person);
    }

    private long request(Fixture fixture, AccessPolicy policy) {
        return sica.request(admin, fixture.person(), fixture.company(), policy, now.minusMinutes(1), now.plusHours(2));
    }

    private Session official(long company) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = suffix + "@acme.co";
        long id = sica.saveUser(admin, null, new UserForm(email, "Funcionario", "Funcionario-12345", true, "FUNCIONARIO", company));
        return sica.login(email, "Funcionario-12345");
    }

    private String scalar(String sql, Object... params) throws SQLException {
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            try (var rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private void sql(String query) throws SQLException {
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    @Test void preRegisteredGuestEntersAndLeavesWithAudit() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.PREVIA);
        assertEquals("APROBADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        new VisitCommand.CheckIn(visit).execute(sica, admin);
        assertEquals("DENTRO", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        new VisitCommand.CheckOut(visit).execute(sica, admin);
        assertEquals("CERRADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        assertThrows(SicaException.class, () -> sica.checkOut(admin, visit));
        assertEquals("3", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE tabla_afectada='visitas' AND registro_id_afectado=? AND accion_realizada IN ('APROBAR','CHECK_IN','CHECK_OUT')", visit));
    }

    @Test void unannouncedGuestNeedsDecisionByCompanyOfficial() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.NO_ANUNCIADA);
        assertThrows(SicaException.class, () -> sica.checkIn(admin, visit));
        Session official = official(f.company());
        sica.decide(official, visit, true, "Identidad y motivo de visita verificados");
        sica.checkIn(admin, visit);
        assertEquals("DENTRO", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        assertEquals(Long.toString(official.userId()), scalar("SELECT visita_aprobada_por FROM visitas WHERE id=?", visit));
        assertThrows(SicaException.class, () -> sica.decide(official, visit, true, "Identidad y motivo de visita verificados"));
    }

    @Test void rejectedAndExpiredRequestsCannotEnterOrBeReapproved() {
        Fixture f = fixture("INVITADO");
        long rejected = request(f, AccessPolicy.NO_ANUNCIADA);
        sica.decide(admin, rejected, false, "Identidad y motivo de visita verificados");
        assertThrows(SicaException.class, () -> sica.checkIn(admin, rejected));
        assertThrows(SicaException.class, () -> sica.decide(admin, rejected, true, "Identidad y motivo de visita verificados"));
        long pending = request(f, AccessPolicy.NO_ANUNCIADA);
        Sica nextDay = new Sica(store, new BcryptHasher(), Clock.offset(clock, Duration.ofDays(1)));
        Session nextSession = nextDay.login("admin@acme.co", "Admin-test-12345");
        assertThrows(SicaException.class, () -> nextDay.decide(nextSession, pending, true, "Identidad y motivo de visita verificados"));
        long approved = request(f, AccessPolicy.PREVIA);
        assertThrows(SicaException.class, () -> nextDay.checkIn(nextSession, approved));
    }

    @Test void workerWithoutBadgeCanOnlyEnterApprovedOnTheSameDay() {
        Fixture f = fixture("TRABAJADOR");
        long visit = request(f, AccessPolicy.SIN_CARNET);
        assertThrows(SicaException.class, () -> sica.checkIn(admin, visit));
        sica.decide(official(f.company()), visit, true, "Identidad y motivo de visita verificados");
        sica.checkIn(admin, visit);
        assertThrows(SicaException.class, () -> sica.request(admin, f.person(), f.company(), AccessPolicy.SIN_CARNET, now, now.plusDays(1)));
    }

    @Test void forgottenExitIsClosedAtomicallyAndNewVisitIsTheOnlyOpenOne() throws Exception {
        Fixture f = fixture("INVITADO");
        long oldVisit = request(f, AccessPolicy.PREVIA);
        long newVisit = request(f, AccessPolicy.PREVIA);
        sica.checkIn(admin, oldVisit);
        sica.checkIn(admin, newVisit);
        assertEquals("SALIDA_OLVIDADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", oldVisit));
        assertNotNull(scalar("SELECT fecha_salida FROM visitas WHERE id=?", oldVisit));
        assertEquals("1", scalar("SELECT COUNT(*) FROM visitas WHERE persona_dentro=?", f.person()));
        assertEquals("DENTRO", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", newVisit));
        assertEquals("1", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE registro_id_afectado=? AND accion_realizada='SALIDA_OLVIDADA'", oldVisit));
    }

    @Test void concurrentSameVisitCheckInHasExactlyOneSuccess() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.PREVIA);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> action = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                sica.checkIn(admin, visit);
                return true;
            } catch (SicaException failure) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(action);
            Future<Boolean> second = executor.submit(action);
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("1", scalar("SELECT COUNT(*) FROM visitas WHERE persona_dentro=?", f.person()));
        assertEquals("1", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE registro_id_afectado=? AND accion_realizada='CHECK_IN'", visit));
    }

    @Test void auditFailureRollsBackForgottenExitAndNewEntry() throws Exception {
        Fixture f = fixture("INVITADO");
        long oldVisit = request(f, AccessPolicy.PREVIA);
        long newVisit = request(f, AccessPolicy.PREVIA);
        sica.checkIn(admin, oldVisit);
        sql("CREATE TRIGGER audit_test_fail BEFORE INSERT ON bitacora_auditoria FOR EACH ROW " +
                "BEGIN IF NEW.accion_realizada='CHECK_IN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fallo de auditoria de prueba'; END IF; END");
        try {
            SicaException failure = assertThrows(SicaException.class, () -> sica.checkIn(admin, newVisit));
            assertTrue(failure.getMessage().contains("auditoría"));
        } finally {
            sql("DROP TRIGGER audit_test_fail");
        }
        assertEquals("DENTRO", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", oldVisit));
        assertEquals("APROBADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", newVisit));
        assertEquals("0", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE registro_id_afectado=? AND accion_realizada='SALIDA_OLVIDADA'", oldVisit));
    }

    @Test void auditIsAppendOnlyAtDatabaseLevel() {
        assertThrows(SQLException.class, () -> sql("UPDATE bitacora_auditoria SET detalles='cambio' LIMIT 1"));
        assertThrows(SQLException.class, () -> sql("DELETE FROM bitacora_auditoria LIMIT 1"));
    }

    @Test void companyScopePreventsCrossCompanyDecisionsReportsAndPersonCreation() {
        Fixture own = fixture("INVITADO");
        Fixture other = fixture("INVITADO");
        Session official = official(own.company());
        long ownVisit = request(own, AccessPolicy.NO_ANUNCIADA);
        long otherVisit = request(other, AccessPolicy.NO_ANUNCIADA);
        assertThrows(SicaException.class, () -> sica.decide(official, otherVisit, true, "Identidad y motivo de visita verificados"));
        assertThrows(SicaException.class, () -> sica.request(official, other.person(), other.company(), AccessPolicy.PREVIA, now, now.plusHours(1)));
        assertThrows(SicaException.class, () -> sica.savePerson(official, null,
                new PersonForm("CROSS", "Invitado", "INVITADO", other.company(), "", "HABILITADO")));
        Data visits = sica.report(official, Report.VISITAS, "", null, null);
        assertTrue(visits.rows().stream().allMatch(row -> row.get(3).equals(Long.toString(own.company()))));
        assertTrue(visits.rows().stream().anyMatch(row -> row.getFirst().equals(Long.toString(ownVisit))));
        assertEquals(List.of(own.company()), sica.companies(official).stream().map(Option::id).toList());
    }

    @Test void crudDuplicatesInactivePeopleAndIncidentsAreValidated() throws Exception {
        Fixture f = fixture("INVITADO");
        String doc = scalar("SELECT documento_identidad FROM personas WHERE id=?", f.person());
        assertThrows(SicaException.class, () -> sica.savePerson(admin, null,
                new PersonForm(doc, "Duplicado", "INVITADO", f.company(), "", "HABILITADO")));
        sica.savePerson(admin, f.person(), new PersonForm(doc, "Nombre actualizado", "INVITADO", f.company(), "", "RESTRINGIDO"));
        assertThrows(SicaException.class, () -> request(f, AccessPolicy.PREVIA));
        sica.savePerson(admin, f.person(), new PersonForm(doc, "Nombre actualizado", "INVITADO", f.company(), "", "HABILITADO"));
        long visit = request(f, AccessPolicy.PREVIA);
        long incident = sica.incident(admin, visit, "Documento deteriorado, se verificó identidad.");
        assertEquals(Long.toString(admin.userId()), scalar("SELECT reportado_por_id FROM incidentes WHERE id=?", incident));
        assertThrows(SicaException.class, () -> sica.incident(admin, visit, ""));
        assertThrows(SicaException.class, () -> sica.incident(admin, 9999999, "Texto"));
        sica.archive(admin, Entity.PERSONAS, f.person());
        assertThrows(SicaException.class, () -> sica.checkIn(admin, visit));
        assertEquals("BAJA", scalar("SELECT codigo FROM persona_estados_acceso WHERE id=(SELECT estado_acceso_id FROM personas WHERE id=?)", f.person()));
    }

    @Test void dynamicPermissionRevocationAndInactiveUsersInvalidateExistingSessions() throws Exception {
        String email = UUID.randomUUID().toString().substring(0, 8) + "@acme.co";
        long id = sica.saveUser(admin, null, new UserForm(email, "Guarda", "Guarda-seguro-123", true, "GUARDA", null));
        Session guard = sica.login(email, "Guarda-seguro-123");
        assertTrue(sica.permissions(guard).contains("ACCESO"));
        sql("DELETE FROM rol_permisos WHERE rol_id=(SELECT id FROM roles WHERE codigo='GUARDA') AND permiso_id=(SELECT id FROM permisos WHERE codigo='ACCESO')");
        try {
            assertFalse(sica.permissions(guard).contains("ACCESO"));
            assertThrows(SicaException.class, () -> sica.checkIn(guard, 1));
        } finally {
            sql("INSERT INTO rol_permisos SELECT r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='GUARDA' AND p.codigo='ACCESO'");
        }
        sica.saveUser(admin, id, new UserForm(email, "Guarda", "", false, "GUARDA", null));
        assertThrows(SicaException.class, () -> sica.login(email, "Guarda-seguro-123"));
        assertThrows(SicaException.class, () -> sica.permissions(guard));
    }

    @Test void reportsApplySearchDateAndDecisionFiltersWithoutSqlInjection() {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.NO_ANUNCIADA);
        sica.decide(admin, visit, false, "Identidad y motivo de visita verificados");
        Data decisions = sica.report(admin, Report.DECISIONES, Long.toString(visit), now.toLocalDate().atStartOfDay(), now.plusDays(1));
        assertTrue(decisions.rows().stream().anyMatch(row -> row.getFirst().equals(Long.toString(visit)) && row.get(6).equals("RECHAZADA")));
        assertTrue(sica.report(admin, Report.VISITAS, "' OR 1=1 --", null, null).rows().isEmpty());
        assertTrue(sica.report(admin, Report.VISITAS, "", now.plusDays(3), now.plusDays(4)).rows().isEmpty());
    }

    @Test void personCompanyAndPhotoValidationAndNotFoundErrors() {
        Fixture f = fixture("INVITADO");
        assertThrows(SicaException.class, () -> sica.savePerson(admin, null,
                new PersonForm("123", "Trabajador", "TRABAJADOR", null, "", "HABILITADO")));
        assertThrows(SicaException.class, () -> sica.savePerson(admin, null,
                new PersonForm("123", "Invitado", "INVITADO", f.company(), "file:///etc/passwd", "HABILITADO")));
        assertThrows(SicaException.class, () -> sica.request(admin, 9999999, f.company(), AccessPolicy.PREVIA, now, now.plusHours(1)));
        assertThrows(SicaException.class, () -> sica.checkIn(admin, 9999999));
        sica.archive(admin, Entity.EMPRESAS, f.company());
        assertThrows(SicaException.class, () -> request(f, AccessPolicy.PREVIA));
        assertThrows(SicaException.class, () -> sica.archive(admin, Entity.USUARIOS, admin.userId()));
    }
    @Test void decisionsRequireAndPreserveTheirReasonInVisitsReportsAndAudit() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.NO_ANUNCIADA);
        assertThrows(SicaException.class, () -> sica.decide(admin, visit, true, " "));
        assertEquals("PENDIENTE", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        String reason = "Ingreso a reunión con el área de logística. Documento verificado.";
        sica.decide(admin, visit, true, reason);
        assertEquals(reason, scalar("SELECT motivo_decision FROM visitas WHERE id=?", visit));
        assertEquals(reason, scalar("SELECT detalles FROM bitacora_auditoria WHERE accion_realizada='APROBAR' AND registro_id_afectado=? AND tabla_afectada='visitas'", visit));
        Data decisions = sica.report(admin, Report.DECISIONES, Long.toString(visit), null, null);
        int reasonColumn = decisions.columns().indexOf("Motivo");
        assertTrue(decisions.rows().stream().anyMatch(row ->
                row.getFirst().equals(Long.toString(visit)) && row.get(reasonColumn).equals(reason)));
    }

    @Test void notificationsPersistAcrossInstancesAndArePrivateAndIdempotentlyRead() throws Exception {
        Fixture own = fixture("INVITADO");
        Fixture other = fixture("INVITADO");
        Session official = official(own.company());
        Session another = official(other.company());
        long visit = request(own, AccessPolicy.NO_ANUNCIADA);
        assertEquals(1, sica.unreadNotifications(official));
        assertEquals(0, sica.unreadNotifications(another));
        Sica second = new Sica(new JdbcStore(url, user, password), new BcryptHasher(), clock);
        Session secondOfficial = second.login(scalar("SELECT email FROM usuarios WHERE id=?", official.userId()), "Funcionario-12345");
        Data inbox = second.report(secondOfficial, Report.NOTIFICACIONES, "", null, null);
        assertEquals(1, inbox.rows().size());
        long notification = Long.parseLong(inbox.rows().getFirst().getFirst());
        assertEquals(Long.toString(visit), inbox.rows().getFirst().get(1));
        assertThrows(SicaException.class, () -> sica.markNotificationRead(another, notification));
        second.markNotificationRead(secondOfficial, notification);
        sica.markNotificationRead(official, notification);
        assertEquals(0, sica.unreadNotifications(official));
        assertEquals("1", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE accion_realizada='LEER_NOTIFICACION' AND registro_id_afectado=?", notification));
        second.decide(secondOfficial, visit, false, "El anfitrión no tiene disponibilidad.");
        Data requesterInbox = sica.report(admin, Report.NOTIFICACIONES, "rechazada", null, null);
        assertTrue(requesterInbox.rows().stream().anyMatch(row -> row.get(1).equals(Long.toString(visit))));
        assertEquals("RECHAZADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        second.logout(secondOfficial);
    }

    @Test void notificationFailureRollsBackRequestAndAudit() throws Exception {
        Fixture f = fixture("INVITADO");
        sql("CREATE TRIGGER notification_test_fail BEFORE INSERT ON notificaciones FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fallo de notificacion de prueba'");
        try {
            assertThrows(SicaException.class, () -> request(f, AccessPolicy.NO_ANUNCIADA));
        } finally {
            sql("DROP TRIGGER notification_test_fail");
        }
        assertEquals("0", scalar("SELECT COUNT(*) FROM visitas WHERE persona_id=?", f.person()));
    }

    @Test void qrIsPrivateSingleUseAndCanBeConsumedByAnotherInstance() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.PREVIA);
        Credential credential = sica.issueCredential(admin, visit);
        assertEquals(CredentialCodes.digest(credential.code()), scalar("SELECT qr_hash FROM visitas WHERE id=?", visit));
        assertFalse(sica.report(admin, Report.VISITAS, "", null, null).columns().contains("qr_hash"));
        Sica second = new Sica(new JdbcStore(url, user, password), new BcryptHasher(), clock);
        Session secondAdmin = second.login("admin@acme.co", "Admin-test-12345");
        second.checkInWithCredential(secondAdmin, credential.code());
        assertEquals("DENTRO", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
        assertNull(scalar("SELECT qr_hash FROM visitas WHERE id=?", visit));
        assertThrows(SicaException.class, () -> sica.checkInWithCredential(admin, credential.code()));
        assertEquals("0", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE detalles LIKE ?", "%" + credential.code() + "%"));
        second.logout(secondAdmin);
    }

    @Test void qrRotationExpirationScopeAndRestrictionsAreEnforced() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.PREVIA);
        Credential first = sica.issueCredential(admin, visit);
        Credential second = sica.issueCredential(admin, visit);
        assertThrows(SicaException.class, () -> sica.checkInWithCredential(admin, first.code()));
        Fixture other = fixture("INVITADO");
        assertThrows(SicaException.class, () -> sica.issueCredential(official(other.company()), visit));
        Sica nextDay = new Sica(store, new BcryptHasher(), Clock.offset(clock, Duration.ofDays(1)));
        Session nextAdmin = nextDay.login("admin@acme.co", "Admin-test-12345");
        assertThrows(SicaException.class, () -> nextDay.checkInWithCredential(nextAdmin, second.code()));
        assertThrows(SicaException.class, () -> nextDay.issueCredential(nextAdmin, visit));
        sica.archive(admin, Entity.PERSONAS, f.person());
        assertThrows(SicaException.class, () -> sica.checkInWithCredential(admin, second.code()));
        long pending = request(other, AccessPolicy.NO_ANUNCIADA);
        assertThrows(SicaException.class, () -> sica.issueCredential(admin, pending));
        nextDay.logout(nextAdmin);
    }

    @Test void concurrentQrScansPermitExactlyOneEntry() throws Exception {
        Fixture f = fixture("INVITADO");
        long visit = request(f, AccessPolicy.PREVIA);
        Credential credential = sica.issueCredential(admin, visit);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> scan = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                sica.checkInWithCredential(admin, credential.code());
                return true;
            } catch (SicaException denied) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(scan);
            Future<Boolean> second = executor.submit(scan);
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("1", scalar("SELECT COUNT(*) FROM bitacora_auditoria WHERE accion_realizada='CHECK_IN' AND registro_id_afectado=?", visit));
    }

    @Test void decisionNotificationsKeepCompanyScopeWithCustomAccessPermissions() throws Exception {
        Fixture own = fixture("INVITADO");
        Fixture other = fixture("INVITADO");
        Session ownOfficial = official(own.company());
        Session otherOfficial = official(other.company());
        sql("INSERT INTO rol_permisos SELECT r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='FUNCIONARIO' AND p.codigo='ACCESO'");
        try {
            long visit = request(own, AccessPolicy.NO_ANUNCIADA);
            sica.decide(ownOfficial, visit, true, "Identidad verificada para esta empresa.");
            assertTrue(sica.report(ownOfficial, Report.NOTIFICACIONES, "aprobada", null, null).rows().stream()
                    .anyMatch(row -> row.get(1).equals(Long.toString(visit))));
            assertTrue(sica.report(otherOfficial, Report.NOTIFICACIONES, "", null, null).rows().isEmpty());
        } finally {
            sql("DELETE FROM rol_permisos WHERE rol_id=(SELECT id FROM roles WHERE codigo='FUNCIONARIO') AND permiso_id=(SELECT id FROM permisos WHERE codigo='ACCESO')");
        }
    }

    @Test void restrictedApplicationAccountSupportsRepeatedPrivilegeInstallationAndCompleteAccessFlow() throws Exception {
        String appUser = "sica_rt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String appPassword = CredentialCodes.generate();
        try {
            try (var connection = DriverManager.getConnection(rootUrl, user, password)) {
                try (var create = connection.prepareStatement("CREATE USER '" + appUser + "'@'%' IDENTIFIED BY ?")) {
                    create.setString(1, appPassword);
                    create.executeUpdate();
                }
                try (var statement = connection.createStatement()) {
                    // Reproduce la cuenta recién creada por Docker, seguida de una actualización.
                    statement.execute("GRANT ALL PRIVILEGES ON " + database + ".* TO '" + appUser + "'@'%'");
                    try (var input = getClass().getResourceAsStream("/db/privileges.sql")) {
                        String grants = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
                                .replace("ON sica_acme.", "ON " + database + ".")
                                .replace("'sica'@'%'", "'" + appUser + "'@'%'");
                        for (int iteration = 0; iteration < 2; iteration++) {
                            for (String query : grants.split(";")) if (!query.isBlank()) statement.execute(query);
                        }
                    }
                }
            }
            Sica deployed = new Sica(new JdbcStore(url, appUser, appPassword), new BcryptHasher(), clock);
            Session deployedAdmin = deployed.login("admin@acme.co", "Admin-test-12345");
            try (var connection = DriverManager.getConnection(rootUrl, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("REVOKE SELECT,INSERT,UPDATE ON " + database + ".notificaciones FROM '" + appUser + "'@'%'");
                SicaException missingPermission = assertThrows(SicaException.class,
                        () -> deployed.unreadNotifications(deployedAdmin));
                assertTrue(missingPermission.getMessage().contains("permisos de MySQL"));
                statement.execute("GRANT SELECT,INSERT,UPDATE ON " + database + ".notificaciones TO '" + appUser + "'@'%'");
            }
            assertDoesNotThrow(() -> deployed.statistics(deployedAdmin));
            assertDoesNotThrow(() -> deployed.companies(deployedAdmin));
            assertDoesNotThrow(() -> deployed.unreadNotifications(deployedAdmin));
            assertDoesNotThrow(() -> deployed.report(deployedAdmin, Report.NOTIFICACIONES, "", null, null));
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            long company = deployed.saveCompany(deployedAdmin, null, new Company("LIMIT-" + suffix, "Empresa de permisos", true));
            long person = deployed.savePerson(deployedAdmin, null,
                    new PersonForm("LIMIT-" + suffix, "Invitado de permisos", "INVITADO", company, "", "HABILITADO"));
            long visit = deployed.request(deployedAdmin, person, company, AccessPolicy.NO_ANUNCIADA, now, now.plusHours(1));
            Data notifications = deployed.report(deployedAdmin, Report.NOTIFICACIONES, "", null, null);
            long notification = notifications.rows().stream().filter(row -> row.get(1).equals(Long.toString(visit)))
                    .map(row -> Long.parseLong(row.getFirst())).findFirst().orElseThrow();
            deployed.markNotificationRead(deployedAdmin, notification);
            deployed.decide(deployedAdmin, visit, true, "Verificación usando la cuenta JDBC restringida.");
            Credential credential = deployed.issueCredential(deployedAdmin, visit);
            deployed.checkInWithCredential(deployedAdmin, credential.code());
            deployed.checkOut(deployedAdmin, visit);
            assertEquals("CERRADA", scalar("SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=?)", visit));
            try (var connection = DriverManager.getConnection(url, appUser, appPassword);
                 var statement = connection.createStatement()) {
                SQLException denied = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE bitacora_auditoria SET detalles='alteración' LIMIT 1"));
                assertEquals(1142, denied.getErrorCode());
            }
            deployed.logout(deployedAdmin);
        } finally {
            try (var connection = DriverManager.getConnection(rootUrl, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP USER IF EXISTS '" + appUser + "'@'%'");
            }
        }
    }

}
