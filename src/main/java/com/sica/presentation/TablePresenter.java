package com.sica.presentation;

import com.sica.domain.port.out.Store.Data;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import java.util.*;

final class TablePresenter {
    private final TableView<List<String>> table;
    private Data data = new Data(List.of(), List.of());

    TablePresenter(TableView<List<String>> table) {
        this.table = table;
    }

    void display(Data result, String state) {
        String selected = table.getSelectionModel().getSelectedItem() == null ? null : table.getSelectionModel().getSelectedItem().getFirst();
        if (!data.columns().equals(result.columns())) {
            table.getColumns().clear();
            for (int index = 0; index < result.columns().size(); index++) {
                int columnIndex = index;
                TableColumn<List<String>, String> column = new TableColumn<>(result.columns().get(index));
                column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(columnIndex)));
                column.setPrefWidth(index == 0 ? 65 : 155);
                table.getColumns().add(column);
            }
        }
        data = result;
        int stateIndex = data.columns().indexOf("Estado");
        List<List<String>> rows = data.rows();
        if (state != null && !state.equals("Todos") && stateIndex >= 0) {
            rows = rows.stream().filter(row -> row.get(stateIndex).equals(state)).toList();
        }
        table.setItems(FXCollections.observableArrayList(rows));
        if (selected != null) rows.stream().filter(row -> row.getFirst().equals(selected)).findFirst()
                .ifPresent(row -> table.getSelectionModel().select(row));
    }

    Map<String, String> selected() {
        List<String> row = table.getSelectionModel().getSelectedItem();
        if (row == null) throw new com.sica.domain.SicaException("Selecciona un registro de la tabla.");
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < data.columns().size(); i++) result.put(data.columns().get(i), row.get(i));
        return Map.copyOf(result);
    }

    Data data() {
        return data;
    }
}
