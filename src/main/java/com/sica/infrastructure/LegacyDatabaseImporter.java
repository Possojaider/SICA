package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.sql.*;

public final class LegacyDatabaseImporter {
    private LegacyDatabaseImporter() {}

    public static void importFrom(Configuration target, String source) {
        if (source == null || !source.matches("[a-zA-Z0-9_]{1,64}")) throw new SicaException("Nombre de base de origen inválido.");
        try (Connection c = DriverManager.getConnection(target.url(), target.user(), target.password())) {
            if (source.equals(c.getCatalog())) throw new SicaException("La base de origen debe ser diferente a la de destino.");
            try (var lock = c.prepareStatement("SELECT GET_LOCK(?,10)")) {
                lock.setString(1, c.getCatalog() + "_import");
                try (var result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new SicaException("Otra importación está en curso.");
                }
            }
            try {
                String version = "importar-v2-" + source;
                try (var check = c.prepareStatement("SELECT 1 FROM migraciones_esquema WHERE version=?")) {
                    check.setString(1, version);
                    try (var result = check.executeQuery()) { if (result.next()) return; }
                }
                try (var statement = c.createStatement();
                     var result = statement.executeQuery("SELECT (SELECT COUNT(*) FROM usuarios)+(SELECT COUNT(*) FROM empresas)")) {
                    result.next();
                    if (result.getLong(1) != 0) throw new SicaException("La importación requiere un destino sin usuarios ni empresas.");
                }
                preflight(c, source);
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                c.setAutoCommit(false);
                try (Statement s = c.createStatement()) {
                    s.executeUpdate("INSERT IGNORE INTO roles (nombre_rol,codigo) SELECT nombre,id FROM " + source + ".roles");
                    s.executeUpdate("INSERT IGNORE INTO permisos (nombre_permiso,descripcion,codigo) SELECT LOWER(id),'Permiso migrado',id FROM " + source + ".permisos");
                    s.executeUpdate("DELETE rp FROM rol_permisos rp JOIN roles r ON r.id=rp.rol_id JOIN " + source + ".roles old ON old.id=r.codigo");
                    s.executeUpdate("""
                            INSERT INTO rol_permisos (rol_id,permiso_id)
                            SELECT r.id,p.id FROM %s.rol_permisos old
                            JOIN roles r ON r.codigo=old.rol JOIN permisos p ON p.codigo=old.permiso
                            """.formatted(source));
                    s.executeUpdate("INSERT IGNORE INTO persona_estados_acceso (nombre_estado,codigo) SELECT nombre,id FROM " + source + ".persona_estados_acceso");
                    s.executeUpdate("INSERT IGNORE INTO visita_estados (nombre_estado,codigo) SELECT nombre,id FROM " + source + ".visita_estados");
                    s.executeUpdate("INSERT INTO empresas (id,nombre,nit,esta_activa) SELECT id,nombre,nit,activo FROM " + source + ".empresas");
                    s.executeUpdate("""
                            INSERT INTO usuarios (id,nombre,email,password,rol_id,esta_activo,empresa_id)
                            SELECT u.id,u.nombre,u.correo,u.hash,r.id,u.activo,u.empresa FROM %s.usuarios u JOIN roles r ON r.codigo=u.rol
                            """.formatted(source));
                    s.executeUpdate("""
                            INSERT INTO personas (id,nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
                            SELECT p.id,p.nombre,p.documento,p.empresa,IF(p.tipo='TRABAJADOR','Trabajador','Invitado'),e.id,p.foto
                            FROM %s.personas p JOIN persona_estados_acceso e ON e.codigo=p.estado
                            """.formatted(source));
                    boolean motives = hasColumn(c, source, "visitas", "motivo_decision");
                    boolean qr = hasColumn(c, source, "visitas", "qr_hash");
                    String reason = motives ? "v.motivo_decision" : "IF(v.decidido_en IS NULL,NULL,'Decisión anterior a la incorporación de motivos')";
                    s.executeUpdate("""
                            INSERT INTO visitas (id,persona_id,fecha_entrada,fecha_salida,estado_visita_id,visita_aprobada_por,
                              empresa_id,tipo_solicitud,inicio_programado,fin_programado,solicitado_por_id,fecha_decision,motivo_decision,qr_hash)
                            SELECT v.id,v.persona,v.entrada,v.salida,e.id,v.decidido_por,v.empresa,v.tipo,v.inicio,v.vence,
                              v.solicitado_por,v.decidido_en,%s,%s
                            FROM %s.visitas v JOIN visita_estados e ON e.codigo=v.estado
                            """.formatted(reason, qr ? "v.qr_hash" : "NULL", source));
                    s.executeUpdate("""
                            INSERT INTO incidentes (id,visita_id,reportado_por_id,fecha,descripcion)
                            SELECT id,visita,reportado_por,fecha,descripcion FROM %s.incidentes
                            """.formatted(source));
                    s.executeUpdate("""
                            INSERT INTO bitacora_auditoria (id,usuario_id,fecha_hora,accion_realizada,tabla_afectada,registro_id_afectado,detalles)
                            SELECT id,usuario,fecha,accion,tabla_afectada,entidad,detalles FROM %s.bitacora_auditoria
                            """.formatted(source));
                    if (hasTable(c, source, "notificaciones")) {
                        s.executeUpdate("""
                                INSERT INTO notificaciones (id,usuario_id,visita_id,evento,mensaje,fecha,leida_en)
                                SELECT id,usuario,visita,evento,mensaje,fecha,leida_en FROM %s.notificaciones
                                """.formatted(source));
                    }
                    verifyCounts(c, source);
                    try (var marker = c.prepareStatement("INSERT INTO migraciones_esquema (version,origen) VALUES (?,?)")) {
                        marker.setString(1, version);
                        marker.setString(2, source);
                        marker.executeUpdate();
                    }
                    s.executeUpdate("""
                            INSERT INTO bitacora_auditoria (accion_realizada,tabla_afectada,detalles)
                            VALUES ('MIGRAR_BASE_ACADEMICA','migraciones_esquema','Datos importados desde el esquema anterior')
                            """);
                    c.commit();
                } catch (SQLException | RuntimeException failure) {
                    c.rollback();
                    throw failure;
                }
            } finally {
                try (var release = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, c.getCatalog() + "_import");
                    release.executeQuery().close();
                }
            }
        } catch (SQLException failure) {
            org.slf4j.LoggerFactory.getLogger(LegacyDatabaseImporter.class).error("Falló la importación del esquema anterior", failure);
            throw new SicaException("No se pudo importar la base anterior. La transacción de datos fue cancelada.");
        }
    }

    private static void preflight(Connection c, String source) throws SQLException {
        try (var statement = c.createStatement();
             var rows = statement.executeQuery("""
                     SELECT
                       (SELECT COUNT(*) FROM %1$s.usuarios WHERE CHAR_LENGTH(nombre)>100 OR CHAR_LENGTH(correo)>100 OR id>2147483647)
                       +(SELECT COUNT(*) FROM %1$s.empresas WHERE CHAR_LENGTH(nombre)>100 OR id>2147483647)
                       +(SELECT COUNT(*) FROM %1$s.personas WHERE CHAR_LENGTH(nombre)>100 OR CHAR_LENGTH(documento)>20 OR CHAR_LENGTH(foto)>255 OR id>2147483647)
                       +(SELECT COUNT(*) FROM %1$s.visitas WHERE id>2147483647)
                       +(SELECT COUNT(*) FROM %1$s.incidentes WHERE id>2147483647)
                       +(SELECT COUNT(*) FROM %1$s.roles WHERE CHAR_LENGTH(nombre)>50)
                       +(SELECT COUNT(*) FROM %1$s.persona_estados_acceso WHERE CHAR_LENGTH(nombre)>50)
                       +(SELECT COUNT(*) FROM %1$s.visita_estados WHERE CHAR_LENGTH(nombre)>50)
                       +(SELECT COUNT(*) FROM %1$s.bitacora_auditoria WHERE entidad>2147483647)
                     """.formatted(source))) {
            rows.next();
            if (rows.getLong(1) > 0) throw new SicaException("Hay registros que superan los tamaños del esquema entregado. Revisa los datos antes de migrar.");
        }
    }

    private static void verifyCounts(Connection c, String source) throws SQLException {
        for (String table : new String[] {"usuarios","empresas","personas","visitas","incidentes","bitacora_auditoria"}) {
            try (var statement = c.createStatement();
                 var result = statement.executeQuery("SELECT (SELECT COUNT(*) FROM " + table +
                         ")=(SELECT COUNT(*) FROM " + source + "." + table + ")")) {
                result.next();
                if (!result.getBoolean(1)) throw new SicaException("La importación no pudo conservar todos los registros de " + table + ".");
            }
        }
        try (var statement = c.createStatement();
             var result = statement.executeQuery("""
                     SELECT (SELECT COUNT(*) FROM rol_permisos rp JOIN roles r ON r.id=rp.rol_id
                              JOIN %1$s.roles old ON old.id=r.codigo)
                           =(SELECT COUNT(*) FROM %1$s.rol_permisos)
                     """.formatted(source))) {
            result.next();
            if (!result.getBoolean(1)) throw new SicaException("La importación no pudo conservar los permisos anteriores.");
        }
    }

    private static boolean hasColumn(Connection c, String database, String table, String column) throws SQLException {
        try (var columns = c.getMetaData().getColumns(database, null, table, column)) { return columns.next(); }
    }

    private static boolean hasTable(Connection c, String database, String table) throws SQLException {
        try (var tables = c.getMetaData().getTables(database, null, table, new String[] {"TABLE"})) { return tables.next(); }
    }
}
