package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class DatabaseMigrator {
    private DatabaseMigrator() {}

    public static void upgrade(Configuration configuration) {
        try (Connection connection = DriverManager.getConnection(configuration.url(), configuration.user(), configuration.password())) {
            String database = connection.getCatalog();
            String lockName = database + "_upgrade_2";
            try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK(?,10)")) {
                lock.setString(1, lockName);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new SicaException("Otra instancia está migrando la base.");
                }
            }
            try {
                addColumn(connection, "motivo_decision", "VARCHAR(1000)");
                addColumn(connection, "qr_hash", "CHAR(64) UNIQUE");
                // Reutiliza exactamente la definición de la instalación nueva.
                try (var input = DatabaseMigrator.class.getResourceAsStream("/db/schema.sql")) {
                    if (input == null) throw new IOException("Esquema no disponible");
                    String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    String notifications = schema.substring(schema.indexOf("CREATE TABLE notificaciones"));
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(notifications.replace("CREATE TABLE notificaciones", "CREATE TABLE IF NOT EXISTS notificaciones").strip());
                        connection.setAutoCommit(false);
                        int permissionAdded = statement.executeUpdate("INSERT IGNORE INTO permisos VALUES ('QR_EMITIR')");
                        if (permissionAdded > 0) {
                            statement.executeUpdate("INSERT INTO rol_permisos (rol,permiso) SELECT id,'QR_EMITIR' FROM roles WHERE id IN ('SUPER','SUPERVISOR','FUNCIONARIO')");
                        }
                        int legacyDecisions = statement.executeUpdate("""
                                UPDATE visitas SET motivo_decision='Decisión anterior a la incorporación de motivos'
                                WHERE decidido_en IS NOT NULL AND motivo_decision IS NULL
                                """);
                        int pendingNotifications = statement.executeUpdate("""
                                INSERT IGNORE INTO notificaciones (usuario,visita,evento,mensaje,fecha)
                                SELECT u.id,v.id,'SOLICITUD',CONCAT('Solicitud de ingreso pendiente · visita ',v.id),CURRENT_TIMESTAMP
                                FROM usuarios u JOIN visitas v ON v.estado='PENDIENTE' AND v.vence>CURRENT_TIMESTAMP
                                JOIN rol_permisos rp ON rp.rol=u.rol AND rp.permiso='DECIDIR'
                                WHERE u.activo=TRUE AND (u.rol<>'FUNCIONARIO' OR u.empresa=v.empresa)
                                """);
                        if (pendingNotifications > 0) {
                            try (PreparedStatement audit = connection.prepareStatement("""
                                    INSERT INTO bitacora_auditoria (accion,tabla_afectada,detalles)
                                    VALUES ('MIGRAR_NOTIFICACIONES','notificaciones',?)
                                    """)) {
                                audit.setString(1, "Avisos de solicitudes pendientes existentes: " + pendingNotifications);
                                audit.executeUpdate();
                            }
                        }
                        if (legacyDecisions > 0) {
                            try (PreparedStatement audit = connection.prepareStatement("""
                                    INSERT INTO bitacora_auditoria (accion,tabla_afectada,detalles)
                                    VALUES ('MIGRAR_MOTIVOS','visitas',?)
                                    """)) {
                                audit.setString(1, "Decisiones históricas identificadas sin motivo original: " + legacyDecisions);
                                audit.executeUpdate();
                            }
                        }
                        connection.commit();
                    }
                }
            } finally {
                try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, lockName);
                    release.executeQuery().close();
                }
            }
        } catch (SQLException | IOException failure) {
            org.slf4j.LoggerFactory.getLogger(DatabaseMigrator.class).error("Falló la migración de esquema", failure);
            throw new SicaException("No se pudo actualizar el esquema. Revisa la conexión y los permisos DDL de la cuenta administradora.");
        }
    }

    private static void addColumn(Connection connection, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "visitas", column)) {
            if (columns.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE visitas ADD COLUMN " + column + " " + definition);
        }
    }
}
