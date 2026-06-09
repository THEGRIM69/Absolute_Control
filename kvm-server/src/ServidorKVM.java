import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorKVM {
    public static void main(String[] args) {
        try {
            Robot robot  = new Robot();
            ServerSocket servidor = new ServerSocket(8080);
            System.out.println("=== SERVIDOR KVM ENCENDIDO (puerto 8080) ===");

            while (true) {
                System.out.println("Esperando cliente...");
                Socket cliente = servidor.accept();
                System.out.println("¡Cliente conectado desde " + cliente.getInetAddress() + "!");

                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(cliente.getInputStream()));
                String linea;

                while ((linea = entrada.readLine()) != null) {

                    if (linea.equals("LIBERAR")) {
                        System.out.println("Cliente liberó el control.");
                        break;
                    }

                    String[] p = linea.split(",");

                    // ── Mouse absoluto: "A,X,Y" ───────────────────────
                    if (p[0].equals("A") && p.length == 3) {
                        int x = Integer.parseInt(p[1]);
                        int y = Integer.parseInt(p[2]);
                        robot.mouseMove(x, y);
                    }

                    // ── Clics: "C,PRESIONAR|LIBERAR,BOTON" ───────────
                    else if (p[0].equals("C") && p.length == 3) {
                        int boton   = Integer.parseInt(p[2]);
                        int mascara = (boton == 1)
                                ? InputEvent.BUTTON1_DOWN_MASK
                                : InputEvent.BUTTON3_DOWN_MASK;

                        if (p[1].equals("PRESIONAR")) robot.mousePress(mascara);
                        else if (p[1].equals("LIBERAR")) robot.mouseRelease(mascara);
                    }

                    // ── Teclado: "K,PRESIONAR|LIBERAR,KEYCODE" ────────
                    else if (p[0].equals("K") && p.length == 3) {
                        int keyCode = convertirKeyCode(Integer.parseInt(p[2]));
                        if (keyCode != -1) {
                            try {
                                if (p[1].equals("PRESIONAR")) robot.keyPress(keyCode);
                                else if (p[1].equals("LIBERAR")) robot.keyRelease(keyCode);
                            } catch (IllegalArgumentException ex) {
                                // Ignorar keycodes que Robot no puede ejecutar
                                System.out.println("Keycode no soportado: " + keyCode);
                            }
                        }
                    }
                }

                entrada.close();
                cliente.close();
                System.out.println("Conexión cerrada.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convierte el keycode de jnativehook (usado en Arch/Linux)
     * al keycode de java.awt.event.KeyEvent (usado por Robot en Windows).
     *
     * La mayoría coincide directamente, pero algunas teclas especiales difieren.
     */
        private static int convertirKeyCode(int code) {
        return switch (code) {
            // Letras
            case 0x0041 -> KeyEvent.VK_A;
            case 0x0042 -> KeyEvent.VK_B;
            case 0x0043 -> KeyEvent.VK_C;
            case 0x0044 -> KeyEvent.VK_D;
            case 0x0045 -> KeyEvent.VK_E;
            case 0x0046 -> KeyEvent.VK_F;
            case 0x0047 -> KeyEvent.VK_G;
            case 0x0048 -> KeyEvent.VK_H;
            case 0x0049 -> KeyEvent.VK_I;
            case 0x004A -> KeyEvent.VK_J;
            case 0x004B -> KeyEvent.VK_K;
            case 0x004C -> KeyEvent.VK_L;
            case 0x004D -> KeyEvent.VK_M;
            case 0x004E -> KeyEvent.VK_N;
            case 0x004F -> KeyEvent.VK_O;
            case 0x0050 -> KeyEvent.VK_P;
            case 0x0051 -> KeyEvent.VK_Q;
            case 0x0052 -> KeyEvent.VK_R;
            case 0x0053 -> KeyEvent.VK_S;
            case 0x0054 -> KeyEvent.VK_T;
            case 0x0055 -> KeyEvent.VK_U;
            case 0x0056 -> KeyEvent.VK_V;
            case 0x0057 -> KeyEvent.VK_W;
            case 0x0058 -> KeyEvent.VK_X;
            case 0x0059 -> KeyEvent.VK_Y;
            case 0x005A -> KeyEvent.VK_Z;
            // Números
            case 0x0030 -> KeyEvent.VK_0;
            case 0x0031 -> KeyEvent.VK_1;
            case 0x0032 -> KeyEvent.VK_2;
            case 0x0033 -> KeyEvent.VK_3;
            case 0x0034 -> KeyEvent.VK_4;
            case 0x0035 -> KeyEvent.VK_5;
            case 0x0036 -> KeyEvent.VK_6;
            case 0x0037 -> KeyEvent.VK_7;
            case 0x0038 -> KeyEvent.VK_8;
            case 0x0039 -> KeyEvent.VK_9;
            // Especiales
            case 0x0008 -> KeyEvent.VK_BACK_SPACE;
            case 0x0009 -> KeyEvent.VK_TAB;
            case 0x000A -> KeyEvent.VK_ENTER;
            case 0x001B -> KeyEvent.VK_ESCAPE;
            case 0x0020 -> KeyEvent.VK_SPACE;
            // Modificadores
            case 0xFFE1 -> KeyEvent.VK_SHIFT;
            case 0xFFE2 -> KeyEvent.VK_SHIFT;
            case 0xFFE3 -> KeyEvent.VK_CONTROL;
            case 0xFFE4 -> KeyEvent.VK_CONTROL;
            case 0xFFE9 -> KeyEvent.VK_ALT;
            case 0xFFEA -> KeyEvent.VK_ALT;
            // Flechas
            case 0xFF51 -> KeyEvent.VK_LEFT;
            case 0xFF52 -> KeyEvent.VK_UP;
            case 0xFF53 -> KeyEvent.VK_RIGHT;
            case 0xFF54 -> KeyEvent.VK_DOWN;
            // Navegación
            case 0xFF50 -> KeyEvent.VK_HOME;
            case 0xFF57 -> KeyEvent.VK_END;
            case 0xFF55 -> KeyEvent.VK_PAGE_UP;
            case 0xFF56 -> KeyEvent.VK_PAGE_DOWN;
            case 0xFF63 -> KeyEvent.VK_INSERT;
            case 0xFFFF -> KeyEvent.VK_DELETE;
            // F1-F12
            case 0xFFBE -> KeyEvent.VK_F1;
            case 0xFFBF -> KeyEvent.VK_F2;
            case 0xFFC0 -> KeyEvent.VK_F3;
            case 0xFFC1 -> KeyEvent.VK_F4;
            case 0xFFC2 -> KeyEvent.VK_F5;
            case 0xFFC3 -> KeyEvent.VK_F6;
            case 0xFFC4 -> KeyEvent.VK_F7;
            case 0xFFC5 -> KeyEvent.VK_F8;
            case 0xFFC6 -> KeyEvent.VK_F9;
            case 0xFFC7 -> KeyEvent.VK_F10;
            case 0xFFC8 -> KeyEvent.VK_F11;
            case 0xFFC9 -> KeyEvent.VK_F12;
            default -> -1;
        };
    }
}