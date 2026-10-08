package Absolute_Control.ui;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Insets;

/** Toggle rectangular con estados selected, hover, pressed, disabled y focus. */
public final class RetroToggleButton extends JToggleButton {
    private static final long serialVersionUID = 1L;
    public RetroToggleButton(String text) {
        super(text);
        setFont(RetroFonts.BUTTON);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(7, 12, 9, 12));
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
        setForeground(!isEnabled() ? RetroPalette.DISABLED
                : isSelected() ? Color.WHITE : RetroPalette.TEXT_PRIMARY);
        Graphics textGraphics = g.create();
        textGraphics.translate(offset, offset);
        super.paintComponent(textGraphics);
        textGraphics.dispose();
    }

    @Override protected void paintBorder(Graphics g) {
        Color color = !isEnabled() ? RetroPalette.DISABLED
                : isFocusOwner() ? RetroPalette.WARNING
                : isSelected() ? RetroPalette.CYAN : RetroPalette.ROYAL_BLUE;
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
        if (isSelected()) return getModel().isRollover()
                ? RetroPalette.ELECTRIC_BLUE : RetroPalette.ROYAL_BLUE;
        return getModel().isRollover() ? RetroPalette.HEADER_BLUE : RetroPalette.CONTROL;
    }
}
