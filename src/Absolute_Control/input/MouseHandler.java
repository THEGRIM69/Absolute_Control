package Absolute_Control.input;

import Absolute_Control.core.GeometriaPantalla;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;

public class MouseHandler implements NativeMouseInputListener, NativeMouseWheelListener {
    private final GeometriaPantalla pantalla;
    private static final int LOCAL_SETTLE_MS = 200;
    private static final int EXIT_DWELL_MS = 120;
    private Consumer<String> onEnviar;
    private final Runnable onConectar;
    private boolean controlando, anclando, bordeArmado = true, habilitado = true;
    private long aceptarDesde, fueraDesde;
    private LongSupplier instanteEvento = System::nanoTime;
    private BooleanSupplier eventoActual = () -> true;
    private boolean secundariaALaDerecha = true;
    private double alturaCruce = 0.5;
    private Point ultimoWarp;
    private Robot robotLocal;

    public MouseHandler(Consumer<String> enviar, Runnable conectar, Runnable desconectar) {
        this(enviar, conectar, desconectar, GeometriaPantalla.actual());
    }

    public MouseHandler(Consumer<String> enviar, Runnable conectar, Runnable desconectar, GeometriaPantalla pantalla) {
        this(enviar, conectar, desconectar, pantalla, crearRobot());
    }

    protected MouseHandler(Consumer<String> enviar, Runnable conectar, Runnable desconectar,
                           GeometriaPantalla pantalla, Robot robot) {
        onEnviar = enviar; onConectar = conectar; this.pantalla = pantalla; robotLocal = robot;
    }

    private static Robot crearRobot() {
        try { return new Robot(); } catch (AWTException e) { return null; }
    }

    public synchronized void setInstanteEvento(LongSupplier instante) { instanteEvento = instante; }
    public synchronized void setEventoActual(BooleanSupplier actual) { eventoActual = actual; }

    public synchronized void setSesion(Consumer<String> enviar) {
        onEnviar = enviar;
        setControlando(true);
    }

    public synchronized void setControlando(boolean controlando) {
        this.controlando = controlando; anclando = false;
        if (!controlando) {
            bordeArmado = false; fueraDesde = 0;
            aceptarDesde = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOCAL_SETTLE_MS);
        }
        if (controlando) anclarEn(pantalla.centro());
    }

    public synchronized void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
        if (!habilitado) setControlando(false);
    }

    public synchronized void setLado(boolean secundariaALaDerecha) {
        this.secundariaALaDerecha = secundariaALaDerecha;
    }

    public synchronized double alturaCruce() { return alturaCruce; }

    // Solo para REGRESAR de la sesion validada por Cliente. Una caida conserva el rearme de Fase 1.1.
    public synchronized void recolocarAlRegresar(double altura) {
        Point entrada = pantalla.regresoPrincipal(secundariaALaDerecha, altura);
        aceptarDesde = System.nanoTime();
        fueraDesde = 0; bordeArmado = true;
        anclarEn(entrada);
    }

    private void anclarEn(Point punto) {
        ultimoWarp = new Point(punto);
        moverCursorLocal(punto);
    }

    protected void moverCursorLocal(Point punto) {
        if (robotLocal != null) robotLocal.mouseMove(punto.x, punto.y);
    }

    protected Point posicionActual() { return MouseInfo.getPointerInfo().getLocation(); }

    @Override public void nativeMouseMoved(NativeMouseEvent e) {
        boolean conectar = false;
        String movimiento = null;
        Consumer<String> enviar;
        synchronized (this) {
            if (!habilitado || anclando || !eventoActual.getAsBoolean()) return;
            enviar = onEnviar; // Capturar el emisor de ESTA sesion antes de soltar el monitor.
            if (controlando) movimiento = movimientoRelativo(e);
            else {
                long instante = instanteEvento.getAsLong();
                if (instante <= aceptarDesde) return;
                // Ignorar warps propios y eventos atrasados que no coinciden con el cursor vivo.
                Point evento = new Point(e.getX(), e.getY());
                if (evento.equals(pantalla.centro()) || evento.equals(ultimoWarp)) return;
                Point actual = posicionActual();
                if (actual.x != e.getX() || actual.y != e.getY()) return;
                boolean enBorde = pantalla.enBordeSalida(e.getX(), secundariaALaDerecha);
                if (!enBorde) {
                    if (fueraDesde == 0) fueraDesde = instante;
                } else {
                    if (bordeArmado || (fueraDesde != 0 && instante - fueraDesde >= TimeUnit.MILLISECONDS.toNanos(EXIT_DWELL_MS))) {
                        conectar = true; alturaCruce = pantalla.alturaRelativa(e.getY());
                    }
                    bordeArmado = false; fueraDesde = 0;
                }
            }
        }
        if (movimiento != null) enviar.accept(movimiento);
        if (conectar) onConectar.run();
    }

    private String movimientoRelativo(NativeMouseEvent e) {
        Point centro = pantalla.centro();
        int dx = e.getX() - centro.x, dy = e.getY() - centro.y;
        anclando = true;
        try { anclarEn(centro); }
        finally { anclando = false; }
        return dx != 0 || dy != 0 ? "D," + dx + "," + dy : null;
    }

    @Override public void nativeMouseDragged(NativeMouseEvent e) {
        String movimiento;
        Consumer<String> enviar;
        synchronized (this) {
            if (!habilitado || !controlando || anclando || !eventoActual.getAsBoolean()) return;
            enviar = onEnviar; movimiento = movimientoRelativo(e);
        }
        if (movimiento != null) enviar.accept(movimiento);
    }

    @Override public void nativeMousePressed(NativeMouseEvent e) { boton(e, "PRESIONAR"); }
    @Override public void nativeMouseReleased(NativeMouseEvent e) { boton(e, "LIBERAR"); }

    private void boton(NativeMouseEvent e, String accion) {
        Consumer<String> enviar;
        synchronized (this) {
            if (!habilitado || !controlando || !eventoActual.getAsBoolean()) return;
            enviar = onEnviar;
        }
        int b = e.getButton() == NativeMouseEvent.BUTTON1 ? 1
              : e.getButton() == NativeMouseEvent.BUTTON2 ? 3 : -1;
        if (b != -1) enviar.accept("C," + accion + "," + b);
    }

    @Override public void nativeMouseClicked(NativeMouseEvent e) {}
    @Override public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
        Consumer<String> enviar;
        synchronized (this) {
            if (!habilitado || !controlando || !eventoActual.getAsBoolean()) return;
            enviar = onEnviar;
        }
        int delta = e.getWheelRotation();
        if (delta != 0) enviar.accept("W," + delta);
    }
}
