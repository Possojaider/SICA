package com.sica;

import com.sica.application.Sica;
import com.sica.domain.*;
import com.sica.domain.port.out.Store.*;
import com.sica.infrastructure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICA_TEST_DB_PASSWORD", matches=".+")
class ProvidedSchemaIntegrationTest {
    private final String server = System.getenv().getOrDefault("SICA_TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/?connectionTimeZone=America/Bogota");
    private final String user = System.getenv().getOrDefault("SICA_TEST_DB_USER", "root");
    private final String password = System.getenv("SICA_TEST_DB_PASSWORD");
    private final List<String> databases = new ArrayList<>();
    private final Clock clock = Clock.system(ZoneId.of("America/Bogota"));

    private String install(String schema, String data, boolean legacy) throws Exception {
        String database = "sica_schema_" + UUID.randomUUID().toString().replace("-", "");
        databases.add(database);
        try (var c = DriverManager.getConnection(server, user, password); var statement = c.createStatement()) {
            for (String resource : data == null ? List.of(schema) : List.of(schema, data)) {
                try (var input = getClass().getResourceAsStream(resource)) {
                    String script = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
                            .replace(legacy ? "sica" : "sica_acme", database);
                    for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
                }
            }
        }
        return database;
    }

    private Configuration configuration(String database) {
        int query = server.indexOf('?');
        String url = server.substring(0, query < 0 ? server.length() : query) + database +
                (query < 0 ? "" : server.substring(query));
        return new Configuration(url, user, password, clock);
    }

    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(configuration(database).url(), user, password);
    }

    private String scalar(String database, String sql) throws SQLException {
        try (var c = connect(database); var statement = c.createStatement(); var rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @AfterEach void cleanup() throws Exception {
        try (var c = DriverManager.getConnection(server, user, password); var statement = c.createStatement()) {
            for (String database : databases) statement.execute("DROP DATABASE IF EXISTS " + database);
        }
    }

    @Test void providedSchemaIsUpgradedWithoutChangingItsNumericIds() throws Exception {
        String database = install("/db/provided-schema.sql", null, false);
        try (var c = connect(database); var s = c.createStatement()) {
            s.execute("INSERT INTO roles VALUES (17,'Superusuario'),(23,'Funcionario de Empresa')");
            s.execute("INSERT INTO permisos VALUES (41,'generar_reporte_auditoria','Permiso original')");
            s.execute("INSERT INTO rol_permisos VALUES (17,41)");
            s.execute("INSERT INTO persona_estados_acceso VALUES (8,'Activo'),(9,'Con Prohibicion de Ingreso')");
            s.execute("INSERT INTO visita_estados VALUES (20,'Dentro'),(21,'Fuera'),(22,'Pendiente de Aprobacion'),(23,'Aprobado')");
            s.execute("INSERT INTO empresas VALUES (71,'Empresa original','Contacto original')");
            s.execute("INSERT INTO personas VALUES (1,'Persona original','ORIGINAL-1',71,'Invitado',8,NULL)");
            s.execute("INSERT INTO visitas (id,persona_id,estado_visita_id) VALUES (1,1,23)");
        }
        DatabaseMigrator.upgrade(configuration(database));
        DatabaseMigrator.upgrade(configuration(database));
        assertEquals("17", scalar(database, "SELECT id FROM roles WHERE codigo='SUPER'"));
        assertEquals("8", scalar(database, "SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'"));
        assertEquals("Contacto original", scalar(database, "SELECT contacto_principal FROM empresas WHERE id=71"));
        assertEquals("EXPIRADA", scalar(database, "SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=1)"));
        try (var c = connect(database); var s = c.createStatement()) {
            s.execute("DELETE FROM rol_permisos WHERE rol_id=23 AND permiso_id=(SELECT id FROM permisos WHERE codigo='QR_EMITIR')");
        }
        DatabaseMigrator.upgrade(configuration(database));
        assertEquals("0", scalar(database, "SELECT COUNT(*) FROM rol_permisos WHERE rol_id=23 AND permiso_id=(SELECT id FROM permisos WHERE codigo='QR_EMITIR')"));
    }

    @Test void demoInsertsAreRepeatableAndAllRolesAuthenticateWithBcrypt() throws Exception {
        String database = install("/db/schema.sql", "/db/data.sql", false);
        DemoDataInstaller.install(configuration(database));
        DemoDataInstaller.install(configuration(database));
        for (var count : Map.of("empresas",2,"usuarios",4,"personas",6,"visitas",5,"incidentes",1,"notificaciones",3).entrySet()) {
            assertEquals(Integer.toString(count.getValue()), scalar(database, "SELECT COUNT(*) FROM " + count.getKey()));
        }
        assertEquals("0", scalar(database, """
                SELECT COUNT(*) FROM visitas WHERE tipo_solicitud='SIN_CARNET'
                  AND (DATE(inicio_programado)<>DATE(DATE_SUB(fin_programado,INTERVAL 1 SECOND))
                       OR fecha_decision>fecha_entrada)
                """));
        Sica app = ApplicationFactory.create(configuration(database));
        for (String account : List.of("super","supervisor","guarda","funcionario")) {
            Session session = app.login(account + "@acme.example", "Acme-Sica-2026!");
            assertFalse(app.permissions(session).isEmpty());
            app.logout(session);
        }
        Session admin = app.login("super@acme.example", "Acme-Sica-2026!");
        assertEquals(1, app.report(admin, Report.DENTRO, "", null, null).rows().size());
        assertTrue(scalar(database,"SELECT password FROM usuarios WHERE email='super@acme.example'").startsWith("$2"));
    }

    @Test void legacyDataCredentialsAuditAndPermissionsArePreserved() throws Exception {
        String source = install("/db/legacy-schema.sql", "/db/legacy-data.sql", true);
        String target = install("/db/schema.sql", "/db/data.sql", false);
        String hash = new BcryptHasher().hash("Legado-seguro-2026");
        String code = CredentialCodes.generate();
        try (var c = connect(source); var s = c.createStatement()) {
            s.execute("INSERT INTO empresas (id,nit,nombre) VALUES (7,'LEGADO-7','Empresa legada')");
            try (var insert = c.prepareStatement("INSERT INTO usuarios (id,nombre,correo,hash,rol,empresa) VALUES (2,'Administrador legado','legacy@acme.co',?,'SUPER',NULL),(3,'Funcionario legado','official@acme.co',?,'FUNCIONARIO',7)")) {
                insert.setString(1, hash); insert.setString(2, hash); insert.executeUpdate();
            }
            s.execute("INSERT INTO personas (id,documento,nombre,tipo,empresa,estado) VALUES (9,'LEGADO-9','Persona legada','INVITADO',7,'HABILITADO')");
            s.execute("""
                    INSERT INTO visitas (id,persona,empresa,tipo,estado,inicio,vence,solicitado_por,decidido_por,decidido_en,motivo_decision)
                    VALUES (14,9,7,'PREVIA','APROBADA',DATE_SUB(NOW(),INTERVAL 1 MINUTE),DATE_ADD(NOW(),INTERVAL 1 HOUR),2,3,NOW(),'Motivo original')
                    """);
            try (var update = c.prepareStatement("UPDATE visitas SET qr_hash=? WHERE id=14")) {
                update.setString(1,CredentialCodes.digest(code)); update.executeUpdate();
            }
            s.execute("INSERT INTO visita_estados (id,nombre) VALUES ('ESTADO_LEGADO','Estado legado personalizado')");
            s.execute("""
                    INSERT INTO visitas (id,persona,empresa,tipo,estado,inicio,vence,entrada,salida,solicitado_por)
                    VALUES (15,9,7,'PREVIA','ESTADO_LEGADO',DATE_SUB(NOW(),INTERVAL 3 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),
                     DATE_SUB(NOW(),INTERVAL 3 HOUR),DATE_SUB(NOW(),INTERVAL 2 HOUR),2)
                    """);
            s.execute("INSERT INTO bitacora_auditoria (id,usuario,accion,tabla_afectada,entidad,detalles) VALUES (19,2,'ORIGINAL','visitas',14,'Detalle conservado')");
            s.execute("DELETE FROM rol_permisos WHERE rol='FUNCIONARIO' AND permiso='QR_EMITIR'");
        }
        LegacyDatabaseImporter.importFrom(configuration(target), source);
        LegacyDatabaseImporter.importFrom(configuration(target), source);
        assertEquals("2", scalar(target, "SELECT COUNT(*) FROM visitas"));
        assertEquals("ESTADO_LEGADO", scalar(target,"SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=15)"));
        assertEquals("Motivo original", scalar(target, "SELECT motivo_decision FROM visitas WHERE id=14"));
        assertEquals("Detalle conservado", scalar(target, "SELECT detalles FROM bitacora_auditoria WHERE id=19"));
        assertEquals(hash, scalar(target, "SELECT password FROM usuarios WHERE id=2"));
        Sica app = ApplicationFactory.create(configuration(target));
        Session admin = app.login("legacy@acme.co", "Legado-seguro-2026");
        Session official = app.login("official@acme.co", "Legado-seguro-2026");
        assertFalse(app.permissions(official).contains("QR_EMITIR"));
        app.checkInWithCredential(admin, code);
        assertEquals("DENTRO", scalar(target, "SELECT codigo FROM visita_estados WHERE id=(SELECT estado_visita_id FROM visitas WHERE id=14)"));
        assertEquals("APROBADA", scalar(source, "SELECT estado FROM visitas WHERE id=14"));
    }

    @Test void contactAndVehiclePlateAreWrittenToTheOriginalColumns() throws Exception {
        String database = install("/db/schema.sql", "/db/data.sql", false);
        Sica app = ApplicationFactory.create(configuration(database));
        app.bootstrap("admin@acme.co", "Admin-seguro-2026");
        Session admin = app.login("admin@acme.co", "Admin-seguro-2026");
        long company = app.saveCompany(admin, null, new Company(null, "Empresa sin NIT", true, "Contacto principal"));
        long person = app.savePerson(admin, null, new PersonForm("DOC-1","Invitado","INVITADO",company,"","HABILITADO"));
        long visit = app.request(admin, person, company, AccessPolicy.PREVIA, app.currentTime().minusMinutes(1), app.currentTime().plusHours(1), "ABC123");
        assertEquals("Contacto principal", scalar(database,"SELECT contacto_principal FROM empresas WHERE id=" + company));
        assertEquals("ABC123", scalar(database,"SELECT vehiculo_placa FROM visitas WHERE id=" + visit));
        assertThrows(SicaException.class, () -> app.request(admin, person, company, AccessPolicy.PREVIA,
                app.currentTime(), app.currentTime().plusHours(1), "PLACA-DEMASIADO-LARGA"));
    }
}
