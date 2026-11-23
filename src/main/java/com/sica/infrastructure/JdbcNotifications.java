package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcNotifications extends JdbcSql implements NotificationRepository {
    JdbcNotifications(Connection connection) { super(connection); }
    @Override
    public void notifyRequest(long visit, long company, LocalDateTime now) {
        execute("""
                INSERT INTO notificaciones (usuario_id,visita_id,evento,mensaje,fecha)
                SELECT u.id,?,'SOLICITUD',?,? FROM usuarios u
                JOIN roles r ON r.id=u.rol_id JOIN rol_permisos rp ON rp.rol_id=r.id
                JOIN permisos p ON p.id=rp.permiso_id AND p.codigo='DECIDIR'
                WHERE u.esta_activo=TRUE AND (r.codigo<>'FUNCIONARIO' OR u.empresa_id=?)
                """, visit, "Solicitud de ingreso pendiente · visita " + visit, now, company);
    }

    @Override
    public void notifyDecision(long visit, String state, LocalDateTime now) {
        execute("""
                INSERT INTO notificaciones (usuario_id,visita_id,evento,mensaje,fecha)
                SELECT u.id,v.id,'DECISION',?,? FROM usuarios u JOIN visitas v ON v.id=?
                JOIN roles r ON r.id=u.rol_id
                WHERE u.esta_activo=TRUE AND (r.codigo<>'FUNCIONARIO' OR u.empresa_id=v.empresa_id)
                  AND (u.id=v.solicitado_por_id OR EXISTS (
                  SELECT 1 FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id
                  WHERE rp.rol_id=u.rol_id AND p.codigo='ACCESO'))
                """, "Solicitud " + state.toLowerCase(Locale.ROOT) + " · visita " + visit, now, visit);
    }

    @Override
    public Data notifications(long user, String search, LocalDateTime from, LocalDateTime until) {
        String sql = """
                SELECT id ID,visita_id Visita,fecha Fecha,evento Evento,mensaje Mensaje,
                  CASE WHEN leida_en IS NULL THEN 'SIN_LEER' ELSE 'LEIDA' END Estado,leida_en LeidaEn
                FROM notificaciones WHERE usuario_id=? AND LOCATE(?,LOWER(mensaje))>0
                """;
        List<Object> parameters = new ArrayList<>(List.of(user, search.strip().toLowerCase(Locale.ROOT)));
        if (from != null) { sql += " AND fecha>=?"; parameters.add(from); }
        if (until != null) { sql += " AND fecha<?"; parameters.add(until); }
        return query(sql + " ORDER BY id DESC", JdbcSql::readData, parameters.toArray());
    }

    @Override
    public long unreadNotifications(long user) {
        return count("SELECT COUNT(*) FROM notificaciones WHERE usuario_id=? AND leida_en IS NULL", user);
    }

    @Override
    public boolean markNotificationRead(long user, long id, LocalDateTime now) {
        boolean unread = query("SELECT leida_en FROM notificaciones WHERE id=? AND usuario_id=? FOR UPDATE", rs -> {
            if (!rs.next()) throw new SicaException("La notificación no está disponible para tu usuario.");
            return rs.getTimestamp(1) == null;
        }, id, user);
        if (unread) update("UPDATE notificaciones SET leida_en=? WHERE id=? AND usuario_id=?", now, id, user);
        return unread;
    }
}
