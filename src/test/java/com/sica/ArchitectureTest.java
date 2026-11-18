package com.sica;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test void domainIsIndependentOfPersistenceAndPresentationTechnologies() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/sica/domain"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : List.of("javafx.", "java.sql.", "com.mysql.", "com.sica.infrastructure.", "com.sica.presentation.", "com.sica.application.")) {
                    assertFalse(source.contains(forbidden), file + " depende de " + forbidden);
                }
            }
        }
    }
}
