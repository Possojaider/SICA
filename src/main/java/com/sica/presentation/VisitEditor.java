package com.sica.presentation;

import com.sica.domain.Session;

import com.sica.domain.port.in.SicaUseCases;
import com.sica.domain.AccessPolicy;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store.*;
import javafx.stage.Window;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class VisitEditor {
    private final SicaUseCases sica;
    private final Session session;
    private final BackgroundTasks tasks;
    private final Window owner;
    private final Runnable saved;

    VisitEditor(SicaUseCases sica, Session session, BackgroundTasks tasks, Window owner, Runnable saved) {
        this.sica = sica;
        this.session = session;
        this.tasks = tasks;
        this.owner = owner;
        this.saved = saved;
    }

    void request(List<Option> companies, Data people, Set<String> permissions) {
        List<String> options = people.rows().stream().map(row -> row.get(2) + " · " + row.get(1) + " · " + row.getFirst()).toList();
        List<String> hosts = companies.stream().map(Option::toString).toList();
        if (options.isEmpty() || hosts.isEmpty()) throw new SicaException("Registra una empresa activa y una persona antes de solicitar la visita.");
        List<String> types = new ArrayList<>();
        if (permissions.contains("PRE_REGISTRAR")) types.add("PREVIA");
        if (permissions.contains("SOLICITAR")) types.addAll(List.of("NO_ANUNCIADA", "SIN_CARNET"));
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime now = sica.currentTime().withSecond(0).withNano(0);
        new FormDialog(owner, "Registrar visita o solicitud")
                .choices("person", "Persona · documento · ID", options, options.getFirst())
                .choices("host", "Empresa anfitriona", hosts, hosts.getFirst())
                .choices("type", "Escenario", types, types.getFirst())
                .text("plate", "Placa del vehículo · opcional", "")
                .text("start", "Inicio · AAAA-MM-DDTHH:MM", now.format(format))
                .text("end", "Fin · AAAA-MM-DDTHH:MM", now.plusHours(1).format(format))
                .show(tasks, values -> {
                    long person = Long.parseLong(values.get("person").substring(values.get("person").lastIndexOf(" · ") + 3));
                    long company = Objects.requireNonNull(EntityEditor.companyId(values.get("host")));
                    AccessPolicy type = AccessPolicy.valueOf(values.get("type"));
                    LocalDateTime start = LocalDateTime.parse(values.get("start"));
                    LocalDateTime end = LocalDateTime.parse(values.get("end"));
                    return () -> sica.request(session, person, company, type, start, end, values.get("plate"));
                }, saved);
    }

    void decide(long visit, boolean approve) {
        new FormDialog(owner, (approve ? "Aprobar" : "Rechazar") + " solicitud " + visit)
                .multiline("reason", "Motivo obligatorio")
                .submitLabel(approve ? "Aprobar" : "Rechazar")
                .show(tasks, values -> () -> {
                    new com.sica.application.VisitCommand.Decide(visit, approve, values.get("reason")).execute(sica, session);
                    return null;
                }, saved);
    }

    void scan() {
        new FormDialog(owner, "Registrar entrada mediante QR")
                .text("code", "Código del lector o del QR", "")
                .submitLabel("Registrar entrada")
                .show(tasks, values -> () -> {
                    sica.checkInWithCredential(session, values.get("code").strip());
                    return null;
                }, saved);
    }

    void incident(String selectedVisit) {
        new FormDialog(owner, "Reportar incidente")
                .text("visit", "ID de visita", selectedVisit)
                .multiline("description", "Descripción")
                .show(tasks, values -> {
                    long visit = Long.parseLong(values.get("visit"));
                    return () -> sica.incident(session, visit, values.get("description"));
                }, saved);
    }
}
