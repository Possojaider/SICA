package com.sica.presentation;

import com.sica.domain.SicaException;
import com.sica.infrastructure.TutorialResources;
import com.sica.infrastructure.TutorialResources.Chapter;
import javafx.fxml.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.io.IOException;
import java.util.Objects;

public final class TutorialController implements AutoCloseable {
    private final BackgroundTasks tasks;
    private Stage stage;
    private MediaPlayer player;
    private boolean closed, syncing, syncingChapter, ended;
    @FXML private MediaView tutorialVideo;
    @FXML private ImageView poster;
    @FXML private StackPane videoPane;
    @FXML private VBox controls, chapterPane;
    @FXML private ProgressIndicator preparing;
    @FXML private Label message, elapsed, total;
    @FXML private Button playPause, fullscreen;
    @FXML private Slider position, volume;
    @FXML private ComboBox<Double> speed;
    @FXML private ListView<Chapter> chapters;

    public TutorialController(BackgroundTasks tasks) { this.tasks = tasks; }

    public static TutorialController open(Window owner, BackgroundTasks tasks) {
        TutorialController controller = new TutorialController(tasks);
        try {
            FXMLLoader loader = new FXMLLoader(TutorialController.class.getResource("/fxml/tutorial.fxml"));
            loader.setControllerFactory(type -> controller);
            Scene scene = new Scene(loader.load(), 1220, 800);
            scene.getStylesheets().add(TutorialController.class.getResource("/css/sica.css").toExternalForm());
            controller.stage = new Stage();
            controller.stage.initOwner(owner);
            controller.stage.setTitle("SICA · Información y videotutorial");
            controller.stage.setMinWidth(1000);
            controller.stage.setMinHeight(660);
            controller.stage.setScene(scene);
            controller.stage.setOnHidden(event -> controller.close());
            controller.stage.fullScreenProperty().addListener((property, before, expanded) -> {
                controller.chapterPane.setVisible(!expanded);
                controller.chapterPane.setManaged(!expanded);
                controller.fullscreen.setText(expanded ? "Salir de pantalla completa" : "Pantalla completa");
            });
            controller.show();
            return controller;
        } catch (IOException failure) {
            controller.close();
            org.slf4j.LoggerFactory.getLogger(TutorialController.class).error("No se pudo cargar la ayuda", failure);
            throw new SicaException("No se pudo abrir la pantalla del videotutorial.");
        }
    }

    @FXML private void initialize() {
        tutorialVideo.fitWidthProperty().bind(videoPane.widthProperty());
        tutorialVideo.fitHeightProperty().bind(videoPane.heightProperty());
        poster.fitWidthProperty().bind(videoPane.widthProperty());
        poster.fitHeightProperty().bind(videoPane.heightProperty());
        poster.setImage(new Image(Objects.requireNonNull(getClass().getResource("/tutorial/portada.png")).toExternalForm(), true));
        speed.getItems().setAll(0.75, 1.0, 1.25, 1.5, 2.0);
        speed.setValue(1.0);
        speed.setOnAction(event -> { if (player != null) player.setRate(speed.getValue()); });
        position.valueChangingProperty().addListener((property, before, changing) -> {
            if (!changing && !syncing) seek(position.getValue());
        });
        position.valueProperty().addListener((property, before, after) -> {
            if (!syncing && !position.isValueChanging()) seek(after.doubleValue());
        });
        chapters.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Chapter chapter, boolean empty) {
                super.updateItem(chapter, empty);
                setText(empty || chapter == null ? null : chapter.toString());
                setWrapText(true);
                setPrefWidth(0);
            }
        });
        chapters.getSelectionModel().selectedItemProperty().addListener((property, before, chapter) -> {
            if (!syncingChapter && chapter != null && player != null && !controls.isDisabled()) {
                seek(chapter.seconds());
                player.play();
            }
        });
        tasks.submit(TutorialResources::prepare, content -> {
            if (closed) return;
            chapters.getItems().setAll(content.chapters());
            try {
                Media media = new Media(content.video().toUri().toString());
                media.setOnError(() -> failed(media.getError()));
                player = new MediaPlayer(media);
                tutorialVideo.setMediaPlayer(player);
                player.volumeProperty().bind(volume.valueProperty());
                player.setOnError(() -> failed(player.getError()));
                player.setOnReady(() -> {
                    if (closed) return;
                    preparing.setVisible(false);
                    controls.setDisable(false);
                    chapters.setDisable(false);
                    position.setMax(player.getTotalDuration().toSeconds());
                    total.setText(time(player.getTotalDuration()));
                    message.setText("Pulsa Reproducir o elige uno de los 25 capítulos.");
                });
                player.setOnPlaying(() -> {
                    ended = false;
                    poster.setVisible(false);
                    playPause.setText("Pausar");
                    message.setText("Videotutorial · audio y subtítulos en español");
                });
                player.setOnPaused(() -> playPause.setText("Reproducir"));
                player.setOnEndOfMedia(() -> {
                    ended = true;
                    player.pause();
                    playPause.setText("Volver a reproducir");
                    message.setText("Tutorial finalizado. Puedes repetirlo o seleccionar un capítulo.");
                });
                player.currentTimeProperty().addListener((property, before, current) -> updateTime(current));
                if (media.getError() != null) failed(media.getError());
                else if (player.getError() != null) failed(player.getError());
            } catch (RuntimeException failure) { failed(failure); }
        }, this::failed);
    }

    private void updateTime(Duration current) {
        if (closed) return;
        elapsed.setText(time(current));
        if (!position.isValueChanging()) {
            syncing = true;
            position.setValue(current.toSeconds());
            syncing = false;
        }
        int index = 0;
        for (int i = 0; i < chapters.getItems().size(); i++) {
            if (chapters.getItems().get(i).seconds() <= current.toSeconds()) index = i;
        }
        syncingChapter = true;
        chapters.getSelectionModel().select(index);
        syncingChapter = false;
    }

    private void seek(double seconds) {
        if (player == null || controls.isDisabled() || closed) return;
        ended = false;
        poster.setVisible(false);
        player.seek(Duration.seconds(seconds));
        updateTime(Duration.seconds(seconds));
    }

    @FXML private void togglePlayback() {
        if (player == null || controls.isDisabled() || closed) return;
        if (player.getStatus() == MediaPlayer.Status.PLAYING) player.pause();
        else {
            if (ended) seek(0);
            player.play();
        }
    }

    @FXML private void restart() { seek(0); if (player != null && !controls.isDisabled()) player.play(); }
    @FXML private void expand() { if (stage != null) stage.setFullScreen(!stage.isFullScreen()); }

    private void failed(Throwable failure) {
        if (closed) return;
        org.slf4j.LoggerFactory.getLogger(getClass()).error("No se pudo reproducir el videotutorial", failure);
        preparing.setVisible(false);
        controls.setDisable(true);
        chapters.setDisable(true);
        message.setText("No se pudo reproducir el video. Cierra y vuelve a abrir el videotutorial.");
        message.getStyleClass().add("error");
        if (player != null) { player.dispose(); tutorialVideo.setMediaPlayer(null); }
    }

    private static String time(Duration duration) {
        long seconds = Math.max(0, (long) duration.toSeconds());
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    public boolean isClosed() { return closed; }
    public void show() { if (!closed && stage != null) { stage.show(); stage.toFront(); } }

    @Override @FXML public void close() {
        if (closed) return;
        closed = true;
        if (player != null) { player.dispose(); tutorialVideo.setMediaPlayer(null); }
        if (stage != null) stage.close();
    }
}
