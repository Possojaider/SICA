package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Recursos locales incluidos en el JAR; prepara un archivo con acceso aleatorio para el video. */
public final class TutorialResources {
    public record Chapter(double seconds, String title) {
        @Override public String toString() {
            long time = (long) seconds;
            return "%02d:%02d  %s".formatted(time / 60, time % 60, title);
        }
    }
    public record Content(Path video, List<Chapter> chapters) {}
    private static Content cached;

    private TutorialResources() {}

    public static synchronized Content prepare() {
        if (cached != null && Files.isRegularFile(cached.video())) return cached;
        try {
            var resource = TutorialResources.class.getResource("/tutorial/SICA-videotutorial.mp4");
            if (resource == null) throw new IOException("No está incluido el video del tutorial");
            List<Chapter> chapters;
            try (var stream = TutorialResources.class.getResourceAsStream("/tutorial/capitulos.tsv")) {
                if (stream == null) throw new IOException("No está incluido el índice del tutorial");
                chapters = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .filter(line -> !line.isBlank()).map(line -> {
                            String[] fields = line.split("\t", 2);
                            return new Chapter(Double.parseDouble(fields[0]), fields[1]);
                        }).toList();
            }
            Path video;
            if (resource.getProtocol().equals("file")) video = Path.of(resource.toURI());
            else {
                Path directory = Files.createTempDirectory("sica-tutorial-");
                directory.toFile().deleteOnExit();
                video = directory.resolve("SICA-videotutorial.mp4");
                video.toFile().deleteOnExit();
                try (InputStream stream = resource.openStream()) { Files.copy(stream, video); }
            }
            cached = new Content(video, List.copyOf(chapters));
            return cached;
        } catch (IOException | URISyntaxException | RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(TutorialResources.class).error("No se pudo preparar el videotutorial", failure);
            throw new SicaException("No se pudo abrir el videotutorial. Revisa que la instalación esté completa.");
        }
    }
}
