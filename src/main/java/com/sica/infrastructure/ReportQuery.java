package com.sica.infrastructure;

import com.sica.domain.port.out.Store.Report;

final class ReportQuery {
    private ReportQuery() {}

    private static final String VISITS = """
            SELECT v.id ID,p.documento_identidad Documento,p.nombre Nombre,v.empresa_id Empresa,e.nombre Anfitrion,
              v.tipo_solicitud Tipo,ev.codigo Estado,v.inicio_programado Fecha,v.fin_programado Vence,
              v.fecha_entrada Entrada,v.fecha_salida Salida,v.fecha_decision Decidido,p.url_foto Foto,
              v.visita_aprobada_por DecididoPor,v.motivo_decision Motivo,v.vehiculo_placa Placa
            FROM visitas v JOIN personas p ON p.id=v.persona_id LEFT JOIN empresas e ON e.id=v.empresa_id
            LEFT JOIN visita_estados ev ON ev.id=v.estado_visita_id
            """;

    static String select(Report report) {
        return switch (report) {
            case NOTIFICACIONES -> throw new IllegalArgumentException("La bandeja requiere el usuario destinatario.");
            case EMPRESAS -> "SELECT id ID,nit NIT,nombre Nombre,esta_activa Activo,contacto_principal Contacto FROM empresas";
            case PERSONAS -> """
                    SELECT p.id ID,p.documento_identidad Documento,p.nombre Nombre,UPPER(p.tipo_persona) Tipo,
                      p.empresa_id Empresa,e.nombre Organizacion,p.url_foto Foto,ea.codigo Estado
                    FROM personas p LEFT JOIN empresas e ON p.empresa_id=e.id
                    LEFT JOIN persona_estados_acceso ea ON ea.id=p.estado_acceso_id
                    """;
            case USUARIOS -> """
                    SELECT u.id ID,u.email Correo,u.nombre Nombre,u.esta_activo Activo,r.codigo Rol,u.empresa_id Empresa
                    FROM usuarios u LEFT JOIN roles r ON r.id=u.rol_id
                    """;
            case VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES -> VISITS;
            case INCIDENTES -> """
                    SELECT i.id ID,i.visita_id Visita,i.reportado_por_id ReportadoPor,i.fecha Fecha,i.descripcion Descripcion,
                      v.empresa_id Empresa FROM incidentes i JOIN visitas v ON v.id=i.visita_id
                    """;
            case AUDITORIA -> """
                    SELECT id ID,usuario_id Usuario,fecha_hora Fecha,accion_realizada Accion,tabla_afectada Tabla,
                      registro_id_afectado Entidad,detalles Detalles FROM bitacora_auditoria
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
            case NOTIFICACIONES -> "q.Mensaje";
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
