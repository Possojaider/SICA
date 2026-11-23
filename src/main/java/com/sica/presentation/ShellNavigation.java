package com.sica.presentation;

import com.sica.domain.port.out.Store.Report;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import java.util.*;
import java.util.function.Consumer;

final class ShellNavigation {
    record Page(String name, Report report) {}
    private ShellNavigation() {}

    static void build(VBox navigation, Page current, Set<String> permissions, Consumer<Page> select) {
        List<Page> pages = new ArrayList<>();
        if (permissions.contains("VISITAS_LEER")) pages.add(new Page("Panel principal", Report.VISITAS));
        pages.add(new Page("Notificaciones", Report.NOTIFICACIONES));
        if (permissions.contains("USUARIOS_LEER")) pages.add(new Page("Usuarios", Report.USUARIOS));
        if (permissions.contains("EMPRESAS_LEER")) pages.add(new Page("Empresas", Report.EMPRESAS));
        if (permissions.contains("PERSONAS_LEER")) pages.add(new Page("Personas", Report.PERSONAS));
        if (permissions.contains("VISITAS_LEER")) pages.add(new Page("Registro de visitas", Report.VISITAS));
        if (permissions.containsAll(Set.of("ACCESO", "VISITAS_LEER"))) pages.add(new Page("Entradas y salidas", Report.VISITAS));
        if (permissions.contains("VISITAS_LEER")) pages.add(new Page("Solicitudes pendientes", Report.SOLICITUDES));
        if (permissions.containsAll(Set.of("DECIDIR", "VISITAS_LEER"))) pages.add(new Page("Aprobar solicitudes", Report.SOLICITUDES));
        if (permissions.contains("INCIDENTES")) pages.add(new Page("Incidentes", Report.INCIDENTES));
        if (permissions.contains("AUDITORIA")) pages.add(new Page("Auditoría", Report.AUDITORIA));
        if (permissions.contains("REPORTES")) pages.add(new Page("Reportes", Report.DENTRO));
        navigation.getChildren().clear();
        for (Page destination : pages) {
            Button button = new Button(destination.name());
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-button");
            if (current.name().equals(destination.name())) button.getStyleClass().add("selected");
            button.setOnAction(event -> select.accept(destination));
            navigation.getChildren().add(button);
        }
    }

    static Map<Report, String> reports(Set<String> permissions) {
        Map<Report, String> names = new LinkedHashMap<>();
        names.put(Report.DENTRO, "Personas dentro del complejo");
        names.put(Report.HISTORIAL, "Historial de entradas y salidas");
        names.put(Report.VISITAS, "Visitas por fecha");
        names.put(Report.DECISIONES, "Solicitudes aprobadas y rechazadas");
        if (permissions.contains("INCIDENTES")) names.put(Report.INCIDENTES, "Incidentes registrados");
        if (permissions.contains("AUDITORIA")) names.put(Report.AUDITORIA, "Historial de auditoría");
        return names;
    }
}
