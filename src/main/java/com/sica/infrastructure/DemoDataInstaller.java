package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.sql.*;
import java.io.IOException;

public final class DemoDataInstaller {
    private DemoDataInstaller() {}

    public static void install(Configuration configuration) {
        try (var connection = DriverManager.getConnection(configuration.url(), configuration.user(), configuration.password())) {
            connection.setAutoCommit(false);
            try {
                SchemaScripts.execute(connection, "/db/demo.sql", true);
                connection.commit();
            } catch (SQLException | IOException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException | IOException failure) {
            org.slf4j.LoggerFactory.getLogger(DemoDataInstaller.class).error("No se pudieron cargar los ejemplos", failure);
            throw new SicaException("No se pudo cargar el conjunto de ejemplo. La operación fue cancelada.");
        }
    }
}
