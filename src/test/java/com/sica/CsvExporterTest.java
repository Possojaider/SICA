package com.sica;

import com.sica.domain.port.out.Store.Data;
import com.sica.infrastructure.CsvExporter;
import com.sica.infrastructure.BcryptHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @TempDir Path temp;

    @Test void csvPreservesUnicodeQuotesAndNewlinesAndNeutralizesFormulas() throws Exception {
        Path path = temp.resolve("report.csv");
        CsvExporter.write(path, new Data(List.of("Nombre", "Detalle"), List.of(
                List.of("José, Pérez", "Línea \"uno\"\nDos"), List.of("=1+1", " +SUM(A1)"))));
        String csv = Files.readString(path);
        assertTrue(csv.startsWith("\uFEFF\"Nombre\",\"Detalle\"\r\n"));
        assertTrue(csv.contains("\"José, Pérez\",\"Línea \"\"uno\"\"\nDos\""));
        assertTrue(csv.contains("\"'=1+1\",\"' +SUM(A1)\""));
    }

    @Test void bcryptUsesSaltAndVerifiesPasswords() {
        BcryptHasher hasher = new BcryptHasher();
        String first = hasher.hash("contraseña-segura-123");
        assertTrue(first.startsWith("$2"));
        assertNotEquals(first, hasher.hash("contraseña-segura-123"));
        assertTrue(hasher.matches("contraseña-segura-123", first));
        assertFalse(hasher.matches("otra-contraseña", first));
    }
}
