package com.sica.presentation;

import com.sica.domain.port.out.Store.Credential;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.*;
import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;

final class CredentialDialog {
    private CredentialDialog() {}

    static void show(Window owner, Credential credential, byte[] png, BackgroundTasks tasks) {
        try {
            FXMLLoader loader = new FXMLLoader(CredentialDialog.class.getResource("/fxml/credential.fxml"));
            DialogPane pane = loader.load();
            pane.getStylesheets().add(CredentialDialog.class.getResource("/css/sica.css").toExternalForm());
            ((Label) loader.getNamespace().get("visit")).setText("Visita " + credential.visit());
            ((Label) loader.getNamespace().get("expires")).setText("Vence el " +
                    credential.expires().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            ((TextField) loader.getNamespace().get("code")).setText(credential.code());
            ((ImageView) loader.getNamespace().get("qr")).setImage(new Image(new ByteArrayInputStream(png)));
            Label message = (Label) loader.getNamespace().get("message");
            Button save = (Button) loader.getNamespace().get("save");
            save.setOnAction(event -> {
                FileChooser chooser = new FileChooser();
                chooser.setInitialFileName("sica-visita-" + credential.visit() + ".png");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen PNG", "*.png"));
                File file = chooser.showSaveDialog(owner);
                if (file == null) return;
                save.setDisable(true);
                tasks.submit(() -> {
                    Files.write(file.toPath(), png);
                    return null;
                }, result -> {
                    message.setText("Imagen guardada.");
                    save.setDisable(false);
                }, failure -> {
                    org.slf4j.LoggerFactory.getLogger(CredentialDialog.class).error("No se pudo guardar el QR", failure);
                    message.setText("No se pudo guardar la imagen.");
                    save.setDisable(false);
                });
            });
            Dialog<Void> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("QR · SICA");
            dialog.setDialogPane(pane);
            dialog.show();
        } catch (IOException failure) {
            throw new com.sica.domain.SicaException("No se pudo cargar la vista de credenciales.");
        }
    }
}
