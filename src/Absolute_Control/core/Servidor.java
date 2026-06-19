package Absolute_Control.core;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class Servidor {

    private final int             puerto;
    private final boolean         clienteALaDerecha;
    private final Consumer<String> logger;

    private ServerSocket         serverSocket;
    private volatile boolean     corriendo = false;
    private volatile boolean     activo    = false;
    private static PrintWriter   salida;
    private static Robot         robot;
    private static int           anchoServidor;
    private static int           altoServidor;

    // Responde a broadcasts UDP de clientes buscando este servidor en la
    // red local, para que el usuario no tenga que escribir la IP a mano.
    private final Discovery discovery = new Discovery();

    public Servidor(int puerto, boolean clienteALaDerecha, Consumer<String> logger) {
        this.puerto            = puerto;
        this.clienteALaDerecha = clienteALaDerecha;
        this.logger            = logger;
    }

    public void iniciar() throws Exception {
        robot = new Robot();
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        anchoServidor = pantalla.width;
        altoServidor  = pantalla.height;

        serverSocket = new ServerSocket(puerto);
        corriendo    = true;

        Thread hilo = new Thread(this::loop);
        hilo.setDaemon(true);
        hilo.start();

        discovery.iniciarResponder(puerto, logger);

        logger.accept("Servidor iniciado en puerto " + puerto);
        logger.accept("Pantalla: " + anchoServidor + "x" + altoServidor);
    }

    private void loop() {
        while (corriendo) {
            try {
                logger.accept("Esperando cliente...");
                Socket cliente = serverSocket.accept();
                logger.accept("Cliente conectado desde " + cliente.getInetAddress());

                salida = new PrintWriter(cliente.getOutputStream(), true);
                activo = true;

                Thread monitor = new Thread(this::monitorearBorde);
                monitor.setDaemon(true);
                monitor.start();

                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(cliente.getInputStream()));
                String linea;

                while ((linea = entrada.readLine()) != null) {
                    if (linea.equals("LIBERAR")) {
                        logger.accept("Cliente libero el control.");
                        activo = false;
                        break;
                    }
                    procesarMensaje(linea);
                }

                activo = false;
                salida = null;
                entrada.close();
                cliente.close();
                logger.accept("Conexion cerrada.");

            } catch (Exception e) {
                if (corriendo) logger.accept("Error: " + e.getMessage());
            }
        }
    }

    private void procesarMensaje(String linea) {
        String[] p = linea.split(",", 3);

        if (p[0].equals("D") && p.length == 3) {
            int dx = Integer.parseInt(p[1]);
            int dy = Integer.parseInt(p[2]);
            Point pos = MouseInfo.getPointerInfo().getLocation();
            int nx = Math.max(0, Math.min(anchoServidor - 1, pos.x + dx));
            int ny = Math.max(0, Math.min(altoServidor  - 1, pos.y + dy));
            robot.mouseMove(nx, ny);
        }
        else if (p[0].equals("A") && p.length == 3) {
            robot.mouseMove(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        else if (p[0].equals("C") && p.length == 3) {
            int b = Integer.parseInt(p[2]);
            int m = b == 1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
            if (p[1].equals("PRESIONAR")) robot.mousePress(m);
            else robot.mouseRelease(m);
        }
        else if (p[0].equals("W") && p.length == 2) {
            robot.mouseWheel(Integer.parseInt(p[1]));
        }
        else if (p[0].equals("T") && p.length == 2) {
            typeCharacter(p[1].charAt(0));
        }
        else if (p[0].equals("K") && p.length == 3) {
            int kc = convertirKeyCode(Integer.parseInt(p[2]));
            if (kc != -1) {
                try {
                    if (p[1].equals("PRESIONAR")) robot.keyPress(kc);
                    else robot.keyRelease(kc);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void monitorearBorde() {
        // El borde de regreso es el opuesto al lado donde está el cliente
        int bordeRegreso = clienteALaDerecha ? 0 : anchoServidor - 1;
        while (activo && salida != null) {
            Point pos = MouseInfo.getPointerInfo().getLocation();
            boolean enBorde = clienteALaDerecha
                    ? pos.x <= 2
                    : pos.x >= anchoServidor - 3;
            if (enBorde) {
                logger.accept("Borde detectado - regresando control");
                salida.println("REGRESAR");
                activo = false;
                robot.mouseMove(anchoServidor / 2, altoServidor / 2);
                break;
            }
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    private static void typeCharacter(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ') {
            int vk = KeyEvent.getExtendedKeyCodeForChar(c);
            if (vk != KeyEvent.VK_UNDEFINED) {
                try { robot.keyPress(vk); robot.keyRelease(vk); return; }
                catch (IllegalArgumentException ignored) {}
            }
        }
        if (c >= 'A' && c <= 'Z') {
            int vk = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
            if (vk != KeyEvent.VK_UNDEFINED) {
                try {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(vk); robot.keyRelease(vk);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                    return;
                } catch (IllegalArgumentException ignored) {}
            }
        }
        typeViaClipboard(c);
    }

    private static void typeViaClipboard(char c) {
        java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        java.awt.datatransfer.Transferable anterior = null;
        try { anterior = cb.getContents(null); } catch (Exception ignored) {}
        java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(String.valueOf(c));
        cb.setContents(sel, sel);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
        if (anterior != null) {
            try { cb.setContents(anterior, null); } catch (Exception ignored) {}
        }
    }

    public void detener() {
        corriendo = false;
        discovery.detenerResponder();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        logger.accept("Servidor detenido.");
    }

    public boolean isCorriendo() { return corriendo; }

    private static int convertirKeyCode(int code) {
        return switch (code) {
            case 30 -> KeyEvent.VK_A; case 48 -> KeyEvent.VK_B;
            case 46 -> KeyEvent.VK_C; case 32 -> KeyEvent.VK_D;
            case 18 -> KeyEvent.VK_E; case 33 -> KeyEvent.VK_F;
            case 34 -> KeyEvent.VK_G; case 35 -> KeyEvent.VK_H;
            case 23 -> KeyEvent.VK_I; case 36 -> KeyEvent.VK_J;
            case 37 -> KeyEvent.VK_K; case 38 -> KeyEvent.VK_L;
            case 50 -> KeyEvent.VK_M; case 49 -> KeyEvent.VK_N;
            case 24 -> KeyEvent.VK_O; case 25 -> KeyEvent.VK_P;
            case 16 -> KeyEvent.VK_Q; case 19 -> KeyEvent.VK_R;
            case 31 -> KeyEvent.VK_S; case 20 -> KeyEvent.VK_T;
            case 22 -> KeyEvent.VK_U; case 47 -> KeyEvent.VK_V;
            case 17 -> KeyEvent.VK_W; case 45 -> KeyEvent.VK_X;
            case 21 -> KeyEvent.VK_Y; case 44 -> KeyEvent.VK_Z;
            case 11 -> KeyEvent.VK_0; case 2  -> KeyEvent.VK_1;
            case 3  -> KeyEvent.VK_2; case 4  -> KeyEvent.VK_3;
            case 5  -> KeyEvent.VK_4; case 6  -> KeyEvent.VK_5;
            case 7  -> KeyEvent.VK_6; case 8  -> KeyEvent.VK_7;
            case 9  -> KeyEvent.VK_8; case 10 -> KeyEvent.VK_9;
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
            case 59 -> KeyEvent.VK_F1;  case 60 -> KeyEvent.VK_F2;
            case 61 -> KeyEvent.VK_F3;  case 62 -> KeyEvent.VK_F4;
            case 63 -> KeyEvent.VK_F5;  case 64 -> KeyEvent.VK_F6;
            case 65 -> KeyEvent.VK_F7;  case 66 -> KeyEvent.VK_F8;
            case 67 -> KeyEvent.VK_F9;  case 68 -> KeyEvent.VK_F10;
            case 87 -> KeyEvent.VK_F11; case 88 -> KeyEvent.VK_F12;
            case 57419 -> KeyEvent.VK_LEFT;  case 57416 -> KeyEvent.VK_UP;
            case 57421 -> KeyEvent.VK_RIGHT; case 57424 -> KeyEvent.VK_DOWN;
            case 57415 -> KeyEvent.VK_HOME;  case 57423 -> KeyEvent.VK_END;
            case 57417 -> KeyEvent.VK_PAGE_UP; case 57425 -> KeyEvent.VK_PAGE_DOWN;
            case 57426 -> KeyEvent.VK_INSERT; case 57427 -> KeyEvent.VK_DELETE;
            case 3667  -> KeyEvent.VK_ENTER;
            default    -> -1;
        };
    }
}