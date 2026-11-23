package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcVisits extends JdbcSql implements VisitRepository {
    JdbcVisits(Connection connection) { super(connection); }
    private static final String VISIT_SELECT = """
            SELECT v.id,v.persona_id,v.empresa_id,e.codigo,v.inicio_programado,v.fin_programado,v.fecha_entrada
            FROM visitas v LEFT JOIN visita_estados e ON e.id=v.estado_visita_id
            """;
    @Override
    public Visit visit(long id, boolean lock) {
        if (lock) query("SELECT id FROM visitas WHERE id=? FOR UPDATE", rs -> rs.next(), id);
        return query(VISIT_SELECT + " WHERE v.id=?", JdbcVisits::readVisit, id);
    }

    @Override
    public Visit inside(long person) {
        Long id = query("SELECT id FROM visitas WHERE persona_dentro=? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, person);
        return id == null ? null : visit(id, false);
    }

    @Override
    public List<Long> expireDue(LocalDateTime now, Long company) {
        List<Long> ids = query("""
                SELECT id FROM visitas WHERE fecha_entrada IS NULL AND fin_programado<=?
                  AND estado_visita_id IN (SELECT id FROM visita_estados WHERE codigo IN ('APROBADA','PENDIENTE'))
                """ + (company == null ? "" : " AND empresa_id=?") + " ORDER BY id FOR UPDATE", rs -> {
            List<Long> result = new ArrayList<>();
            while (rs.next()) result.add(rs.getLong(1));
            return result;
        }, company == null ? new Object[] {now} : new Object[] {now, company});
        for (long id : ids) update("""
                UPDATE visitas SET estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='EXPIRADA'),
                  qr_hash=NULL WHERE id=?
                """, id);
        return ids;
    }

    @Override
    public long createVisit(long person, long company, AccessPolicy type, String state, LocalDateTime start,
                            LocalDateTime end, long actor, LocalDateTime now, String plate) {
        boolean approved = state.equals("APROBADA");
        return insert("""
                INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,
                  inicio_programado,fin_programado,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
                VALUES (?,?,?,(SELECT id FROM visita_estados WHERE codigo=?),?,?,?,?,?,?,?)
                """, person, company, type.name(), state, start, end, actor,
                approved ? actor : null, approved ? now : null, approved ? "Registro anticipado autorizado" : null, plate);
    }

    @Override
    public void decide(long visit, String state, long actor, LocalDateTime now, String reason) {
        update("""
                UPDATE visitas SET estado_visita_id=(SELECT id FROM visita_estados WHERE codigo=?),
                  visita_aprobada_por=?,fecha_decision=?,motivo_decision=? WHERE id=?
                """, state, actor, now, reason, visit);
    }

    @Override
    public Visit visitByCredential(String hash) {
        return query(VISIT_SELECT + " WHERE v.qr_hash=?",
                JdbcVisits::readVisit, hash);
    }

    @Override
    public String credentialHash(long visit) {
        return query("SELECT qr_hash FROM visitas WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, visit);
    }

    @Override
    public void setCredential(long visit, String hash) {
        update("UPDATE visitas SET qr_hash=? WHERE id=?", hash, visit);
    }

    @Override
    public void enter(long visit, LocalDateTime now) {
        update("UPDATE visitas SET estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='DENTRO'),fecha_entrada=?,qr_hash=NULL WHERE id=?", now, visit);
    }

    @Override
    public void exit(long visit, String state, LocalDateTime now) {
        update("UPDATE visitas SET estado_visita_id=(SELECT id FROM visita_estados WHERE codigo=?),fecha_salida=? WHERE id=?", state, now, visit);
    }

    @Override
    public long incident(long visit, long reporter, String description, LocalDateTime now) {
        return insert("INSERT INTO incidentes (visita_id,reportado_por_id,descripcion,fecha) VALUES (?,?,?,?)",
                visit, reporter, description, now);
    }
    private static Visit readVisit(ResultSet rs) throws SQLException {
        return rs.next() ? new Visit(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getObject(5, LocalDateTime.class), rs.getObject(6, LocalDateTime.class),
                rs.getObject(7, LocalDateTime.class)) : null;
    }
}
