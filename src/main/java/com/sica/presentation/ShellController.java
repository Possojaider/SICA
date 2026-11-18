package com.sica.presentation;

import com.sica.domain.port.in.SicaUseCases;

import com.sica.domain.Session;

import com.sica.application.*;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store.*;
import com.sica.infrastructure.CsvExporter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ShellController implements AutoCloseable {
    private record Page(String name, Report report) {}
    private record Snapshot(Data data, Statistics stats, Set<String> permissions, List<Option> companies) {}
    private final SicaUseCases sica;
    private final Session session;
    private final BackgroundTasks tasks;
    private final Runnable onLogout;
    private final Map<Report, String> reportNames = new LinkedHashMap<>();
    private Set<String> permissions = Set.of();
    private List<Option> companies = List.of();
    private TablePresenter presenter;
    private ScheduledFuture<?> polling;
    private Page page = new Page("Panel principal", Report.VISITAS);
    private boolean busy;
    private boolean closed;
    private long generation;
    private final DashboardViewModel dashboard = new DashboardViewModel();
    @FXML private VBox navigation;
    @FXML private Label heading, notice, rowCount, status, reportLabel, stateLabel;
    @FXML private HBox statistics, actions, reportSelector;
    @FXML private TextField search;
    @FXML private DatePicker from, until;
    @FXML private ComboBox<String> reportType, stateFilter;
    @FXML private ProgressIndicator loading;
    @FXML private TableView<List<String>> table;

    public ShellController(SicaUseCases sica, Session session, BackgroundTasks tasks, Runnable onLogout) {
        this.sica = sica;
        this.session = session;
        this.tasks = tasks;
        this.onLogout = onLogout;
    }

    @FXML
    private void initialize() {
        presenter = new TablePresenter(table);
        statistics.getChildren().setAll(card("Personas dentro", dashboard.insideProperty()),
                card("Pendientes vigentes", dashboard.pendingProperty()),
                card("Aprobadas vigentes", dashboard.approvedProperty()), card("Incidentes hoy", dashboard.incidentsProperty()));
        dashboard.pendingProperty().addListener((observable, before, after) -> {
            if (after.longValue() > before.longValue()) {
                status.setText("Hay nuevas solicitudes pendientes de aprobación.");
            }
        });
        stateFilter.getItems().setAll("Todos", "PENDIENTE", "APROBADA", "RECHAZADA", "DENTRO", "CERRADA", "SALIDA_OLVIDADA");
        stateFilter.setValue("Todos");
        stateFilter.setOnAction(event -> render(presenter.data()));
        reportType.setOnAction(event -> {
            if ("Reportes".equals(page.name())) refresh();
        });
        refresh();
        polling = tasks.poll(() -> {
            if (!busy) load(true);
        });
    }

    private void navigation() {
        navigation.getChildren().clear();
        menu("Panel principal", Report.VISITAS);
        if (permissions.contains("USUARIOS")) menu("Usuarios", Report.USUARIOS);
        if (permissions.contains("EMPRESAS")) menu("Empresas", Report.EMPRESAS);
        if (permissions.contains("PERSONAS_LEER")) menu("Personas", Report.PERSONAS);
        if (permissions.contains("VISITAS_LEER")) menu("Registro de visitas", Report.VISITAS);
        if (permissions.contains("ACCESO")) menu("Entradas y salidas", Report.VISITAS);
        if (permissions.contains("VISITAS_LEER")) menu("Solicitudes pendientes", Report.SOLICITUDES);
        if (permissions.contains("DECIDIR")) menu("Aprobar solicitudes", Report.SOLICITUDES);
        if (permissions.contains("INCIDENTES")) menu("Incidentes", Report.INCIDENTES);
        if (permissions.contains("AUDITORIA")) menu("Auditoría", Report.AUDITORIA);
        if (permissions.contains("REPORTES")) menu("Reportes", Report.DENTRO);
        reportNames.clear();
        reportNames.put(Report.DENTRO, "Personas dentro del complejo");
        reportNames.put(Report.HISTORIAL, "Historial de entradas y salidas");
        reportNames.put(Report.VISITAS, "Visitas por fecha");
        reportNames.put(Report.DECISIONES, "Solicitudes aprobadas y rechazadas");
        if (permissions.contains("INCIDENTES")) reportNames.put(Report.INCIDENTES, "Incidentes registrados");
        if (permissions.contains("AUDITORIA")) reportNames.put(Report.AUDITORIA, "Historial de auditoría");
        String selected = reportType.getValue();
        reportType.getItems().setAll(reportNames.values());
        reportType.setValue(reportNames.containsValue(selected) ? selected : reportNames.get(Report.DENTRO));
    }

    private void menu(String title, Report report) {
        Button button = new Button(title);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        if (page.name().equals(title)) button.getStyleClass().add("selected");
        button.setOnAction(event -> {
            if (busy) return;
            generation++;
            page = new Page(title, report);
            search.clear();
            from.setValue(null);
            until.setValue(null);
            stateFilter.setValue(title.contains("solicitudes") || title.contains("pendientes") ? "PENDIENTE" : "Todos");
            heading.setText(title);
            status.setText("");
            updateLayout();
            refresh();
        });
        navigation.getChildren().add(button);
    }

    private void updateLayout() {
        boolean dashboard = page.name().equals("Panel principal");
        statistics.setManaged(dashboard);
        statistics.setVisible(dashboard);
        boolean reports = page.name().equals("Reportes");
        reportType.setManaged(reports);
        reportType.setVisible(reports);
        reportLabel.setManaged(reports);
        reportLabel.setVisible(reports);
        boolean visit = EnumSet.of(Report.VISITAS, Report.SOLICITUDES, Report.DENTRO, Report.HISTORIAL, Report.DECISIONES).contains(currentReport());
        boolean stateVisible = visit && !dashboard;
        stateFilter.setManaged(stateVisible);
        stateFilter.setVisible(stateVisible);
        stateLabel.setManaged(stateVisible);
        stateLabel.setVisible(stateVisible);
        reportSelector.setManaged(reports || stateVisible);
        reportSelector.setVisible(reports || stateVisible);
        actions.getChildren().clear();
        switch (page.name()) {
            case "Usuarios", "Empresas", "Personas" -> {
                if (!page.name().equals("Personas") || permissions.contains("PERSONAS_CREAR")) action("Nuevo", () -> edit(false), true);
                if (!page.name().equals("Personas") || permissions.contains("PERSONAS")) {
                    action("Editar", () -> edit(true), false);
                    action("Dar de baja", this::archive, false);
                }
                if (page.name().equals("Personas")) action("Ver fotografía", this::photo, false);
            }
            case "Registro de visitas" -> {
                if (permissions.contains("PRE_REGISTRAR") || permissions.contains("SOLICITAR")) action("Registrar visita", this::request, true);
                action("Ver fotografía", this::photo, false);
            }
            case "Entradas y salidas" -> {
                action("Registrar entrada", () -> visitCommand("Confirmar entrada", new VisitCommand.CheckIn(selectedId())), true);
                action("Registrar salida", () -> visitCommand("Confirmar salida", new VisitCommand.CheckOut(selectedId())), false);
                if (permissions.contains("SOLICITAR")) action("Solicitar ingreso", this::request, false);
                action("Ver fotografía", this::photo, false);
                if (permissions.contains("INCIDENTES")) action("Reportar incidente", () -> visitEditor().incident(Long.toString(selectedId())), false);
            }
            case "Aprobar solicitudes" -> {
                action("Aprobar", () -> visitCommand("Aprobar solicitud", new VisitCommand.Decide(selectedId(), true)), true);
                action("Rechazar", () -> visitCommand("Rechazar solicitud", new VisitCommand.Decide(selectedId(), false)), false);
                action("Ver fotografía", this::photo, false);
            }
            case "Incidentes" -> action("Reportar incidente", () -> visitEditor().incident(""), true);
            case "Reportes" -> action("Exportar CSV", this::export, true);
            default -> { }
        }
    }

    private void action(String title, Runnable action, boolean primary) {
        Button button = new Button(title);
        if (primary) button.getStyleClass().add("primary");
        button.setOnAction(event -> {
            if (busy) return;
            try {
                action.run();
            } catch (RuntimeException failure) {
                error(failure);
            }
        });
        actions.getChildren().add(button);
    }

    private Report currentReport() {
        if (!page.name().equals("Reportes")) return page.report();
        return reportNames.entrySet().stream().filter(entry -> entry.getValue().equals(reportType.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(Report.DENTRO);
    }

    @FXML private void refresh() { load(false); }

    private void load(boolean automatic) {
        if (busy || closed) return;
        Report report = currentReport();
        String query = search.getText();
        LocalDateTime begin = from.getValue() == null ? null : from.getValue().atStartOfDay();
        LocalDateTime end = until.getValue() == null ? null : until.getValue().plusDays(1).atStartOfDay();
        long requestGeneration = generation;
        run(() -> new Snapshot(sica.report(session, report, query, begin, end), sica.statistics(session),
                        sica.permissions(session), sica.companies(session)), snapshot -> {
            if (requestGeneration != generation) return;
            boolean permissionsChanged = !permissions.equals(snapshot.permissions());
            permissions = snapshot.permissions();
            companies = snapshot.companies();
            if (permissionsChanged || navigation.getChildren().isEmpty()) navigation();
            updateLayout();
            render(snapshot.data());
            dashboard.update(snapshot.stats());
            notice.setText(snapshot.stats().pending() + " solicitudes pendientes vigentes · Actualizado " +
                    sica.currentTime().toLocalTime().withNano(0));
            if (!automatic) status.setText("Datos actualizados");
        });
    }

    private VBox card(String title, javafx.beans.property.ReadOnlyLongProperty value) {
        Label number = new Label();
        number.textProperty().bind(value.asString());
        number.getStyleClass().add("stat-number");
        Label label = new Label(title);
        label.getStyleClass().add("muted");
        VBox card = new VBox(6, label, number);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void render(Data data) {
        presenter.display(data, stateFilter.getValue());
        rowCount.setText(table.getItems().size() + " registros");
    }

    private void edit(boolean existing) {
        Map<String, String> selected = existing ? presenter.selected() : Map.of();
        EntityEditor editor = new EntityEditor(sica, session, tasks, table.getScene().getWindow(), companies, this::saved);
        switch (page.report()) {
            case USUARIOS -> editor.user(selected);
            case EMPRESAS -> editor.company(selected);
            case PERSONAS -> editor.person(selected, permissions.contains("PERSONAS"));
            default -> throw new SicaException("Selecciona una entidad editable.");
        }
    }

    private void archive() {
        long id = selectedId();
        Entity entity = Entity.valueOf(page.report().name());
        confirm("Dar de baja", "Se desactivará el registro " + id + ".", () -> {
            sica.archive(session, entity, id);
            return null;
        });
    }

    private void request() {
        run(() -> sica.report(session, Report.PERSONAS, "", null, null),
                people -> visitEditor().request(companies, people, permissions));
    }

    private VisitEditor visitEditor() {
        return new VisitEditor(sica, session, tasks, table.getScene().getWindow(), this::saved);
    }

    private void saved() {
        if (closed) return;
        status.setText("Operación registrada correctamente.");
        refresh();
    }

    private void visitCommand(String title, VisitCommand command) {
        confirm(title, "La operación quedará registrada en la auditoría.", () -> {
            command.execute(sica, session);
            return null;
        });
    }

    private void confirm(String title, String detail, Callable<?> command) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, detail, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(table.getScene().getWindow());
        alert.setHeaderText(title);
        alert.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored -> run(command, result -> saved()));
    }

    private long selectedId() {
        return Long.parseLong(presenter.selected().get("ID"));
    }

    private void photo() {
        String url = presenter.selected().getOrDefault("Foto", "");
        if (url.isBlank()) throw new SicaException("Esta persona no tiene una fotografía registrada.");
        Image image = new Image(url, 500, 500, true, true, true);
        ImageView view = new ImageView(image);
        Label message = new Label("Cargando fotografía…");
        image.progressProperty().addListener((observable, oldValue, value) -> {
            if (value.doubleValue() >= 1) message.setText(image.isError() ? "No se pudo cargar la fotografía." : "");
        });
        image.errorProperty().addListener((observable, oldValue, failed) -> {
            if (failed) message.setText("No se pudo cargar la fotografía.");
        });
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(table.getScene().getWindow());
        alert.setHeaderText("Fotografía de identificación");
        alert.getDialogPane().setContent(new VBox(10, view, message));
        alert.setOnHidden(event -> image.cancel());
        alert.show();
    }

    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar reporte");
        chooser.setInitialFileName("sica-" + currentReport().name().toLowerCase(Locale.ROOT) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV UTF-8", "*.csv"));
        java.io.File destination = chooser.showSaveDialog(table.getScene().getWindow());
        if (destination == null) return;
        Report report = currentReport();
        String query = search.getText();
        String state = stateFilter.getValue();
        LocalDateTime begin = from.getValue() == null ? null : from.getValue().atStartOfDay();
        LocalDateTime end = until.getValue() == null ? null : until.getValue().plusDays(1).atStartOfDay();
        run(() -> {
            Data data = sica.report(session, report, query, begin, end);
            int index = data.columns().indexOf("Estado");
            if (index >= 0 && !"Todos".equals(state)) data = new Data(data.columns(),
                    data.rows().stream().filter(row -> row.get(index).equals(state)).toList());
            CsvExporter.write(destination.toPath(), data);
            return null;
        }, result -> status.setText("Reporte CSV exportado."));
    }

    @FXML private void clearFilters() {
        search.clear();
        from.setValue(null);
        until.setValue(null);
        stateFilter.setValue("Todos");
        refresh();
    }

    @FXML private void logout() {
        if (busy) return;
        run(() -> { sica.logout(session); return null; }, result -> onLogout.run());
    }

    private <T> void run(Callable<T> operation, Consumer<T> success) {
        busy = true;
        loading.setManaged(true);
        loading.setVisible(true);
        actions.setDisable(true);
        tasks.submit(operation, result -> {
            if (closed) return;
            idle();
            status.getStyleClass().remove("error");
            try {
                success.accept(result);
            } catch (RuntimeException failure) {
                error(failure);
            }
        }, failure -> {
            if (closed) return;
            idle();
            error(failure);
        });
    }

    private void idle() {
        busy = false;
        loading.setVisible(false);
        loading.setManaged(false);
        actions.setDisable(false);
    }

    private void error(Throwable failure) {
        if (!(failure instanceof SicaException)) LoggerFactory.getLogger(getClass()).error("Error en pantalla", failure);
        status.setText(failure instanceof SicaException ? failure.getMessage() : "No se pudo completar la operación.");
        status.getStyleClass().setAll("error");
    }

    @Override
    public void close() {
        closed = true;
        generation++;
        if (polling != null) polling.cancel(false);
    }
}
