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

    private static final class SqlFailure extends RuntimeException {
        private final SQLException cause;

        private SqlFailure(SQLException cause) {
            super(cause);
            this.cause = cause;
        }
    }

    @FunctionalInterface
    private interface Read<T> {
        T read(ResultSet result) throws SQLException;
    }

    private static final class JdbcUnit implements Unit {
        private final Connection connection;

        private JdbcUnit(Connection connection) {
            this.connection = connection;
        }
        @Override
        public List<Option> companies(Long company) {
            String sql = "SELECT id,nombre FROM empresas WHERE activo=TRUE" +
                    (company == null ? "" : " AND id=?") + " ORDER BY nombre";
            return query(sql, rs -> {
                List<Option> result = new ArrayList<>();
                while (rs.next()) result.add(new Option(rs.getLong(1), rs.getString(2)));
                return List.copyOf(result);
            }, company == null ? new Object[0] : new Object[] {company});
        }

        @Override
        public Statistics statistics(Long company) {
            String scope = company == null ? "" : " AND empresa=?";
            long inside = count("SELECT COUNT(*) FROM visitas WHERE estado='DENTRO'" + scope, company);
            long pending = count("SELECT COUNT(*) FROM visitas WHERE estado='PENDIENTE' AND vence>CURRENT_TIMESTAMP" + scope, company);
            long approved = count("SELECT COUNT(*) FROM visitas WHERE estado='APROBADA' AND vence>CURRENT_TIMESTAMP" + scope, company);
            String incidentScope = company == null ? "" : " AND v.empresa=?";
            long incidents = count("SELECT COUNT(*) FROM incidentes i JOIN visitas v ON v.id=i.visita WHERE DATE(i.fecha)=CURRENT_DATE" + incidentScope, company);
            return new Statistics(inside, pending, approved, incidents);
        }

        private long count(String sql, Long company) {
            return query(sql, rs -> { rs.next(); return rs.getLong(1); },
                    company == null ? new Object[0] : new Object[] {company});
        }

        @Override
        public Account byEmail(String email) {
            return accountWhere("correo", email);
        }

        @Override
        public Account account(long id) {
            return accountWhere("id", id);
        }

        private Account accountWhere(String column, Object value) {
            return query("SELECT u.id,u.hash,u.activo,u.rol,u.empresa,rp.permiso FROM usuarios u " +
                    "LEFT JOIN rol_permisos rp ON rp.rol=u.rol WHERE u." + column + "=?", rs -> {
                if (!rs.next()) return null;
                long id = rs.getLong("id");
                String hash = rs.getString("hash");
                boolean active = rs.getBoolean("activo");
                String role = rs.getString("rol");
                Long company = rs.getObject("empresa", Long.class);
                Set<String> permissions = new HashSet<>();
                do {
                    String permission = rs.getString("permiso");
                    if (permission != null) permissions.add(permission);
                } while (rs.next());
                return new Account(id, hash, active, role, company, Set.copyOf(permissions));
            }, value);
        }

        @Override
        public boolean activeCompany(long id) {
            return query("SELECT activo FROM empresas WHERE id=? FOR SHARE", rs -> rs.next() && rs.getBoolean(1), id);
        }

        @Override
        public Person lockPerson(long id) {
            return query("SELECT tipo,empresa,estado FROM personas WHERE id=? FOR UPDATE",
                    rs -> rs.next() ? new Person(rs.getString(1), rs.getObject(2, Long.class), rs.getString(3)) : null, id);
        }

        @Override
        public Visit visit(long id, boolean lock) {
            return query("SELECT id,persona,empresa,estado,inicio,vence,entrada FROM visitas WHERE id=?" +
                    (lock ? " FOR UPDATE" : ""), JdbcUnit::readVisit, id);
        }

        @Override
        public Visit inside(long person) {
            return query("SELECT id,persona,empresa,estado,inicio,vence,entrada FROM visitas WHERE persona_dentro=? FOR UPDATE",
                    JdbcUnit::readVisit, person);
        }

        private static Visit readVisit(ResultSet rs) throws SQLException {
            return rs.next() ? new Visit(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                    rs.getObject(5, LocalDateTime.class), rs.getObject(6, LocalDateTime.class),
                    rs.getObject(7, LocalDateTime.class)) : null;
        }

        @Override
        public long createVisit(long person, long company, AccessPolicy type, String state, LocalDateTime start,
                                LocalDateTime end, long actor, LocalDateTime now) {
            boolean approved = state.equals("APROBADA");
            return insert("INSERT INTO visitas (persona,empresa,tipo,estado,inicio,vence,solicitado_por,decidido_por,decidido_en,motivo_decision) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)", person, company, type.name(), state, start, end, actor,
                    approved ? actor : null, approved ? now : null, approved ? "Registro anticipado autorizado" : null);
        }

        @Override
        public void decide(long visit, String state, long actor, LocalDateTime now, String reason) {
            update("UPDATE visitas SET estado=?,decidido_por=?,decidido_en=?,motivo_decision=? WHERE id=?",
                    state, actor, now, reason, visit);
        }

        @Override
        public void notifyRequest(long visit, long company, LocalDateTime now) {
            execute("""
                    INSERT INTO notificaciones (usuario,visita,evento,mensaje,fecha)
                    SELECT u.id,?,'SOLICITUD',?,? FROM usuarios u
                    JOIN rol_permisos rp ON rp.rol=u.rol AND rp.permiso='DECIDIR'
                    WHERE u.activo=TRUE AND (u.rol<>'FUNCIONARIO' OR u.empresa=?)
                    """, visit, "Solicitud de ingreso pendiente · visita " + visit, now, company);
        }

        @Override
        public void notifyDecision(long visit, String state, LocalDateTime now) {
            execute("""
                    INSERT INTO notificaciones (usuario,visita,evento,mensaje,fecha)
                    SELECT u.id,v.id,'DECISION',?,? FROM usuarios u JOIN visitas v ON v.id=?
                    WHERE u.activo=TRUE AND (u.rol<>'FUNCIONARIO' OR u.empresa=v.empresa)
                      AND (u.id=v.solicitado_por OR EXISTS (
                      SELECT 1 FROM rol_permisos rp WHERE rp.rol=u.rol AND rp.permiso='ACCESO'))
                    """, "Solicitud " + state.toLowerCase(Locale.ROOT) + " · visita " + visit, now, visit);
        }

        @Override
        public Data notifications(long user, String search, LocalDateTime from, LocalDateTime until) {
            String sql = """
                    SELECT id ID,visita Visita,fecha Fecha,evento Evento,mensaje Mensaje,
                      CASE WHEN leida_en IS NULL THEN 'SIN_LEER' ELSE 'LEIDA' END Estado,leida_en LeidaEn
                    FROM notificaciones WHERE usuario=? AND LOCATE(?,LOWER(mensaje))>0
                    """;
            List<Object> parameters = new ArrayList<>(List.of(user, search.strip().toLowerCase(Locale.ROOT)));
            if (from != null) { sql += " AND fecha>=?"; parameters.add(from); }
            if (until != null) { sql += " AND fecha<?"; parameters.add(until); }
            return query(sql + " ORDER BY id DESC", JdbcUnit::readData, parameters.toArray());
        }

        @Override
        public long unreadNotifications(long user) {
            return count("SELECT COUNT(*) FROM notificaciones WHERE usuario=? AND leida_en IS NULL", user);
        }

        @Override
        public boolean markNotificationRead(long user, long id, LocalDateTime now) {
            boolean unread = query("SELECT leida_en FROM notificaciones WHERE id=? AND usuario=? FOR UPDATE", rs -> {
                if (!rs.next()) throw new SicaException("La notificación no está disponible para tu usuario.");
                return rs.getTimestamp(1) == null;
            }, id, user);
            if (unread) update("UPDATE notificaciones SET leida_en=? WHERE id=? AND usuario=?", now, id, user);
            return unread;
        }

        @Override
        public Visit visitByCredential(String hash) {
            return query("SELECT id,persona,empresa,estado,inicio,vence,entrada FROM visitas WHERE qr_hash=?",
                    JdbcUnit::readVisit, hash);
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
            update("UPDATE visitas SET estado='DENTRO',entrada=?,qr_hash=NULL WHERE id=?", now, visit);
        }

        @Override
        public void exit(long visit, String state, LocalDateTime now) {
            update("UPDATE visitas SET estado=?,salida=? WHERE id=?", state, now, visit);
        }

        @Override
        public long company(Long id, Company form) {
            if (id == null) return insert("INSERT INTO empresas (nit,nombre,activo) VALUES (?,?,?)",
                    form.taxId(), form.name(), form.active());
            update("UPDATE empresas SET nit=?,nombre=?,activo=? WHERE id=?", form.taxId(), form.name(), form.active(), id);
            return id;
        }

        @Override
        public long person(Long id, PersonForm form) {
            if (id == null) return insert("INSERT INTO personas (documento,nombre,tipo,empresa,foto,estado) VALUES (?,?,?,?,?,?)",
                    form.document(), form.name(), form.type(), form.company(), form.photo(), form.state());
            update("UPDATE personas SET documento=?,nombre=?,tipo=?,empresa=?,foto=?,estado=? WHERE id=?",
                    form.document(), form.name(), form.type(), form.company(), form.photo(), form.state(), id);
            return id;
        }

        @Override
        public long user(Long id, UserForm form) {
            if (id == null) return insert("INSERT INTO usuarios (correo,nombre,hash,activo,rol,empresa) VALUES (?,?,?,?,?,?)",
                    form.email(), form.name(), form.hash(), form.active(), form.role(), form.company());
            update("UPDATE usuarios SET correo=?,nombre=?,hash=COALESCE(?,hash),activo=?,rol=?,empresa=? WHERE id=?",
                    form.email(), form.name(), form.hash(), form.active(), form.role(), form.company(), id);
            return id;
        }

        @Override
        public void archive(Entity entity, long id) {
            String sql = switch (entity) {
                case USUARIOS -> "UPDATE usuarios SET activo=FALSE WHERE id=?";
                case EMPRESAS -> "UPDATE empresas SET activo=FALSE WHERE id=?";
                case PERSONAS -> "UPDATE personas SET estado='BAJA' WHERE id=?";
            };
            update(sql, id);
        }

        @Override
        public long incident(long visit, long reporter, String description, LocalDateTime now) {
            return insert("INSERT INTO incidentes (visita,reportado_por,descripcion,fecha) VALUES (?,?,?,?)",
                    visit, reporter, description, now);
        }

        @Override
        public void audit(Long actor, String action, String table, Long entity, String details) {
            try {
                update("INSERT INTO bitacora_auditoria (usuario,accion,tabla_afectada,entidad,detalles) VALUES (?,?,?,?,?)",
                        actor, action, table, entity, details);
            } catch (SqlFailure failure) {
                LOG.error("Error al registrar auditoría: SQLState={}, código={}",
                        failure.cause.getSQLState(), failure.cause.getErrorCode());
                throw new SicaException("No se pudo registrar la auditoría. La operación fue cancelada.");
            }
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
                case SOLICITUDES -> filters.append(" AND q.Estado IN ('PENDIENTE','APROBADA','RECHAZADA')");
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
            return query("SELECT q.* FROM (" + sql + ") q" + filters + " ORDER BY q.ID DESC", JdbcUnit::readData, params.toArray());
        }

        private static Data readData(ResultSet rs) throws SQLException {
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

        private void execute(String sql, Object... arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, arguments);
                statement.executeUpdate();
            } catch (SQLException failure) {
                throw new SqlFailure(failure);
            }
        }

        private <T> T query(String sql, Read<T> reader, Object... arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, arguments);
                try (ResultSet rs = statement.executeQuery()) {
                    return reader.read(rs);
                }
            } catch (SQLException failure) {
                throw new SqlFailure(failure);
            }
        }

        private long insert(String sql, Object... arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, arguments);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                    throw new SQLException("No se obtuvo la clave generada");
                }
            } catch (SQLException failure) {
                throw new SqlFailure(failure);
            }
        }

        private void update(String sql, Object... arguments) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, arguments);
                if (statement.executeUpdate() != 1) throw new SicaException("El registro ya no existe.");
            } catch (SQLException failure) {
                throw new SqlFailure(failure);
            }
        }

        private static void bind(PreparedStatement statement, Object[] arguments) throws SQLException {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
        }
    }
}
