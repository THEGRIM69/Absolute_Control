import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.net.Socket;

public class VentanaCliente extends JFrame { // Corregido JWindows por JFrame
    
    private JTextField campoIP;
    private JButton botonConectar;
    private JLabel etiquetaEstado;
    
    // Variables de red y control
    private Socket socket;
    private PrintWriter salida;
    private boolean enEjecucion = false;
    private Thread hiloKVM;
    
    // Nueva ventana para la barra invisible del borde
    private JWindow barraInvisible; 

    public VentanaCliente() {
        setTitle("KVM Controller");
        setSize(350, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 
        setLayout(new GridLayout(4, 1, 10, 10)); 

        JLabel titulo = new JLabel("🚀 KVM NETWORK SHARE", SwingConstants.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 16));
        add(titulo);

        JPanel panelIP = new JPanel();
        panelIP.add(new JLabel("IP del Receptor:"));
        campoIP = new JTextField("192.168.1.104", 15); // Cambiada a tu IP de Windows .104 por defecto
        panelIP.add(campoIP);
        add(panelIP);

        botonConectar = new JButton("Iniciar Control");
        add(botonConectar);

        etiquetaEstado = new JLabel("Estado: Desconectado 🔴", SwingConstants.CENTER);
        add(etiquetaEstado);

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

        hiloKVM = new Thread(() -> {
            try {
                int puerto = 8080;
                socket = new Socket(ip, puerto);
                salida = new PrintWriter(socket.getOutputStream(), true);
                
                SwingUtilities.invokeLater(() -> {
                    etiquetaEstado.setText("¡Conectado y transmitiendo! 🟢");
                    crearBarraInvisible(); // Activamos el portal en el borde de la pantalla
                });

                Dimension tamañoPantalla = Toolkit.getDefaultToolkit().getScreenSize();
                int anchoMax = (int) tamañoPantalla.getWidth();
                int altoMax = (int) tamañoPantalla.getHeight();
                
                // Robot local para manipular el mouse de la laptop cuando cruce el borde
                Robot robotLocal = new Robot(); 
                boolean controlandoWindows = false;
                int ultimoX = 0, ultimoY = 0;

                while (enEjecucion) {
                    Point puntoMouse = MouseInfo.getPointerInfo().getLocation();
                    int xActual = puntoMouse.x;
                    int yActual = puntoMouse.y;

                    // DETECCIÓN: Si el mouse toca el extremo derecho de Arch Linux
                    if (!controlandoWindows && xActual >= (anchoMax - 2)) {
                        controlandoWindows = true;
                        // Centramos el mouse en el portal invisible para tener espacio de movimiento
                        ultimoX = anchoMax - 5;
                        ultimoY = yActual;
                        robotLocal.mouseMove(ultimoX, ultimoY);
                        continue;
                    }

                    // MODO TRANSMISIÓN: Si el portal está activo
                    if (controlandoWindows) {
                        // Si el usuario mueve el mouse bruscamente a la izquierda, rompe el ciclo y regresa a Arch
                        if (xActual < (anchoMax - 30)) {
                            controlandoWindows = false;
                            salida.println("LIBERAR"); // Comando opcional por si quieres programar una liberación en tu servidor
                            Thread.sleep(100);
                            continue;
                        }

                        // Calcular el desplazamiento relativo (Delta)
                        int deltaX = xActual - ultimoX;
                        int deltaY = yActual - ultimoY;

                        if (deltaX != 0 || deltaY != 0) {
                            // Enviamos posiciones proporcionales precisas mapeando la pantalla (Casteo double corregido)
                            double porcentajeX = (double) xActual / (double) anchoMax;
                            double porcentajeY = (double) yActual / (double) altoMax;
                            
                            salida.println("P," + porcentajeX + "," + porcentajeY);
                        }

                        // Mantenemos el mouse de la laptop "enganchado" en la zona del borde para que no se escape de la pantalla
                        ultimoX = anchoMax - 5;
                        ultimoY = yActual;
                        robotLocal.mouseMove(ultimoX, ultimoY);
                    }

                    Thread.sleep(12); // Optimizado a ~80Hz para suavidad extrema sin saturar la red Wi-Fi
                }

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    etiquetaEstado.setText("Error de conexión 🔴");
                    detenerKVM();
                });
            }
        });

        hiloKVM.start(); 
    }

    // Crea el hilo invisible en el extremo derecho de tu monitor
    private void crearBarraInvisible() {
        Dimension tamaño = Toolkit.getDefaultToolkit().getScreenSize();
        barraInvisible = new JWindow();
        barraInvisible.setSize(2, (int) tamaño.getHeight());
        barraInvisible.setLocation((int) tamaño.getWidth() - 2, 0);
        barraInvisible.setAlwaysOnTop(true);
        
        // Hacerlo completamente transparente
        barraInvisible.setBackground(new Color(0, 0, 0, 1)); 
        barraInvisible.setVisible(true);
    }

    private void detenerKVM() {
        enEjecucion = false;
        botonConectar.setText("Iniciar Control");
        etiquetaEstado.setText("Estado: Desconectado 🔴");
        
        if (barraInvisible != null) {
            barraInvisible.dispose();
        }
        
        try {
            if (salida != null) salida.close();
            if (socket != null) socket.close();
            if (hiloKVM != null) hiloKVM.interrupt();
        } catch (Exception ex) {
            // Ignorar al cerrar
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { e.printStackTrace(); }

        SwingUtilities.invokeLater(() -> {
            new VentanaCliente().setVisible(true);
        });
    }
}