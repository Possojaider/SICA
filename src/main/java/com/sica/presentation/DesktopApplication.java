package com.sica.presentation;

import com.sica.domain.Session;

import com.sica.domain.port.in.SicaUseCases;
import com.sica.infrastructure.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.io.IOException;

public final class DesktopApplication extends Application {
    private Stage stage;
    private SicaUseCases sica;
    private BackgroundTasks tasks;
    private ShellController shell;
    private LoginController login;
    private com.sica.domain.port.out.SavedLoginStore savedLogins;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        tasks = new BackgroundTasks();
        stage.setTitle("SICA · Zona Acme");
        stage.setMinWidth(1040);
        stage.setMinHeight(700);
        tasks.submit(() -> {
            Configuration configuration = Configuration.environment();
            savedLogins = MacKeychainLoginStore.forConnection(configuration.url());
            return ApplicationFactory.create(configuration);
        }, application -> {
            sica = application;
            showLogin();
        }, failure -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, failure.getMessage());
            alert.setHeaderText("Configuración de SICA");
            alert.showAndWait();
            stage.close();
            javafx.application.Platform.exit();
        });
    }

    private void showLogin() {
        if (shell != null) {
            shell.close();
            shell = null;
        }
        login = new LoginController(sica, tasks, savedLogins, this::showShell, message -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message);
            alert.initOwner(stage);
            alert.setHeaderText("Cuenta recordada");
            alert.show();
        });
        load("/fxml/login.fxml", login);
    }

    private void showShell(Session session) {
        if (login != null) {
            login.close();
            login = null;
        }
        shell = new ShellController(sica, session, tasks, this::showLogin);
        load("/fxml/shell.fxml", shell);
    }

    private void load(String resource, Object controller) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
            loader.setControllerFactory(type -> controller);
            Scene scene = new Scene(loader.load(), 1280, 820);
            scene.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (IOException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("No se pudo cargar la vista {}", resource, failure);
            new Alert(Alert.AlertType.ERROR, "No se pudo cargar la pantalla. Revisa los registros técnicos.").showAndWait();
            stage.close();
            javafx.application.Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (login != null) login.close();
        if (shell != null) shell.close();
        if (tasks != null) tasks.close();
    }
}
