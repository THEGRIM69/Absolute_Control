package Absolute_Control.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/** Scrollbar simple y visible para la consola retro. */
public final class RetroScrollBarUI extends BasicScrollBarUI {
    @Override protected void configureScrollBarColors() {
        trackColor = RetroPalette.CONTROL_DISABLED;
        thumbColor = RetroPalette.ROYAL_BLUE;
        thumbHighlightColor = RetroPalette.ELECTRIC_BLUE;
        thumbDarkShadowColor = RetroPalette.ROYAL_BLUE;
    }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
        if (!scrollbar.isEnabled() || bounds.isEmpty()) return;
        g.setColor(isDragging ? RetroPalette.ELECTRIC_BLUE : RetroPalette.ROYAL_BLUE);
        g.fillRect(bounds.x + 2, bounds.y + 2,
                Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4));
    }

    @Override protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    private JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }
}
