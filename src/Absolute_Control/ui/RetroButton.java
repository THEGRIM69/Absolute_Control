package Absolute_Control.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Insets;

/** Botón rectangular con estados retro, sin incorporar acciones propias. */
public final class RetroButton extends JButton {
    private static final long serialVersionUID = 1L;
    public enum Style { PRIMARY, SECONDARY, HEADER, NAVIGATION, NAV_SELECTED }

    private final Style style;

    public RetroButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(RetroFonts.BUTTON);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(7, 14, 9, 14));
    }

    @Override protected void paintComponent(Graphics g) {
        boolean pressed = isEnabled() && getModel().isPressed();
        int offset = pressed ? 2 : 0;
        int faceWidth = Math.max(0, getWidth() - 3);
        int faceHeight = Math.max(0, getHeight() - 3);

        g.setColor(RetroPalette.PIXEL_SHADOW);
        g.fillRect(3, 3, faceWidth, faceHeight);
        g.setColor(background());
        g.fillRect(offset, offset, faceWidth, faceHeight);

        if (isEnabled()) {
            g.setColor(pressed ? RetroPalette.PIXEL_SHADOW : RetroPalette.PIXEL_HIGHLIGHT);
            g.fillRect(offset + 2, offset + 2, Math.max(0, faceWidth - 4), 2);
            g.fillRect(offset + 2, offset + 2, 2, Math.max(0, faceHeight - 4));
            g.setColor(pressed ? RetroPalette.PIXEL_HIGHLIGHT : RetroPalette.PIXEL_SHADOW);
            g.fillRect(offset + 2, offset + faceHeight - 3, Math.max(0, faceWidth - 3), 2);
            g.fillRect(offset + faceWidth - 3, offset + 2, 2, Math.max(0, faceHeight - 3));
        }

        setForeground(foreground());
        Graphics textGraphics = g.create();
        textGraphics.translate(offset, offset);
        super.paintComponent(textGraphics);
        textGraphics.dispose();
    }

    @Override protected void paintBorder(Graphics g) {
        Color color = !isEnabled() ? RetroPalette.DISABLED
                : isFocusOwner() ? RetroPalette.WARNING
                : style == Style.PRIMARY ? RetroPalette.NEON_GREEN
                : style == Style.NAV_SELECTED ? RetroPalette.CYAN
                : RetroPalette.ELECTRIC_BLUE;
        g.setColor(color);
        int offset = isEnabled() && getModel().isPressed() ? 2 : 0;
        g.drawRect(offset, offset, getWidth() - 4, getHeight() - 4);
        if (isFocusOwner()) {
            for (int x = 5; x < getWidth() - 7; x += 4) g.fillRect(x, 4, 2, 2);
            for (int x = 5; x < getWidth() - 7; x += 4) g.fillRect(x, getHeight() - 8, 2, 2);
        }
    }

    private Color background() {
        if (!isEnabled()) return RetroPalette.CONTROL_DISABLED;
        if (getModel().isPressed()) return RetroPalette.HEADER_BLUE;
        if (getModel().isRollover()) {
            return style == Style.PRIMARY ? RetroPalette.CYAN : RetroPalette.ROYAL_BLUE;
        }
        return switch (style) {
            case PRIMARY -> RetroPalette.NEON_GREEN;
            case HEADER, NAV_SELECTED -> RetroPalette.ROYAL_BLUE;
            case SECONDARY, NAVIGATION -> RetroPalette.CONTROL;
        };
    }

    private Color foreground() {
        if (!isEnabled()) return RetroPalette.DISABLED;
        return style == Style.PRIMARY ? RetroPalette.BACKGROUND : RetroPalette.TEXT_PRIMARY;
    }
}
