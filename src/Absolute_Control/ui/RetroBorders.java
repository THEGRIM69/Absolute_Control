package Absolute_Control.ui;

import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;

/** Fábrica pequeña para bordes cuadrados y padding consistente. */
public final class RetroBorders {
    private RetroBorders() {}

    public static Border panel(Color color) {
        return padded(color, 2, 10, 12);
    }

    public static Border compact(Color color) {
        return padded(color, 2, 7, 10);
    }

    public static Border padded(Color color, int width, int vertical, int horizontal) {
        return new PixelBorder(color, Math.max(2, width), vertical, horizontal);
    }

    private static final class PixelBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Color color;
        private final int width;
        private final int vertical;
        private final int horizontal;

        private PixelBorder(Color color, int width, int vertical, int horizontal) {
            this.color = color;
            this.width = width;
            this.vertical = vertical;
            this.horizontal = horizontal;
        }

        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = width + vertical;
            insets.left = width + horizontal;
            insets.bottom = width + vertical + 2;
            insets.right = width + horizontal + 2;
            return insets;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            int right = x + w - 1;
            int bottom = y + h - 1;

            g.setColor(RetroPalette.PIXEL_SHADOW);
            g.fillRect(x + 4, bottom - 2, Math.max(0, w - 4), 3);
            g.fillRect(right - 2, y + 4, 3, Math.max(0, h - 4));

            g.setColor(color);
            for (int i = 0; i < width; i++) {
                g.drawRect(x + i, y + i, w - 4 - (i * 2), h - 4 - (i * 2));
            }

            g.setColor(RetroPalette.PIXEL_HIGHLIGHT);
            g.drawLine(x + 4, y + width, right - 6, y + width);
            g.drawLine(x + width, y + 4, x + width, bottom - 6);

            g.setColor(RetroPalette.PIXEL_SHADOW);
            g.fillRect(x, y, 3, 3);
            g.fillRect(right - 4, y, 3, 3);
            g.fillRect(x, bottom - 4, 3, 3);
            g.fillRect(right - 4, bottom - 4, 3, 3);
        }
    }
}
