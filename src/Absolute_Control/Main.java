package Absolute_Control;

import Absolute_Control.core.Cliente;
import Absolute_Control.core.Discovery;
import Absolute_Control.core.Servidor;
import Absolute_Control.core.GeometriaPantalla;
import Absolute_Control.ui.HeaderPanel;
import Absolute_Control.ui.NavigationPanel;
import Absolute_Control.ui.RetroPalette;
import Absolute_Control.ui.TopologyPanel;
import com.github.kwhat.jnativehook.GlobalScreen;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends JFrame {

    /**
     * Nivel de estado para el indicador tipo semáforo de la GUI.
     * INACTIVO = rojo, ESPERANDO = amarillo, ACTIVO = verde.
     */
    private enum EstadoConexion {
        INACTIVO, ESPERANDO, ACTIVO
    }

    // ── Colores ───────────────────────────────────────────────────
    private static final Font  MONO     = new Font("Consolas", Font.PLAIN, 12);
    private static final Font  UI_FONT  = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  BOLD     = new Font("Segoe UI", Font.BOLD, 13);

    // ── Estado ────────────────────────────────────────────────────
    private boolean modoRey       = true;
    private boolean secundariaALaDerecha = true;
    private boolean panelAbierto  = true;

    // Indica si el modo Rey está "activo" (hook registrado, esperando en el
    // borde o controlando), independientemente de si en este instante el
    // socket está conectado o no. isConectado() del Cliente refleja solo el
    // estado del socket, que se apaga y prende solo cada vez que el mouse
    // cruza el borde — por eso no sirve para decidir si el botón debe decir
    // CONECTAR o DETENER.
    private boolean reyActivo = false;
    private long generacion = 0;
    private boolean parando, cierreEnCurso, cerrarVentana;

    private Cliente  cliente;
    private Servidor servidor;

    // ── Componentes ───────────────────────────────────────────────
    private JPanel     panelConfig;
    private JButton    btnTogglePanel;
    private JToggleButton btnRey, btnEsclavo;
    private JTextField txtIp, txtPuerto;
    private JButton    btnBuscarIp;
    private JToggleButton btnIzquierda, btnDerecha;
    private JLabel     lblIpLocal;
    private JButton    btnAccion;
    private JLabel     lblEstado;
    private JLabel     lblFooterEstado;
    private JTextArea  logArea;
    private HeaderPanel headerPanel;

    public Main() {
        setTitle("Absolute Control");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(RetroPalette.BACKGROUND);
        setLayout(new BorderLayout());

        add(buildHeader(),         BorderLayout.NORTH);
        add(new NavigationPanel(), BorderLayout.WEST);
        add(buildMainPanel(),      BorderLayout.CENTER);
        add(buildFooter(),         BorderLayout.SOUTH);

        detectarIpLocal();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                detenerComponentes(true);
            }
        });
    }

    // ── Panel toggle (cabecera colapsable) ────────────────────────

    private JPanel buildHeader() {
        btnTogglePanel = new JButton("▲  CONFIGURACIÓN");
        btnTogglePanel.setFont(UI_FONT);
        btnTogglePanel.setForeground(RetroPalette.TEXT_PRIMARY);
        btnTogglePanel.setBackground(RetroPalette.ROYAL_BLUE);
        btnTogglePanel.setBorder(new CompoundBorder(
                new LineBorder(RetroPalette.ELECTRIC_BLUE, 1),
                new EmptyBorder(7, 10, 7, 10)));
        btnTogglePanel.setFocusPainted(false);
        btnTogglePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTogglePanel.addActionListener(e -> toggleConfig());
        headerPanel = new HeaderPanel(btnTogglePanel);
        return headerPanel;
    }

    private void toggleConfig() {
        panelAbierto = !panelAbierto;
        panelConfig.setVisible(panelAbierto);
        btnTogglePanel.setText(panelAbierto ? "▲  CONFIGURACIÓN" : "▼  CONFIGURACIÓN");
        revalidate();
        repaint();
    }

    // ── Panel de configuración (colapsable) ───────────────────────

    private JPanel buildConfigPanel() {
        panelConfig = new JPanel(new GridBagLayout());
        panelConfig.setOpaque(false);
        panelConfig.setVisible(true);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 3, 0);

        c.gridy = 0;
        panelConfig.add(buildLabel("IP DEL SERVIDOR (SOLO REY)"), c);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 7, 0);
        panelConfig.add(buildIpRow(), c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 3, 0);
        panelConfig.add(buildLabel("PUERTO"), c);
        txtPuerto = buildTextField("", "8080");
        c.gridy = 3;
        c.insets = new Insets(0, 0, 7, 0);
        panelConfig.add(txtPuerto, c);

        c.gridy = 4;
        c.insets = new Insets(0, 0, 3, 0);
        panelConfig.add(buildLabel("POSICIÓN DE LA PC SECUNDARIA"), c);
        c.gridy = 5;
        c.insets = new Insets(0, 0, 6, 0);
        panelConfig.add(buildPosicionSelector(), c);

        lblIpLocal = new JLabel("IP local: detectando...");
        lblIpLocal.setFont(MONO);
        lblIpLocal.setForeground(RetroPalette.CYAN);
        c.gridy = 6;
        c.insets = new Insets(0, 0, 0, 0);
        panelConfig.add(lblIpLocal, c);

        return panelConfig;
    }

    private JPanel buildModoSelector() {
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnRey     = buildToggle("REY / CLIENTE", true);
        btnEsclavo = buildToggle("ESCLAVO / SERVIDOR", false);

        ButtonGroup g = new ButtonGroup();
        g.add(btnRey); g.add(btnEsclavo);
        btnRey.setSelected(true);

        btnRey.addActionListener(e -> { modoRey = true;  actualizarModo(); });
        btnEsclavo.addActionListener(e -> { modoRey = false; actualizarModo(); });

        p.add(btnRey); p.add(btnEsclavo);
        return p;
    }

    private JPanel buildIpRow() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        txtIp = buildTextField("Sin IP — usá \"Buscar\" o escribila", "");
        p.add(txtIp, BorderLayout.CENTER);

        btnBuscarIp = new JButton("Buscar");
        btnBuscarIp.setFont(UI_FONT);
        btnBuscarIp.setForeground(RetroPalette.TEXT_PRIMARY);
        btnBuscarIp.setBackground(RetroPalette.CONTROL);
        btnBuscarIp.setBorderPainted(false);
        btnBuscarIp.setFocusPainted(false);
        btnBuscarIp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBuscarIp.addActionListener(e -> buscarServidorEnRed());
        p.add(btnBuscarIp, BorderLayout.EAST);

        return p;
    }

    /**
     * Busca un Servidor (Esclavo) en la red local por broadcast UDP y, si
     * lo encuentra, rellena el campo de IP automáticamente. La búsqueda en
     * sí es bloqueante (espera hasta 2s una respuesta), así que corre en un
     * hilo de fondo para no congelar la GUI.
     */
    private void buscarServidorEnRed() {
        final long actual = ++generacion;
        btnBuscarIp.setEnabled(false);
        btnBuscarIp.setText("Buscando...");
        setEstado(EstadoConexion.ESPERANDO, "Buscando servidor en la red...");
        log("Buscando servidor en la red local...");

        Thread hilo = new Thread(() -> {
            Discovery.ServidorEncontrado encontrado = Discovery.buscarServidor(2000);
            SwingUtilities.invokeLater(() -> {
                if (actual != generacion || reyActivo || (servidor != null && servidor.isCorriendo())) return;
                btnBuscarIp.setEnabled(modoRey);
                btnBuscarIp.setText("Buscar");
                if (encontrado != null) {
                    txtIp.setText(encontrado.ip);
                    txtPuerto.setText(String.valueOf(encontrado.puertoTcp));
                    log("Servidor encontrado: " + encontrado.ip + ":" + encontrado.puertoTcp);
                    setEstado(EstadoConexion.INACTIVO, "Servidor encontrado — listo para conectar");
                } else {
                    log("No se encontró ningún servidor en la red. Probá escribiendo la IP manualmente.");
                    setEstado(EstadoConexion.INACTIVO, "Inactivo");
                }
            });
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    private JPanel buildPosicionSelector() {
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnIzquierda = buildToggle("◄  Secundaria a izquierda", false);
        btnDerecha   = buildToggle("Secundaria a derecha  ►",   true);

        ButtonGroup g = new ButtonGroup();
        g.add(btnIzquierda); g.add(btnDerecha);
        btnDerecha.setSelected(true);

        btnIzquierda.addActionListener(e -> secundariaALaDerecha = false);
        btnDerecha.addActionListener(e   -> secundariaALaDerecha = true);

        p.add(btnIzquierda); p.add(btnDerecha);
        return p;
    }

    private void actualizarModo() {
        generacion++;
        btnBuscarIp.setText("Buscar");
        txtIp.setEnabled(modoRey);
        btnBuscarIp.setEnabled(modoRey);
        txtIp.setBackground(modoRey ? RetroPalette.CONTROL : RetroPalette.CONTROL_DISABLED);
        btnAccion.setText(modoRey ? "CONECTAR" : "INICIAR SERVIDOR");
    }

    // ── Panel principal (siempre visible) ─────────────────────────

    private JPanel buildMainPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(RetroPalette.BACKGROUND);
        p.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel dashboard = new JPanel();
        dashboard.setLayout(new BoxLayout(dashboard, BoxLayout.Y_AXIS));
        dashboard.setOpaque(false);

        dashboard.add(buildSectionTitle("MODO DE OPERACIÓN"));
        dashboard.add(Box.createVerticalStrut(5));
        dashboard.add(buildOperationPanel());
        dashboard.add(Box.createVerticalStrut(10));
        dashboard.add(buildSectionTitle("CONEXIONES"));
        dashboard.add(Box.createVerticalStrut(5));
        dashboard.add(buildConnectionsPanel());
        dashboard.add(Box.createVerticalStrut(10));
        dashboard.add(buildSectionTitle("DISTRIBUCIÓN DE PANTALLAS"));
        dashboard.add(Box.createVerticalStrut(5));
        TopologyPanel topology = new TopologyPanel();
        topology.setAlignmentX(LEFT_ALIGNMENT);
        topology.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        topology.setPreferredSize(new Dimension(0, 78));
        dashboard.add(topology);

        p.add(dashboard, BorderLayout.NORTH);
        p.add(buildConsolePanel(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildOperationPanel() {
        JPanel panel = retroPanel(new BorderLayout(12, 0), RetroPalette.ROYAL_BLUE);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        panel.setPreferredSize(new Dimension(0, 54));
        panel.add(buildModoSelector(), BorderLayout.CENTER);

        JLabel hint = new JLabel("SELECCIONA EL ROL DE ESTE EQUIPO");
        hint.setFont(new Font("Consolas", Font.PLAIN, 10));
        hint.setForeground(RetroPalette.CYAN);
        panel.add(hint, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildConnectionsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));
        panel.setPreferredSize(new Dimension(0, 270));
        panel.add(buildPc1Panel());
        panel.add(buildPc2Panel());
        return panel;
    }

    private JPanel buildPc1Panel() {
        JPanel panel = retroPanel(new BorderLayout(0, 7), RetroPalette.ELECTRIC_BLUE);

        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        JLabel name = cardTitle("PC1");
        JLabel tag = new JLabel("CONEXIÓN ACTUAL");
        tag.setFont(new Font("Consolas", Font.BOLD, 10));
        tag.setForeground(RetroPalette.NEON_GREEN);
        title.add(name, BorderLayout.WEST);
        title.add(tag, BorderLayout.EAST);

        btnAccion = buildAccionButton("CONECTAR");
        btnAccion.setPreferredSize(new Dimension(0, 38));
        JPanel action = new JPanel(new GridLayout(1, 2, 8, 0));
        action.setOpaque(false);
        action.setPreferredSize(new Dimension(0, 38));
        action.add(btnAccion);
        action.add(buildConnectionStatus());

        panel.add(title, BorderLayout.NORTH);
        panel.add(buildConfigPanel(), BorderLayout.CENTER);
        panel.add(action, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPc2Panel() {
        JPanel panel = retroPanel(new BorderLayout(), RetroPalette.DISABLED);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel name = cardTitle("PC2");
        name.setForeground(RetroPalette.DISABLED);
        name.setAlignmentX(CENTER_ALIGNMENT);
        JLabel future = new JLabel("PRÓXIMAMENTE");
        future.setFont(new Font("Consolas", Font.BOLD, 18));
        future.setForeground(RetroPalette.DISABLED);
        future.setAlignmentX(CENTER_ALIGNMENT);
        JLabel detail = new JLabel("SEGUNDA CONEXIÓN NO IMPLEMENTADA");
        detail.setFont(new Font("Consolas", Font.PLAIN, 11));
        detail.setForeground(RetroPalette.CLASSIC_GRAY);
        detail.setAlignmentX(CENTER_ALIGNMENT);

        content.add(Box.createVerticalGlue());
        content.add(name);
        content.add(Box.createVerticalStrut(24));
        content.add(future);
        content.add(Box.createVerticalStrut(7));
        content.add(detail);
        content.add(Box.createVerticalGlue());
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildConnectionStatus() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);

        lblEstado = new JLabel("○  Inactivo");
        lblEstado.setFont(UI_FONT);
        lblEstado.setForeground(RetroPalette.ERROR);
        p.add(lblEstado);
        return p;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.add(buildSectionTitle("CONSOLA DE EVENTOS"), BorderLayout.NORTH);
        panel.add(buildLog(), BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane buildLog() {
        logArea = new JTextArea();
        logArea.setFont(MONO);
        logArea.setBackground(RetroPalette.CONSOLE);
        logArea.setForeground(RetroPalette.NEON_GREEN);
        logArea.setEditable(false);
        logArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(new LineBorder(RetroPalette.ELECTRIC_BLUE, 2));
        return sp;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(RetroPalette.PANEL);
        footer.setBorder(new CompoundBorder(
                new MatteBorder(2, 0, 0, 0, RetroPalette.ROYAL_BLUE),
                new EmptyBorder(7, 16, 7, 16)));

        JLabel brand = new JLabel("ABSOLUTE CONTROL // NETWORK KVM");
        brand.setFont(new Font("Consolas", Font.BOLD, 10));
        brand.setForeground(RetroPalette.CYAN);
        lblFooterEstado = new JLabel("SESIÓN: INACTIVA");
        lblFooterEstado.setFont(new Font("Consolas", Font.BOLD, 10));
        lblFooterEstado.setForeground(RetroPalette.ERROR);
        footer.add(brand, BorderLayout.WEST);
        footer.add(lblFooterEstado, BorderLayout.EAST);
        return footer;
    }

    // ── Lógica de acción ──────────────────────────────────────────

    private void handleAccion() {
        if (parando) { detenerComponentes(false); return; }
        if (modoRey) {
            // Antes se usaba "cliente != null && cliente.isConectado()" para
            // decidir si el botón debía desconectar o conectar. El problema:
            // isConectado() refleja solo el estado del socket, que el propio
            // Cliente apaga automáticamente cada vez que el control "regresa"
            // al cruzar el borde. Si el usuario apretaba Desconectar justo
            // después de que el control hubiera vuelto solo, isConectado()
            // ya era false, así que el botón entraba al else y arrancaba un
            // cliente nuevo en vez de detener el modo Rey. Usamos reyActivo,
            // que solo se apaga cuando el usuario detiene explícitamente.
            if (reyActivo) {
                detenerModoRey();
            } else {
                iniciarCliente();
            }
        } else {
            if (servidor != null) {
                detenerComponentes(false);
            } else {
                iniciarServidor();
            }
        }
    }

    private void iniciarCliente() {
        String ip    = txtIp.getText().trim();
        int    puerto;
        try { puerto = Integer.parseInt(txtPuerto.getText().trim()); }
        catch (NumberFormatException e) { log("Puerto invalido"); return; }

        GeometriaPantalla pantalla = GeometriaPantalla.actual();

        if (ip.isEmpty() || puerto < 1 || puerto > 65535) { log("IP o puerto invalido"); return; }
        final long actual = ++generacion;
        cliente = new Cliente(ip, puerto, secundariaALaDerecha, pantalla, this::log, estado ->
                SwingUtilities.invokeLater(() -> {
                    if (actual != generacion || !reyActivo || parando) return;
                    switch (estado) {
                        case REMOTO -> setEstado(EstadoConexion.ACTIVO, "Control remoto activo");
                        case CONECTANDO -> setEstado(EstadoConexion.ESPERANDO, "Conectando...");
                        case ERROR -> setEstado(EstadoConexion.INACTIVO, "Desconectado/error - control local; vuelve al borde");
                        default -> setEstado(EstadoConexion.ESPERANDO, "Control local - listo para cruzar el borde");
                    }
                }));

        try {
            if (!GlobalScreen.isNativeHookRegistered()) GlobalScreen.registerNativeHook();
            cliente.iniciarHandlers();
            reyActivo = true;
            bloquearConfig(true);
            setEstado(EstadoConexion.ESPERANDO, "Rey activo — lleva el mouse al borde para conectar");
            btnAccion.setText("DETENER");
            log("Modo Rey iniciado. Servidor: " + ip + ":" + puerto);
        } catch (Exception e) {
            detenerModoRey();
            log("Error: " + e.getMessage());
        }
    }

    private void detenerModoRey() { detenerComponentes(false); }

    private void detenerComponentes(boolean cerrar) {
        cerrarVentana |= cerrar;
        if (cierreEnCurso) return;
        generacion++; reyActivo = false; parando = true; cierreEnCurso = true;
        final Cliente anteriorCliente = cliente;
        final Servidor anteriorServidor = servidor;
        if (anteriorCliente != null) anteriorCliente.detenerHandlers();
        if (anteriorServidor != null) anteriorServidor.detener();
        bloquearConfig(true); btnAccion.setEnabled(false);
        setEstado(EstadoConexion.ESPERANDO, "Control local - finalizando conexiones...");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                boolean terminado = true;
                if (anteriorCliente != null) {
                    try { if (GlobalScreen.isNativeHookRegistered()) GlobalScreen.unregisterNativeHook(); }
                    catch (Exception e) { log("Error retirando hook: " + e.getMessage()); terminado = false; }
                    terminado &= anteriorCliente.esperarDetenido();
                }
                if (anteriorServidor != null) terminado &= anteriorServidor.esperarDetenido();
                return terminado;
            }
            @Override protected void done() {
                boolean terminado = false;
                try { terminado = get(); } catch (Exception e) { log("Error de cierre: " + e.getMessage()); }
                cierreEnCurso = false;
                if (cerrarVentana && terminado) { dispose(); System.exit(0); return; }
                btnAccion.setEnabled(true);
                if (terminado) {
                    cliente = null; servidor = null; parando = false;
                    bloquearConfig(false);
                    setEstado(EstadoConexion.INACTIVO, "Control local - limpieza terminada");
                    btnAccion.setText(modoRey ? "CONECTAR" : "INICIAR SERVIDOR");
                } else {
                    setEstado(EstadoConexion.INACTIVO, "Cierre incompleto - revisar registro");
                    btnAccion.setText("VERIFICAR CIERRE");
                    // Conservar objetos; nunca iniciar otra instancia encima de hilos antiguos.
                }
            }
        }.execute();
    }

    private void bloquearConfig(boolean bloquear) {
        btnRey.setEnabled(!bloquear); btnEsclavo.setEnabled(!bloquear);
        txtIp.setEnabled(!bloquear && modoRey); txtPuerto.setEnabled(!bloquear);
        btnIzquierda.setEnabled(!bloquear); btnDerecha.setEnabled(!bloquear);
        btnBuscarIp.setEnabled(!bloquear && modoRey);
        if (!bloquear) btnBuscarIp.setText("Buscar");
    }

    private void iniciarServidor() {
        int puerto;
        try { puerto = Integer.parseInt(txtPuerto.getText().trim()); }
        catch (NumberFormatException e) { log("Puerto invalido"); return; }

        if (puerto < 1 || puerto > 65535) { log("Puerto invalido"); return; }
        final long actual = ++generacion;
        servidor = new Servidor(puerto, secundariaALaDerecha, this::log, estado ->
                SwingUtilities.invokeLater(() -> {
                    if (actual != generacion || servidor == null || parando) return;
                    if (estado == Servidor.Estado.REMOTO)
                        setEstado(EstadoConexion.ACTIVO, "Servidor - control remoto activo");
                    else if (estado == Servidor.Estado.ESPERANDO)
                        setEstado(EstadoConexion.ESPERANDO, "Servidor listo - esperando cliente");
                    else setEstado(EstadoConexion.INACTIVO, "Servidor detenido/error");
                }));
        try {
            servidor.iniciar();
            bloquearConfig(true);
            setEstado(EstadoConexion.ESPERANDO, "Esclavo activo en :" + puerto + " — esperando cliente");
            btnAccion.setText("DETENER SERVIDOR");
        } catch (Exception e) {
            log("Error iniciando servidor: " + e.getMessage());
            detenerComponentes(false);
        }
    }

    // ── Utilidades ────────────────────────────────────────────────

    private void setEstado(EstadoConexion estado, String msg) {
        String punto = "●  ";
        Color color;
        switch (estado) {
            case ACTIVO:
                color = RetroPalette.NEON_GREEN;
                break;
            case ESPERANDO:
                color = RetroPalette.WARNING;
                break;
            default:
                color = RetroPalette.ERROR;
                punto = "○  ";
                break;
        }
        lblEstado.setText(punto + msg);
        lblEstado.setForeground(color);
        if (lblFooterEstado != null) {
            lblFooterEstado.setText("SESIÓN: " + msg.toUpperCase());
            lblFooterEstado.setForeground(color);
        }
        if (headerPanel != null) headerPanel.setSystemState(msg.toUpperCase(), color);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            java.time.LocalTime t = java.time.LocalTime.now();
            logArea.append(String.format("[%02d:%02d:%02d] %s%n",
                    t.getHour(), t.getMinute(), t.getSecond(), msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());

        });
    }

    private void detectarIpLocal() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            if (lblIpLocal != null) lblIpLocal.setText("IP local: " + ip);
        } catch (Exception e) {
            if (lblIpLocal != null) lblIpLocal.setText("IP local: desconocida");
        }
    }

    // ── Helpers de UI ─────────────────────────────────────────────

    private JPanel retroPanel(LayoutManager layout, Color borderColor) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(RetroPalette.PANEL);
        panel.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 2),
                new EmptyBorder(8, 10, 8, 10)));
        return panel;
    }

    private JLabel buildSectionTitle(String text) {
        JLabel label = new JLabel("// " + text);
        label.setFont(new Font("Consolas", Font.BOLD, 12));
        label.setForeground(RetroPalette.CYAN);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Consolas", Font.BOLD, 16));
        label.setForeground(RetroPalette.TEXT_PRIMARY);
        return label;
    }

    private JLabel buildLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(RetroPalette.DISABLED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JTextField buildTextField(String placeholder) {
        return buildTextField(placeholder, "");
    }

    /**
     * Crea un campo de texto con placeholder real: el texto de ejemplo se ve
     * en gris solo cuando el campo está vacío y sin foco, y desaparece en
     * cuanto el usuario escribe algo o lo completa el botón "Buscar" (a
     * diferencia de poner el placeholder como texto real dentro del campo,
     * que se confundía con un valor ya ingresado, p. ej. una IP de ejemplo
     * que parecía una IP real).
     *
     * @param placeholder  texto gris de ejemplo, se ve cuando el campo está vacío
     * @param valorInicial valor real con el que arranca el campo (puede ser "")
     */
    private JTextField buildTextField(String placeholder, String valorInicial) {
        JTextField f = new JTextField(valorInicial) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (placeholder != null && !placeholder.isEmpty()
                        && getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(RetroPalette.DISABLED);
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(placeholder, ins.left, y);
                    g2.dispose();
                }
            }
        };
        f.setFont(MONO);
        f.setForeground(RetroPalette.TEXT_PRIMARY);
        f.setBackground(RetroPalette.CONTROL);
        f.setCaretColor(RetroPalette.ELECTRIC_BLUE);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(RetroPalette.ROYAL_BLUE, 1),
                new EmptyBorder(6, 10, 6, 10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // Repintamos al ganar/perder foco para que el placeholder
        // aparezca/desaparezca de inmediato sin esperar otro evento.
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { f.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e)   { f.repaint(); }
        });

        return f;
    }

    private JToggleButton buildToggle(String text, boolean selected) {
        JToggleButton b = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? RetroPalette.ROYAL_BLUE : RetroPalette.CONTROL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(BOLD);
        b.setForeground(selected ? Color.WHITE : RetroPalette.DISABLED);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> b.setForeground(b.isSelected()
                ? Color.WHITE : RetroPalette.DISABLED));
        return b;
    }

    private JButton buildAccionButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? RetroPalette.NEON_GREEN : RetroPalette.DISABLED);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(BOLD);
        b.setForeground(RetroPalette.BACKGROUND);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.addActionListener(e -> handleAccion());
        return b;
    }

    // ── Main ──────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            Logger hookLog = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            hookLog.setLevel(Level.WARNING);
            hookLog.setUseParentHandlers(false);
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
