package com.client.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

/**
 * Image conversion and default avatar utilities.
 * Extracted from ChatView to keep view classes focused on UI layout.
 */
public final class ImageUtils {
    private ImageUtils() {}

    /**
     * Converts a JavaFX Image to base64-encoded PNG data URL string.
     */
    public static String imageToBase64Png(Image image) {
        byte[] pngBytes = imageToPngBytes(image);
        if (pngBytes.length == 0) return null;
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }

    /**
     * Converts a JavaFX Image to PNG byte array.
     */
    public static byte[] imageToPngBytes(Image image) {
        try {
            BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * Creates a default avatar image.
     * Tries local file first, then online fallback, then generated colored circle.
     */
    public static Image createDefaultAvatarImage() {
        // Try local file
        try {
            File file = new File("avatarMacDinh.jpg");
            if (file.exists()) {
                Image img = new Image(file.toURI().toString(), false);
                if (!img.isError()) return img;
            }
        } catch (Exception ignored) {}

        // Online fallback
        try {
            Image img = new Image("https://i.pravatar.cc/300?img=0", false);
            if (!img.isError()) return img;
        } catch (Exception ignored) {}

        // Generated colored circle fallback
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(110, 110);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#7c5cfc"));
        gc.fillOval(0, 0, 110, 110);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /**
     * Decodes a base64-encoded image data URL or normal URL to a JavaFX Image.
     */
    public static Image decodeAvatarDataUrl(String dataUrl) {
        try {
            if (dataUrl != null && dataUrl.startsWith("data:image/")) {
                String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
                byte[] imgBytes = Base64.getDecoder().decode(base64);
                return new Image(new ByteArrayInputStream(imgBytes));
            }
            if (dataUrl != null && !dataUrl.isEmpty()) {
                return new Image(dataUrl, true);
            }
            return null;
        } catch (Exception e) {
            System.err.println("Failed to decode avatar: " + e.getMessage());
            return null;
        }
    }
}
