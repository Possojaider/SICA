package com.sica.presentation;

import com.sica.domain.SicaException;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.stage.Window;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

final class FormDialog {
    private final Dialog<Void> dialog = new Dialog<>();
    private final GridPane fields = new GridPane();
    private final Map<String, Supplier<String>> inputs = new LinkedHashMap<>();
    private final Label error = new Label();
    private final ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    private int row;

    FormDialog(Window owner, String title) {
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());
        fields.setHgap(16);
        fields.setVgap(12);
        fields.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().setPrefWidth(610);
        error.setWrapText(true);
        error.getStyleClass().add("error");
    }

    FormDialog text(String key, String label, String value) {
        TextField input = new TextField(value);
        input.setPrefWidth(320);
        add(key, label, input, input::getText);
        return this;
    }

    FormDialog secret(String key, String label) {
        PasswordField input = new PasswordField();
        input.setPromptText("12 caracteres mínimo");
        add(key, label, input, input::getText);
        return this;
    }

    FormDialog choices(String key, String label, List<String> values, String selected) {
        ComboBox<String> input = new ComboBox<>();
        input.getItems().setAll(values);
        input.setMaxWidth(Double.MAX_VALUE);
        input.setValue(values.contains(selected) ? selected : values.stream().findFirst().orElse(""));
        add(key, label, input, () -> Objects.toString(input.getValue(), ""));
        return this;
    }

    FormDialog multiline(String key, String label) {
        TextArea input = new TextArea();
        input.setWrapText(true);
        input.setPrefRowCount(5);
        add(key, label, input, input::getText);
        return this;
    }

    private void add(String key, String label, Node input, Supplier<String> reader) {
        fields.add(new Label(label), 0, row);
        fields.add(input, 1, row++);
        inputs.put(key, reader);
    }

    void show(BackgroundTasks tasks, Function<Map<String, String>, Callable<?>> command, Runnable onSaved) {
        fields.add(error, 0, row, 2, 1);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            Map<String, String> values = new LinkedHashMap<>();
            inputs.forEach((key, reader) -> values.put(key, reader.get()));
            Callable<?> action;
            try {
                action = command.apply(Map.copyOf(values));
            } catch (RuntimeException invalid) {
                error.setText(invalid instanceof SicaException ? invalid.getMessage() : "Revisa los identificadores y las fechas (AAAA-MM-DDTHH:MM).");
                return;
            }
            save.setDisable(true);
            cancel.setDisable(true);
            dialog.setOnCloseRequest(close -> close.consume());
            error.setText("Guardando…");
            tasks.submit(action, result -> {
                dialog.setOnCloseRequest(null);
                dialog.close();
                onSaved.run();
            }, failure -> {
                save.setDisable(false);
                cancel.setDisable(false);
                dialog.setOnCloseRequest(null);
                if (!(failure instanceof SicaException)) org.slf4j.LoggerFactory.getLogger(getClass()).error("Error en formulario", failure);
                error.setText(failure instanceof SicaException ? failure.getMessage() : "No se pudo guardar. Revisa los registros técnicos.");
            });
        });
        dialog.show();
    }
}
