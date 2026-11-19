package com.sica.infrastructure;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sica.domain.SicaException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;

public final class QrCodec {
    private QrCodec() {}

    public static byte[] encode(String code) throws IOException {
        try {
            var matrix = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 360, 360,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, "png", output)) throw new IOException("Codificador PNG no disponible");
                return output.toByteArray();
            }
        } catch (WriterException failure) {
            throw new SicaException("No se pudo generar la imagen QR.");
        }
    }

    public static String read(Path path) throws IOException {
        if (Files.size(path) > 10 * 1024 * 1024) throw new SicaException("La imagen QR debe ocupar como máximo 10 MB.");
        // Consulta las dimensiones antes de descomprimir para limitar el consumo de memoria.
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new SicaException("La imagen no se pudo abrir.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new SicaException("Selecciona una imagen PNG o JPEG válida.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) {
                    throw new SicaException("La imagen QR supera las dimensiones permitidas.");
                }
                BufferedImage image = reader.read(0);
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
                return new MultiFormatReader().decode(bitmap, Map.of(DecodeHintType.POSSIBLE_FORMATS,
                        java.util.List.of(BarcodeFormat.QR_CODE))).getText();
            } catch (NotFoundException failure) {
                throw new SicaException("No se encontró un código QR legible en la imagen.");
            } finally {
                reader.dispose();
            }
        }
    }
}
