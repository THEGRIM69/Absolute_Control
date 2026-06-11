import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServidorKVM {

    private static Robot      robot;
    private static PrintWriter salida;
    private static int         anchoServidor;
    private static int         altoServidor;
    private static volatile boolean activo = false;
    private static volatile boolean shiftActivo = false; // trackea Shift para OEM chars con mayúscula

    // Mapa de char → VK para caracteres especiales del layout español
    // En Windows con layout español, Robot necesita los VK_* correctos.
    // Para ñ/Ñ y símbolos que Robot no puede hacer con getExtendedKeyCodeForChar
    // usamos typeViaClipboard como fallback seguro.
    private static final Map<Character, int[]> CHAR_TO_VK = new HashMap<>();

    static {
        // Vocales con tilde (minúsculas) — en Windows layout español: AltGr no aplica,
        // se escriben con tecla muerta (´) + vocal. Robot no puede simular teclas muertas
        // directamente, así que usamos clipboard para estos.
        // Solo mapeamos los que Robot SÍ puede hacer directamente:
        // Ñ/ñ: en layout español Windows el VK real es 0x00D1 (ñ) y 0x00D0 (Ñ)
        // pero Robot.keyPress solo acepta VK_* definidos en KeyEvent — no acepta 0x00D1.
        // La solución confiable para ñ y símbolos complejos es clipboard.
    }

    public static void main(String[] args) {
        try {
            robot = new Robot();
            Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
            anchoServidor = pantalla.width;
            altoServidor  = pantalla.height;

            ServerSocket servidor = new ServerSocket(8080);
            System.out.println("=== SERVIDOR KVM ENCENDIDO (puerto 8080) ===");
            System.out.println("Pantalla: " + anchoServidor + "x" + altoServidor);

            while (true) {
                System.out.println("Esperando cliente...");
                Socket cliente = servidor.accept();
                System.out.println("Cliente conectado desde " + cliente.getInetAddress());

                salida = new PrintWriter(cliente.getOutputStream(), true);
                activo = true;

                Thread monitor = new Thread(ServidorKVM::monitorearBorde);
                monitor.setDaemon(true);
                monitor.start();

                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(cliente.getInputStream()));
                String linea;

                while ((linea = entrada.readLine()) != null) {

                    if (linea.equals("LIBERAR")) {
                        System.out.println("Cliente libero el control.");
                        activo = false;
                        break;
                    }

                    String[] p = linea.split(",", 3); // máx 3 partes para no romper chars como ","

                    // Mouse absoluto: "A,X,Y"
                    if (p[0].equals("A") && p.length == 3) {
                        int x = Integer.parseInt(p[1]);
                        int y = Integer.parseInt(p[2]);
                        robot.mouseMove(x, y);
                    }

                    // Mouse relativo: "D,deltaX,deltaY"
                    else if (p[0].equals("D") && p.length == 3) {
                        int dx = Integer.parseInt(p[1]);
                        int dy = Integer.parseInt(p[2]);
                        Point pos = MouseInfo.getPointerInfo().getLocation();
                        int newX = Math.max(0, Math.min(anchoServidor - 1, pos.x + dx));
                        int newY = Math.max(0, Math.min(altoServidor  - 1, pos.y + dy));
                        robot.mouseMove(newX, newY);
                    }

                    // Clics: "C,PRESIONAR|LIBERAR,BOTON"
                    else if (p[0].equals("C") && p.length == 3) {
                        int boton   = Integer.parseInt(p[2]);
                        int mascara = (boton == 1)
                                ? InputEvent.BUTTON1_DOWN_MASK
                                : InputEvent.BUTTON3_DOWN_MASK;
                        if (p[1].equals("PRESIONAR")) robot.mousePress(mascara);
                        else if (p[1].equals("LIBERAR")) robot.mouseRelease(mascara);
                    }

                    // Teclado raw (teclas de control, flechas, F-keys, etc.): "K,PRESIONAR|LIBERAR,RAWCODE"
                    else if (p[0].equals("K") && p.length == 3) {
                        int rawCode = Integer.parseInt(p[2]);

                        // Trackear Shift para OEM chars
                        if (rawCode == 160 || rawCode == 161) {
                            shiftActivo = p[1].equals("PRESIONAR");
                        }

                        // VK OEM codes — cliente Windows con jNativeHook en modo VK directo
                        // Llegan como K, pero son chars — manejar via clipboard solo en PRESIONAR
                        char oemChar = oemVkToChar(rawCode, shiftActivo);
                        if (oemChar != 0) {
                            if (p[1].equals("PRESIONAR")) typeCharacter(oemChar);
                            // ignorar LIBERAR de OEM keys
                        } else {
                            int keyCode = convertirKeyCode(rawCode);
                            if (keyCode != -1) {
                                try {
                                    if (p[1].equals("PRESIONAR")) robot.keyPress(keyCode);
                                    else if (p[1].equals("LIBERAR")) robot.keyRelease(keyCode);
                                } catch (IllegalArgumentException ex) {
                                    System.out.println("Keycode no ejecutable: " + rawCode);
                                }
                            } else {
                                System.out.println("[SKIP] raw=" + rawCode + " no mapeado");
                            }
                        }
                    }

                    // Carácter tipado (ñ, á, é, letras normales, etc.): "T,<char>"
                    else if (p[0].equals("T") && p.length == 2) {
                        char c = p[1].charAt(0);
                        typeCharacter(c);
                    }

                    // Scroll: "W,delta"  (negativo=arriba, positivo=abajo)
                    else if (p[0].equals("W") && p.length == 2) {
                        int delta = Integer.parseInt(p[1]);
                        robot.mouseWheel(delta);
                    }
                }

                activo = false;
                salida = null;
                entrada.close();
                cliente.close();
                System.out.println("Conexion cerrada.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tipea un carácter en el servidor.
     *
     * Robot.keyPress() solo funciona para VK codes que Windows acepta directamente.
     * Para ñ, á, é, símbolos del layout español, etc. — Robot falla con "Invalid key code".
     * La solución confiable: pegar via clipboard (Ctrl+V) el char exacto.
     *
     * Flujo:
     *  1. Char ASCII simple (a-z, A-Z, 0-9, espacio) → Robot directo (rápido)
     *  2. Todo lo demás → clipboard + Ctrl+V
     */
    private static void typeCharacter(char c) {
        // 1. ASCII imprimible básico: a-z, A-Z, 0-9, espacio
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ') {
            int vk = KeyEvent.getExtendedKeyCodeForChar(c);
            if (vk != KeyEvent.VK_UNDEFINED) {
                try {
                    robot.keyPress(vk);
                    robot.keyRelease(vk);
                    System.out.println("[T] '" + c + "' → VK=" + vk);
                    return;
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (c >= 'A' && c <= 'Z') {
            int vk = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
            if (vk != KeyEvent.VK_UNDEFINED) {
                try {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(vk);
                    robot.keyRelease(vk);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                    System.out.println("[T] '" + c + "' → SHIFT+VK=" + vk);
                    return;
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 2. Cualquier otro char (ñ, á, é, ¿, ¡, símbolos, etc.) → clipboard
        typeViaClipboard(c);
    }

    /**
     * Pega un carácter usando el portapapeles del sistema.
     * Guarda y restaura el contenido anterior del clipboard.
     */
    private static void typeViaClipboard(char c) {
        java.awt.datatransfer.Clipboard cb =
                Toolkit.getDefaultToolkit().getSystemClipboard();

        // Guardar contenido anterior
        java.awt.datatransfer.Transferable anterior = null;
        try { anterior = cb.getContents(null); } catch (Exception ignored) {}

        // Poner el char en el clipboard
        java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(String.valueOf(c));
        cb.setContents(sel, sel);

        // Ctrl+V
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        // Pequeña pausa para que la app destino procese el paste
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}

        // Restaurar clipboard anterior
        if (anterior != null) {
            try { cb.setContents(anterior, null); } catch (Exception ignored) {}
        }

        System.out.println("[T-CB] '" + c + "' via clipboard");
    }


    /**
     * Convierte VK OEM codes de Windows al char correspondiente en layout español.
     * Estos códigos llegan cuando jNativeHook reporta VK directos en vez de scancodes.
     * Retorna 0 si el rawCode no es un VK OEM conocido.
     */
    private static char oemVkToChar(int vk, boolean shift) {
        return switch (vk) {
            case 186 -> shift ? 'Ñ' : 'ñ';   // VK_OEM_1
            case 187 -> shift ? '*' : '+';    // VK_OEM_PLUS
            case 188 -> shift ? ';' : ',';    // VK_OEM_COMMA
            case 189 -> shift ? '_' : '-';    // VK_OEM_MINUS
            case 190 -> shift ? ':' : '.';    // VK_OEM_PERIOD
            case 191 -> shift ? '?' : '¿';   // VK_OEM_2
            case 192 -> shift ? '"' : '\'' ;  // VK_OEM_3
            case 219 -> shift ? '^' : '`';   // VK_OEM_4
            case 220 -> shift ? 'Ç' : 'ç';   // VK_OEM_5
            case 221 -> shift ? '*' : '¨';   // VK_OEM_6 (puede variar)
            case 222 -> shift ? '¨' : '´';   // VK_OEM_7
            default  -> 0;
        };
    }

    private static void monitorearBorde() {
        while (activo && salida != null) {
            Point pos = MouseInfo.getPointerInfo().getLocation();
            if (pos.x <= 2) {
                System.out.println("Borde izquierdo - regresando control");
                salida.println("REGRESAR");
                activo = false;
                robot.mouseMove(anchoServidor / 2, altoServidor / 2);
                break;
            }
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    private static int convertirKeyCode(int code) {
        return switch (code) {
            case 30 -> KeyEvent.VK_A;
            case 48 -> KeyEvent.VK_B;
            case 46 -> KeyEvent.VK_C;
            case 32 -> KeyEvent.VK_D;
            case 18 -> KeyEvent.VK_E;
            case 33 -> KeyEvent.VK_F;
            case 34 -> KeyEvent.VK_G;
            case 35 -> KeyEvent.VK_H;
            case 23 -> KeyEvent.VK_I;
            case 36 -> KeyEvent.VK_J;
            case 37 -> KeyEvent.VK_K;
            case 38 -> KeyEvent.VK_L;
            case 50 -> KeyEvent.VK_M;
            case 49 -> KeyEvent.VK_N;
            case 24 -> KeyEvent.VK_O;
            case 25 -> KeyEvent.VK_P;
            case 16 -> KeyEvent.VK_Q;
            case 19 -> KeyEvent.VK_R;
            case 31 -> KeyEvent.VK_S;
            case 20 -> KeyEvent.VK_T;
            case 22 -> KeyEvent.VK_U;
            case 47 -> KeyEvent.VK_V;
            case 17 -> KeyEvent.VK_W;
            case 45 -> KeyEvent.VK_X;
            case 21 -> KeyEvent.VK_Y;
            case 44 -> KeyEvent.VK_Z;
            case 11 -> KeyEvent.VK_0;
            case 2  -> KeyEvent.VK_1;
            case 3  -> KeyEvent.VK_2;
            case 4  -> KeyEvent.VK_3;
            case 5  -> KeyEvent.VK_4;
            case 6  -> KeyEvent.VK_5;
            case 7  -> KeyEvent.VK_6;
            case 8  -> KeyEvent.VK_7;
            case 9  -> KeyEvent.VK_8;
            case 10 -> KeyEvent.VK_9;
            case 14   -> KeyEvent.VK_BACK_SPACE;
            case 15   -> KeyEvent.VK_TAB;
            case 28   -> KeyEvent.VK_ENTER;
            case 1    -> KeyEvent.VK_ESCAPE;
            case 57   -> KeyEvent.VK_SPACE;
            case 58   -> KeyEvent.VK_CAPS_LOCK;
            case 42   -> KeyEvent.VK_SHIFT;
            case 54   -> KeyEvent.VK_SHIFT;
            case 3638 -> KeyEvent.VK_SHIFT;
            case 29   -> KeyEvent.VK_CONTROL;
            case 3613 -> KeyEvent.VK_CONTROL;
            case 56   -> KeyEvent.VK_ALT;
            case 3640 -> KeyEvent.VK_ALT;
            case 3675 -> KeyEvent.VK_WINDOWS;
            case 12  -> -1; // layout español: llega por T,char
            case 13  -> -1; // layout español: llega por T,char
            case 26  -> -1; // layout español: llega por T,char
            case 27  -> -1; // layout español: llega por T,char
            case 39  -> -1; // layout español: llega por T,char
            case 40  -> -1; // layout español: llega por T,char
            case 41  -> -1; // ñ en layout español — llega por T,ñ via clipboard, ignorar K
            case 43  -> -1; // layout español: llega por T,char
            case 51  -> -1; // layout español: llega por T,char
            case 52  -> -1; // layout español: llega por T,char
            case 53  -> -1; // layout español: llega por T,char
            case 59 -> KeyEvent.VK_F1;
            case 60 -> KeyEvent.VK_F2;
            case 61 -> KeyEvent.VK_F3;
            case 62 -> KeyEvent.VK_F4;
            case 63 -> KeyEvent.VK_F5;
            case 64 -> KeyEvent.VK_F6;
            case 65 -> KeyEvent.VK_F7;
            case 66 -> KeyEvent.VK_F8;
            case 67 -> KeyEvent.VK_F9;
            case 68 -> KeyEvent.VK_F10;
            case 87 -> KeyEvent.VK_F11;
            case 88 -> KeyEvent.VK_F12;
            case 57419 -> KeyEvent.VK_LEFT;
            case 57416 -> KeyEvent.VK_UP;
            case 57421 -> KeyEvent.VK_RIGHT;
            case 57424 -> KeyEvent.VK_DOWN;
            case 57415 -> KeyEvent.VK_HOME;
            case 57423 -> KeyEvent.VK_END;
            case 57417 -> KeyEvent.VK_PAGE_UP;
            case 57425 -> KeyEvent.VK_PAGE_DOWN;
            case 57426 -> KeyEvent.VK_INSERT;
            case 57427 -> KeyEvent.VK_DELETE;
            case 3667  -> KeyEvent.VK_ENTER;
            default    -> -1;
        };
    }
}