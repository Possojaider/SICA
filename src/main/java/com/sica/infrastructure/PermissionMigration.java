package com.sica.infrastructure;

import java.io.IOException;
import java.sql.*;

final class PermissionMigration {
    private PermissionMigration() {}

    static void upgrade(Connection connection) throws SQLException, IOException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1 FROM migraciones_esquema WHERE version='permisos-por-accion-v1'")) {
            if (result.next()) return;
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            translate(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO bitacora_auditoria (accion_realizada,tabla_afectada,detalles) VALUES ('MIGRAR_PERMISOS','rol_permisos','Permisos de gestión convertidos en consultar, crear, editar y dar de baja')");
            }
            connection.commit();
        } catch (SQLException | IOException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally { connection.setAutoCommit(autoCommit); }
    }

    static void translate(Connection connection) throws SQLException, IOException {
        SchemaScripts.execute(connection, "/db/action-permissions.sql", true);
    }
}
