package Absolute_Control.ui;

import Absolute_Control.equipos.EquipoGuardado;
import Absolute_Control.equipos.PersistenciaEquiposException;
import Absolute_Control.equipos.RepositorioEquiposJson;
import Absolute_Control.equipos.ServicioEquipos;
import Absolute_Control.equipos.ValidacionEquipoException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** Pantalla de gestión visual; toda persistencia pasa por ServicioEquipos. */
public final class ConnectionPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @FunctionalInterface
    public interface CreadorServicio {
        ServicioEquipos crear() throws PersistenciaEquiposException;
    }

    @FunctionalInterface
    public interface Confirmacion {
        boolean confirmar(Component parent, EquipoGuardado equipo);
    }

    public enum EstadoConexionRapida {
        DISPONIBLE, ROL_NO_PERMITIDO, INICIANDO, ACTIVO_LOCAL,
        CONECTANDO, CONECTADO, ERROR_ACTIVO, FINALIZANDO
    }

    public interface ControlConexion {
        ResultadoSolicitud conectar(EquipoGuardado equipo);
        ResultadoSolicitud detener();
    }

    public static final class ResultadoSolicitud {
        private final boolean aceptada;
        private final String mensaje;

        private ResultadoSolicitud(boolean aceptada, String mensaje) {
            this.aceptada = aceptada;
            this.mensaje = mensaje;
        }

        public static ResultadoSolicitud aceptada(String mensaje) {
            return new ResultadoSolicitud(true, mensaje);
        }

        public static ResultadoSolicitud rechazada(String mensaje) {
            return new ResultadoSolicitud(false, mensaje);
        }

        public boolean isAceptada() { return aceptada; }
        public String getMensaje() { return mensaje; }
    }

    private ServicioEquipos servicio;
    private final Confirmacion confirmacion;
    private final ControlConexion controlConexion;
    private final JPanel recordsPanel = new JPanel();
    private final List<AbstractButton> recordButtons = new ArrayList<>();
    private JTextField nameField;
    private JTextField ipField;
    private JTextField portField;
    private JLabel formTitle;
    private JLabel statusLabel;
    private RetroButton saveButton;
    private RetroButton cancelButton;
    private RetroButton connectButton;
    private JLabel selectedLabel;
    private JLabel connectionStatusLabel;
    private UUID editingId;
    private UUID selectedId;
    private boolean busy;
    private EstadoConexionRapida connectionState = EstadoConexionRapida.DISPONIBLE;

    public ConnectionPanel() {
        this((CreadorServicio) () -> new ServicioEquipos(RepositorioEquiposJson.predeterminado()),
                ConnectionPanel::confirmWithDialog, null);
    }

    public ConnectionPanel(ControlConexion control) {
        this((CreadorServicio) () -> new ServicioEquipos(RepositorioEquiposJson.predeterminado()),
                ConnectionPanel::confirmWithDialog, control);
    }

    public ConnectionPanel(CreadorServicio creator, Confirmacion confirmation) {
        this(creator, confirmation, null);
    }

    public ConnectionPanel(CreadorServicio creator, Confirmacion confirmation,
                           ControlConexion control) {
        this.confirmacion = confirmation;
        this.controlConexion = control;
        buildInterface();
        loadService(creator);
    }

    public ConnectionPanel(ServicioEquipos service, Confirmacion confirmation) {
        this(service, confirmation, null);
    }

    public ConnectionPanel(ServicioEquipos service, Confirmacion confirmation,
                           ControlConexion control) {
        this.confirmacion = confirmation;
        this.controlConexion = control;
        buildInterface();
        this.servicio = service;
        refreshRecords();
        showStatus("EQUIPOS CARGADOS", RetroPalette.NEON_GREEN);
        setBusy(false);
    }

    private void buildInterface() {
        setLayout(new BorderLayout(14, 12));
        setBackground(RetroPalette.BACKGROUND);
        setBorder(new EmptyBorder(14, 14, 12, 14));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("EQUIPOS GUARDADOS");
        title.setFont(RetroFonts.CARD_TITLE);
        title.setForeground(Color.WHITE);
        title.setIcon(RetroIcons.icon(RetroIcons.Type.CONNECTION, RetroPalette.CYAN, 20));
        title.setIconTextGap(9);
        statusLabel = new JLabel("CARGANDO EQUIPOS...");
        statusLabel.setName("connection.statusLabel");
        statusLabel.setFont(RetroFonts.STATUS);
        statusLabel.setForeground(RetroPalette.WARNING);
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        recordsPanel.setOpaque(false);
        recordsPanel.setLayout(new BoxLayout(recordsPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(recordsPanel);
        scroll.setBorder(RetroBorders.padded(RetroPalette.ROYAL_BLUE, 2, 8, 8));
        scroll.getViewport().setBackground(RetroPalette.PANEL);
        scroll.setBackground(RetroPalette.PANEL);
        scroll.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel form = buildForm();
        form.setPreferredSize(new Dimension(390, 0));

        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);
        content.add(form, BorderLayout.EAST);

        add(heading, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(RetroPalette.PANEL_RAISED);
        form.setBorder(RetroBorders.padded(RetroPalette.ELECTRIC_BLUE, 2, 14, 16));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 6, 0);

        formTitle = new JLabel("AGREGAR EQUIPO");
        formTitle.setFont(RetroFonts.CARD_TITLE);
        formTitle.setForeground(RetroPalette.CYAN);
        c.gridy = 0;
        form.add(formTitle, c);

        JLabel manual = new JLabel("SE GUARDA ÚNICAMENTE AL PULSAR GUARDAR");
        manual.setFont(RetroFonts.SECONDARY);
        manual.setForeground(RetroPalette.TEXT_SECONDARY);
        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        form.add(manual, c);

        c.insets = new Insets(0, 0, 5, 0);
        c.gridy++;
        form.add(fieldLabel("NOMBRE"), c);
        nameField = textField("connection.nameField", "");
        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        form.add(nameField, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 5, 0);
        form.add(fieldLabel("DIRECCIÓN IPv4"), c);
        ipField = textField("connection.ipField", "");
        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        form.add(ipField, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 5, 0);
        form.add(fieldLabel("PUERTO"), c);
        portField = textField("connection.portField", "8080");
        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        form.add(portField, c);

        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        saveButton = new RetroButton("GUARDAR", RetroButton.Style.PRIMARY);
        saveButton.setName("connection.saveButton");
        saveButton.addActionListener(event -> saveForm());
        cancelButton = new RetroButton("CANCELAR", RetroButton.Style.SECONDARY);
        cancelButton.setName("connection.cancelButton");
        cancelButton.addActionListener(event -> clearForm());
        actions.add(saveButton);
        actions.add(cancelButton);
        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        form.add(actions, c);

        selectedLabel = new JLabel("DESTINO: NINGÚN EQUIPO SELECCIONADO");
        selectedLabel.setName("connection.selectedLabel");
        selectedLabel.setFont(RetroFonts.SECONDARY);
        selectedLabel.setForeground(RetroPalette.TEXT_SECONDARY);
        c.gridy++;
        c.insets = new Insets(0, 0, 7, 0);
        form.add(selectedLabel, c);

        connectionStatusLabel = new JLabel("DISPONIBLE PARA CONECTAR");
        connectionStatusLabel.setName("connection.connectionStatusLabel");
        connectionStatusLabel.setFont(RetroFonts.STATUS);
        connectionStatusLabel.setForeground(RetroPalette.CYAN);
        c.gridy++;
        c.insets = new Insets(0, 0, 9, 0);
        form.add(connectionStatusLabel, c);

        connectButton = new RetroButton("CONECTAR", RetroButton.Style.PRIMARY);
        connectButton.setName("connection.connectButton");
        connectButton.addActionListener(event -> handleConnectionAction());
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(connectButton, c);

        c.gridy++;
        c.weighty = 1;
        form.add(Box.createVerticalGlue(), c);
        return form;
    }

    private void loadService(CreadorServicio creator) {
        setBusy(true);
        new SwingWorker<ServicioEquipos, Void>() {
            @Override protected ServicioEquipos doInBackground() throws Exception {
                return creator.crear();
            }

            @Override protected void done() {
                try {
                    servicio = get();
                    refreshRecords();
                    showStatus("EQUIPOS CARGADOS", RetroPalette.NEON_GREEN);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showStatus("CARGA INTERRUMPIDA", RetroPalette.ERROR);
                } catch (ExecutionException error) {
                    showError(error.getCause());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void saveForm() {
        if (busy || servicio == null) return;
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException error) {
            showStatus("ERROR: EL PUERTO DEBE SER NUMÉRICO", RetroPalette.ERROR);
            return;
        }
        String name = nameField.getText();
        String ip = ipField.getText();
        UUID id = editingId;
        runOperation(() -> id == null
                        ? servicio.guardar(name, ip, port)
                        : servicio.editar(id, name, ip, port),
                id == null ? "EQUIPO GUARDADO" : "EQUIPO ACTUALIZADO",
                this::clearForm);
    }

    private void edit(EquipoGuardado equipo) {
        if (busy) return;
        editingId = equipo.getId();
        formTitle.setText("EDITAR EQUIPO");
        nameField.setText(equipo.getNombre());
        ipField.setText(equipo.getIp());
        portField.setText(String.valueOf(equipo.getPuerto()));
        nameField.requestFocusInWindow();
        showStatus("EDITANDO: " + equipo.getNombre(), RetroPalette.WARNING);
    }

    private void requestDelete(EquipoGuardado equipo) {
        if (busy || !confirmacion.confirmar(this, equipo)) return;
        runOperation(() -> {
            if (!servicio.eliminar(equipo.getId())) {
                throw new IllegalStateException("El equipo ya no existe");
            }
            return equipo;
        }, "EQUIPO ELIMINADO", () -> {
            if (equipo.getId().equals(editingId)) clearForm();
            if (equipo.getId().equals(selectedId)) selectedId = null;
        });
    }

    private void select(EquipoGuardado equipo) {
        if (busy) return;
        selectedId = equipo.getId();
        refreshRecords();
        showStatus("EQUIPO SELECCIONADO", RetroPalette.NEON_GREEN);
    }

    private void handleConnectionAction() {
        if (busy || controlConexion == null) return;
        if (isActiveConnectionState(connectionState)) {
            EstadoConexionRapida previous = connectionState;
            applyConnectionState(EstadoConexionRapida.FINALIZANDO, "FINALIZANDO CONEXIÓN...");
            ResultadoSolicitud result = controlConexion.detener();
            if (!result.isAceptada()) {
                applyConnectionState(previous, result.getMensaje());
                showStatus("ERROR: " + result.getMensaje(), RetroPalette.ERROR);
            }
            return;
        }

        EquipoGuardado selected = selectedRecord();
        if (selected == null || connectionState != EstadoConexionRapida.DISPONIBLE) return;
        applyConnectionState(EstadoConexionRapida.INICIANDO, "INICIANDO MODO REY...");
        ResultadoSolicitud result = controlConexion.conectar(selected);
        if (!result.isAceptada()) {
            applyConnectionState(EstadoConexionRapida.DISPONIBLE, result.getMensaje());
            showStatus("ERROR: " + result.getMensaje(), RetroPalette.ERROR);
        }
    }

    public void actualizarEstadoConexion(EstadoConexionRapida state, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> actualizarEstadoConexion(state, message));
            return;
        }
        applyConnectionState(state, message);
    }

    private void applyConnectionState(EstadoConexionRapida state, String message) {
        connectionState = state;
        connectionStatusLabel.setText(message);
        connectionStatusLabel.setForeground(connectionColor(state));
        updateConnectionControls();
    }

    private void runOperation(EquipmentTask task, String success, Runnable afterSuccess) {
        setBusy(true);
        showStatus("GUARDANDO CAMBIOS...", RetroPalette.WARNING);
        new SwingWorker<EquipoGuardado, Void>() {
            @Override protected EquipoGuardado doInBackground() throws Exception {
                return task.run();
            }

            @Override protected void done() {
                try {
                    get();
                    afterSuccess.run();
                    refreshRecords();
                    showStatus(success, RetroPalette.NEON_GREEN);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showStatus("OPERACIÓN INTERRUMPIDA", RetroPalette.ERROR);
                } catch (ExecutionException error) {
                    showError(error.getCause());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void refreshRecords() {
        recordsPanel.removeAll();
        recordButtons.clear();
        List<EquipoGuardado> records = servicio == null ? List.of() : servicio.listar();
        boolean selectionExists = false;
        for (EquipoGuardado record : records) {
            if (record.getId().equals(selectedId)) {
                selectionExists = true;
                break;
            }
        }
        if (!selectionExists) selectedId = null;
        if (records.isEmpty()) {
            JLabel empty = new JLabel("[ NO HAY EQUIPOS GUARDADOS ]", SwingConstants.CENTER);
            empty.setFont(RetroFonts.SECTION);
            empty.setForeground(RetroPalette.DISABLED);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(28, 8, 8, 8));
            recordsPanel.add(empty);
        } else {
            for (EquipoGuardado equipo : records) {
                JPanel row = buildRecord(equipo);
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
                recordsPanel.add(row);
                recordsPanel.add(Box.createVerticalStrut(8));
            }
        }
        recordsPanel.revalidate();
        recordsPanel.repaint();
        updateConnectionControls();
    }

    private JPanel buildRecord(EquipoGuardado equipo) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        boolean selected = equipo.getId().equals(selectedId);
        row.setBackground(selected ? RetroPalette.HEADER_BLUE : RetroPalette.PANEL_RAISED);
        row.setBorder(RetroBorders.compact(
                selected ? RetroPalette.CYAN : RetroPalette.ROYAL_BLUE));

        JPanel data = new JPanel();
        data.setOpaque(false);
        data.setLayout(new BoxLayout(data, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(equipo.getNombre());
        name.setFont(RetroFonts.SECTION);
        name.setForeground(Color.WHITE);
        JLabel address = new JLabel(equipo.getIp() + ":" + equipo.getPuerto());
        address.setFont(RetroFonts.BODY);
        address.setForeground(RetroPalette.CYAN);
        data.add(name);
        data.add(Box.createVerticalStrut(5));
        data.add(address);

        JPanel actions = new JPanel(new GridLayout(1, 3, 7, 0));
        actions.setOpaque(false);
        RetroButton selectButton = new RetroButton(
                selected ? "SELECCIONADO" : "SELECCIONAR", RetroButton.Style.SECONDARY);
        selectButton.setName("connection.select." + equipo.getId());
        selectButton.addActionListener(event -> select(equipo));
        RetroButton editButton = new RetroButton("EDITAR", RetroButton.Style.SECONDARY);
        editButton.setName("connection.edit." + equipo.getId());
        editButton.addActionListener(event -> edit(equipo));
        RetroButton deleteButton = new RetroButton("ELIMINAR", RetroButton.Style.SECONDARY);
        deleteButton.setName("connection.delete." + equipo.getId());
        deleteButton.addActionListener(event -> requestDelete(equipo));
        actions.add(selectButton);
        actions.add(editButton);
        actions.add(deleteButton);
        recordButtons.add(selectButton);
        recordButtons.add(editButton);
        recordButtons.add(deleteButton);

        row.add(data, BorderLayout.CENTER);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void clearForm() {
        editingId = null;
        formTitle.setText("AGREGAR EQUIPO");
        nameField.setText("");
        ipField.setText("");
        portField.setText("8080");
    }

    private void setBusy(boolean value) {
        busy = value;
        boolean enabled = !value && servicio != null;
        nameField.setEnabled(enabled);
        ipField.setEnabled(enabled);
        portField.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
        for (AbstractButton button : recordButtons) button.setEnabled(!value);
        updateConnectionControls();
    }

    private void updateConnectionControls() {
        if (connectButton == null) return;
        EquipoGuardado selected = selectedRecord();
        selectedLabel.setText(selected == null
                ? "DESTINO: NINGÚN EQUIPO SELECCIONADO"
                : "DESTINO: " + selected.getNombre() + " // "
                        + selected.getIp() + ":" + selected.getPuerto());

        boolean active = isActiveConnectionState(connectionState);
        connectButton.setText(active ? "DETENER" : "CONECTAR");
        boolean transitional = connectionState == EstadoConexionRapida.INICIANDO
                || connectionState == EstadoConexionRapida.FINALIZANDO;
        boolean canConnect = connectionState == EstadoConexionRapida.DISPONIBLE
                && selected != null;
        connectButton.setEnabled(!busy && controlConexion != null && !transitional
                && (active || canConnect));
        connectButton.setCursor(connectButton.isEnabled()
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
    }

    private EquipoGuardado selectedRecord() {
        if (selectedId == null || servicio == null) return null;
        for (EquipoGuardado equipo : servicio.listar()) {
            if (equipo.getId().equals(selectedId)) return equipo;
        }
        return null;
    }

    private static boolean isActiveConnectionState(EstadoConexionRapida state) {
        return state == EstadoConexionRapida.ACTIVO_LOCAL
                || state == EstadoConexionRapida.CONECTANDO
                || state == EstadoConexionRapida.CONECTADO
                || state == EstadoConexionRapida.ERROR_ACTIVO;
    }

    private static Color connectionColor(EstadoConexionRapida state) {
        switch (state) {
            case CONECTADO: return RetroPalette.NEON_GREEN;
            case INICIANDO:
            case CONECTANDO:
            case FINALIZANDO: return RetroPalette.WARNING;
            case ROL_NO_PERMITIDO:
            case ERROR_ACTIVO: return RetroPalette.ERROR;
            default: return RetroPalette.CYAN;
        }
    }

    private void showError(Throwable error) {
        if (error instanceof ValidacionEquipoException) {
            showStatus("ERROR: " + error.getMessage().toUpperCase(), RetroPalette.ERROR);
        } else if (error instanceof PersistenciaEquiposException) {
            showStatus("ERROR DE PERSISTENCIA: " + error.getMessage().toUpperCase(),
                    RetroPalette.ERROR);
        } else {
            showStatus("ERROR: NO SE PUDO COMPLETAR LA OPERACIÓN", RetroPalette.ERROR);
        }
    }

    private void showStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(RetroFonts.LABEL);
        label.setForeground(RetroPalette.TEXT_SECONDARY);
        return label;
    }

    private static JTextField textField(String name, String value) {
        JTextField field = new JTextField(value);
        field.setName(name);
        field.setFont(RetroFonts.BODY);
        field.setForeground(RetroPalette.TEXT_PRIMARY);
        field.setBackground(RetroPalette.CONTROL);
        field.setCaretColor(RetroPalette.ELECTRIC_BLUE);
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(RetroPalette.ROYAL_BLUE, 1),
                new EmptyBorder(7, 9, 7, 9)));
        return field;
    }

    private static boolean confirmWithDialog(Component parent, EquipoGuardado equipo) {
        int result = JOptionPane.showConfirmDialog(parent,
                "¿Eliminar el equipo \"" + equipo.getNombre() + "\"?",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    @FunctionalInterface
    private interface EquipmentTask {
        EquipoGuardado run() throws Exception;
    }
}
