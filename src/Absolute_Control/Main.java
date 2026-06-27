package Absolute_Control;

import Absolute_Control.core.Cliente;
import Absolute_Control.core.Discovery;
import Absolute_Control.core.Servidor;
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
    private static final Color BG       = new Color(24, 26, 34);
    private static final Color PANEL_BG = new Color(32, 35, 46);
    private static final Color ACCENT   = new Color(64, 196, 255);
    private static final Color SUCCESS  = new Color(50, 210, 130);
    private static final Color WARNING  = new Color(240, 195, 60);
    private static final Color DANGER   = new Color(255, 80, 100);
    private static final Color TEXT     = new Color(220, 225, 240);
    private static final Color TEXT_DIM = new Color(120, 130, 155);
    private static final Color BORDER   = new Color(45, 52, 70);
    private static final Font  MONO     = new Font("Consolas", Font.PLAIN, 12);
    private static final Font  UI_FONT  = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  BOLD     = new Font("Segoe UI", Font.BOLD, 13);

    // ── Estado ────────────────────────────────────────────────────
    private boolean modoRey       = true;
    private boolean servidorDerecha = true;
    private boolean panelAbierto  = false;

    // Indica si el modo Rey está "activo" (hook registrado, esperando en el
    // borde o controlando), independientemente de si en este instante el
    // socket está conectado o no. isConectado() del Cliente refleja solo el
    // estado del socket, que se apaga y prende solo cada vez que el mouse
    // cruza el borde — por eso no sirve para decidir si el botón debe decir
    // CONECTAR o DETENER.
    private boolean reyActivo = false;

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
    private JTextArea  logArea;

    public Main() {
        setTitle("Absolute Control");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(480, 480);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildTogglePanel(), BorderLayout.NORTH);
        add(buildMainPanel(),   BorderLayout.CENTER);

        detectarIpLocal();
    }

    // ── Panel toggle (cabecera colapsable) ────────────────────────

    private JPanel buildTogglePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL_BG);
        p.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));

        JLabel title = new JLabel("Absolute Control");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(ACCENT);
        title.setBorder(new EmptyBorder(12, 14, 12, 0));

        btnTogglePanel = new JButton("▼  Configuración");
        btnTogglePanel.setFont(UI_FONT);
        btnTogglePanel.setForeground(TEXT_DIM);
        btnTogglePanel.setBackground(PANEL_BG);
        btnTogglePanel.setBorderPainted(false);
        btnTogglePanel.setFocusPainted(false);
        btnTogglePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTogglePanel.setBorder(new EmptyBorder(0, 0, 0, 14));
        btnTogglePanel.addActionListener(e -> toggleConfig());

        p.add(title,         BorderLayout.WEST);
        p.add(btnTogglePanel, BorderLayout.EAST);
        return p;
    }

    private void toggleConfig() {
        panelAbierto = !panelAbierto;
        panelConfig.setVisible(panelAbierto);
        btnTogglePanel.setText(panelAbierto ? "▲  Configuración" : "▼  Configuración");
        pack();
        setSize(480, panelAbierto ? 600 : 480);
    }

    // ── Panel de configuración (colapsable) ───────────────────────

    private JPanel buildConfigPanel() {
        panelConfig = new JPanel();
        panelConfig.setLayout(new BoxLayout(panelConfig, BoxLayout.Y_AXIS));
        panelConfig.setBackground(PANEL_BG);
        panelConfig.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(16, 16, 16, 16)));
        panelConfig.setVisible(false);

        // Modo Rey / Esclavo
        panelConfig.add(buildLabel("Modo"));
        panelConfig.add(Box.createVerticalStrut(6));
        panelConfig.add(buildModoSelector());
        panelConfig.add(Box.createVerticalStrut(14));

        // IP
        panelConfig.add(buildLabel("IP del Servidor (solo modo Rey)"));
        panelConfig.add(Box.createVerticalStrut(6));
        panelConfig.add(buildIpRow());
        panelConfig.add(Box.createVerticalStrut(14));

        // Puerto
        panelConfig.add(buildLabel("Puerto"));
        panelConfig.add(Box.createVerticalStrut(6));
        txtPuerto = buildTextField("", "8080");
        panelConfig.add(txtPuerto);
        panelConfig.add(Box.createVerticalStrut(14));

        // Posición del servidor
        panelConfig.add(buildLabel("El servidor está a la..."));
        panelConfig.add(Box.createVerticalStrut(6));
        panelConfig.add(buildPosicionSelector());
        panelConfig.add(Box.createVerticalStrut(14));

        // IP local
        lblIpLocal = new JLabel("IP local: detectando...");
        lblIpLocal.setFont(MONO);
        lblIpLocal.setForeground(TEXT_DIM);
        panelConfig.add(lblIpLocal);

        return panelConfig;
    }

    private JPanel buildModoSelector() {
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnRey    = buildToggle("Cliente",    true);
        btnEsclavo = buildToggle("Servidor", false);

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
        btnBuscarIp.setForeground(TEXT);
        btnBuscarIp.setBackground(new Color(40, 44, 58));
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
        btnBuscarIp.setEnabled(false);
        btnBuscarIp.setText("Buscando...");
        setEstado(EstadoConexion.ESPERANDO, "Buscando servidor en la red...");
        log("Buscando servidor en la red local...");

        Thread hilo = new Thread(() -> {
            Discovery.ServidorEncontrado encontrado = Discovery.buscarServidor(2000);
            SwingUtilities.invokeLater(() -> {
                btnBuscarIp.setEnabled(true);
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

        btnIzquierda = buildToggle("◄  Izquierda", false);
        btnDerecha   = buildToggle("Derecha  ►",   true);

        ButtonGroup g = new ButtonGroup();
        g.add(btnIzquierda); g.add(btnDerecha);
        btnDerecha.setSelected(true);

        btnIzquierda.addActionListener(e -> servidorDerecha = false);
        btnDerecha.addActionListener(e   -> servidorDerecha = true);

        p.add(btnIzquierda); p.add(btnDerecha);
        return p;
    }

    private void actualizarModo() {
        txtIp.setEnabled(modoRey);
        txtIp.setBackground(modoRey ? new Color(40, 44, 58) : new Color(30, 33, 44));
        btnAccion.setText(modoRey ? "CONECTAR" : "INICIAR SERVIDOR");
    }

    // ── Panel principal (siempre visible) ─────────────────────────

    private JPanel buildMainPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(BG);
        center.setBorder(new EmptyBorder(16, 16, 8, 16));

        // Botón acción
        btnAccion = buildAccionButton("CONECTAR");
        btnAccion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        center.add(buildConfigPanel());
        center.add(Box.createVerticalStrut(12));
        center.add(btnAccion);
        center.add(Box.createVerticalStrut(12));
        center.add(buildStatusBar());

        p.add(center,     BorderLayout.NORTH);
        p.add(buildLog(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        lblEstado = new JLabel("●  Inactivo");
        lblEstado.setFont(UI_FONT);
        lblEstado.setForeground(TEXT_DIM);
        p.add(lblEstado);
        return p;
    }

    private JScrollPane buildLog() {
        logArea = new JTextArea();
        logArea.setFont(MONO);
        logArea.setBackground(new Color(16, 18, 24));
        logArea.setForeground(new Color(130, 220, 160));
        logArea.setEditable(false);
        logArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        logArea.setLineWrap(true);

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));
        return sp;
    }

    // ── Lógica de acción ──────────────────────────────────────────

    private void handleAccion() {
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
            if (servidor != null && servidor.isCorriendo()) {
                servidor.detener();
                servidor = null;
                setEstado(EstadoConexion.INACTIVO, "Servidor detenido");
                btnAccion.setText("▶  INICIAR SERVIDOR");
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

        int ancho = Toolkit.getDefaultToolkit().getScreenSize().width;

        cliente = new Cliente(ip, puerto, servidorDerecha, ancho, this::log, () ->
                SwingUtilities.invokeLater(() -> {
                    // El socket se cerró (el control "regresó" al cruzar el
                    // borde de vuelta). El modo Rey sigue activo: el hook
                    // sigue registrado y el usuario puede volver a cruzar
                    // el borde para reconectar, así que NO tocamos reyActivo
                    // ni el texto del botón aquí.
                    setEstado(EstadoConexion.ESPERANDO, "Rey activo — lleva el mouse al borde para conectar");
                })
        );

        try {
            GlobalScreen.registerNativeHook();
            cliente.iniciarHandlers();
            reyActivo = true;
            setEstado(EstadoConexion.ESPERANDO, "Rey activo — lleva el mouse al borde para conectar");
            btnAccion.setText("DETENER");
            log("Modo Rey iniciado. Servidor: " + ip + ":" + puerto);
        } catch (Exception e) {
            log("Error: " + e.getMessage());
        }
    }

    private void detenerModoRey() {
        if (cliente != null) {
            cliente.detenerHandlers();
            if (cliente.isConectado()) {
                cliente.desconectar();
            }
        }
        cliente   = null;
        reyActivo = false;
        setEstado(EstadoConexion.INACTIVO, "Desconectado");
        btnAccion.setText("CONECTAR");
        log("Modo Rey detenido.");
    }

    private void iniciarServidor() {
        int puerto;
        try { puerto = Integer.parseInt(txtPuerto.getText().trim()); }
        catch (NumberFormatException e) { log("Puerto invalido"); return; }

        servidor = new Servidor(puerto, !servidorDerecha, this::log);
        try {
            servidor.iniciar();
            setEstado(EstadoConexion.ESPERANDO, "Esclavo activo en :" + puerto + " — esperando cliente");
            btnAccion.setText("DETENER SERVIDOR");
        } catch (Exception e) {
            log("Error iniciando servidor: " + e.getMessage());
        }
    }

    // ── Utilidades ────────────────────────────────────────────────

    private void setEstado(EstadoConexion estado, String msg) {
        String punto = "●  ";
        Color color;
        switch (estado) {
            case ACTIVO:
                color = SUCCESS;
                break;
            case ESPERANDO:
                color = WARNING;
                break;
            default:
                color = DANGER;
                punto = "○  ";
                break;
        }
        lblEstado.setText(punto + msg);
        lblEstado.setForeground(color);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            java.time.LocalTime t = java.time.LocalTime.now();
            logArea.append(String.format("[%02d:%02d:%02d] %s%n",
                    t.getHour(), t.getMinute(), t.getSecond(), msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());
            actualizarSemaforoPorLog(msg);
        });
    }

    /**
     * Cliente.java y Servidor.java avisan eventos de conexión real (control
     * cruzando de una PC a otra) únicamente a través de mensajes de texto
     * por el logger, no por un callback de estado dedicado. Para no tener
     * que modificar esas clases, detectamos aquí las frases clave que ya
     * emiten y las traducimos al color del semáforo. Si en algún momento
     * cambia el texto exacto de esos logs en Cliente.java o Servidor.java,
     * hay que actualizar también las frases acá.
     */
    private void actualizarSemaforoPorLog(String msg) {
        if (!reyActivo && (servidor == null || !servidor.isCorriendo())) return;

        if (msg.startsWith("Conectado a ")) {
            // Cliente: el socket conectó, el control está activo
            setEstado(EstadoConexion.ACTIVO, "Rey controlando");
        } else if (msg.equals("Control regresado por el servidor")) {
            setEstado(EstadoConexion.ESPERANDO, "Rey activo — lleva el mouse al borde para conectar");
        } else if (msg.startsWith("Cliente conectado desde ")) {
            // Servidor: llegó un cliente, el control está activo
            setEstado(EstadoConexion.ACTIVO, "Esclavo activo — control en uso");
        } else if (msg.equals("Cliente libero el control.") || msg.equals("Conexion cerrada.")) {
            if (servidor != null && servidor.isCorriendo()) {
                setEstado(EstadoConexion.ESPERANDO, "Esclavo activo — esperando cliente");
            }
        }
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

    private JLabel buildLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(TEXT_DIM);
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
                    g2.setColor(TEXT_DIM);
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
        f.setForeground(TEXT);
        f.setBackground(new Color(40, 44, 58));
        f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
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
                g2.setColor(isSelected() ? ACCENT : PANEL_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(BOLD);
        b.setForeground(selected ? BG : TEXT_DIM);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> b.setForeground(b.isSelected() ? BG : TEXT_DIM));
        return b;
    }

    private JButton buildAccionButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(BOLD);
        b.setForeground(BG);
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