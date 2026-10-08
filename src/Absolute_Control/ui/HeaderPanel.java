package Absolute_Control.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/** Cabecera visual del dashboard; no contiene lógica de conexión. */
public final class HeaderPanel extends JPanel {
    private final JLabel systemState;

    public HeaderPanel(JButton configurationButton) {
        super(new BorderLayout());
        setBackground(RetroPalette.HEADER_BLUE);
        setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 3, 0, RetroPalette.ELECTRIC_BLUE),
                new EmptyBorder(10, 18, 10, 18)));
        setPreferredSize(new Dimension(0, 72));

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ABSOLUTE CONTROL");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("NETWORK KVM");
        subtitle.setFont(new Font("Consolas", Font.BOLD, 11));
        subtitle.setForeground(RetroPalette.CYAN);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        identity.add(title);
        identity.add(Box.createVerticalStrut(2));
        identity.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 7));
        right.setOpaque(false);
        systemState = new JLabel("SISTEMA LISTO");
        systemState.setFont(new Font("Consolas", Font.BOLD, 12));
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
}
