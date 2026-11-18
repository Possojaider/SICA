package com.sica.infrastructure;

import com.sica.domain.port.out.Store.Report;

final class ReportQuery {
    private ReportQuery() {}

    private static final String VISITS = """
            SELECT v.id ID, p.documento Documento, p.nombre Nombre, v.empresa Empresa, e.nombre Anfitrion,
              v.tipo Tipo, v.estado Estado, v.inicio Fecha, v.vence Vence, v.entrada Entrada, v.salida Salida,
              v.decidido_en Decidido, p.foto Foto
            FROM visitas v JOIN personas p ON p.id=v.persona JOIN empresas e ON e.id=v.empresa
            """;

    static String select(Report report) {
        return switch (report) {
            case EMPRESAS -> "SELECT id ID,nit NIT,nombre Nombre,activo Activo FROM empresas";
            case PERSONAS -> """
                    SELECT p.id ID,p.documento Documento,p.nombre Nombre,p.tipo Tipo,p.empresa Empresa,
                           e.nombre Organizacion,p.foto Foto,p.estado Estado
                    FROM personas p LEFT JOIN empresas e ON p.empresa=e.id
                    """;
            case USUARIOS -> "SELECT id ID,correo Correo,nombre Nombre,activo Activo,rol Rol,empresa Empresa FROM usuarios";
            case VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES -> VISITS;
            case INCIDENTES -> """
                    SELECT i.id ID,i.visita Visita,i.reportado_por ReportadoPor,i.fecha Fecha,i.descripcion Descripcion,
                      v.empresa Empresa FROM incidentes i JOIN visitas v ON v.id=i.visita
                    """;
            case AUDITORIA -> """
                    SELECT id ID,usuario Usuario,fecha Fecha,accion Accion,tabla_afectada Tabla,entidad Entidad,detalles Detalles
                    FROM bitacora_auditoria
                    """;
        };
    }

    static boolean dated(Report report) {
        return switch (report) {
            case EMPRESAS, PERSONAS, USUARIOS -> false;
            default -> true;
        };
    }

    static boolean scoped(Report report) {
        return switch (report) {
            case PERSONAS, VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES, INCIDENTES -> true;
            default -> false;
        };
    }

    static String searchColumn(Report report) {
        return switch (report) {
            case EMPRESAS -> "CONCAT_WS(' ',q.ID,q.NIT,q.Nombre)";
            case PERSONAS -> "CONCAT_WS(' ',q.ID,q.Documento,q.Nombre,q.Organizacion,q.Estado)";
            case USUARIOS -> "CONCAT_WS(' ',q.ID,q.Correo,q.Nombre,q.Rol,q.Empresa)";
            case VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES ->
                    "CONCAT_WS(' ',q.ID,q.Documento,q.Nombre,q.Anfitrion,q.Estado,q.Tipo)";
            case INCIDENTES -> "CONCAT_WS(' ',q.ID,q.Visita,q.ReportadoPor,q.Descripcion)";
            case AUDITORIA -> "CONCAT_WS(' ',q.ID,q.Usuario,q.Accion,q.Tabla,q.Entidad,q.Detalles)";
        };
    }
}
