package com.sica.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

final class SchemaScripts {
    private SchemaScripts() {}

    static List<String> read(String resource) throws IOException {
        try (var input = SchemaScripts.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Recurso ausente " + resource);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*--.*$", "");
            return Arrays.stream(script.split(";")).map(String::strip).filter(sql -> !sql.isEmpty()).toList();
        }
    }

    static void execute(Connection connection, String resource, boolean selectedDatabase) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : read(resource)) {
                if (selectedDatabase && (sql.startsWith("USE ") || sql.startsWith("CREATE DATABASE "))) continue;
                statement.execute(sql);
            }
        }
    }

    static boolean column(Connection connection, String table, String column) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    static void addColumn(Connection connection, String table, String column, String definition) throws SQLException {
        if (column(connection, table, column)) return;
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
