package Absolute_Control.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Carga y escala los recursos oficiales de marca sin afectar el arranque. */
public final class BrandAssets {
    private static final String CROWN_RESOURCE =
            "/assets/branding/absolute-control-crown-original.png";
    private static final Path CROWN_DEVELOPMENT_PATH =
            Paths.get("assets", "branding", "absolute-control-crown-original.png");
    private static final BufferedImage CROWN = loadCrown();

    private BrandAssets() {}

    /** Devuelve la corona a tamaño cuadrado, o {@code null} si no está disponible. */
    public static ImageIcon crownIcon(int size) {
        Image image = scaledCrown(size);
        return image == null ? null : new ImageIcon(image);
    }

    /** Resoluciones usadas por AWT/Windows para la barra de título y el selector de ventanas. */
    public static List<Image> windowIcons() {
        if (CROWN == null) return Collections.emptyList();

        int[] sizes = {16, 32, 48, 64};
        List<Image> icons = new ArrayList<>(sizes.length);
        for (int size : sizes) {
            icons.add(scaleNearestNeighbor(CROWN, size, size));
        }
        return Collections.unmodifiableList(icons);
    }

    private static Image scaledCrown(int size) {
        if (CROWN == null || size <= 0) return null;
        return scaleNearestNeighbor(CROWN, size, size);
    }

    private static BufferedImage loadCrown() {
        try (InputStream resource = BrandAssets.class.getResourceAsStream(CROWN_RESOURCE)) {
            if (resource != null) {
                BufferedImage image = ImageIO.read(resource);
                if (image != null) return image;
            }
        } catch (IOException ignored) {
            // Se intenta el respaldo de desarrollo antes de usar el fallback visual.
        }

        if (Files.isRegularFile(CROWN_DEVELOPMENT_PATH)) {
            try {
                BufferedImage image = ImageIO.read(CROWN_DEVELOPMENT_PATH.toFile());
                if (image != null) return image;
            } catch (IOException ignored) {
                // La aplicación continuará con los recursos retro existentes.
            }
        }

        System.err.println("Advertencia: no se pudo cargar la corona oficial; se usará el icono visual de respaldo.");
        return null;
    }

    private static BufferedImage scaleNearestNeighbor(
            BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(width, height, imageType);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }
}
