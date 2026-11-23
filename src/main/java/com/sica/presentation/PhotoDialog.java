package com.sica.presentation;
import com.sica.domain.SicaException;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
final class PhotoDialog {
    private PhotoDialog() {}
    static void show(Window owner, String url) {
        if (url.isBlank()) throw new SicaException("Esta persona no tiene una fotografía registrada.");
        Image image = new Image(url, 500, 500, true, true, true);
        ImageView view = new ImageView(image);
        view.setId("identity-photo");
        view.setFitWidth(460);
        view.setFitHeight(400);
        view.setPreserveRatio(true);
        StackPane frame = new StackPane(view);
        frame.setMinSize(460, 400);
        frame.setPrefSize(460, 400);
        Label message = new Label("Cargando fotografía…");
        Runnable updateMessage = () -> message.setText(image.isError() ? "No se pudo cargar la fotografía."
                : image.getProgress() >= 1 ? "" : "Cargando fotografía…");
        image.progressProperty().addListener((observable, oldValue, value) -> updateMessage.run());
        image.errorProperty().addListener((observable, oldValue, failed) -> updateMessage.run());
        updateMessage.run();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setHeaderText("Fotografía de identificación");
        alert.getDialogPane().setContent(new VBox(10, frame, message));
        alert.setOnHidden(event -> image.cancel());
        alert.show();
    }
}
