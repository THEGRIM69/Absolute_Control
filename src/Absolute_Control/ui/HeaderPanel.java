package Absolute_Control.ui;

import javax.swing.*;
import java.awt.*;

/** Cabecera visual del dashboard; no contiene lógica de conexión. */
public final class HeaderPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel systemState;

    public HeaderPanel(JButton configurationButton) {
        super(new BorderLayout());
        setBackground(RetroPalette.HEADER_BLUE);
        setBorder(RetroBorders.padded(RetroPalette.ELECTRIC_BLUE, 3, 10, 18));
        setPreferredSize(new Dimension(0, 76));

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ABSOLUTE CONTROL");
        title.setFont(RetroFonts.TITLE);
        title.setForeground(Color.WHITE);
        title.setIcon(RetroIcons.icon(RetroIcons.Type.CONNECTION, RetroPalette.CYAN, 20));
        title.setIconTextGap(10);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("NETWORK KVM");
        subtitle.setFont(RetroFonts.LABEL);
        subtitle.setForeground(RetroPalette.CYAN);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        identity.add(title);
        identity.add(Box.createVerticalStrut(2));
        identity.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 7));
        right.setOpaque(false);
        systemState = new JLabel("SISTEMA LISTO");
        systemState.setFont(RetroFonts.STATUS);
        systemState.setForeground(RetroPalette.NEON_GREEN);
        right.add(systemState);
        right.add(configurationButton);

        add(identity, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setSystemState(String text, Color color) {
        systemState.setText(text);
        systemState.setForeground(color);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(RetroPalette.ROYAL_BLUE);
        g.fillRect(12, getHeight() - 12, 34, 3);
        g.fillRect(50, getHeight() - 12, 6, 3);
        g.fillRect(60, getHeight() - 12, 18, 3);
        g.setColor(RetroPalette.CYAN);
        g.fillRect(12, 8, 5, 5);
        g.fillRect(21, 8, 3, 3);
    }
}
