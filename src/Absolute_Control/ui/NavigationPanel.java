package Absolute_Control.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/** Barra lateral visual. La navegación se habilitará en una fase posterior. */
public final class NavigationPanel extends JPanel {
    private static final String[] ITEMS = {
            "INICIO", "CONEXIÓN", "PANTALLAS", "EVENTOS", "CONFIGURACIÓN", "AYUDA"
    };

    public NavigationPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(RetroPalette.PANEL);
        setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 0, 2, RetroPalette.ROYAL_BLUE),
                new EmptyBorder(22, 12, 12, 12)));
        setPreferredSize(new Dimension(185, 0));

        JLabel section = new JLabel("// MENÚ PRINCIPAL");
        section.setFont(new Font("Consolas", Font.BOLD, 10));
        section.setForeground(RetroPalette.CYAN);
        section.setAlignmentX(LEFT_ALIGNMENT);
        add(section);
        add(Box.createVerticalStrut(14));

        for (int i = 0; i < ITEMS.length; i++) {
            add(item(ITEMS[i], i == 0));
            add(Box.createVerticalStrut(7));
        }
        add(Box.createVerticalGlue());
    }

    private JButton item(String text, boolean selected) {
        JButton button = new JButton(text);
        button.setFont(new Font("Consolas", Font.BOLD, 12));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(selected ? Color.WHITE : RetroPalette.DISABLED);
        button.setBackground(selected ? RetroPalette.ROYAL_BLUE : RetroPalette.PANEL);
        button.setBorder(new CompoundBorder(
                new MatteBorder(1, selected ? 4 : 1, 1, 1,
                        selected ? RetroPalette.ELECTRIC_BLUE : RetroPalette.PANEL),
                new EmptyBorder(9, 10, 9, 8)));
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return button;
    }
}
