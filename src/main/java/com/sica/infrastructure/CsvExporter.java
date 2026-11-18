package com.sica.infrastructure;

import com.sica.domain.port.out.Store.Data;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class CsvExporter {
    private CsvExporter() {}

    public static void write(Path path, Data data) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF");
            writer.write(line(data.columns()));
            for (var row : data.rows()) writer.write(line(row));
        }
    }

    private static String line(List<String> cells) {
        return cells.stream().map(CsvExporter::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String cell(String value) {
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@\t\r\n".indexOf(stripped.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
