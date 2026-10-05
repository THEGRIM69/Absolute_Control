package Absolute_Control.core;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

public class Servidor {

    private final int             puerto;
    private final boolean         secundariaALaDerecha;
    private final Consumer<String> logger;

    public enum Estado { ESPERANDO, REMOTO, DETENIDO, ERROR }
    private static final int SESSION_IDLE_TIMEOUT_MS = 4000;
    private final Object lock = new Object();
    private final Consumer<Estado> onEstado;
    private volatile boolean corriendo, paradaTerminada = true;
    private ServerSocket serverSocket;
    private Thread hiloServidor, hiloParada;
    private Sesion sesion;
    private Robot robot;
    private GeometriaPantalla pantalla;
    private final Discovery discovery = new Discovery();

    private static class Sesion {
        final Socket socket;
        final AtomicBoolean cierreSolicitado = new AtomicBoolean();
        final CountDownLatch finLimpieza = new CountDownLatch(1);
        final Object entradaLock = new Object(), salidaLock = new Object();
        final Set<Integer> teclas = new HashSet<>(), botones = new HashSet<>();
        // Geometria de la transicion, protegida por entradaLock; no altera el ciclo de cierre.
        boolean mouseInicializado, secundariaALaDerecha, transicionConAltura;
        volatile boolean limpiezaTerminada;
        BufferedReader entrada;
        PrintWriter salida;
        Thread monitor, lector;
        volatile Thread limpieza;
        Sesion(Socket socket) { this.socket = socket; }
    }

    public Servidor(int puerto, boolean secundariaALaDerecha, Consumer<String> logger, Consumer<Estado> onEstado) {
        this.puerto = puerto; this.secundariaALaDerecha = secundariaALaDerecha;
        this.logger = logger; this.onEstado = onEstado;
    }

    public void iniciar() throws Exception {
        synchronized (lock) {
            if (corriendo) return;
            if (!paradaTerminada || (hiloServidor != null && hiloServidor.isAlive())
                    || (hiloParada != null && hiloParada.isAlive()))
                throw new IllegalStateException("Parada anterior incompleta; no reutilizar servidor");
            robot = new Robot();
            pantalla = geometriaActual();
            serverSocket = new ServerSocket(puerto);
            paradaTerminada = false; hiloParada = null; corriendo = true;
            discovery.iniciarResponder(puerto, logger);
            onEstado.accept(Estado.ESPERANDO);
            hiloServidor = new Thread(this::loop, "kvm-servidor");
            hiloServidor.setDaemon(true); hiloServidor.start();
            logger.accept("Servidor iniciado en puerto " + puerto);
        }
    }

    private void loop() {
        try {
            while (corriendo) {
                Sesion actual = null;
                try {
                    actual = new Sesion(serverSocket.accept());
                    synchronized (lock) {
                        sesion = actual;
                        if (corriendo) prepararSesion(actual);
                    }
                } catch (Exception e) {
                    if (corriendo) logger.accept("Error de sesion: " + e.getMessage());
                    if (actual != null) cerrarSesion(actual,
                            corriendo ? "SESSION_START_ERROR" : "SERVER_STOP", detalle(e));
                }
                if (actual != null) {
                    if (!corriendo) cerrarSesion(actual, "SERVER_STOP", "servidor detenido");
                    // Nunca aceptar otra sesion antes de finalizar TODA la limpieza anterior.
                    while (corriendo && !actual.finLimpieza.await(100, TimeUnit.MILLISECONDS)) {}
                    if (!corriendo) cerrarSesion(actual, "SERVER_STOP", "servidor detenido");
                    if (corriendo) Cliente.finalizarHilo(actual.limpieza, logger);
                    if (corriendo && !actual.limpiezaTerminada) {
                        logger.accept("Limpieza de sesion incompleta; servidor se detiene");
                        break;
                    }
                    synchronized (lock) {
                        if (sesion == actual && actual.limpiezaTerminada) sesion = null;
                    }
                    if (corriendo) onEstado.accept(Estado.ESPERANDO);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally { detener(); }
    }

    // Bajo lock: no se puede publicar/arrancar un hilo despues de solicitar cierre.
    private void prepararSesion(Sesion s) throws Exception {
        if (s.cierreSolicitado.get()) return;
        s.socket.setTcpNoDelay(true); s.socket.setSoTimeout(SESSION_IDLE_TIMEOUT_MS);
        s.entrada = new BufferedReader(new InputStreamReader(s.socket.getInputStream(), StandardCharsets.UTF_8));
        s.salida = new PrintWriter(new java.io.OutputStreamWriter(s.socket.getOutputStream(), StandardCharsets.UTF_8), true);
        s.monitor = new Thread(() -> monitorearBorde(s), "kvm-borde");
        s.lector = new Thread(() -> atender(s), "kvm-recibir-servidor");
        s.monitor.setDaemon(true); s.lector.setDaemon(true);
        s.monitor.start(); s.lector.start();
        onEstado.accept(Estado.REMOTO);
        logger.accept("Cliente conectado desde " + s.socket.getInetAddress());
    }

    private void atender(Sesion s) {
        String razon = "EOF";
        String detalle = "cliente cerro el flujo";
        try {
            String linea;
            while (!s.cierreSolicitado.get() && (linea = s.entrada.readLine()) != null) {
                if (linea.equals("LIBERAR")) { razon = "LIBERAR"; detalle = null; break; }
                if (linea.equals("PING")) {
                    if (!enviarRespuesta(s, "PONG")) { razon = "WRITE_ERROR"; detalle = "PONG"; break; }
                } else synchronized (s.entradaLock) {
                    if (!s.cierreSolicitado.get()) procesarMensaje(s, linea);
                }
            }
        } catch (Exception e) {
            if (!s.cierreSolicitado.get()) {
                razon = e instanceof SocketTimeoutException ? "IDLE_TIMEOUT"
                        : e instanceof SocketException ? "SOCKET_RESET"
                        : e instanceof IOException ? "READ_ERROR" : "INPUT_ERROR";
                detalle = detalle(e);
            }
        } finally { cerrarSesion(s, razon, detalle); }
    }

    private boolean enviarRespuesta(Sesion s, String mensaje) {
        synchronized (s.salidaLock) {
            if (s.cierreSolicitado.get()) return false;
            s.salida.println(mensaje);
            return !s.salida.checkError();
        }
    }

    private void cerrarSesion(Sesion s, String razon, String detalle) {
        synchronized (lock) {
            if (!s.cierreSolicitado.compareAndSet(false, true)) return;
            s.limpieza = new Thread(() -> limpiar(s), "kvm-limpiar-servidor");
            s.limpieza.setDaemon(true); s.limpieza.start();
        }
        logger.accept("CIERRE_SESION razon=" + razon + " lado=SERVIDOR sesion="
                + Integer.toHexString(System.identityHashCode(s))
                + (detalle == null || detalle.isBlank() ? "" : " detalle=" + detalle));
    }

    private void limpiar(Sesion s) {
        try {
            Consumer<String> logSesion = msg -> logger.accept("Sesion servidor "
                    + Integer.toHexString(System.identityHashCode(s)) + ": " + msg);
            Cliente.cerrarSocket(s.socket, s.socket::isClosed, logSesion);
            if (s.monitor != null) s.monitor.interrupt();
            if (s.lector != null) s.lector.interrupt();
            Cliente.finalizarHilo(s.monitor, logSesion);
            Cliente.finalizarHilo(s.lector, logSesion);
            liberarEntradas(s, logSesion);
            Cliente.cerrarStream(s.entrada, logSesion);
            Cliente.cerrarStream(s.salida, logSesion);
            s.limpiezaTerminada = true;
        } finally { s.finLimpieza.countDown(); }
    }

    private void liberarEntradas(Sesion s, Consumer<String> logSesion) {
        boolean interrumpido = false, avisado = false;
        while (true) {
            synchronized (s.entradaLock) {
                for (var teclas = s.teclas.iterator(); teclas.hasNext();) {
                    int tecla = teclas.next();
                    try { robot.keyRelease(tecla); teclas.remove(); }
                    catch (RuntimeException e) {
                        if (!avisado) logSesion.accept("Liberacion pendiente de tecla " + tecla + ": " + e.getMessage());
                    }
                }
                for (var botones = s.botones.iterator(); botones.hasNext();) {
                    int boton = botones.next();
                    try { robot.mouseRelease(boton); botones.remove(); }
                    catch (RuntimeException e) {
                        if (!avisado) logSesion.accept("Liberacion pendiente de boton " + boton + ": " + e.getMessage());
                    }
                }
                if (s.teclas.isEmpty() && s.botones.isEmpty()) break;
            }
            avisado = true;
            try { Thread.sleep(100); } catch (InterruptedException e) { interrumpido = true; }
        }
        if (interrumpido) Thread.currentThread().interrupt();
    }

    private void procesarMensaje(Sesion s, String linea) {
        String[] p = linea.split(",", 3);

        if (p[0].equals("ENTRAR")) {
            if (p.length != 3 || (!p[1].equals("DERECHA") && !p[1].equals("IZQUIERDA")))
                throw new IllegalArgumentException("ENTRAR invalido");
            double altura = GeometriaPantalla.validarAltura(Double.parseDouble(p[2]));
            if (!s.mouseInicializado) inicializarMouse(s, p[1].equals("DERECHA"), altura, true);
            return;
        }
        if (!s.mouseInicializado) inicializarMouse(s, secundariaALaDerecha, 0.5, false);

        if (p[0].equals("D") && p.length == 3) {
            int dx = Integer.parseInt(p[1]);
            int dy = Integer.parseInt(p[2]);
            Point destino = pantalla.desplazar(posicionActual(), dx, dy);
            robot.mouseMove(destino.x, destino.y);
        }
        else if (p[0].equals("A") && p.length == 3) {
            robot.mouseMove(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        else if (p[0].equals("C") && p.length == 3) {
            int b = Integer.parseInt(p[2]);
            int m = b == 1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
            if (p[1].equals("PRESIONAR")) { s.botones.add(m); robot.mousePress(m); }
            else { robot.mouseRelease(m); s.botones.remove(m); }
        }
        else if (p[0].equals("W") && p.length == 2) {
            robot.mouseWheel(Integer.parseInt(p[1]));
        }
        else if (p[0].equals("T") && p.length == 2) {
            typeCharacter(s, p[1].charAt(0));
        }
        else if (p[0].equals("K") && p.length == 3) {
            int kc = convertirKeyCode(Integer.parseInt(p[2]));
            if (kc != -1) {
                try {
                    if (p[1].equals("PRESIONAR")) { s.teclas.add(kc); robot.keyPress(kc); }
                    else { robot.keyRelease(kc); s.teclas.remove(kc); }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    // Bajo entradaLock: la posicion se aplica antes de habilitar el borde de regreso.
    private void inicializarMouse(Sesion s, boolean secundariaALaDerecha, double altura, boolean conAltura) {
        Point entrada = pantalla.entradaSecundaria(secundariaALaDerecha, altura);
        robot.mouseMove(entrada.x, entrada.y);
        s.secundariaALaDerecha = secundariaALaDerecha;
        s.transicionConAltura = conAltura;
        s.mouseInicializado = true;
    }

    private void monitorearBorde(Sesion s) {
        try {
            while (!s.cierreSolicitado.get() && corriendo) {
                String regreso = null;
                synchronized (s.entradaLock) {
                    if (s.mouseInicializado && !s.cierreSolicitado.get()) {
                        Point pos = posicionActual();
                        if (pantalla.enBordeRegreso(pos.x, s.secundariaALaDerecha))
                            regreso = s.transicionConAltura ? "REGRESAR," + pantalla.alturaRelativa(pos.y) : "REGRESAR";
                    }
                }
                if (regreso != null) {
                    boolean enviado = enviarRespuesta(s, regreso);
                    cerrarSesion(s, enviado ? "REGRESAR" : "WRITE_ERROR", enviado ? regreso : "REGRESAR");
                    return;
                }
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!s.cierreSolicitado.get()) cerrarSesion(s,
                    corriendo ? "MONITOR_STOPPED" : "SERVER_STOP", "monitor de borde finalizado");
        }
    }

    protected Point posicionActual() { return MouseInfo.getPointerInfo().getLocation(); }
    protected GeometriaPantalla geometriaActual() { return GeometriaPantalla.actual(); }

    private boolean presionarTemporal(Sesion s, int tecla) {
        if (s.teclas.contains(tecla)) return false;
        s.teclas.add(tecla);
        robot.keyPress(tecla);
        return true;
    }

    private void liberarTemporal(Sesion s, int tecla, boolean propia) {
        if (propia) { robot.keyRelease(tecla); s.teclas.remove(tecla); }
    }

    private void typeCharacter(Sesion s, char c) {
        if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ' || (c >= 'A' && c <= 'Z')) {
            int vk = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
            if (vk != KeyEvent.VK_UNDEFINED) {
                boolean shift = false, tecla = false;
                try {
                    if (c >= 'A' && c <= 'Z') shift = presionarTemporal(s, KeyEvent.VK_SHIFT);
                    tecla = presionarTemporal(s, vk);
                    return;
                } catch (IllegalArgumentException ignored) {
                } finally {
                    liberarTemporal(s, vk, tecla);
                    liberarTemporal(s, KeyEvent.VK_SHIFT, shift);
                }
            }
        }
        typeViaClipboard(s, c);
    }

    private void typeViaClipboard(Sesion s, char c) {
        java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        java.awt.datatransfer.Transferable anterior = null;
        try { anterior = cb.getContents(null); } catch (Exception ignored) {}
        java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(String.valueOf(c));
        boolean ctrl = false, tecla = false;
        try {
            cb.setContents(sel, sel);
            ctrl = presionarTemporal(s, KeyEvent.VK_CONTROL);
            tecla = presionarTemporal(s, KeyEvent.VK_V);
        } finally {
            liberarTemporal(s, KeyEvent.VK_V, tecla);
            liberarTemporal(s, KeyEvent.VK_CONTROL, ctrl);
            try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (anterior != null) {
                try { cb.setContents(anterior, null); } catch (Exception ignored) {}
            }
        }
    }

    /** Solicita parada y retorna inmediatamente; las esperas son de hilo de fondo. */
    public void detener() {
        synchronized (lock) {
            corriendo = false;
            if (hiloParada != null) return;
            hiloParada = new Thread(this::limpiarServidor, "kvm-detener-servidor");
            hiloParada.setDaemon(true); hiloParada.start();
        }
    }

    private void limpiarServidor() {
        Sesion actual;
        ServerSocket escucha;
        Thread servidor;
        synchronized (lock) { actual = sesion; escucha = serverSocket; servidor = hiloServidor; }
        if (escucha != null) Cliente.cerrarSocket(escucha, escucha::isClosed, logger);
        if (actual != null) cerrarSesion(actual, "SERVER_STOP", "servidor detenido");
        discovery.detenerResponder();
        discovery.finalizarResponder();
        if (servidor != null) servidor.interrupt();
        Cliente.finalizarHilo(servidor, logger);
        // Una aceptacion concurrente pudo publicar una sesion antes de corriendo=false.
        synchronized (lock) { if (sesion != null) actual = sesion; }
        if (actual != null) {
            cerrarSesion(actual, "SERVER_STOP", "servidor detenido");
            Cliente.finalizarHilo(actual.limpieza, logger);
        }
        boolean terminado = discovery.isDetenido() && (actual == null || actual.limpiezaTerminada);
        synchronized (lock) {
            paradaTerminada = terminado;
            if (terminado) { sesion = null; serverSocket = null; }
        }
        onEstado.accept(terminado ? Estado.DETENIDO : Estado.ERROR);
        logger.accept(terminado ? "Servidor detenido; limpieza terminada" : "Parada incompleta; no reutilizar servidor");
    }

    public boolean esperarDetenido() {
        if (javax.swing.SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Esperar cierre fuera de Swing");
        Thread parada;
        synchronized (lock) { parada = hiloParada; }
        return Cliente.esperarHilo(parada, logger) && paradaTerminada;
    }

    public boolean isCorriendo() { return corriendo; }

    private static String detalle(Exception e) {
        String mensaje = e.getMessage();
        return e.getClass().getSimpleName() + (mensaje == null || mensaje.isBlank() ? "" : ": " + mensaje);
    }

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
