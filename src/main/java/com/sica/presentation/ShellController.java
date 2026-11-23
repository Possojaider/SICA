package com.sica.presentation;

import com.sica.domain.port.in.SicaUseCases;

import com.sica.domain.Session;
import com.sica.domain.Permissions;
import com.sica.presentation.ShellNavigation.Page;

import com.sica.application.*;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.Store.*;
import com.sica.infrastructure.CsvExporter;
import com.sica.infrastructure.QrCodec;
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
    private record Snapshot(Page page, Data data, Statistics stats, Set<String> permissions, List<Option> companies, long unread) {}
    private record QrView(Credential credential, byte[] image) {}
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
    private boolean updatingNavigation;
    private boolean refreshPending;
    private TutorialController tutorial;
    private long generation;
    private final DashboardViewModel dashboard = new DashboardViewModel();
    @FXML private VBox navigation;
    @FXML private Label heading, notice, rowCount, status, reportLabel, stateLabel;
    @FXML private HBox statistics, reportSelector;
    @FXML private FlowPane actions;
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
        stateFilter.getItems().setAll("Todos", "PENDIENTE", "APROBADA", "RECHAZADA", "DENTRO", "CERRADA", "SALIDA_OLVIDADA", "EXPIRADA");
        stateFilter.setValue("Todos");
        stateFilter.setOnAction(event -> render(presenter.data()));
        reportType.setOnAction(event -> {
            if (!updatingNavigation && "Reportes".equals(page.name())) {
                generation++;
                refresh();
            }
        });
        refresh();
        polling = tasks.poll(() -> {
            if (!busy) load(true);
        });
    }

    private void navigation() {
        ShellNavigation.build(navigation, page, permissions, this::navigate);
        reportNames.clear();
        reportNames.putAll(ShellNavigation.reports(permissions));
        String selected = reportType.getValue();
        updatingNavigation = true;
        try {
            reportType.getItems().setAll(reportNames.values());
            reportType.setValue(reportNames.containsValue(selected) ? selected : reportNames.get(Report.DENTRO));
        } finally {
            updatingNavigation = false;
        }
    }

    private void navigate(Page destination) {
        String title = destination.name();
        Report report = destination.report();
        if (busy) return;
        generation++;
        page = new Page(title, report);
        navigation.getChildren().forEach(node -> node.getStyleClass().remove("selected"));
        navigation.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(title)).forEach(button -> button.getStyleClass().add("selected"));
        search.clear();
        from.setValue(null);
        until.setValue(null);
        stateFilter.getItems().setAll(report == Report.NOTIFICACIONES
                ? List.of("Todos", "SIN_LEER", "LEIDA")
                : List.of("Todos", "PENDIENTE", "APROBADA", "RECHAZADA", "DENTRO", "CERRADA", "SALIDA_OLVIDADA", "EXPIRADA"));
        stateFilter.setValue(title.contains("solicitudes") || title.contains("pendientes") ? "PENDIENTE" : "Todos");
        heading.setText(title);
        status.setText("");
        updateLayout();
        refresh();
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
        boolean stateVisible = (visit || page.report() == Report.NOTIFICACIONES) && !dashboard;
        stateFilter.setManaged(stateVisible);
        stateFilter.setVisible(stateVisible);
        stateLabel.setManaged(stateVisible);
        stateLabel.setVisible(stateVisible);
        reportSelector.setManaged(reports || stateVisible);
        reportSelector.setVisible(reports || stateVisible);
        actions.getChildren().clear();
        switch (page.name()) {
            case "Usuarios", "Empresas", "Personas" -> {
                Entity entity = Entity.valueOf(page.report().name());
                if (permissions.contains(Permissions.create(entity))) action("Nuevo", () -> edit(false), true);
                if (permissions.contains(Permissions.edit(entity))) action("Editar", () -> edit(true), false);
                if (permissions.contains(Permissions.archive(entity))) action("Dar de baja", this::archive, false);
                if (page.name().equals("Personas")) action("Ver fotografía", this::photo, false);
            }
            case "Notificaciones" -> {
                action("Marcar como leída", () -> {
                    long id = selectedId();
                    run(() -> { sica.markNotificationRead(session, id); return null; }, result -> saved());
                }, true);
                action("Consultar visita", this::notificationVisit, false);
            }
            case "Registro de visitas" -> {
                if (permissions.contains("PRE_REGISTRAR") || permissions.contains("SOLICITAR")) action("Registrar visita", this::request, true);
                action("Ver fotografía", this::photo, false);
            }
            case "Entradas y salidas" -> {
                action("Registrar entrada", () -> visitCommand("Confirmar entrada", new VisitCommand.CheckIn(selectedId())), true);
                action("Registrar salida", () -> visitCommand("Confirmar salida", new VisitCommand.CheckOut(selectedId())), false);
                action("Ingresar con QR", () -> visitEditor().scan(), false);
                action("Ingresar con imagen QR", this::scanImage, false);
                if (permissions.contains("SOLICITAR")) action("Solicitar ingreso", this::request, false);
                action("Ver fotografía", this::photo, false);
                if (permissions.contains("INCIDENTES")) action("Reportar incidente", () -> visitEditor().incident(Long.toString(selectedId())), false);
            }
            case "Aprobar solicitudes" -> {
                action("Aprobar", () -> visitEditor().decide(selectedId(), true), true);
                action("Rechazar", () -> visitEditor().decide(selectedId(), false), false);
                action("Ver fotografía", this::photo, false);
            }
            case "Incidentes" -> action("Reportar incidente", () -> visitEditor().incident(""), true);
            case "Reportes" -> action("Exportar CSV", this::export, true);
            default -> { }
        }
        if (!dashboard && EnumSet.of(Report.VISITAS, Report.SOLICITUDES).contains(page.report()) && permissions.contains("QR_EMITIR")) {
            action("Emitir o renovar QR", this::issueQr, false);
        }
    }

    private void issueQr() {
        long id = selectedId();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Se emitirá un QR de un solo ingreso para la visita " + id + ". Si existe otro QR, quedará invalidado.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(table.getScene().getWindow());
        confirmation.setHeaderText("Emitir credencial temporal");
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored ->
                run(() -> {
                    Credential credential = sica.issueCredential(session, id);
                    return new QrView(credential, QrCodec.encode(credential.code()));
                }, view -> CredentialDialog.show(table.getScene().getWindow(), view.credential(), view.image(), tasks)));
    }

    private void scanImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar QR para registrar entrada");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes QR", "*.png", "*.jpg", "*.jpeg"));
        java.io.File image = chooser.showOpenDialog(table.getScene().getWindow());
        if (image == null) return;
        run(() -> {
            sica.checkInWithCredential(session, QrCodec.read(image.toPath()));
            return null;
        }, result -> saved());
    }

    private void notificationVisit() {
        long visit = Long.parseLong(presenter.selected().get("Visita"));
        run(() -> sica.report(session, Report.VISITAS, Long.toString(visit), null, null), data -> {
            Data selected = new Data(data.columns(), data.rows().stream()
                    .filter(row -> row.getFirst().equals(Long.toString(visit))).toList());
            if (selected.rows().isEmpty()) throw new SicaException("La visita ya no está disponible para tu rol o empresa.");
            TableView<List<String>> details = new TableView<>();
            new TablePresenter(details).display(selected, "Todos");
            details.setPrefSize(950, 190);
            Alert dialog = new Alert(Alert.AlertType.INFORMATION);
            dialog.initOwner(table.getScene().getWindow());
            dialog.setHeaderText("Detalle de visita " + visit);
            dialog.getDialogPane().setContent(details);
            dialog.show();
        });
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

    @FXML private void refresh() {
        if (busy) refreshPending = true;
        else load(false);
    }

    private static boolean canView(Page page, Report report, Set<String> permissions) {
        String permission = Permissions.read(report);
        return (permission == null || permissions.contains(permission)) && switch (page.name()) {
            case "Entradas y salidas" -> permissions.contains("ACCESO");
            case "Aprobar solicitudes" -> permissions.contains("DECIDIR");
            case "Reportes" -> permissions.contains("REPORTES");
            default -> true;
        };
    }

    private void load(boolean automatic) {
        if (busy || closed) return;
        Report report = currentReport();
        Page requestedPage = page;
        String query = search.getText();
        LocalDateTime begin = from.getValue() == null ? null : from.getValue().atStartOfDay();
        LocalDateTime end = until.getValue() == null ? null : until.getValue().plusDays(1).atStartOfDay();
        long requestGeneration = generation;
        run(() -> {
            Set<String> currentPermissions = sica.permissions(session);
            boolean allowed = canView(requestedPage, report, currentPermissions);
            Page destination = allowed ? requestedPage : new Page("Notificaciones", Report.NOTIFICACIONES);
            Data data = sica.report(session, allowed ? report : destination.report(),
                    allowed ? query : "", allowed ? begin : null, allowed ? end : null);
            Statistics stats = currentPermissions.contains("VISITAS_LEER")
                    ? sica.statistics(session) : new Statistics(0, 0, 0, 0);
            return new Snapshot(destination, data, stats, currentPermissions,
                    sica.companies(session), sica.unreadNotifications(session));
        }, snapshot -> {
            if (requestGeneration != generation) return;
            if (!page.equals(snapshot.page())) {
                page = snapshot.page();
                heading.setText(page.name());
                search.clear();
                from.setValue(null);
                until.setValue(null);
                stateFilter.getItems().setAll("Todos", "SIN_LEER", "LEIDA");
                stateFilter.setValue("Todos");
            }
            boolean permissionsChanged = !permissions.equals(snapshot.permissions());
            permissions = snapshot.permissions();
            companies = snapshot.companies();
            if (permissionsChanged || navigation.getChildren().isEmpty()) navigation();
            updateLayout();
            render(snapshot.data());
            dashboard.update(snapshot.stats());
            notice.setText(snapshot.unread() + " notificaciones sin leer · " + snapshot.stats().pending() +
                    " solicitudes pendientes · Actualizado " +
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
            case PERSONAS -> editor.person(selected, permissions.contains("PERSONAS_EDITAR") || (!existing && permissions.contains("TRABAJADORES_CREAR")));
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
        PhotoDialog.show(table.getScene().getWindow(), presenter.selected().getOrDefault("Foto", ""));
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

    @FXML private void help() {
        if (tutorial == null || tutorial.isClosed()) {
            tutorial = TutorialController.open(table.getScene().getWindow(), tasks);
        } else tutorial.show();
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
            drainRefresh();
        }, failure -> {
            if (closed) return;
            idle();
            error(failure);
            drainRefresh();
        });
    }

    private void drainRefresh() {
        if (refreshPending && !busy && !closed) {
            refreshPending = false;
            refresh();
        }
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
        if (tutorial != null) tutorial.close();
        generation++;
        if (polling != null) polling.cancel(false);
    }
}
