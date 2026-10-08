package Absolute_Control.ui;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/** Iconos decorativos dibujados sobre una cuadrícula pixelada de 16×16. */
public final class RetroIcons {
    public enum Type { HOME, CONNECTION, MONITOR, SERVER, EVENTS, SETTINGS, HELP }

    private RetroIcons() {}

    public static Icon icon(Type type, Color color, int size) {
        return new PixelIcon(type, color, Math.max(16, size));
    }

    private static final class PixelIcon implements Icon {
        private final Type type;
        private final Color color;
        private final int size;

        private PixelIcon(Type type, Color color, int size) {
            this.type = type;
            this.color = color;
            this.size = size;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics iconGraphics = g.create();
            int scale = Math.max(1, size / 16);
            int ox = x + (size - (16 * scale)) / 2;
            int oy = y + (size - (16 * scale)) / 2;
            iconGraphics.setColor(color);
            switch (type) {
                case HOME -> home(iconGraphics, ox, oy, scale);
                case CONNECTION -> connection(iconGraphics, ox, oy, scale);
                case MONITOR -> monitor(iconGraphics, ox, oy, scale);
                case SERVER -> server(iconGraphics, ox, oy, scale);
                case EVENTS -> events(iconGraphics, ox, oy, scale);
                case SETTINGS -> settings(iconGraphics, ox, oy, scale);
                case HELP -> help(iconGraphics, ox, oy, scale);
            }
            iconGraphics.dispose();
        }

        private static void px(Graphics g, int x, int y, int w, int h, int s) {
            g.fillRect(x * s, y * s, w * s, h * s);
        }

        private static void home(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 7, 2, 2, 2, s); px(g, 5, 4, 6, 2, s);
            px(g, 3, 6, 10, 2, s); px(g, 4, 8, 8, 6, s); px(g, 7, 10, 2, 4, s); g.translate(-x, -y);
        }

        private static void connection(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 2, 3, 4, 4, s); px(g, 10, 9, 4, 4, s);
            px(g, 5, 6, 2, 2, s); px(g, 7, 7, 2, 2, s); px(g, 9, 8, 2, 2, s); g.translate(-x, -y);
        }

        private static void monitor(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 2, 2, 12, 2, s); px(g, 2, 4, 2, 7, s);
            px(g, 12, 4, 2, 7, s); px(g, 2, 11, 12, 2, s); px(g, 7, 13, 2, 1, s);
            px(g, 5, 14, 6, 1, s); g.translate(-x, -y);
        }

        private static void server(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 3, 2, 10, 3, s); px(g, 3, 7, 10, 3, s);
            px(g, 3, 12, 10, 3, s); g.setColor(RetroPalette.PIXEL_SHADOW);
            px(g, 10, 3, 1, 1, s); px(g, 10, 8, 1, 1, s); px(g, 10, 13, 1, 1, s); g.translate(-x, -y);
        }

        private static void events(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 2, 2, 3, 3, s); px(g, 7, 2, 7, 2, s);
            px(g, 2, 7, 3, 3, s); px(g, 7, 7, 7, 2, s); px(g, 2, 12, 3, 3, s); px(g, 7, 12, 7, 2, s); g.translate(-x, -y);
        }

        private static void settings(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 6, 1, 4, 3, s); px(g, 6, 12, 4, 3, s);
            px(g, 1, 6, 3, 4, s); px(g, 12, 6, 3, 4, s); px(g, 4, 4, 8, 8, s);
            g.setColor(RetroPalette.PIXEL_SHADOW); px(g, 7, 7, 2, 2, s); g.translate(-x, -y);
        }

        private static void help(Graphics g, int x, int y, int s) {
            g.translate(x, y); px(g, 5, 2, 6, 2, s); px(g, 3, 4, 2, 3, s);
            px(g, 10, 4, 2, 4, s); px(g, 7, 7, 4, 2, s); px(g, 6, 9, 2, 3, s); px(g, 6, 14, 2, 2, s); g.translate(-x, -y);
        }
    }
}
