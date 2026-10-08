package Absolute_Control.ui;

import javax.swing.*;
import java.awt.*;

/** Representación exclusivamente visual de la topología prevista. */
public final class TopologyPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    public TopologyPanel() {
        super(new GridBagLayout());
        setBackground(RetroPalette.PANEL);
        setBorder(RetroBorders.compact(RetroPalette.ROYAL_BLUE));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.weightx = 0.22;
        add(node("PC1", "CONEXIÓN ACTUAL", RetroPalette.CYAN), c);
        c.gridx = 1; c.weightx = 0.14;
        add(link("<────"), c);
        c.gridx = 2; c.weightx = 0.28;
        add(node("REY", "ESTE EQUIPO", RetroPalette.NEON_GREEN), c);
        c.gridx = 3; c.weightx = 0.14;
        add(link("────>"), c);
        c.gridx = 4; c.weightx = 0.22;
        add(node("PC2", "PRÓXIMAMENTE", RetroPalette.DISABLED), c);
    }

    private JPanel node(String title, String subtitle, Color color) {
        JPanel node = new JPanel(new GridLayout(2, 1, 0, 1));
        node.setBackground(RetroPalette.CONTROL_DISABLED);
        node.setBorder(RetroBorders.compact(color));

        JLabel main = centered(title, RetroFonts.CARD_TITLE, color);
        main.setIcon(RetroIcons.icon(RetroIcons.Type.MONITOR, color, 16));
        main.setIconTextGap(6);
        JLabel detail = centered(subtitle, RetroFonts.SECONDARY, color);
        node.add(main);
        node.add(detail);
        return node;
    }

    private JLabel link(String text) {
        return centered(text, RetroFonts.CARD_TITLE, RetroPalette.ELECTRIC_BLUE);
    }

    private JLabel centered(String text, Font font, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }
}
