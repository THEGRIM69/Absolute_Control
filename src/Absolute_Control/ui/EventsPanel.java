package Absolute_Control.ui;

import Absolute_Control.historial.EventoHistorial;
import Absolute_Control.historial.HistorialAsync;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Presentación de historial; toda lectura se solicita al servicio asíncrono. */
public final class EventsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss").withZone(ZoneId.systemDefault());

    private final HistorialAsync history;
    private final JPanel eventList = new JPanel();
    private final JLabel status = new JLabel("HISTORIAL SIN CARGAR");
    private final RetroButton refreshButton = new RetroButton("ACTUALIZAR", RetroButton.Style.SECONDARY);

    public EventsPanel(HistorialAsync history) {
        super(new BorderLayout(0, 10));
        this.history = history;
        setBackground(RetroPalette.BACKGROUND);
        setBorder(new EmptyBorder(14, 14, 12, 14));

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel("HISTORIAL DE CONEXIONES KVM");
        title.setFont(RetroFonts.CARD_TITLE);
        title.setForeground(Color.WHITE);
        title.setIcon(RetroIcons.icon(RetroIcons.Type.EVENTS, RetroPalette.CYAN, 20));
        title.setIconTextGap(9);
        status.setName("events.statusLabel");
        status.setFont(RetroFonts.STATUS);
        status.setForeground(RetroPalette.TEXT_SECONDARY);
        refreshButton.setName("events.refreshButton");
        refreshButton.addActionListener(event -> actualizar());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(status);
        actions.add(refreshButton);
        heading.add(title, BorderLayout.WEST);
        heading.add(actions, BorderLayout.EAST);

        eventList.setOpaque(false);
        eventList.setLayout(new BoxLayout(eventList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(eventList);
        scroll.setBorder(RetroBorders.padded(RetroPalette.ROYAL_BLUE, 2, 8, 8));
        scroll.getViewport().setBackground(RetroPalette.PANEL);
        scroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        add(heading, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void actualizar() {
        refreshButton.setEnabled(false);
        status.setText("ACTUALIZANDO...");
        status.setForeground(RetroPalette.WARNING);
        boolean accepted = history.recargar(events -> SwingUtilities.invokeLater(() -> {
            showEvents(events);
            refreshButton.setEnabled(true);
            status.setText(events.size() + " EVENTO(S)");
            status.setForeground(RetroPalette.NEON_GREEN);
        }));
        if (!accepted) {
            refreshButton.setEnabled(true);
            status.setText("ERROR AL SOLICITAR ACTUALIZACIÓN");
            status.setForeground(RetroPalette.ERROR);
        }
    }

    public void mostrarError(String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> mostrarError(message));
            return;
        }
        refreshButton.setEnabled(true);
        status.setText("ERROR: " + message);
        status.setForeground(RetroPalette.ERROR);
    }

    private void showEvents(List<EventoHistorial> events) {
        eventList.removeAll();
        if (events.isEmpty()) {
            JLabel empty = new JLabel("[ NO HAY EVENTOS REALES REGISTRADOS ]", SwingConstants.CENTER);
            empty.setFont(RetroFonts.SECTION);
            empty.setForeground(RetroPalette.DISABLED);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(34, 8, 8, 8));
            eventList.add(empty);
        } else {
            for (EventoHistorial event : events) {
                JPanel row = eventRow(event);
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
                eventList.add(row);
                eventList.add(Box.createVerticalStrut(8));
            }
        }
        eventList.revalidate();
        eventList.repaint();
    }

    private JPanel eventRow(EventoHistorial event) {
        Color color = event.getTipo() == EventoHistorial.Tipo.CONEXION_ESTABLECIDA
                ? RetroPalette.NEON_GREEN
                : event.getTipo() == EventoHistorial.Tipo.CONEXION_FALLIDA
                ? RetroPalette.ERROR : RetroPalette.CYAN;
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setBackground(RetroPalette.PANEL_RAISED);
        row.setBorder(RetroBorders.compact(color));

        JPanel identity = vertical();
        identity.setPreferredSize(new Dimension(230, 0));
        identity.add(label(event.getNombreEquipo(), RetroFonts.SECTION, Color.WHITE));
        identity.add(label(event.getIp() + ":" + event.getPuerto(), RetroFonts.BODY,
                RetroPalette.CYAN));

        JPanel eventData = vertical();
        eventData.add(label(typeText(event.getTipo()), RetroFonts.SECTION, color));
        eventData.add(label("RESULTADO: " + event.getResultado().name(),
                RetroFonts.LABEL, color));
        if (!event.getDetalle().isEmpty()) {
            eventData.add(label(event.getDetalle(), RetroFonts.SECONDARY,
                    RetroPalette.TEXT_SECONDARY));
        }

        JLabel date = label(DATE_FORMAT.format(event.getFecha()), RetroFonts.BODY,
                RetroPalette.TEXT_SECONDARY);
        date.setHorizontalAlignment(SwingConstants.RIGHT);
        date.setPreferredSize(new Dimension(170, 0));
        row.add(identity, BorderLayout.WEST);
        row.add(eventData, BorderLayout.CENTER);
        row.add(date, BorderLayout.EAST);
        return row;
    }

    private static JPanel vertical() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static String typeText(EventoHistorial.Tipo type) {
        switch (type) {
            case CONEXION_ESTABLECIDA: return "CONEXIÓN ESTABLECIDA";
            case CONEXION_FALLIDA: return "CONEXIÓN FALLIDA";
            default: return "SESIÓN FINALIZADA";
        }
    }
}
