package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
abstract class JdbcSql {
    protected final Connection connection;
    JdbcSql(Connection connection) { this.connection=connection; }
    @FunctionalInterface protected interface Read<T> { T read(ResultSet result) throws SQLException; }
    protected long count(String sql, Long company) {
        return query(sql, rs -> { rs.next(); return rs.getLong(1); },
                company == null ? new Object[0] : new Object[] {company});
    }

    protected static Data readData(ResultSet rs) throws SQLException {
        int count = rs.getMetaData().getColumnCount();
        List<String> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) columns.add(rs.getMetaData().getColumnLabel(i));
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) row.add(Objects.toString(rs.getString(i), ""));
            rows.add(List.copyOf(row));
        }
        return new Data(List.copyOf(columns), List.copyOf(rows));
    }

    protected void execute(String sql, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new JdbcStore.SqlFailure(failure);
        }
    }

    protected <T> T query(String sql, Read<T> reader, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet rs = statement.executeQuery()) {
                return reader.read(rs);
            }
        } catch (SQLException failure) {
            throw new JdbcStore.SqlFailure(failure);
        }
    }

    protected long insert(String sql, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, arguments);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
                throw new SQLException("No se obtuvo la clave generada");
            }
        } catch (SQLException failure) {
            throw new JdbcStore.SqlFailure(failure);
        }
    }

    protected void update(String sql, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            if (statement.executeUpdate() != 1) throw new SicaException("El registro ya no existe.");
        } catch (SQLException failure) {
            throw new JdbcStore.SqlFailure(failure);
        }
    }

    protected static void bind(PreparedStatement statement, Object[] arguments) throws SQLException {
        for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
    }
}
