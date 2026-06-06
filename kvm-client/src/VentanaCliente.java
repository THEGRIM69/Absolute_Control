import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;

public class VentanaCliente extends JFrame implements NativeMouseInputListener {
    private static JTextField txtIp;
    private static JButton btnConectar;
    private static JLabel lblEstado;
    
    private static Socket socket;
    private static PrintWriter salida;
    private static boolean controlandoWindows = false;
    private static int anchoPantalla = 1366;

    public VentanaCliente() {
        setTitle("KVM Coordenadas - Arch Linux");
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        txtIp = new JTextField("192.168.1.102", 12);
        btnConectar = new JButton("Iniciar Control");
        lblEstado = new JLabel("Estado: Desconectado 🔴");

        add(new JLabel("IP Servidor:"));
        add(txtIp);
        add(btnConectar);
        add(lblEstado);

        btnConectar.addActionListener(e -> alternarConexion());
    }

    private void alternarConexion() {
        if (!controlandoWindows && socket == null) {
            try {
                socket = new Socket(txtIp.getText(), 8080);
                salida = new PrintWriter(socket.getOutputStream(), true);
                lblEstado.setText("Estado: ¡Controlando Windows! 🟢");
                btnConectar.setText("Detener");
                controlandoWindows = true;
            } catch (Exception ex) {
                lblEstado.setText("Error de conexión 🔴");
            }
        } else {
            cerrarConexion();
        }
    }

    private void cerrarConexion() {
        controlandoWindows = false;
        try {
            if (salida != null) salida.println("LIBERAR");
            if (socket != null) socket.close();
        } catch (Exception e) {}
        socket = null;
        salida = null;
        lblEstado.setText("Estado: Desconectado 🔴");
        btnConectar.setText("Iniciar Control");
    }

    public static void main(String[] args) {
        try {
            GlobalScreen.registerNativeHook();
            VentanaCliente cliente = new VentanaCliente();
            GlobalScreen.addNativeMouseListener(cliente);
            GlobalScreen.addNativeMouseMotionListener(cliente);
            SwingUtilities.invokeLater(() -> cliente.setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- ENVIAR COORDENADAS ABSOLUTAS ---
    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
        if (!controlandoWindows || salida == null) return;

        // ESCAPE: Si llevas el mouse al borde IZQUIERDO absoluto (píxel 0), recuperas el control en Arch
        if (e.getX() <= 2) {
            cerrarConexion();
            return;
        }

        // Enviamos la posición "A,X,Y" directa a Windows sin modificarla
        salida.println("A," + e.getX() + "," + e.getY());
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        if (controlandoWindows && salida != null) {
            if (e.getButton() == NativeMouseEvent.BUTTON1) salida.println("C,PRESIONAR,1");
            if (e.getButton() == NativeMouseEvent.BUTTON2) salida.println("C,PRESIONAR,3");
        }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        if (controlandoWindows && salida != null) {
            if (e.getButton() == NativeMouseEvent.BUTTON1) salida.println("C,LIBERAR,1");
            if (e.getButton() == NativeMouseEvent.BUTTON2) salida.println("C,LIBERAR,3");
        }
    }

    @Override public void nativeMouseClicked(NativeMouseEvent e) {}
    @Override public void nativeMouseDragged(NativeMouseEvent e) {}
}
