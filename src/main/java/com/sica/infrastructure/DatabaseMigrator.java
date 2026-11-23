package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.sql.*;
import java.io.IOException;

public final class DatabaseMigrator {
    private DatabaseMigrator() {}

    public static void upgrade(Configuration configuration) {
        try (Connection connection = DriverManager.getConnection(configuration.url(), configuration.user(), configuration.password())) {
            if (!SchemaScripts.column(connection, "usuarios", "email")) {
                throw new SicaException("La base usa el esquema anterior. Instala sica_acme y utiliza --import-legacy.");
            }
            try (var lock = connection.prepareStatement("SELECT GET_LOCK(?,10)")) {
                lock.setString(1, connection.getCatalog() + "_upgrade");
                try (var result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new SicaException("Otra instancia está actualizando la base.");
                }
            }
            try {
                addOperationalColumns(connection);
                try (var statement = connection.createStatement()) {
                    for (String sql : SchemaScripts.read("/db/schema.sql")) {
                        if (sql.startsWith("CREATE TABLE ")) {
                            statement.execute(sql.replaceFirst("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
                        } else if (sql.startsWith("CREATE TRIGGER ")) {
                            String name = sql.split("\\s+")[2];
                            try (var exists = connection.prepareStatement(
                                    "SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=? AND TRIGGER_NAME=?")) {
                                exists.setString(1, connection.getCatalog());
                                exists.setString(2, name);
                                try (var result = exists.executeQuery()) {
                                    if (!result.next()) statement.execute(sql);
                                }
                            }
                        }
                    }
                    populateCodes(statement);
                    // Los catálogos y sus permisos iniciales se instalan una sola vez.
                    try (var check = statement.executeQuery("SELECT 1 FROM migraciones_esquema WHERE version='base-academica-v3'")) {
                        if (check.next()) {
                            PermissionMigration.upgrade(connection);
                            return;
                        }
                    }
                    connection.setAutoCommit(false);
                    SchemaScripts.execute(connection, "/db/data.sql", true);
                    statement.executeUpdate("""
                            UPDATE visitas v LEFT JOIN personas p ON p.id=v.persona_id
                            SET v.empresa_id=COALESCE(v.empresa_id,p.empresa_id)
                            """);
                    int expired = statement.executeUpdate("""
                            UPDATE visitas SET estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='EXPIRADA'),
                              inicio_programado=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND),fin_programado=CURRENT_TIMESTAMP
                            WHERE (inicio_programado IS NULL OR fin_programado IS NULL)
                              AND estado_visita_id IN (SELECT id FROM visita_estados WHERE codigo IN ('PENDIENTE','APROBADA'))
                            """);
                    if (expired > 0) statement.executeUpdate("""
                            INSERT INTO bitacora_auditoria (accion_realizada,tabla_afectada,detalles)
                            VALUES ('MIGRAR_VISITAS','visitas','Autorizaciones históricas sin horario identificadas como expiradas')
                            """);
                    statement.executeUpdate("INSERT IGNORE INTO migraciones_esquema (version,origen) VALUES ('base-academica-v3',DATABASE())");
                    connection.commit();
                }
            } finally {
                try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, connection.getCatalog() + "_upgrade");
                    release.executeQuery().close();
                }
            }
        } catch (SQLException | IOException failure) {
            org.slf4j.LoggerFactory.getLogger(DatabaseMigrator.class).error("Falló la adaptación del esquema académico", failure);
            throw new SicaException("No se pudo adaptar la base. Revisa los permisos DDL y los registros técnicos.");
        }
    }

    private static void addOperationalColumns(Connection c) throws SQLException {
        SchemaScripts.addColumn(c, "roles", "codigo", "VARCHAR(32) UNIQUE");
        SchemaScripts.addColumn(c, "permisos", "codigo", "VARCHAR(32) UNIQUE");
        SchemaScripts.addColumn(c, "persona_estados_acceso", "codigo", "VARCHAR(24) UNIQUE");
        SchemaScripts.addColumn(c, "visita_estados", "codigo", "VARCHAR(32) UNIQUE");
        SchemaScripts.addColumn(c, "empresas", "nit", "VARCHAR(32) UNIQUE");
        SchemaScripts.addColumn(c, "empresas", "esta_activa", "BOOLEAN NOT NULL DEFAULT TRUE");
        SchemaScripts.addColumn(c, "usuarios", "empresa_id", "INT");
        SchemaScripts.addColumn(c, "visitas", "empresa_id", "INT");
        SchemaScripts.addColumn(c, "visitas", "tipo_solicitud", "ENUM('PREVIA','NO_ANUNCIADA','SIN_CARNET') NOT NULL DEFAULT 'NO_ANUNCIADA'");
        SchemaScripts.addColumn(c, "visitas", "inicio_programado", "DATETIME");
        SchemaScripts.addColumn(c, "visitas", "fin_programado", "DATETIME");
        SchemaScripts.addColumn(c, "visitas", "solicitado_por_id", "INT");
        SchemaScripts.addColumn(c, "visitas", "fecha_decision", "DATETIME");
        SchemaScripts.addColumn(c, "visitas", "motivo_decision", "VARCHAR(1000)");
        SchemaScripts.addColumn(c, "visitas", "qr_hash", "CHAR(64) UNIQUE");
        SchemaScripts.addColumn(c, "visitas", "persona_dentro",
                "INT GENERATED ALWAYS AS (CASE WHEN fecha_entrada IS NOT NULL AND fecha_salida IS NULL THEN persona_id END) STORED UNIQUE");
        boolean expirationIndex = false;
        try (var indexes = c.getMetaData().getIndexInfo(c.getCatalog(), null, "visitas", false, false)) {
            while (indexes.next()) if ("idx_visitas_vencimiento".equals(indexes.getString("INDEX_NAME"))) expirationIndex = true;
        }
        if (!expirationIndex) try (var statement = c.createStatement()) {
            statement.execute("CREATE INDEX idx_visitas_vencimiento ON visitas (estado_visita_id,fin_programado,id)");
        }
        addForeignKey(c, "usuarios", "empresa_id", "empresas");
        addForeignKey(c, "visitas", "empresa_id", "empresas");
        addForeignKey(c, "visitas", "solicitado_por_id", "usuarios");
    }

    private static void addForeignKey(Connection c, String table, String column, String parent) throws SQLException {
        try (var keys = c.getMetaData().getImportedKeys(c.getCatalog(), null, table)) {
            while (keys.next()) if (column.equals(keys.getString("FKCOLUMN_NAME"))) return;
        }
        try (var statement = c.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD FOREIGN KEY (" + column + ") REFERENCES " + parent + "(id)");
        }
    }

    private static void populateCodes(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE roles SET codigo=CASE nombre_rol
                 WHEN 'Superusuario' THEN 'SUPER' WHEN 'Supervisor de Seguridad' THEN 'SUPERVISOR'
                 WHEN 'Guarda de Seguridad' THEN 'GUARDA' WHEN 'Funcionario de Empresa' THEN 'FUNCIONARIO'
                 ELSE CONCAT('ROL_',id) END WHERE codigo IS NULL
                """);
        statement.executeUpdate("""
                UPDATE persona_estados_acceso SET codigo=CASE nombre_estado
                 WHEN 'Activo' THEN 'HABILITADO' WHEN 'Con Prohibicion de Ingreso' THEN 'RESTRINGIDO'
                 WHEN 'Baja' THEN 'BAJA' ELSE CONCAT('ESTADO_',id) END WHERE codigo IS NULL
                """);
        statement.executeUpdate("""
                UPDATE visita_estados SET codigo=CASE nombre_estado
                 WHEN 'Dentro' THEN 'DENTRO' WHEN 'Fuera' THEN 'CERRADA'
                 WHEN 'Pendiente de Aprobacion' THEN 'PENDIENTE' WHEN 'Aprobado' THEN 'APROBADA'
                 WHEN 'Rechazado' THEN 'RECHAZADA' WHEN 'Expirado' THEN 'EXPIRADA'
                 WHEN 'Cerrada por Sistema (Salida Olvidada)' THEN 'SALIDA_OLVIDADA'
                 ELSE CONCAT('ESTADO_',id) END WHERE codigo IS NULL
                """);
        // Conserva los permisos propios del esquema entregado antes de completar el catálogo.
        statement.executeUpdate("""
                UPDATE permisos SET codigo=CASE nombre_permiso
                 WHEN 'gestionar_usuarios' THEN 'USUARIOS' WHEN 'gestionar_empresas' THEN 'EMPRESAS'
                 WHEN 'gestionar_personas' THEN 'PERSONAS' WHEN 'consultar_personas' THEN 'PERSONAS_LEER'
                 WHEN 'crear_persona' THEN 'PERSONAS_CREAR' WHEN 'consultar_visitas' THEN 'VISITAS_LEER'
                 WHEN 'prerregistrar_invitado' THEN 'PRE_REGISTRAR' WHEN 'registrar_visita' THEN 'SOLICITAR'
                 WHEN 'aprobar_rechazar_visita' THEN 'DECIDIR' WHEN 'registrar_entrada_salida' THEN 'ACCESO'
                 WHEN 'registrar_consultar_incidentes' THEN 'INCIDENTES' WHEN 'generar_reporte_auditoria' THEN 'AUDITORIA'
                 WHEN 'generar_reportes' THEN 'REPORTES' WHEN 'emitir_qr_visita' THEN 'QR_EMITIR'
                 ELSE CONCAT('PERMISO_',id) END WHERE codigo IS NULL
                """);
    }
}
