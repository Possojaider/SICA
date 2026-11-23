package com.sica.domain;

import com.sica.domain.port.out.Store.Entity;
import com.sica.domain.port.out.Store.Report;

/** Nombres compartidos entre autorización de aplicación y navegación. */
public final class Permissions {
    private Permissions() {}
    public static String create(Entity entity) { return entity.name() + "_CREAR"; }
    public static String edit(Entity entity) { return entity.name() + "_EDITAR"; }
    public static String archive(Entity entity) { return entity.name() + "_BAJA"; }
    public static String read(Report report) {
        return switch (report) {
            case NOTIFICACIONES -> null;
            case EMPRESAS, USUARIOS, PERSONAS -> report.name() + "_LEER";
            case INCIDENTES, AUDITORIA -> report.name();
            case VISITAS, SOLICITUDES -> "VISITAS_LEER";
            default -> "REPORTES";
        };
    }
}
