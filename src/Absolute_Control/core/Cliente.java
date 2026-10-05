package Absolute_Control.core;

import Absolute_Control.input.KeyboardHandler;
import Absolute_Control.input.MouseHandler;
import com.github.kwhat.jnativehook.GlobalScreen;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Protocolo de la aplicacion integrada: lineas UTF-8, comandos D/A/C/W/K/T
 * existentes, ENTRAR,lado,altura relativa al activar el mouse, PING/PONG existentes.
 * REGRESAR puede incluir altura relativa; un REGRESAR sin altura sigue aceptado.
 * Escape/cierre local cierra TCP (EOF); LIBERAR sigue aceptado por el servidor.
 * Ambas PCs deben usar esta implementacion integrada, sin negociar versiones.
 */
public class Cliente {
    public enum Estado { LOCAL, CONECTANDO, REMOTO, ERROR, DETENIDO }
    public static final int CONNECT_TIMEOUT_MS = 2000;
    public static final int PING_INTERVAL_MS = 1000;
    public static final int HEARTBEAT_TIMEOUT_MS = 3000;
    private static final int WATCHDOG_INTERVAL_MS = 100;
    private static final int JOIN_TIMEOUT_MS = 1500;
    private final String ip;
    private final int puerto;
    private final GeometriaPantalla pantalla;
    private final boolean secundariaALaDerecha;
    private final Consumer<String> logger;
    private final Consumer<Estado> onEstado;
    private final Object lock = new Object();
    private boolean habilitado, pendiente;
    private volatile boolean conectado;
    private Sesion sesion;
    private final Set<Sesion> limpiando = new HashSet<>();
    private MouseHandler mouseHandler;
    private KeyboardHandler keyboardHandler;
    private InputDispatcher dispatcher;

    private static class Sesion {
        final double alturaEntrada;
        volatile Double alturaRegreso;
        final Socket socket = new Socket();
        final ArrayBlockingQueue<String> mensajes = new ArrayBlockingQueue<>(2048);
        final CountDownLatch finLimpieza = new CountDownLatch(1);
        volatile boolean cerrada, limpiezaTerminada;
        volatile long ultimoPong = System.nanoTime();
        volatile BufferedReader entrada;
        volatile PrintWriter salida;
        Thread conexion, receptor, escritor, heartbeat;
        volatile Thread limpieza;
        Sesion(double alturaEntrada) { this.alturaEntrada = alturaEntrada; }
    }

    // Marca en el momento de ENCOLAR, no al ejecutar el callback nativo.
    // Un cambio de sesion descarta toda entrada capturada en la generacion anterior.
    private static class InputDispatcher extends ThreadPoolExecutor {
        private final AtomicLong generacion = new AtomicLong();
        private final ThreadLocal<long[]> contexto = new ThreadLocal<>();
        InputDispatcher() {
            super(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), tarea -> {
                Thread t = new Thread(tarea, "kvm-input"); t.setDaemon(true); return t;
            });
        }
        @Override public void execute(Runnable tarea) {
            long epoca = generacion.get(), instante = System.nanoTime();
            try {
                super.execute(() -> {
                    if (epoca != generacion.get()) return;
                    contexto.set(new long[]{epoca, instante});
                    try { tarea.run(); } finally { contexto.remove(); }
                });
            } catch (RejectedExecutionException ignored) { /* hook finalizando */ }
        }
        void invalidar() { generacion.incrementAndGet(); getQueue().clear(); }
        boolean esActual() {
            long[] c = contexto.get();
            return c == null || c[0] == generacion.get();
        }
        long instanteEvento() {
            long[] c = contexto.get(); return c == null ? System.nanoTime() : c[1];
        }
    }

    public Cliente(String ip, int puerto, boolean secundariaALaDerecha, GeometriaPantalla pantalla,
                   Consumer<String> logger, Consumer<Estado> onEstado) {
        this.ip = ip; this.puerto = puerto; this.secundariaALaDerecha = secundariaALaDerecha;
        this.pantalla = pantalla; this.logger = logger; this.onEstado = onEstado;
    }

    public void iniciarHandlers() {
        synchronized (lock) {
            if (habilitado) return;
            dispatcher = new InputDispatcher();
            GlobalScreen.setEventDispatcher(dispatcher);
            mouseHandler = new MouseHandler(msg -> {}, this::conectar, this::desconectar, pantalla);
            mouseHandler.setInstanteEvento(dispatcher::instanteEvento);
            mouseHandler.setEventoActual(dispatcher::esActual);
            mouseHandler.setLado(secundariaALaDerecha);
            keyboardHandler = new KeyboardHandler(msg -> {}, this::desconectar);
            keyboardHandler.setEventoActual(dispatcher::esActual);
            habilitado = true;
            try {
                GlobalScreen.addNativeMouseListener(mouseHandler);
                GlobalScreen.addNativeMouseMotionListener(mouseHandler);
                GlobalScreen.addNativeMouseWheelListener(mouseHandler);
                GlobalScreen.addNativeKeyListener(keyboardHandler);
            } catch (RuntimeException e) {
                detenerHandlers(); throw e;
            }
            onEstado.accept(Estado.LOCAL);
        }
    }

    /** Solicita parada; no espera hilos ni realiza joins en el callback/Swing. */
    public void detenerHandlers() {
        Sesion actual;
        synchronized (lock) {
            habilitado = false; actual = sesion;
        }
        cerrarSesion(actual, Estado.DETENIDO, "CLIENT_STOP", "Cliente detenido");
        synchronized (lock) {
            if (dispatcher != null) { dispatcher.invalidar(); dispatcher.shutdownNow(); }
            if (mouseHandler != null) {
                mouseHandler.setHabilitado(false);
                GlobalScreen.removeNativeMouseListener(mouseHandler);
                GlobalScreen.removeNativeMouseMotionListener(mouseHandler);
                GlobalScreen.removeNativeMouseWheelListener(mouseHandler);
            }
            if (keyboardHandler != null) GlobalScreen.removeNativeKeyListener(keyboardHandler);
        }
    }

    /** Verificacion acotada para el trabajador de parada, nunca el EDT. */
    public boolean esperarDetenido() {
        if (javax.swing.SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Esperar cierre fuera de Swing");
        Sesion[] anteriores;
        InputDispatcher input;
        synchronized (lock) { anteriores = limpiando.toArray(new Sesion[0]); input = dispatcher; }
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(JOIN_TIMEOUT_MS);
        boolean terminado = true;
        for (Sesion s : anteriores) terminado &= esperarHilo(s.limpieza, logger, limite) && s.limpiezaTerminada;
        if (input != null) {
            try { input.awaitTermination(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (!input.isTerminated()) { logger.accept("Hilo kvm-input sigue activo; no reutilizar cliente"); terminado = false; }
        }
        return terminado;
    }

    private void conectar() {
        synchronized (lock) {
            if (!habilitado || pendiente || conectado || (dispatcher != null && !dispatcher.esActual())) return;
            limpiando.removeIf(anterior -> anterior.limpiezaTerminada && anterior.limpieza != null && !anterior.limpieza.isAlive());
            Sesion[] anteriores = limpiando.toArray(new Sesion[0]);
            Sesion s = new Sesion(mouseHandler.alturaCruce());
            sesion = s; pendiente = true;
            onEstado.accept(Estado.CONECTANDO);
            s.conexion = hilo("kvm-conectar", () -> abrir(s, anteriores));
            s.conexion.start();
        }
    }

    private void abrir(Sesion s, Sesion[] anteriores) {
        try {
            // El cruce solicita conexion, pero no se activa B hasta que terminen los limpiadores de A.
            for (Sesion anterior : anteriores) {
                while (!s.cerrada && anterior.limpieza.isAlive()) anterior.limpieza.join(100);
                if (s.cerrada) return;
                if (!anterior.limpiezaTerminada) throw new IOException("Limpieza anterior sin completar");
            }
            if (s.cerrada) return;
            s.socket.connect(new InetSocketAddress(ip, puerto), CONNECT_TIMEOUT_MS);
            s.socket.setTcpNoDelay(true);
            s.entrada = new BufferedReader(new InputStreamReader(s.socket.getInputStream(), StandardCharsets.UTF_8));
            s.salida = new PrintWriter(new OutputStreamWriter(s.socket.getOutputStream(), StandardCharsets.UTF_8), true);
            synchronized (lock) {
                if (sesion != s || s.cerrada || !habilitado) return;
                s.ultimoPong = System.nanoTime();
                if (dispatcher != null) dispatcher.invalidar();
                mouseHandler.setSesion(msg -> enviar(s, msg));
                keyboardHandler.setSesion(msg -> enviar(s, msg),
                        () -> cerrarPorEvento(s));
                pendiente = false; conectado = true;
                onEstado.accept(Estado.REMOTO);
                s.escritor = hilo("kvm-enviar", () -> escribir(s));
                s.receptor = hilo("kvm-recibir", () -> escucharServidor(s));
                s.heartbeat = hilo("kvm-heartbeat", () -> vigilar(s));
                s.escritor.start(); s.receptor.start(); s.heartbeat.start();
            }
            logger.accept("Conectado a " + ip + ":" + puerto);
        } catch (Exception e) {
            cerrarSesion(s, Estado.ERROR, "CONNECT_ERROR", detalle(e));
        }
        // El propietario de limpieza espera a este hilo antes de cerrar streams.
    }

    private void escucharServidor(Sesion s) {
        try {
            String linea;
            while (!s.cerrada && (linea = s.entrada.readLine()) != null) {
                if (linea.equals("PONG")) s.ultimoPong = System.nanoTime();
                else if (linea.equals("REGRESAR") || linea.startsWith("REGRESAR,")) {
                    String[] regreso = linea.split(",", -1);
                    if (regreso.length > 2) throw new IOException("REGRESAR invalido");
                    s.alturaRegreso = regreso.length == 2
                            ? GeometriaPantalla.validarAltura(Double.parseDouble(regreso[1])) : s.alturaEntrada;
                    cerrarSesion(s, Estado.LOCAL, "REGRESAR", linea); return;
                }
            }
            cerrarSesion(s, Estado.ERROR, "EOF", "Servidor desconectado");
        } catch (IOException | IllegalArgumentException e) {
            String razon = e instanceof SocketException ? "SOCKET_RESET"
                    : e instanceof IllegalArgumentException ? "PROTOCOL_ERROR" : "READ_ERROR";
            cerrarSesion(s, Estado.ERROR, razon, detalle(e));
        }
    }

    private void escribir(Sesion s) {
        // El mismo escritor coloca el cursor remoto antes de cualquier delta encolado.
        if (s.cerrada) return;
        s.salida.println("ENTRAR," + (secundariaALaDerecha ? "DERECHA" : "IZQUIERDA") + "," + s.alturaEntrada);
        if (s.salida.checkError()) { cerrarSesion(s, Estado.ERROR, "WRITE_ERROR", "ENTRAR"); return; }
        long proximoPing = System.nanoTime();
        try {
            while (!s.cerrada) {
                long ahora = System.nanoTime();
                String msg;
                if (ahora >= proximoPing) {
                    msg = "PING";
                    proximoPing = ahora + TimeUnit.MILLISECONDS.toNanos(PING_INTERVAL_MS);
                } else {
                    msg = s.mensajes.poll(Math.min(100, Math.max(1,
                            TimeUnit.NANOSECONDS.toMillis(proximoPing - ahora))), TimeUnit.MILLISECONDS);
                    if (msg == null) continue;
                }
                if (s.cerrada) break;
                s.salida.println(msg);
                if (s.salida.checkError()) {
                    cerrarSesion(s, Estado.ERROR, "WRITE_ERROR", "escritor TCP"); break;
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void vigilar(Sesion s) {
        try {
            while (!s.cerrada) {
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.ultimoPong) >= HEARTBEAT_TIMEOUT_MS) {
                    long sinPongMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s.ultimoPong);
                    cerrarSesion(s, Estado.ERROR, "HEARTBEAT_TIMEOUT", "sin_pong_ms=" + sinPongMs); return;
                }
                Thread.sleep(WATCHDOG_INTERVAL_MS);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void desconectar() {
        Sesion s;
        synchronized (lock) { s = sesion; }
        cerrarSesion(s, Estado.LOCAL, "LOCAL_REQUEST", "desconexion solicitada");
    }

    private void cerrarSesion(Sesion s, Estado estado, String razon, String detalle) {
        synchronized (lock) {
            if (s == null || sesion != s || s.cerrada) return;
            s.cerrada = true; conectado = false; pendiente = false; sesion = null;
            if (dispatcher != null) dispatcher.invalidar();
            mouseHandler.setControlando(false);
            keyboardHandler.setControlando(false);
            limpiando.add(s);
            // Un unico propietario fisico, separado del hilo que solicita cierre.
            s.limpieza = hilo("kvm-limpiar-cliente", () -> limpiar(s));
            s.limpieza.start();
            if (s.alturaRegreso != null) {
                try { mouseHandler.recolocarAlRegresar(s.alturaRegreso); }
                catch (RuntimeException e) { logger.accept("No se pudo recolocar cursor local: " + e.getMessage()); }
            }
            onEstado.accept(estado);
        }
        logger.accept("CIERRE_SESION razon=" + razon + " lado=CLIENTE sesion="
                + Integer.toHexString(System.identityHashCode(s))
                + (detalle == null || detalle.isBlank() ? "" : " detalle=" + detalle));
    }

    // Conserva el punto de prueba de Fase 1.1 que simula un cierre tardio de una sesion anterior.
    private void cerrarSesion(Sesion s, Estado estado, String detalle) {
        cerrarSesion(s, estado, "TEST_REQUEST", detalle);
    }

    private void limpiar(Sesion s) {
        try {
            // Socket primero desbloquea I/O. El control local ya fue recuperado.
            Consumer<String> logSesion = msg -> logger.accept("Sesion cliente "
                    + Integer.toHexString(System.identityHashCode(s)) + ": " + msg);
            cerrarSocket(s.socket, s.socket::isClosed, logSesion);
            Thread[] hilos = {s.conexion, s.receptor, s.escritor, s.heartbeat};
            for (Thread t : hilos) if (t != null) t.interrupt();
            for (Thread t : hilos) finalizarHilo(t, logSesion);
            cerrarStream(s.entrada, logSesion);
            cerrarStream(s.salida, logSesion);
            s.mensajes.clear();
            s.limpiezaTerminada = true;
        } finally { s.finLimpieza.countDown(); }
    }

    private void cerrarPorEvento(Sesion s) {
        synchronized (lock) {
            if (dispatcher != null && !dispatcher.esActual()) return;
            cerrarSesion(s, Estado.LOCAL, "ESCAPE", "Escape capturado por hook global");
        }
    }

    private void enviar(Sesion s, String msg) {
        synchronized (lock) {
            if (sesion != s || !conectado || s.cerrada || (dispatcher != null && !dispatcher.esActual())) return;
            if (s.mensajes.offer(msg)) return;
        }
        cerrarSesion(s, Estado.ERROR, "QUEUE_SATURATION", "size=" + s.mensajes.size());
    }

    private static String detalle(Exception e) {
        String mensaje = e.getMessage();
        return e.getClass().getSimpleName() + (mensaje == null || mensaje.isBlank() ? "" : ": " + mensaje);
    }

    static boolean esperarHilo(Thread t, Consumer<String> logger) {
        return esperarHilo(t, logger, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(JOIN_TIMEOUT_MS));
    }

    private static boolean esperarHilo(Thread t, Consumer<String> logger, long limite) {
        if (t == null) return true;
        if (t == Thread.currentThread()) return false;
        long restante = limite - System.nanoTime();
        try { if (t.isAlive() && restante > 0) TimeUnit.NANOSECONDS.timedJoin(t, restante); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (t.isAlive()) { logger.accept("Hilo " + t.getName() + " sigue activo tras timeout"); return false; }
        return true;
    }

    /** Solo para propietarios de limpieza en segundo plano; el timeout no abandona recursos. */
    static void finalizarHilo(Thread t, Consumer<String> logger) {
        if (t == null) return;
        if (t == Thread.currentThread()) throw new IllegalStateException("Un hilo no puede esperarse a si mismo");
        boolean interrumpido = false, avisado = false;
        while (t.isAlive()) {
            try { t.join(JOIN_TIMEOUT_MS); }
            catch (InterruptedException e) { interrumpido = true; }
            if (t.isAlive() && !avisado) {
                logger.accept("Hilo " + t.getName() + " sigue activo tras timeout; la limpieza continua");
                avisado = true;
            }
        }
        if (avisado) logger.accept("Hilo " + t.getName() + " termino; limpieza tardia continua");
        if (interrumpido) Thread.currentThread().interrupt();
    }

    static void cerrarSocket(Closeable socket, BooleanSupplier cerrado, Consumer<String> logger) {
        boolean interrumpido = false;
        while (!cerrado.getAsBoolean()) {
            try { socket.close(); }
            catch (IOException e) { logger.accept("Cierre TCP pendiente: " + e.getMessage()); }
            if (!cerrado.getAsBoolean()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { interrumpido = true; }
            }
        }
        if (interrumpido) Thread.currentThread().interrupt();
    }

    static void cerrarStream(Closeable stream, Consumer<String> logger) {
        if (stream == null) return;
        boolean interrumpido = false, avisado = false;
        while (true) {
            try { stream.close(); break; }
            catch (IOException | RuntimeException e) {
                if (!avisado) logger.accept("Cierre de stream pendiente; se reintentara: " + e.getMessage());
                avisado = true;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { interrumpido = true; }
        }
        if (interrumpido) Thread.currentThread().interrupt();
    }

    private static Thread hilo(String nombre, Runnable tarea) {
        Thread t = new Thread(tarea, nombre); t.setDaemon(true); return t;
    }
    public boolean isConectado() { return conectado; }
}
