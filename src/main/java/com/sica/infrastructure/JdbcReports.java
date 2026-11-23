package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcReports extends JdbcSql implements ReportingRepository {
    JdbcReports(Connection connection) { super(connection); }
    @Override
    public Statistics statistics(Long company) {
        String scope = company == null ? "" : " AND empresa_id=?";
        long inside = count("SELECT COUNT(*) FROM visitas WHERE estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='DENTRO')" + scope, company);
        long pending = count("SELECT COUNT(*) FROM visitas WHERE estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='PENDIENTE') AND fin_programado>CURRENT_TIMESTAMP" + scope, company);
        long approved = count("SELECT COUNT(*) FROM visitas WHERE estado_visita_id=(SELECT id FROM visita_estados WHERE codigo='APROBADA') AND fin_programado>CURRENT_TIMESTAMP" + scope, company);
        String incidentScope = company == null ? "" : " AND v.empresa_id=?";
        long incidents = count("SELECT COUNT(*) FROM incidentes i JOIN visitas v ON v.id=i.visita_id WHERE DATE(i.fecha)=CURRENT_DATE" + incidentScope, company);
        return new Statistics(inside, pending, approved, incidents);
    }

    @Override
    public Data report(Report report, Long company, String search, LocalDateTime from, LocalDateTime until) {
        String sql = ReportQuery.select(report);
        List<Object> params = new ArrayList<>();
        StringBuilder filters = new StringBuilder(" WHERE 1=1");
        if (company != null && ReportQuery.scoped(report)) {
            filters.append(" AND q.Empresa=?");
            params.add(company);
        }
        switch (report) {
            case SOLICITUDES -> filters.append(" AND q.Estado IN ('PENDIENTE','APROBADA','RECHAZADA','EXPIRADA')");
            case DENTRO -> filters.append(" AND q.Estado='DENTRO'");
            case HISTORIAL -> filters.append(" AND q.Entrada IS NOT NULL");
            case DECISIONES -> filters.append(" AND q.Decidido IS NOT NULL");
            default -> { }
        }
        if (from != null && ReportQuery.dated(report)) {
            filters.append(" AND q.Fecha>=?");
            params.add(from);
        }
        if (until != null && ReportQuery.dated(report)) {
            filters.append(" AND q.Fecha<?");
            params.add(until);
        }
        if (!search.isBlank()) {
            filters.append(" AND LOCATE(?,LOWER(").append(ReportQuery.searchColumn(report)).append("))>0");
            params.add(search.toLowerCase(Locale.ROOT));
        }
        return query("SELECT q.* FROM (" + sql + ") q" + filters + " ORDER BY q.ID DESC", JdbcSql::readData, params.toArray());
    }
}
