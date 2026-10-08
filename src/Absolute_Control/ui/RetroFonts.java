package Absolute_Control.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/** Jerarquía tipográfica retro con fallback lógico seguro. */
public final class RetroFonts {
    private static final String FAMILY = resolveFamily();

    public static final Font TITLE       = font(Font.BOLD, 24);
    public static final Font CARD_TITLE  = font(Font.BOLD, 18);
    public static final Font SECTION     = font(Font.BOLD, 14);
    public static final Font BUTTON      = font(Font.BOLD, 13);
    public static final Font LABEL       = font(Font.BOLD, 12);
    public static final Font BODY        = font(Font.PLAIN, 12);
    public static final Font SECONDARY   = font(Font.PLAIN, 11);
    public static final Font STATUS      = font(Font.BOLD, 12);
    public static final Font CONSOLE     = font(Font.PLAIN, 13);

    private RetroFonts() {}

    private static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    private static String resolveFamily() {
        try {
            return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames())
                    .anyMatch("Consolas"::equalsIgnoreCase)
                    ? "Consolas" : Font.MONOSPACED;
        } catch (RuntimeException ignored) {
            return Font.MONOSPACED;
        }
    }
}
