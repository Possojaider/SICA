package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcAudit extends JdbcSql implements AuditRepository {
    JdbcAudit(Connection connection) { super(connection); }
    @Override
    public void audit(Long actor, String action, String table, Long entity, String details) {
        try {
            update("INSERT INTO bitacora_auditoria (usuario_id,accion_realizada,tabla_afectada,registro_id_afectado,detalles) VALUES (?,?,?,?,?)",
                    actor, action, table, entity, details);
        } catch (JdbcStore.SqlFailure failure) {
            org.slf4j.LoggerFactory.getLogger(JdbcAudit.class).error("Error al registrar auditoría: SQLState={}, código={}",
                    failure.cause.getSQLState(), failure.cause.getErrorCode());
            throw new SicaException("No se pudo registrar la auditoría. La operación fue cancelada.");
        }
    }
}
