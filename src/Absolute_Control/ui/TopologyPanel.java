package Absolute_Control.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Selector y representación visual de la distribución prevista de tres equipos. */
public final class TopologyPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Consumer<Boolean> onPositionChanged;
    private final RetroToggleButton leftButton;
    private final RetroToggleButton rightButton;
    private final JLabel availabilityLabel;
    private final JPanel diagram;
    private boolean pc1Right;

    public TopologyPanel(boolean pc1Right, Consumer<Boolean> onPositionChanged) {
        super(new BorderLayout(0, 7));
        this.pc1Right = pc1Right;
        this.onPositionChanged = onPositionChanged;
        setBackground(RetroPalette.PANEL);
        setBorder(RetroBorders.compact(RetroPalette.ROYAL_BLUE));

        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        JLabel label = new JLabel("POSICIÓN DE PC1");
        label.setFont(RetroFonts.LABEL);
        label.setForeground(RetroPalette.TEXT_SECONDARY);

        JPanel choices = new JPanel(new GridLayout(1, 2, 8, 0));
        choices.setOpaque(false);
        leftButton = new RetroToggleButton("PC1 A LA IZQUIERDA");
        leftButton.setName("topology.leftButton");
        rightButton = new RetroToggleButton("PC1 A LA DERECHA");
        rightButton.setName("topology.rightButton");
        ButtonGroup group = new ButtonGroup();
        group.add(leftButton);
        group.add(rightButton);
        leftButton.addActionListener(event -> this.onPositionChanged.accept(false));
        rightButton.addActionListener(event -> this.onPositionChanged.accept(true));
        choices.add(leftButton);
        choices.add(rightButton);

        availabilityLabel = new JLabel("CONFIGURACIÓN DISPONIBLE");
        availabilityLabel.setName("topology.availabilityLabel");
        availabilityLabel.setFont(RetroFonts.SECONDARY);
        availabilityLabel.setForeground(RetroPalette.CYAN);
        availabilityLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        controls.add(label, BorderLayout.WEST);
        controls.add(choices, BorderLayout.CENTER);
        controls.add(availabilityLabel, BorderLayout.EAST);

        diagram = new JPanel(new GridBagLayout());
        diagram.setOpaque(false);
        add(controls, BorderLayout.NORTH);
        add(diagram, BorderLayout.CENTER);
        setPc1Right(pc1Right);
    }

    public void setPc1Right(boolean pc1Right) {
        this.pc1Right = pc1Right;
        leftButton.setSelected(!pc1Right);
        rightButton.setSelected(pc1Right);
        rebuildDiagram();
    }

    public boolean isPc1Right() {
        return pc1Right;
    }

    public void setPositionSelectionEnabled(boolean enabled) {
        leftButton.setEnabled(enabled);
        rightButton.setEnabled(enabled);
        availabilityLabel.setText(enabled
                ? "CONFIGURACIÓN DISPONIBLE"
                : "BLOQUEADO MIENTRAS KVM ESTÁ ACTIVO");
        availabilityLabel.setForeground(enabled ? RetroPalette.CYAN : RetroPalette.WARNING);
    }

    public boolean isPositionSelectionEnabled() {
        return leftButton.isEnabled() && rightButton.isEnabled();
    }

    private void rebuildDiagram() {
        diagram.removeAll();
        String left = pc1Right ? "PC2" : "PC1";
        String right = pc1Right ? "PC1" : "PC2";

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 2, 0, 2);

        c.gridx = 0;
        c.weightx = 0.26;
        diagram.add(node(left, positionSubtitle(left), positionColor(left), "topology.leftNode"), c);
        c.gridx = 1;
        c.weightx = 0.10;
        diagram.add(link("────"), c);
        c.gridx = 2;
        c.weightx = 0.28;
        diagram.add(node("REY", "ESTE EQUIPO", RetroPalette.NEON_GREEN,
                "topology.centerNode"), c);
        c.gridx = 3;
        c.weightx = 0.10;
        diagram.add(link("────"), c);
        c.gridx = 4;
        c.weightx = 0.26;
        diagram.add(node(right, positionSubtitle(right), positionColor(right),
                "topology.rightNode"), c);

        diagram.revalidate();
        diagram.repaint();
    }

    private JPanel node(String title, String subtitle, Color color, String name) {
        JPanel node = new JPanel(new GridLayout(2, 1, 0, 1));
        node.setName(name);
        node.setBackground(RetroPalette.CONTROL_DISABLED);
        node.setBorder(RetroBorders.compact(color));
        boolean enabled = !"PC2".equals(title);
        node.setEnabled(enabled);

        JLabel main = centered(title, RetroFonts.CARD_TITLE, color);
        main.setName(name + ".title");
        main.setEnabled(enabled);
        main.setIcon(RetroIcons.icon(RetroIcons.Type.MONITOR, color, 16));
        main.setIconTextGap(6);
        JLabel detail = centered(subtitle, RetroFonts.SECONDARY, color);
        detail.setName(name + ".subtitle");
        detail.setEnabled(enabled);
        node.add(main);
        node.add(detail);
        return node;
    }

    private static String positionSubtitle(String equipment) {
        return "PC1".equals(equipment) ? "CONEXIÓN ACTUAL" : "PRÓXIMAMENTE";
    }

    private static Color positionColor(String equipment) {
        return "PC1".equals(equipment) ? RetroPalette.CYAN : RetroPalette.DISABLED;
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
