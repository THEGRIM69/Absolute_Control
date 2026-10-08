package Absolute_Control.ui;

import javax.swing.*;
import java.awt.*;

/** Barra lateral visual. La navegación se habilitará en una fase posterior. */
public final class NavigationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String[] ITEMS = {
            "INICIO", "CONEXIÓN", "PANTALLAS", "EVENTOS", "CONFIGURACIÓN", "AYUDA"
    };
    private static final RetroIcons.Type[] ICONS = {
            RetroIcons.Type.HOME, RetroIcons.Type.CONNECTION, RetroIcons.Type.MONITOR,
            RetroIcons.Type.EVENTS, RetroIcons.Type.SETTINGS, RetroIcons.Type.HELP
    };

    public NavigationPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(RetroPalette.PANEL);
        setBorder(RetroBorders.padded(RetroPalette.ROYAL_BLUE, 2, 20, 12));
        setPreferredSize(new Dimension(185, 0));

        JLabel section = new JLabel("// MENÚ PRINCIPAL");
        section.setFont(RetroFonts.LABEL);
        section.setForeground(RetroPalette.CYAN);
        section.setAlignmentX(LEFT_ALIGNMENT);
        add(section);
        add(Box.createVerticalStrut(14));

        for (int i = 0; i < ITEMS.length; i++) {
            add(item(ITEMS[i], ICONS[i], i == 0));
            add(Box.createVerticalStrut(8));
        }
        add(Box.createVerticalGlue());
    }

    private JButton item(String text, RetroIcons.Type icon, boolean selected) {
        RetroButton button = new RetroButton(text, selected
                ? RetroButton.Style.NAV_SELECTED : RetroButton.Style.NAVIGATION);
        button.setIcon(RetroIcons.icon(icon,
                selected ? RetroPalette.CYAN : RetroPalette.DISABLED, 16));
        button.setDisabledIcon(RetroIcons.icon(icon, RetroPalette.DISABLED, 16));
        button.setIconTextGap(9);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setEnabled(selected);
        if (!selected) button.setCursor(Cursor.getDefaultCursor());
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int y = getHeight() - 34;
        g.setColor(RetroPalette.ROYAL_BLUE);
        g.fillRect(14, y, 52, 3);
        g.fillRect(72, y, 18, 3);
        g.fillRect(96, y, 6, 3);
        g.setColor(RetroPalette.DISABLED);
        g.fillRect(14, y + 9, 4, 4);
        g.fillRect(24, y + 9, 4, 4);
        g.fillRect(34, y + 9, 4, 4);
    }
}
