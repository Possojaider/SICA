package com.sica.presentation;

import com.sica.domain.Session;

import com.sica.domain.port.in.SicaUseCases;
import com.sica.domain.port.out.Store.*;
import javafx.stage.Window;
import java.util.*;

final class EntityEditor {
    private final SicaUseCases sica;
    private final Session session;
    private final BackgroundTasks tasks;
    private final Window owner;
    private final List<Option> companies;
    private final Runnable saved;

    EntityEditor(SicaUseCases sica, Session session, BackgroundTasks tasks, Window owner,
                 List<Option> companies, Runnable saved) {
        this.sica = sica;
        this.session = session;
        this.tasks = tasks;
        this.owner = owner;
        this.companies = companies;
        this.saved = saved;
    }

    void company(Map<String, String> row) {
        Long id = id(row);
        new FormDialog(owner, id == null ? "Nueva empresa" : "Editar empresa")
                .text("nit", "NIT", row.getOrDefault("NIT", ""))
                .text("name", "Nombre", row.getOrDefault("Nombre", ""))
                .choices("active", "Estado", List.of("Activa", "Inactiva"), "0".equals(row.get("Activo")) ? "Inactiva" : "Activa")
                .show(tasks, values -> () -> sica.saveCompany(session, id,
                        new Company(values.get("nit"), values.get("name"), values.get("active").equals("Activa"))), saved);
    }

    void person(Map<String, String> row, boolean manage) {
        Long id = id(row);
        FormDialog form = new FormDialog(owner, id == null ? "Registrar persona" : "Editar persona")
                .text("doc", "Documento", row.getOrDefault("Documento", ""))
                .text("name", "Nombre completo", row.getOrDefault("Nombre", ""))
                .choices("type", "Tipo", manage ? List.of("INVITADO", "TRABAJADOR") : List.of("INVITADO"),
                        row.getOrDefault("Tipo", "INVITADO"))
                .choices("company", "Empresa", companyOptions(), companyValue(row.get("Empresa")))
                .text("photo", "Foto · URL HTTPS", row.getOrDefault("Foto", ""))
                .choices("state", "Acceso", manage ? List.of("HABILITADO", "RESTRINGIDO") : List.of("HABILITADO"),
                        row.getOrDefault("Estado", "HABILITADO"));
        form.show(tasks, values -> {
            Long company = companyId(values.get("company"));
            return () -> sica.savePerson(session, id, new PersonForm(values.get("doc"), values.get("name"),
                    values.get("type"), company, values.get("photo"), values.get("state")));
        }, saved);
    }

    void user(Map<String, String> row) {
        Long id = id(row);
        new FormDialog(owner, id == null ? "Nuevo usuario" : "Editar usuario")
                .text("email", "Correo", row.getOrDefault("Correo", ""))
                .text("name", "Nombre", row.getOrDefault("Nombre", ""))
                .secret("password", id == null ? "Contraseña" : "Contraseña · vacío conserva")
                .choices("role", "Rol", List.of("SUPER", "SUPERVISOR", "GUARDA", "FUNCIONARIO"),
                        row.getOrDefault("Rol", "GUARDA"))
                .choices("company", "Empresa", companyOptions(), companyValue(row.get("Empresa")))
                .choices("active", "Estado", List.of("Activo", "Inactivo"), "0".equals(row.get("Activo")) ? "Inactivo" : "Activo")
                .show(tasks, values -> {
                    Long company = companyId(values.get("company"));
                    return () -> sica.saveUser(session, id, new UserForm(values.get("email"), values.get("name"),
                            values.get("password"), values.get("active").equals("Activo"), values.get("role"), company));
                }, saved);
    }

    private List<String> companyOptions() {
        List<String> result = new ArrayList<>();
        result.add("Sin empresa");
        companies.forEach(company -> result.add(company.toString()));
        return result;
    }

    private String companyValue(String id) {
        return companies.stream().filter(company -> Long.toString(company.id()).equals(id))
                .map(Option::toString).findFirst().orElse("Sin empresa");
    }

    static Long companyId(String value) {
        return value.equals("Sin empresa") ? null : Long.parseLong(value.substring(value.lastIndexOf(" · ") + 3));
    }

    private static Long id(Map<String, String> row) {
        return row.containsKey("ID") ? Long.valueOf(row.get("ID")) : null;
    }
}
