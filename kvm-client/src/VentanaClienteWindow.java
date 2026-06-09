import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VentanaClienteWindow extends JFrame
        implements NativeMouseInputListener, NativeKeyListener {

    private static final int PUERTO         = 8080;
    private static final int ANCHO_PANTALLA = 1366;
    private static final int BORDE_DERECHO  = ANCHO_PANTALLA - 2;

    private JTextField txtIp;
    private JButton    btnConectar;
    private JLabel     lblEstado;

    private Socket      socket;
    private PrintWriter salida;
    private volatile boolean controlando = false;

    public VentanaClienteWindow() {
        setTitle("KVM Cliente - Windows");
        setSize(420, 120);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        setResizable(false);

        txtIp       = new JTextField("192.168.1.114", 13);
        btnConectar = new JButton("Iniciar Control");
        lblEstado   = new JLabel("Estado: Desconectado 🔴");

        add(new JLabel("IP Servidor:"));
        add(txtIp);
        add(btnConectar);
        add(lblEstado);

        btnConectar.addActionListener(e -> alternarConexion());
    }

    // ── Conexión ─────────────────────────────────────────────────

    private void alternarConexion() {
        if (!controlando) {
            try {
                socket      = new Socket(txtIp.getText().trim(), PUERTO);
                salida      = new PrintWriter(socket.getOutputStream(), true);
                controlando = true;
                lblEstado.setText("Estado: Controlando 🟢");
                btnConectar.setText("Detener");
            } catch (Exception ex) {
                lblEstado.setText("Error de conexión 🔴");
            }
        } else {
            cerrarConexion();
        }
    }

    private void cerrarConexion() {
        controlando = false;
        SwingUtilities.invokeLater(() -> {
            lblEstado.setText("Estado: Desconectado 🔴");
            btnConectar.setText("Iniciar Control");
        });
        try {
            if (salida != null) salida.println("LIBERAR");
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        salida = null;
    }

    private void enviar(String msg) {
        if (controlando && salida != null) salida.println(msg);
    }

    // ── Mouse ────────────────────────────────────────────────────

    private int ultimoX = 0, ultimoY = 0;

    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {

        if (!controlando && e.getX() >= BORDE_DERECHO) {
            SwingUtilities.invokeLater(this::alternarConexion);
            return;
        }
        if (!controlando) return;
        
        if (e.getX() <= 2) {
            cerrarConexion();
            return;
        }
        // Solo enviar si se movió más de 2 píxeles
        if (Math.abs(e.getX() - ultimoX) > 2 || Math.abs(e.getY() - ultimoY) > 2) {
            ultimoX = e.getX();
            ultimoY = e.getY();
            enviar("A," + e.getX() + "," + e.getY());
        }
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
        if (!controlando) return;
        enviar("A," + e.getX() + "," + e.getY());
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        int boton = nativeBtnToServidor(e.getButton());
        if (boton != -1) enviar("C,PRESIONAR," + boton);
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        int boton = nativeBtnToServidor(e.getButton());
        if (boton != -1) enviar("C,LIBERAR," + boton);
    }

    @Override public void nativeMouseClicked(NativeMouseEvent e) {}

    private int nativeBtnToServidor(int btn) {
        if (btn == NativeMouseEvent.BUTTON1) return 1;
        if (btn == NativeMouseEvent.BUTTON2) return 3;
        return -1;
    }

    // ── Teclado ──────────────────────────────────────────────────

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // ESC → liberar control
        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE && controlando) {
            cerrarConexion();
            return;
        }
        if (!controlando) return;
        enviar("K,PRESIONAR," + e.getKeyCode());
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (!controlando) return;
        enviar("K,LIBERAR," + e.getKeyCode());
    }

    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    // ── Main ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            // Silenciar logs de jnativehook
            Logger log = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            log.setLevel(Level.WARNING);
            log.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();

            VentanaClienteWindow cliente = new VentanaClienteWindow();
            GlobalScreen.addNativeMouseListener(cliente);
            GlobalScreen.addNativeMouseMotionListener(cliente);
            GlobalScreen.addNativeKeyListener(cliente);

            SwingUtilities.invokeLater(() -> cliente.setVisible(true));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}