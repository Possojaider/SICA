package com.sica.presentation;

import com.sica.domain.SicaException;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

final class FormDialog {
    private final Dialog<Void> dialog = new Dialog<>();
    private final Map<String, Supplier<String>> inputs = new LinkedHashMap<>();
    private final Label error;
    private final ButtonType saveType;
    private final Map<String, Object> controls;

    FormDialog(Window owner, String title, String resource) {
        try {
            var loader = new javafx.fxml.FXMLLoader(getClass().getResource(resource));
            DialogPane pane = loader.load();
            controls = loader.getNamespace();
            error = (Label) controls.get("formError");
            saveType = pane.getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            pane.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());
            dialog.initOwner(owner);
            dialog.setTitle(title);
            dialog.setHeaderText(title);
            dialog.setDialogPane(pane);
        } catch (java.io.IOException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("No se pudo cargar el formulario {}", resource, failure);
            throw new SicaException("No se pudo cargar el formulario.");
        }
    }

    private <T extends Node> T control(String key, String label, Class<T> type) {
        ((Label) controls.get(key + "Label")).setText(label);
        return type.cast(controls.get(key));
    }

    FormDialog submitLabel(String label) {
        ((Button) dialog.getDialogPane().lookupButton(saveType)).setText(label);
        return this;
    }

    FormDialog text(String key, String label, String value) {
        TextField input = control(key, label, TextField.class);
        input.setText(value);
        inputs.put(key, input::getText);
        return this;
    }

    FormDialog secret(String key, String label) {
        PasswordField input = control(key, label, PasswordField.class);
        input.setPromptText("12 caracteres mínimo");
        inputs.put(key, input::getText);
        return this;
    }

    @SuppressWarnings("unchecked")
    FormDialog choices(String key, String label, List<String> values, String selected) {
        ComboBox<String> input = control(key, label, ComboBox.class);
        input.getItems().setAll(values);
        input.setValue(values.contains(selected) ? selected : values.stream().findFirst().orElse(""));
        inputs.put(key, () -> Objects.toString(input.getValue(), ""));
        return this;
    }

    FormDialog multiline(String key, String label) {
        TextArea input = control(key, label, TextArea.class);
        inputs.put(key, input::getText);
        return this;
    }

    void show(BackgroundTasks tasks, Function<Map<String, String>, Callable<?>> command, Runnable onSaved) {
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.setId("form-save");
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
