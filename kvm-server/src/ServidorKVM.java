import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorKVM {
    public static void main(String[] args) {
        int puerto = 8080;

        try {
            // 1. REDES: Iniciamos el servidor en el puerto 8080
            ServerSocket servidor = new ServerSocket(puerto);
            System.out.println("=== RECEPTOR KVM PROPORCIONAL INICIADO ===");
            System.out.println("Escuchando en el puerto " + puerto + "...");
            System.out.println("Esperando conexión de la PC Maestra...");

            // El programa se detiene aquí hasta que el cliente se conecte
            Socket cliente = servidor.accept(); 
            System.out.println("¡PC Maestra conectada desde: " + cliente.getInetAddress() + "!");

            // 2. SISTEMAS OPERATIVOS: Detectamos la resolución de ESTA pantalla automáticamente
            Dimension tamañoPantalla = Toolkit.getDefaultToolkit().getScreenSize();
            int anchoLocal = (int) tamañoPantalla.getWidth();
            int altoLocal = (int) tamañoPantalla.getHeight();
            System.out.println("Resolución de pantalla local detectada: " + anchoLocal + "x" + altoLocal);

            // Creamos el Robot que moverá el mouse real a nivel de Sistema Operativo
            Robot robot = new Robot();
            
            // Preparamos el lector de la red
            BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            String comando;

            // 3. PROGRAMACIÓN 2: Bucle que procesa los datos optimizado para pantalla secundaria
            while ((comando = entrada.readLine()) != null) {
                
                // Si el cliente pide liberar el control, reiniciamos el estado en el servidor
                if (comando.equals("LIBERAR")) {
                    System.out.println("Control devuelto a la PC Maestra temporalmente...");
                    continue; 
                }

            // Formato esperado: "P,porcentajeX,porcentajeY"
            if (controlandoWindows) {
                    // ESCAPE: Si tiras el mouse a la izquierda, regresas a Arch
                    if (xActual < (anchoMax / 2 - 150)) {
                        controlandoWindows = false;
                        salida.println("LIBERAR"); // Envía la señal de liberación al servidor
                        Thread.sleep(100);
                        continue;
                    }

                    // Calculamos cuánto moviste el mouse físicamente en este instante
                    int deltaX = xActual - ultimoX;
                    int deltaY = yActual - ultimoY;

                    if (deltaX != 0 || deltaY != 0) {
                        // Convertimos el delta físico a una escala proporcional pura para la red
                        double propX = (double) deltaX / (double) anchoMax;
                        double propY = (double) deltaY / (double) altoMax;

                        // Enviamos el desplazamiento puro al servidor
                        salida.println("P," + propX + "," + propY);
                    }

                    // Regresamos el mouse de la laptop al centro virtual para mantener el recorrido infinito
                    ultimoX = anchoMax / 2;
                    ultimoY = altoMax / 2; // Centrado completo
                    robotLocal.mouseMove(ultimoX, ultimoY);
                }
            }
        }

            System.out.println("La PC Maestra se ha desconectado.");
            entrada.close();
            cliente.close();
            servidor.close();

        } catch (Exception e) {
            System.out.println("Error en el servidor KVM: " + e.getMessage());
            e.printStackTrace();
        }
    }
}