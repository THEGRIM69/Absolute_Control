import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VentanaClienteWindow extends JFrame
        implements NativeMouseInputListener, NativeMouseWheelListener, NativeKeyListener {

    private static final int PUERTO         = 8080;
    private static final int ANCHO_PANTALLA = 1366;
    private static final int ALTO_PANTALLA  = 768;
    private static final int BORDE_DERECHO  = ANCHO_PANTALLA - 2;
    private static final int CENTRO_X       = ANCHO_PANTALLA / 2;
    private static final int CENTRO_Y       = ALTO_PANTALLA  / 2;

    private JTextField txtIp;
    private JButton    btnConectar;
    private JLabel     lblEstado;

    private Socket         socket;
    private PrintWriter    salida;
    private BufferedReader entrada;
    private volatile boolean controlando = false;
    private volatile boolean conectando  = false;
    private volatile boolean anclando    = false;

    private Robot robotLocal;

    // Trackea teclas modificadoras activas
    private volatile boolean shiftActivo  = false;
    private volatile boolean altGrActivo  = false;

    private final Set<Integer> teclasPresionadas = new HashSet<>();

    public VentanaClienteWindow() {
        setTitle("KVM Cliente - Windows");
        setSize(420, 120);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        setResizable(false);

        txtIp       = new JTextField("192.168.1.114", 13);
        btnConectar = new JButton("Iniciar Control");
        lblEstado   = new JLabel("Estado: Desconectado [OFF]");

        add(new JLabel("IP Servidor:"));
        add(txtIp);
        add(btnConectar);
        add(lblEstado);

        btnConectar.addActionListener(e -> alternarConexion());

        try { robotLocal = new Robot(); } catch (AWTException ignored) {}
    }

    private void alternarConexion() {
        if (!controlando) {
            try {
                socket      = new Socket(txtIp.getText().trim(), PUERTO);
                salida      = new PrintWriter(socket.getOutputStream(), true);
                entrada     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                controlando = true;
                lblEstado.setText("Estado: Controlando [ON]");
                btnConectar.setText("Detener");

                if (robotLocal != null) robotLocal.mouseMove(CENTRO_X, CENTRO_Y);

                Thread receptor = new Thread(this::escucharServidor);
                receptor.setDaemon(true);
                receptor.start();

            } catch (Exception ex) {
                lblEstado.setText("Error de conexion");
                conectando = false;
            }
        } else {
            cerrarConexion();
        }
    }

    private void escucharServidor() {
        try {
            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (linea.equals("REGRESAR")) {
                    System.out.println("Servidor pide regresar control");
                    cerrarConexion();
                    if (robotLocal != null)
                        robotLocal.mouseMove(BORDE_DERECHO - 50, CENTRO_Y);
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private void cerrarConexion() {
        controlando = false;
        SwingUtilities.invokeLater(() -> {
            lblEstado.setText("Estado: Desconectado [OFF]");
            btnConectar.setText("Iniciar Control");
        });
        try {
            if (salida != null) salida.println("LIBERAR");
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket  = null;
        salida  = null;
        entrada = null;
    }

    private void enviar(String msg) {
        if (controlando && salida != null) salida.println(msg);
    }

    // ── Mouse ────────────────────────────────────────────────────

    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
        if (anclando) return;

        if (controlando) {
            int deltaX = e.getX() - CENTRO_X;
            int deltaY = e.getY() - CENTRO_Y;
            anclando = true;
            if (robotLocal != null) robotLocal.mouseMove(CENTRO_X, CENTRO_Y);
            anclando = false;
            if (deltaX != 0 || deltaY != 0)
                enviar("D," + deltaX + "," + deltaY);
            return;
        }

        if (!conectando && e.getX() >= BORDE_DERECHO) {
            conectando = true;
            SwingUtilities.invokeLater(() -> {
                alternarConexion();
                conectando = false;
            });
        }
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
        if (!controlando) return;
        int deltaX = e.getX() - CENTRO_X;
        int deltaY = e.getY() - CENTRO_Y;
        anclando = true;
        if (robotLocal != null) robotLocal.mouseMove(CENTRO_X, CENTRO_Y);
        anclando = false;
        if (deltaX != 0 || deltaY != 0)
            enviar("D," + deltaX + "," + deltaY);
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        if (!controlando) return;
        int boton = nativeBtnToServidor(e.getButton());
        if (boton != -1) enviar("C,PRESIONAR," + boton);
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        if (!controlando) return;
        int boton = nativeBtnToServidor(e.getButton());
        if (boton != -1) enviar("C,LIBERAR," + boton);
    }

    @Override public void nativeMouseClicked(NativeMouseEvent e) {}

    @Override
    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
        if (!controlando) return;
        int delta = e.getWheelRotation();
        if (delta != 0) enviar("W," + delta);
    }

    private int nativeBtnToServidor(int btn) {
        if (btn == NativeMouseEvent.BUTTON1) return 1;
        if (btn == NativeMouseEvent.BUTTON2) return 3;
        return -1;
    }

    // ── Teclado ──────────────────────────────────────────────────

    /**
     * Mapeo de raw OEM codes → char según teclado español Windows.
     * Tiene en cuenta si Shift o AltGr están activos.
     */
    private char oemRawToChar(int raw) {
        if (altGrActivo) {
            return switch (raw) {
                case 219 -> '[';
                case 221 -> ']';
                case 222 -> '{';
                case 220 -> '}';
                case 187 -> '~';
                case 49  -> '|';
                case 50  -> '@';
                case 51  -> '#';
                case 52  -> '~';
                case 53  -> '€';
                default  -> 0;
            };
        }
        if (shiftActivo) {
            return switch (raw) {
                case 192 -> 'Ñ';
                case 186 -> 'Ñ';
                case 187 -> '*';
                case 188 -> ';';
                case 189 -> '_';
                case 190 -> ':';
                case 191 -> '¡';
                case 219 -> '¿';
                case 220 -> 'Ç';
                case 221 -> 'ª';
                case 222 -> '¨';
                case 161 -> '?';
                default  -> 0;
            };
        }
        // Sin modificador
        return switch (raw) {
            case 192 -> 'ñ';
            case 186 -> 'ñ';
            case 187 -> '+';
            case 188 -> ',';
            case 189 -> '-';
            case 190 -> '.';
            case 191 -> ']';
            case 219 -> '?';
            case 220 -> 'ç';
            case 221 -> '¿';
            case 222 -> '[';
            case 161 -> ';';
            default  -> 0;
        };
    }

    private boolean esModificador(int raw) {
        return raw == 160 || raw == 161  // Shift L/R
            || raw == 162 || raw == 163  // Ctrl L/R
            || raw == 164 || raw == 165  // Alt L / AltGr
            || raw == 91  || raw == 92;  // Win L/R
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int raw = e.getRawCode();

        // Trackear modificadores
        if (raw == 160 || raw == 161) shiftActivo = true;
        if (raw == 165)               altGrActivo = true;

        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE && controlando) {
            cerrarConexion();
            return;
        }
        if (!controlando) return;

        // Modificadores — enviar como K
        if (esModificador(raw)) {
            enviar("K,PRESIONAR," + e.getKeyCode());
            return;
        }

        char c = e.getKeyChar();
        boolean tieneChar = (c != NativeKeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c));

        if (tieneChar) {
            // jnativehook resolvió el char — marcar para nativeKeyTyped
            teclasPresionadas.add(raw);
        } else {
            // Intentar mapeo OEM manual
            char oemChar = oemRawToChar(raw);
            if (oemChar != 0) {
                enviar("T," + oemChar);
            } else {
                // Tecla de control (flecha, F-key, etc.)
                enviar("K,PRESIONAR," + e.getKeyCode());
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int raw = e.getRawCode();

        // Actualizar modificadores
        if (raw == 160 || raw == 161) shiftActivo = false;
        if (raw == 165)               altGrActivo = false;

        if (!controlando) return;

        if (esModificador(raw)) {
            enviar("K,LIBERAR," + e.getKeyCode());
            return;
        }

        char c = e.getKeyChar();
        boolean tieneChar = (c != NativeKeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c));

        if (!tieneChar) {
            char oemChar = oemRawToChar(raw);
            if (oemChar == 0) {
                enviar("K,LIBERAR," + e.getKeyCode());
            }
            // OEM chars no tienen LIBERAR — el servidor los tipea directo
        }
        teclasPresionadas.remove(raw);
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        if (!controlando) return;
        char c = e.getKeyChar();
        if (c == NativeKeyEvent.CHAR_UNDEFINED || Character.isISOControl(c)) return;
        if (teclasPresionadas.remove(e.getRawCode())) {
            enviar("T," + c);
        }
    }

    // ── Main ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            Logger log = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            log.setLevel(Level.WARNING);
            log.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();

            VentanaClienteWindow cliente = new VentanaClienteWindow();
            GlobalScreen.addNativeMouseListener(cliente);
            GlobalScreen.addNativeMouseMotionListener(cliente);
            GlobalScreen.addNativeMouseWheelListener(cliente);
            GlobalScreen.addNativeKeyListener(cliente);

            SwingUtilities.invokeLater(() -> cliente.setVisible(true));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


