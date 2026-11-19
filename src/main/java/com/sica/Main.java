package com.sica;

import com.sica.infrastructure.*;
import com.sica.presentation.DesktopApplication;
import com.sica.domain.SicaException;
import javafx.application.Application;
import java.util.Arrays;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            if (Arrays.asList(args).contains("--init-db")) {
                DatabaseInstaller.install(Configuration.environment());
                System.out.println("Esquema y catálogos instalados.");
            } else if (Arrays.asList(args).contains("--import-legacy")) {
                LegacyDatabaseImporter.importFrom(Configuration.environment(),
                        LocalEnvironment.load().getOrDefault("SICA_LEGACY_DATABASE", "sica"));
                System.out.println("Datos anteriores importados al esquema académico.");
            } else if (Arrays.asList(args).contains("--seed-demo")) {
                DemoDataInstaller.install(Configuration.environment());
                System.out.println("Datos de ejemplo insertados.");
            } else if (Arrays.asList(args).contains("--migrate-db")) {
                DatabaseMigrator.upgrade(Configuration.environment());
                System.out.println("Esquema actualizado. Aplica db/privileges.sql a la cuenta de aplicación.");
            } else if (Arrays.asList(args).contains("--create-admin")) {
                var console = System.console();
                String email = LocalEnvironment.load().get("SICA_ADMIN_EMAIL");
                String password = LocalEnvironment.load().get("SICA_ADMIN_PASSWORD");
                if (email == null && console != null) email = console.readLine("Correo del administrador: ");
                if (password == null && console != null) password = new String(console.readPassword("Contraseña (12 caracteres mínimo): "));
                if (email == null || password == null) throw new SicaException("Define SICA_ADMIN_EMAIL y SICA_ADMIN_PASSWORD o usa una consola interactiva.");
                ApplicationFactory.create(Configuration.environment()).bootstrap(email, password);
                System.out.println("Administrador creado y auditado.");
            } else {
                Application.launch(DesktopApplication.class, args);
            }
        } catch (SicaException failure) {
            System.err.println(failure.getMessage());
            System.exit(1);
        }
    }
}
