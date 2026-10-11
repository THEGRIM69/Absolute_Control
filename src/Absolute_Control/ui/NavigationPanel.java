package Absolute_Control.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Barra lateral visual con navegación habilitada para Inicio y Conexión. */
public final class NavigationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    public enum Page { INICIO, CONEXION }

    private static final String[] ITEMS = {
            "INICIO", "CONEXIÓN", "PANTALLAS", "EVENTOS", "CONFIGURACIÓN", "AYUDA"
    };
    private static final RetroIcons.Type[] ICONS = {
            RetroIcons.Type.HOME, RetroIcons.Type.CONNECTION, RetroIcons.Type.MONITOR,
            RetroIcons.Type.EVENTS, RetroIcons.Type.SETTINGS, RetroIcons.Type.HELP
    };
    private final RetroButton inicioButton;
    private final RetroButton conexionButton;
    private Page activePage = Page.INICIO;

    public NavigationPanel(Consumer<Page> onNavigate) {
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

        inicioButton = item(ITEMS[0], ICONS[0], true);
        conexionButton = item(ITEMS[1], ICONS[1], false);
        inicioButton.addActionListener(event -> onNavigate.accept(Page.INICIO));
        conexionButton.addActionListener(event -> onNavigate.accept(Page.CONEXION));

        for (int i = 0; i < ITEMS.length; i++) {
            JButton button = i == 0 ? inicioButton
                    : i == 1 ? conexionButton
                    : item(ITEMS[i], ICONS[i], false);
            if (i > 1) {
                button.setEnabled(false);
                button.setCursor(Cursor.getDefaultCursor());
            }
            add(button);
            add(Box.createVerticalStrut(8));
        }
        add(Box.createVerticalGlue());
        setActivePage(Page.INICIO);
    }

    public void setActivePage(Page page) {
        activePage = page;
        applyState(inicioButton, RetroIcons.Type.HOME, page == Page.INICIO);
        applyState(conexionButton, RetroIcons.Type.CONNECTION, page == Page.CONEXION);
    }

    public Page getActivePage() {
        return activePage;
    }

    private RetroButton item(String text, RetroIcons.Type icon, boolean selected) {
        RetroButton button = new RetroButton(text, selected
                ? RetroButton.Style.NAV_SELECTED : RetroButton.Style.NAVIGATION);
        button.setIcon(RetroIcons.icon(icon,
                selected ? RetroPalette.CYAN : RetroPalette.DISABLED, 16));
        button.setDisabledIcon(RetroIcons.icon(icon, RetroPalette.DISABLED, 16));
        button.setIconTextGap(9);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setEnabled(true);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    private void applyState(RetroButton button, RetroIcons.Type icon, boolean selected) {
        button.setStyle(selected ? RetroButton.Style.NAV_SELECTED : RetroButton.Style.NAVIGATION);
        button.setIcon(RetroIcons.icon(icon,
                selected ? RetroPalette.CYAN : RetroPalette.DISABLED, 16));
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
