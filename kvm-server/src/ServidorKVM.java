import java.awt.*;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorKVM {
    public static void main(String[] args) {
        try {
            Robot robot = new Robot();
            ServerSocket servidor = new ServerSocket(8080);
            System.out.println("=== SERVIDOR KVM ABSOLUTO (1366x768) ENCIENDIDO ===");

            while (true) {
                System.out.println("Esperando conexión...");
                Socket cliente = servidor.accept();
                System.out.println("¡Conectado!");

                BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                String linea;

                while ((linea = entrada.readLine()) != null) {
                    if (linea.equals("LIBERAR")) break;

                    // Mover de forma absoluta "A,X,Y"
                    if (linea.startsWith("A")) {
                        String[] partes = linea.split(",");
                        int x = Integer.parseInt(partes[1]);
                        int y = Integer.parseInt(partes[2]);

                        // Coloca el mouse exactamente en la misma coordenada que tu laptop
                        robot.mouseMove(x, y);
                    }

                    // Clics "C,ACCION,BOTON"
                    if (linea.startsWith("C")) {
                        String[] partes = linea.split(",");
                        String accion = partes[1];
                        int boton = Integer.parseInt(partes[2]);
                        int mascara = (boton == 1) ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;

                        if (accion.equals("PRESIONAR")) robot.mousePress(mascara);
                        else if (accion.equals("LIBERAR")) robot.mouseRelease(mascara);
                    }
                }
                entrada.close();
                cliente.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}