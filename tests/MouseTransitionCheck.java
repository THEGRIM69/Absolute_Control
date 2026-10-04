import Absolute_Control.core.*;
import Absolute_Control.input.*;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public class MouseTransitionCheck {
    static final int[][] RESOLUCIONES = {{1366,768,683,384},{1920,1080,960,540},{2560,1440,1280,720}};

    static void check(boolean ok, String mensaje) { if (!ok) throw new AssertionError(mensaje); }
    static void esperar(BooleanSupplier condicion, String mensaje) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) Thread.sleep(5);
        check(condicion.getAsBoolean(), mensaje);
    }
    static Field campo(Object objeto, String nombre) throws Exception {
        Class<?> tipo = objeto.getClass();
        while (tipo != null) {
            try { Field f = tipo.getDeclaredField(nombre); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { tipo = tipo.getSuperclass(); }
        }
        throw new NoSuchFieldException(nombre);
    }
    static Object leer(Object objeto, String nombre) throws Exception { return campo(objeto,nombre).get(objeto); }
    static void poner(Object objeto, String nombre, Object valor) throws Exception { campo(objeto,nombre).set(objeto,valor); }

    static class MouseSimulado extends MouseHandler {
        volatile Point posicion;
        volatile boolean remoto;
        final List<Point> warps = new CopyOnWriteArrayList<>();
        MouseSimulado(GeometriaPantalla pantalla, Consumer<String> enviar, Runnable conectar) {
            super(enviar, conectar, ()->{}, pantalla, null);
            posicion = pantalla.centro();
        }
        @Override protected Point posicionActual() { return new Point(posicion); }
        @Override protected void moverCursorLocal(Point punto) { posicion = new Point(punto); warps.add(new Point(punto)); }
        @Override public synchronized void setControlando(boolean controlando) { super.setControlando(controlando); remoto = controlando; }
        void mover(Point punto) {
            posicion = new Point(punto);
            nativeMouseMoved(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_MOVED,0,punto.x,punto.y,0));
        }
    }

    static void geometria() throws Exception {
        for (int[] r : RESOLUCIONES) {
            GeometriaPantalla pantalla = new GeometriaPantalla(r[0],r[1]);
            check(pantalla.centro().equals(new Point(r[2],r[3])), "Centro incorrecto " + r[0]);
            check(pantalla.izquierda()==0 && pantalla.derecha()==r[0]-1, "Limites incorrectos");
            Point centro = pantalla.centro(); centro.x = -1;
            check(pantalla.centro().x==r[2], "Centro mutable desde fuera");
            check(pantalla.alturaRelativa(-100)==0 && pantalla.alturaRelativa(r[1]+100)==1, "Altura sin limitar");
            check(pantalla.desplazar(new Point(10,10),Integer.MAX_VALUE,Integer.MIN_VALUE).equals(new Point(r[0]-1,0)), "Delta extremo sin limitar");
            for (boolean derecha : new boolean[]{true,false}) {
                int salida = derecha ? pantalla.derecha() : pantalla.izquierda();
                int regreso = derecha ? pantalla.izquierda() : pantalla.derecha();
                check(pantalla.enBordeSalida(salida,derecha) && !pantalla.enBordeSalida(regreso,derecha), "Salida por lado equivocado");
                check(pantalla.enBordeRegreso(regreso,derecha) && !pantalla.enBordeRegreso(salida,derecha), "Regreso por lado equivocado");
                Point entrada = pantalla.entradaSecundaria(derecha,0.7);
                check(entrada.x==(derecha ? 8 : r[0]-9), "Margen de entrada incorrecto");
                check(!pantalla.enBordeRegreso(entrada.x,derecha), "Entrada activa regreso inmediato");
                for (int[] destino : RESOLUCIONES) {
                    GeometriaPantalla remota = new GeometriaPantalla(destino[0],destino[1]);
                    int yOrigen = (int)Math.round(0.7*(pantalla.alto()-1));
                    Point punto = remota.entradaSecundaria(derecha,pantalla.alturaRelativa(yOrigen));
                    check(Math.abs(punto.y-0.7*(remota.alto()-1))<=1.5, "Altura relativa incorrecta entre resoluciones");
                }

                AtomicInteger cruces = new AtomicInteger(); List<String> mensajes = new CopyOnWriteArrayList<>();
                MouseSimulado mouse = new MouseSimulado(pantalla,mensajes::add,cruces::incrementAndGet);
                mouse.setLado(derecha);
                AtomicLong reloj = new AtomicLong(System.nanoTime()); mouse.setInstanteEvento(reloj::get);
                int y = (int)Math.round(0.7*(pantalla.alto()-1));
                mouse.mover(new Point(regreso,y)); check(cruces.get()==0, "Borde opuesto conecto");
                mouse.mover(new Point(salida,y)); check(cruces.get()==1, "Cruce inicial no conecto");
                check(Math.abs(mouse.alturaCruce()-0.7)<=1.0/(pantalla.alto()-1), "No capturo altura del cruce");
                mouse.setSesion(mensajes::add);
                check(mouse.posicion.equals(pantalla.centro()), "No anclo en centro dinamico");
                mouse.mover(new Point(r[2]+17,r[3]-9));
                check(mensajes.equals(List.of("D,17,-9")), "Movimiento dejo de ser relativo");
                check(mouse.posicion.equals(pantalla.centro()), "No reanclo cursor");
                mouse.nativeMouseDragged(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_DRAGGED,0,r[2]-12,r[3]+4,0));
                check(mensajes.get(1).equals("D,-12,4"), "Arrastre relativo incorrecto");
                mouse.setControlando(false); mouse.recolocarAlRegresar(0.8);
                Point local = pantalla.regresoPrincipal(derecha,0.8);
                check(mouse.posicion.equals(local) && !pantalla.enBordeSalida(local.x,derecha), "Regreso local fuera del margen");
                reloj.set(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1));
                mouse.mover(local); check(cruces.get()==1, "Warp de regreso reconecto");
                mouse.mover(new Point(salida,local.y)); check(cruces.get()==2, "Nuevo cruce rapido tras REGRESAR ignorado");

                // Una caida mantiene las condiciones de rearme de Fase 1.1.
                mouse.setControlando(false); mouse.mover(new Point(salida,y)); check(cruces.get()==2, "Caida reconecto por residual");
                reloj.set(System.nanoTime()+TimeUnit.SECONDS.toNanos(1));
                mouse.mover(new Point(r[2]+10,200)); reloj.addAndGet(TimeUnit.MILLISECONDS.toNanos(130));
                mouse.mover(new Point(salida,y)); check(cruces.get()==3, "Caida no permitio nuevo cruce valido");
            }
        }
        for (double altura : new double[]{Double.NaN,Double.POSITIVE_INFINITY,-0.1,1.1}) {
            try { GeometriaPantalla.validarAltura(altura); throw new AssertionError("Altura invalida aceptada"); }
            catch (IllegalArgumentException esperado) {}
        }
        GeometriaPantalla minima = new GeometriaPantalla(5,1);
        check(minima.alturaRelativa(10)==0 && minima.entradaSecundaria(true,1).equals(new Point(2,0)), "Pantalla minima fuera de limites");
        System.out.println("PASS geometria headless: 3 resoluciones, ambos lados, 18 combinaciones de alturas, deltas, margen y rearme");
    }

    static class RobotSimulado extends Robot {
        final AtomicReference<Point> posicion;
        final java.util.Set<Integer> teclas = ConcurrentHashMap.newKeySet(), botones = ConcurrentHashMap.newKeySet();
        RobotSimulado(AtomicReference<Point> posicion) throws Exception { this.posicion = posicion; }
        @Override public void mouseMove(int x,int y) { posicion.set(new Point(x,y)); }
        @Override public void keyPress(int tecla) { teclas.add(tecla); }
        @Override public void keyRelease(int tecla) { teclas.remove(tecla); }
        @Override public void mousePress(int boton) { botones.add(boton); }
        @Override public void mouseRelease(int boton) { botones.remove(boton); }
        @Override public void mouseWheel(int delta) {}
    }
    static class ServidorSimulado extends Servidor implements AutoCloseable {
        final GeometriaPantalla geometria;
        final AtomicReference<Point> cursor;
        RobotSimulado robotSimulado;
        ServidorSimulado(GeometriaPantalla geometria, boolean derecha) {
            super(0,!derecha,msg->{},estado->{}); // El paquete de la principal decide la disposicion de la sesion.
            this.geometria = geometria;
            cursor = new AtomicReference<>(new Point(derecha ? 0 : geometria.derecha(),100));
        }
        @Override protected GeometriaPantalla geometriaActual() { return geometria; }
        @Override protected Point posicionActual() { return new Point(cursor.get()); }
        void arrancar() throws Exception {
            iniciar(); robotSimulado = new RobotSimulado(cursor);
            synchronized (leer(this,"lock")) { poner(this,"robot",robotSimulado); }
        }
        int puerto() throws Exception { return ((ServerSocket)leer(this,"serverSocket")).getLocalPort(); }
        @Override public void close() { detener(); check(esperarDetenido(),"Servidor de geometria no termino"); }
    }
    static void sinRespuesta(BufferedReader entrada) throws Exception {
        try { String inesperado=entrada.readLine(); throw new AssertionError("Regreso inesperado: " + inesperado); }
        catch (SocketTimeoutException esperado) {}
    }
    static void protocolo() throws Exception {
        for (int[] r : RESOLUCIONES) for (boolean derecha : new boolean[]{true,false}) {
            GeometriaPantalla pantalla = new GeometriaPantalla(r[0],r[1]);
            try (ServidorSimulado servidor = new ServidorSimulado(pantalla,derecha)) {
                servidor.arrancar();
                try (Socket socket = new Socket("127.0.0.1",servidor.puerto())) {
                    socket.setSoTimeout(100);
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    PrintWriter salida = new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8);
                    sinRespuesta(entrada); // Cursor anterior sobre el borde: no se debe devolver antes de ENTRAR.
                    Point cursorAntesDePing = servidor.cursor.get();
                    salida.println("PING");
                    socket.setSoTimeout(2000); check("PONG".equals(entrada.readLine()),"PING/PONG no respondio antes de ENTRAR");
                    check(servidor.cursor.get().equals(cursorAntesDePing),"PING movio el cursor antes de ENTRAR");
                    socket.setSoTimeout(100); sinRespuesta(entrada);
                    salida.println("ENTRAR,"+(derecha ? "DERECHA" : "IZQUIERDA")+",0.7"); salida.println("PING");
                    socket.setSoTimeout(2000); check("PONG".equals(entrada.readLine()),"PING/PONG no respondio tras ENTRAR");
                    check(servidor.cursor.get().equals(pantalla.entradaSecundaria(derecha,0.7)),"ENTRAR no coloco altura/margen");
                    socket.setSoTimeout(100); sinRespuesta(entrada);
                    salida.println("D,"+(derecha ? r[0]*2 : -r[0]*2)+",0"); salida.println("PING");
                    socket.setSoTimeout(2000); check("PONG".equals(entrada.readLine()),"Borde opuesto devolvio el control");
                    socket.setSoTimeout(100); sinRespuesta(entrada);
                    int y = (int)Math.round(0.8*(r[1]-1));
                    salida.println("D,"+(derecha ? -r[0]*2 : r[0]*2)+","+(y-servidor.cursor.get().y));
                    socket.setSoTimeout(2000); String regreso = entrada.readLine();
                    check(regreso!=null && regreso.startsWith("REGRESAR,"),"No devolvio altura al tocar borde correcto");
                    check(Math.abs(Double.parseDouble(regreso.split(",")[1])-0.8)<=1.0/(r[1]-1),"Altura de regreso incorrecta");
                    check(entrada.readLine()==null,"Regreso no cerro la sesion");
                }
            }
        }
        System.out.println("PASS protocolo localhost: PING sin movimiento, ENTRAR antes del borde, margen, lado correcto y REGRESAR con altura");
    }
    static void clienteServidor() throws Exception {
        int ciclos=0;
        for (int[] principal : RESOLUCIONES) for (int[] secundaria : RESOLUCIONES) for (boolean derecha : new boolean[]{true,false}) {
            GeometriaPantalla local = new GeometriaPantalla(principal[0],principal[1]);
            GeometriaPantalla remota = new GeometriaPantalla(secundaria[0],secundaria[1]);
            try (ServidorSimulado servidor = new ServidorSimulado(remota,derecha)) {
                servidor.arrancar();
                Cliente cliente = new Cliente("127.0.0.1",servidor.puerto(),derecha,local,msg->{},estado->{});
                MouseSimulado mouse = new MouseSimulado(local,msg->{},()->{
                    try { Method conectar=Cliente.class.getDeclaredMethod("conectar"); conectar.setAccessible(true); conectar.invoke(cliente); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                mouse.setLado(derecha);
                KeyboardHandler teclado = new KeyboardHandler(msg->{},()->{});
                poner(cliente,"mouseHandler",mouse); poner(cliente,"keyboardHandler",teclado); poner(cliente,"habilitado",true);
                try {
                    for (int i=0;i<3;i++) {
                        int yEntrada = (int)Math.round(0.7*(local.alto()-1));
                        mouse.mover(new Point(derecha ? local.derecha() : local.izquierda(),yEntrada));
                        esperar(cliente::isConectado,"Cruce no conecto");
                        Point entrada = remota.entradaSecundaria(derecha,local.alturaRelativa(yEntrada));
                        esperar(()->servidor.cursor.get().equals(entrada),"Cliente no transmitio altura inicial");
                        check(mouse.posicion.equals(local.centro()),"Cliente no anclo en su centro");
                        int dx = derecha ? 12 : -12;
                        Point movimiento = remota.desplazar(entrada,dx,-6);
                        mouse.mover(new Point(local.centro().x+dx,local.centro().y-6));
                        esperar(()->servidor.cursor.get().equals(movimiento),"Delta remoto incorrecto");
                        teclado.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,162,NativeKeyEvent.VC_CONTROL,NativeKeyEvent.CHAR_UNDEFINED));
                        mouse.nativeMousePressed(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_PRESSED,0,local.centro().x,local.centro().y,1,NativeMouseEvent.BUTTON1));
                        esperar(()->servidor.robotSimulado.teclas.contains(KeyEvent.VK_CONTROL)
                                && servidor.robotSimulado.botones.contains(InputEvent.BUTTON1_DOWN_MASK),"Entradas no llegaron a sesion");
                        int yRegreso = (int)Math.round(0.8*(remota.alto()-1));
                        mouse.mover(new Point(local.centro().x+(derecha ? -32 : 32),local.centro().y+yRegreso-movimiento.y));
                        Point regreso = local.regresoPrincipal(derecha,remota.alturaRelativa(yRegreso));
                        esperar(()->!cliente.isConectado() && !mouse.remoto && mouse.posicion.equals(regreso),"Cliente no regreso al borde y altura esperados");
                        esperar(()->servidor.robotSimulado.teclas.isEmpty() && servidor.robotSimulado.botones.isEmpty(),"Regreso retuvo entradas de sesion");
                        ciclos++;
                    }
                } finally {
                    cliente.detenerHandlers(); check(cliente.esperarDetenido(),"Cliente de geometria no termino");
                }
            }
        }
        check(ciclos==54,"No se probaron todas las combinaciones");
        System.out.println("PASS 54 cruces cliente/servidor: 18 combinaciones, ida 70%, regreso 80%, deltas y liberacion por sesion");
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("Usar --geometry o --integration");
        if (args[0].equals("--geometry")) geometria();
        else if (args[0].equals("--integration")) { protocolo(); clienteServidor(); }
        else throw new IllegalArgumentException("Modo desconocido");
        List<String> vivos = Thread.getAllStackTraces().keySet().stream()
                .filter(t->t.isAlive() && t.getName().startsWith("kvm-")).map(Thread::getName).toList();
        check(vivos.isEmpty(),"Hilos sobrevivientes: " + vivos);
        System.out.println("PASS sin hilos kvm sobrevivientes en transiciones");
    }
}
