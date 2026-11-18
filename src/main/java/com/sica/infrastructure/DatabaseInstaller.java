package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseInstaller {
    private DatabaseInstaller() {}

    public static void install(Configuration configuration) {
        String serverUrl = LocalEnvironment.load().getOrDefault("SICA_DB_ADMIN_URL",
                "jdbc:mysql://127.0.0.1:3306/?connectionTimeZone=America/Bogota&connectTimeout=5000");
        try (var connection = DriverManager.getConnection(serverUrl, configuration.user(), configuration.password());
             var statement = connection.createStatement()) {
            for (String resource : new String[] {"/db/schema.sql", "/db/data.sql"}) {
                try (var input = DatabaseInstaller.class.getResourceAsStream(resource)) {
                    if (input == null) throw new IOException("Recurso ausente " + resource);
                    String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    for (String sql : script.split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
            }
        } catch (SQLException | IOException failure) {
            throw new SicaException("No se pudo instalar el esquema. Usa una base vacía y una cuenta con permisos de DDL. " +
                    "Código técnico: " + (failure instanceof SQLException sql ? sql.getErrorCode() : "recurso"));
        }
    }
}
