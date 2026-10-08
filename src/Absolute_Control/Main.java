package Absolute_Control;

import Absolute_Control.core.Cliente;
import Absolute_Control.core.Discovery;
import Absolute_Control.core.Servidor;
import Absolute_Control.core.GeometriaPantalla;
import Absolute_Control.ui.*;
import com.github.kwhat.jnativehook.GlobalScreen;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
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
    private JPanel     panelConfigResumen;
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
        btnTogglePanel = new RetroButton("▲  CONFIGURACIÓN", RetroButton.Style.HEADER);
        btnTogglePanel.addActionListener(e -> toggleConfig());
        headerPanel = new HeaderPanel(btnTogglePanel);
        return headerPanel;
    }

    private void toggleConfig() {
        panelAbierto = !panelAbierto;
        panelConfig.setVisible(panelAbierto);
        panelConfigResumen.setVisible(!panelAbierto);
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
        lblIpLocal.setFont(RetroFonts.BODY);
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

        btnBuscarIp = new RetroButton("BUSCAR", RetroButton.Style.SECONDARY);
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
        hint.setFont(RetroFonts.SECONDARY);
        hint.setForeground(RetroPalette.TEXT_SECONDARY);
        panel.add(hint, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildConnectionsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 292));
        panel.setPreferredSize(new Dimension(0, 292));
        panel.add(buildPc1Panel());
        panel.add(buildPc2Panel());
        return panel;
    }

    private JPanel buildPc1Panel() {
        JPanel panel = retroPanel(new BorderLayout(0, 7), RetroPalette.ELECTRIC_BLUE);

        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        JLabel name = cardTitle("PC1");
        name.setIcon(RetroIcons.icon(RetroIcons.Type.MONITOR, RetroPalette.CYAN, 18));
        name.setIconTextGap(7);
        JLabel tag = new JLabel("CONEXIÓN ACTUAL");
        tag.setFont(RetroFonts.LABEL);
        tag.setForeground(RetroPalette.NEON_GREEN);
        title.add(name, BorderLayout.WEST);
        title.add(tag, BorderLayout.EAST);

        btnAccion = buildAccionButton("CONECTAR");
        btnAccion.setPreferredSize(new Dimension(0, 36));
        JPanel action = new JPanel(new BorderLayout(0, 4));
        action.setOpaque(false);
        action.setPreferredSize(new Dimension(0, 60));
        action.add(btnAccion, BorderLayout.NORTH);
        action.add(buildConnectionStatus(), BorderLayout.CENTER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(buildPc1Content(), BorderLayout.CENTER);
        panel.add(action, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPc1Content() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(buildConfigPanel());

        panelConfigResumen = new JPanel();
        panelConfigResumen.setOpaque(false);
        panelConfigResumen.setLayout(new BoxLayout(panelConfigResumen, BoxLayout.Y_AXIS));
        panelConfigResumen.setVisible(false);

        JLabel hidden = new JLabel("[ CONFIGURACIÓN OCULTA ]");
        hidden.setFont(RetroFonts.SECTION);
        hidden.setForeground(RetroPalette.CYAN);
        hidden.setAlignmentX(CENTER_ALIGNMENT);
        JLabel hint = new JLabel("USA EL BOTÓN DEL HEADER PARA MOSTRAR IP, PUERTO Y POSICIÓN");
        hint.setFont(RetroFonts.SECONDARY);
        hint.setForeground(RetroPalette.TEXT_SECONDARY);
        hint.setAlignmentX(CENTER_ALIGNMENT);

        panelConfigResumen.add(Box.createVerticalGlue());
        panelConfigResumen.add(hidden);
        panelConfigResumen.add(Box.createVerticalStrut(8));
        panelConfigResumen.add(hint);
        panelConfigResumen.add(Box.createVerticalGlue());
        content.add(panelConfigResumen);
        return content;
    }

    private JPanel buildPc2Panel() {
        JPanel panel = retroPanel(new BorderLayout(), RetroPalette.DISABLED);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel name = cardTitle("PC2");
        name.setIcon(RetroIcons.icon(RetroIcons.Type.MONITOR, RetroPalette.DISABLED, 18));
        name.setIconTextGap(7);
        name.setForeground(RetroPalette.DISABLED);
        name.setAlignmentX(CENTER_ALIGNMENT);
        JLabel future = new JLabel("PRÓXIMAMENTE");
        future.setFont(RetroFonts.CARD_TITLE);
        future.setForeground(RetroPalette.DISABLED);
        future.setAlignmentX(CENTER_ALIGNMENT);
        JLabel detail = new JLabel("SEGUNDA CONEXIÓN NO IMPLEMENTADA");
        detail.setFont(RetroFonts.SECONDARY);
        detail.setForeground(RetroPalette.TEXT_SECONDARY);
        detail.setAlignmentX(CENTER_ALIGNMENT);
        JLabel slot = new JLabel("[ SLOT REMOTO 02 // RESERVADO ]");
        slot.setFont(RetroFonts.LABEL);
        slot.setForeground(RetroPalette.DISABLED);
        slot.setAlignmentX(CENTER_ALIGNMENT);
        JLabel divider = new JLabel("──────────  FUTURE EXPANSION  ──────────");
        divider.setFont(RetroFonts.SECONDARY);
        divider.setForeground(RetroPalette.ROYAL_BLUE);
        divider.setAlignmentX(CENTER_ALIGNMENT);

        content.add(Box.createVerticalGlue());
        content.add(name);
        content.add(Box.createVerticalStrut(10));
        content.add(slot);
        content.add(Box.createVerticalStrut(18));
        content.add(future);
        content.add(Box.createVerticalStrut(7));
        content.add(detail);
        content.add(Box.createVerticalStrut(18));
        content.add(divider);
        content.add(Box.createVerticalGlue());
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildConnectionStatus() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        lblEstado = new JLabel("○  Inactivo");
        lblEstado.setFont(RetroFonts.STATUS);
        lblEstado.setForeground(RetroPalette.ERROR);
        p.add(lblEstado, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel consoleTitle = buildSectionTitle("CONSOLA DE EVENTOS");
        consoleTitle.setIcon(RetroIcons.icon(RetroIcons.Type.EVENTS, RetroPalette.CYAN, 16));
        consoleTitle.setIconTextGap(7);
        heading.add(consoleTitle, BorderLayout.WEST);
        JLabel channel = new JLabel("[ EVENT LOG // OUTPUT ]");
        channel.setFont(RetroFonts.SECONDARY);
        channel.setForeground(RetroPalette.DISABLED);
        heading.add(channel, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(buildLog(), BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane buildLog() {
        logArea = new JTextArea();
        logArea.setFont(RetroFonts.CONSOLE);
        logArea.setBackground(RetroPalette.CONSOLE);
        logArea.setForeground(RetroPalette.NEON_GREEN);
        logArea.setEditable(false);
        logArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(RetroBorders.compact(RetroPalette.ELECTRIC_BLUE));
        sp.getVerticalScrollBar().setUI(new RetroScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(14, 0));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(RetroPalette.CONSOLE);
        return sp;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 3));
        footer.setBackground(RetroPalette.PANEL);
        footer.setBorder(RetroBorders.padded(RetroPalette.ROYAL_BLUE, 2, 4, 16));
        footer.setPreferredSize(new Dimension(0, 52));

        JLabel brand = new JLabel("ABSOLUTE CONTROL // NETWORK KVM");
        brand.setFont(RetroFonts.LABEL);
        brand.setForeground(RetroPalette.CYAN);
        brand.setIcon(RetroIcons.icon(RetroIcons.Type.CONNECTION, RetroPalette.CYAN, 16));
        brand.setIconTextGap(7);
        lblFooterEstado = new JLabel("SESIÓN: INACTIVA");
        lblFooterEstado.setFont(RetroFonts.STATUS);
        lblFooterEstado.setForeground(RetroPalette.ERROR);
        lblFooterEstado.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(brand, BorderLayout.NORTH);
        footer.add(lblFooterEstado, BorderLayout.CENTER);
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
        JPanel panel = new JPanel(layout) {
            private static final long serialVersionUID = 1L;

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(RetroPalette.PANEL_RAISED);
                for (int y = 9; y < getHeight() - 8; y += 18) {
                    for (int x = 9; x < getWidth() - 8; x += 18) g.fillRect(x, y, 1, 1);
                }
                g.setColor(RetroPalette.ROYAL_BLUE);
                g.fillRect(7, 7, 4, 4);
                g.fillRect(Math.max(7, getWidth() - 13), 7, 4, 4);
            }
        };
        panel.setBackground(RetroPalette.PANEL);
        panel.setBorder(RetroBorders.panel(borderColor));
        return panel;
    }

    private JLabel buildSectionTitle(String text) {
        JLabel label = new JLabel("// " + text);
        label.setFont(RetroFonts.SECTION);
        label.setForeground(RetroPalette.CYAN);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(RetroFonts.CARD_TITLE);
        label.setForeground(RetroPalette.TEXT_PRIMARY);
        return label;
    }

    private JLabel buildLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(RetroFonts.LABEL);
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
        f.setFont(RetroFonts.BODY);
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
        JToggleButton b = new RetroToggleButton(text);
        b.setSelected(selected);
        return b;
    }

    private JButton buildAccionButton(String text) {
        JButton b = new RetroButton(text, RetroButton.Style.PRIMARY);
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
