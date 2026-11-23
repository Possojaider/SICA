package com.sica.infrastructure;

import com.sica.domain.AccessPolicy;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public final class JdbcStore implements Store {
    private final String url;
    private final String username;
    private final String password;

    public JdbcStore(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }
    private static final Logger LOG = LoggerFactory.getLogger(JdbcStore.class);

    @Override
    public <T> T transaction(Function<Unit, T> operation) {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = operation.apply(new JdbcUnit(connection));
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } catch (SqlFailure wrapped) {
            throw translate(wrapped.cause);
        } catch (SQLException failure) {
            throw translate(failure);
        }
    }

    private static SicaException translate(SQLException failure) {
        LOG.error("Operación MySQL fallida: SQLState={}, código={}", failure.getSQLState(), failure.getErrorCode());
        if (failure.getErrorCode() == 1142 || failure.getErrorCode() == 1143) {
            return new SicaException("Faltan permisos de MySQL para operar SICA. Actualiza los privilegios de instalación.");
        }
        if (failure.getErrorCode() == 1062) return new SicaException("Ya existe un registro con ese identificador único.");
        if (failure.getErrorCode() == 1205 || failure.getErrorCode() == 1213) return new SicaException("Conflicto de concurrencia. Intenta de nuevo.");
        if (failure.getSQLState() != null && failure.getSQLState().startsWith("08")) {
            return new SicaException("No se pudo conectar con MySQL. Revisa la configuración.");
        }
        return new SicaException("MySQL no pudo completar la operación.");
    }

    static final class SqlFailure extends RuntimeException {
        final SQLException cause;

        SqlFailure(SQLException cause) {
            super(cause);
            this.cause = cause;
        }
    }



}
