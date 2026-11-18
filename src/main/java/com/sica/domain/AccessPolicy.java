package com.sica.domain;

import java.time.LocalDateTime;

public enum AccessPolicy {
    PREVIA, NO_ANUNCIADA, SIN_CARNET;

    public void validate(String personType, Long personCompany, long host, LocalDateTime start,
                         LocalDateTime end, LocalDateTime now) {
        if (start == null || end == null || now == null) throw new SicaException("Completa el intervalo autorizado.");
        if (!end.isAfter(start) || !end.isAfter(now)) {
            throw new SicaException("El fin debe ser posterior al inicio y a la hora actual.");
        }
        if (this == SIN_CARNET) {
            if (!"TRABAJADOR".equals(personType) || personCompany == null || personCompany != host) {
                throw new SicaException("La excepción requiere un trabajador de la empresa indicada.");
            }
            if (!start.toLocalDate().equals(now.toLocalDate()) ||
                    end.isAfter(now.toLocalDate().plusDays(1).atStartOfDay())) {
                throw new SicaException("La autorización sin carnet solo puede cubrir el día actual.");
            }
        } else if (!"INVITADO".equals(personType)) {
            throw new SicaException("El registro de visita requiere una persona de tipo invitado.");
        }
    }

    public static void authorize(String state, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (!"APROBADA".equals(state)) throw new SicaException("La visita requiere una aprobación vigente.");
        if (now.isBefore(start)) throw new SicaException("El horario autorizado aún no comienza.");
        if (!now.isBefore(end)) throw new SicaException("La autorización expiró.");
    }

}
