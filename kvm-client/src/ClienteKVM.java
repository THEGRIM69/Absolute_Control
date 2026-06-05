import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteKVM {
    public static void main(String[] args) {
        // ⚠️ MAÑANA ES HOY: Cambia esto por la IP real de tu laptop (la de 'ip a')
        String ipServidor = "192.168.1.114"; 
        int puerto = 8080;

        try {
            System.out.println("=== EMISOR KVM INICIADO ===");
            System.out.println("Conectando al receptor en " + ipServidor + ":" + puerto + "...");
            
            // 1. REDES: Nos conectamos a la laptop a través de la red local
            Socket socket = new Socket(ipServidor, puerto);
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("¡Conectado exitosamente! Rastreando mouse...");

            // 2. SISTEMAS OPERATIVOS: Detectamos el tamaño de la pantalla de la Desktop
            Dimension tamañoPantalla = Toolkit.getDefaultToolkit().getScreenSize();
            double anchoMax = tamañoPantalla.getWidth();
            double altoMax = tamañoPantalla.getHeight();

            int ultimoX = 0, ultimoY = 0;

            // 3. PROGRAMACIÓN 2: Bucle infinito para monitorear el mouse físico
            while (true) {
                Point puntoMouse = MouseInfo.getPointerInfo().getLocation();
                int xActual = puntoMouse.x;
                int yActual = puntoMouse.y;

                if (xActual != ultimoX || yActual != ultimoY) {
                    // Convertimos la posición a porcentajes
                    double porcentajeX = xActual / anchoMax;
                    double porcentajeY = yActual / altoMax;

                    // Enviamos el comando por red
                    salida.println("P," + porcentajeX + "," + porcentajeY);

                    ultimoX = xActual;
                    ultimoY = yActual;
                }
                Thread.sleep(10); // Pausa de estabilidad
            }

        } catch (Exception e) {
            System.out.println("Error en el cliente KVM: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
