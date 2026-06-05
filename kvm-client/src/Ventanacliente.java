import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.net.Socket;

public class VentanaCliente extends JFrame {
    private JTextField campoIP;
    private JButton botonConectar;
    private JLabel etiquetaEstado;
    
    // Variables de red y control
    private Socket socket;
    private PrintWriter salida;
    private boolean enEjecucion = false;
    private Thread hiloKVM;

    public VentanaCliente() {
        // Configuración básica de la ventana (apariencia de app nativa)
        setTitle("KVM Controller");
        setSize(350, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centrar en la pantalla
        setLayout(new GridLayout(4, 1, 10, 10)); // Organización visual

        // 1. Etiqueta de título
        JLabel titulo = new JLabel("🚀 KVM NETWORK SHARE", SwingConstants.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 16));
        add(titulo);

        // 2. Campo para la IP
        JPanel panelIP = new JPanel();
        panelIP.add(new JLabel("IP del Receptor:"));
        campoIP = new JTextField("192.168.1.114", 15);
        panelIP.add(campoIP);
        add(panelIP);

        // 3. Botón de acción
        botonConectar = new JButton("Iniciar Control");
        add(botonConectar);

        // 4. Barra de estado
        etiquetaEstado = new JLabel("Estado: Desconectado 🔴", SwingConstants.CENTER);
        add(etiquetaEstado);

        // Lógica del Botón
        botonConectar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!enEjecucion) {
                    iniciarKVM(campoIP.getText());
                } else {
                    detenerKVM();
                }
            }
        });
    }

    private void iniciarKVM(String ip) {
        enEjecucion = true;
        etiquetaEstado.setText("Conectando... 🟡");
        botonConectar.setText("Detener");

        // Creamos un Hilo secundario (Thread) para que la red no congele la ventana
        hiloKVM = new Thread(() -> {
            try {
                int puerto = 8080;
                socket = new Socket(ip, puerto);
                salida = new PrintWriter(socket.getOutputStream(), true);
                
                // Cambiamos el estado en la interfaz de forma segura
                SwingUtilities.invokeLater(() -> etiquetaEstado.setText("¡Conectado y transmitiendo! 🟢"));

                Dimension tamañoPantalla = Toolkit.getDefaultToolkit().getScreenSize();
                double anchoMax = tamañoPantalla.getWidth();
                double altoMax = tamañoPantalla.getHeight();
                int ultimoX = 0, ultimoY = 0;

                // Motor del mouse
                while (enEjecucion) {
                    Point puntoMouse = MouseInfo.getPointerInfo().getLocation();
                    int xActual = puntoMouse.x;
                    int yActual = puntoMouse.y;

                    if (xActual != ultimoX || yActual != ultimoY) {
                        double porcentajeX = xActual / anchoMax;
                        double porcentajeY = yActual / altoMax;
                        salida.println("P," + porcentajeX + "," + porcentajeY);

                        ultimoX = xActual;
                        ultimoY = yActual;
                    }
                    Thread.sleep(10);
                }

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    etiquetaEstado.setText("Error de conexión 🔴");
                    detenerKVM();
                });
            }
        });

        hiloKVM.start(); // Arranca el motor en segundo plano
    }

    private void detenerKVM() {
        enEjecucion = false;
        botonConectar.setText("Iniciar Control");
        etiquetaEstado.setText("Estado: Desconectado 🔴");
        try {
            if (salida != null) salida.close();
            if (socket != null) socket.close();
            if (hiloKVM != null) hiloKVM.interrupt();
        } catch (Exception ex) {
            // Ignorar al cerrar
        }
    }

    public static void main(String[] args) {
        // Cambiar el diseño para que use el estilo nativo del Sistema Operativo (Windows o Linux GTK)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { e.printStackTrace(); }

        // Hacer visible la ventana
        SwingUtilities.invokeLater(() -> {
            new VentanaCliente().setVisible(true);
        });
    }
}