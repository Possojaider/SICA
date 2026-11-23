package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcDirectory extends JdbcSql implements DirectoryRepository, AuthenticationRepository {
    JdbcDirectory(Connection connection) { super(connection); }
    @Override
    public List<Option> companies(Long company) {
        String sql = "SELECT id,nombre FROM empresas WHERE esta_activa=TRUE" +
                (company == null ? "" : " AND id=?") + " ORDER BY nombre";
        return query(sql, rs -> {
            List<Option> result = new ArrayList<>();
            while (rs.next()) result.add(new Option(rs.getLong(1), rs.getString(2)));
            return List.copyOf(result);
        }, company == null ? new Object[0] : new Object[] {company});
    }

    @Override
    public boolean activeCompany(long id) {
        return query("SELECT esta_activa FROM empresas WHERE id=? FOR SHARE", rs -> rs.next() && rs.getBoolean(1), id);
    }

    @Override
    public Person lockPerson(long id) {
        query("SELECT id FROM personas WHERE id=? FOR UPDATE", rs -> rs.next(), id);
        return query("""
                SELECT UPPER(p.tipo_persona),p.empresa_id,e.codigo FROM personas p
                LEFT JOIN persona_estados_acceso e ON e.id=p.estado_acceso_id WHERE p.id=?
                """, rs -> rs.next() ? new Person(rs.getString(1), rs.getObject(2, Long.class), rs.getString(3)) : null, id);
    }

    @Override
    public long company(Long id, Company form) {
        if (id == null) return insert("INSERT INTO empresas (nit,nombre,esta_activa,contacto_principal) VALUES (?,?,?,?)",
                form.taxId(), form.name(), form.active(), form.contact());
        update("UPDATE empresas SET nit=?,nombre=?,esta_activa=?,contacto_principal=? WHERE id=?",
                form.taxId(), form.name(), form.active(), form.contact(), id);
        return id;
    }

    @Override
    public long person(Long id, PersonForm form) {
        String type = "TRABAJADOR".equals(form.type()) ? "Trabajador" : "Invitado";
        if (id == null) return insert("""
                INSERT INTO personas (documento_identidad,nombre,tipo_persona,empresa_id,url_foto,estado_acceso_id)
                VALUES (?,?,?,?,?,(SELECT id FROM persona_estados_acceso WHERE codigo=?))
                """, form.document(), form.name(), type, form.company(), form.photo(), form.state());
        update("""
                UPDATE personas SET documento_identidad=?,nombre=?,tipo_persona=?,empresa_id=?,url_foto=?,
                  estado_acceso_id=(SELECT id FROM persona_estados_acceso WHERE codigo=?) WHERE id=?
                """, form.document(), form.name(), type, form.company(), form.photo(), form.state(), id);
        return id;
    }

    @Override
    public long user(Long id, UserForm form) {
        if (id == null) return insert("""
                INSERT INTO usuarios (email,nombre,password,esta_activo,rol_id,empresa_id)
                VALUES (?,?,?,?,(SELECT id FROM roles WHERE codigo=?),?)
                """, form.email(), form.name(), form.hash(), form.active(), form.role(), form.company());
        update("""
                UPDATE usuarios SET email=?,nombre=?,password=COALESCE(?,password),esta_activo=?,
                  rol_id=(SELECT id FROM roles WHERE codigo=?),empresa_id=? WHERE id=?
                """, form.email(), form.name(), form.hash(), form.active(), form.role(), form.company(), id);
        return id;
    }

    @Override
    public void archive(Entity entity, long id) {
        String sql = switch (entity) {
            case USUARIOS -> "UPDATE usuarios SET esta_activo=FALSE WHERE id=?";
            case EMPRESAS -> "UPDATE empresas SET esta_activa=FALSE WHERE id=?";
            case PERSONAS -> "UPDATE personas SET estado_acceso_id=(SELECT id FROM persona_estados_acceso WHERE codigo='BAJA') WHERE id=?";
        };
        update(sql, id);
    }

    @Override
    public Account byEmail(String email) {
        return accountWhere("email", email);
    }

    @Override
    public Account account(long id) {
        return accountWhere("id", id);
    }
    private Account accountWhere(String column, Object value) {
        return query("SELECT u.id,u.password hash,u.esta_activo activo,r.codigo rol,u.empresa_id empresa,p.codigo permiso FROM usuarios u " +
                "LEFT JOIN roles r ON r.id=u.rol_id LEFT JOIN rol_permisos rp ON rp.rol_id=r.id " +
                "LEFT JOIN permisos p ON p.id=rp.permiso_id WHERE u." + column + "=?", rs -> {
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
}
