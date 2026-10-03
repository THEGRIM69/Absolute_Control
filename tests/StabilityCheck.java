import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import Absolute_Control.core.*;
import Absolute_Control.input.*;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

public class StabilityCheck {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static void waitFor(BooleanSupplier condition, int ms) throws Exception {
        long end = System.nanoTime() + ms * 1000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        check(condition.getAsBoolean(), "Timeout esperando condicion");
    }
    static void set(Object o, String name, Object value) throws Exception {
        Field f=field(o,name);
        if(o instanceof Servidor && name.equals("robot")) {
            // Publicar el Robot simulado antes de que prepararSesion adquiera el mismo lock.
            synchronized(field(o,"lock").get(o)) {f.set(o,value);}
        } else f.set(o,value);
    }
    static Field field(Object o, String name) throws Exception {
        Class<?> type=o.getClass();
        while(type!=null) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
            catch(NoSuchFieldException e){type=type.getSuperclass();}
        }
        throw new NoSuchFieldException(name);
    }
    static Object get(Object o, String name) throws Exception {return field(o,name).get(o);}
    static void diagnoseClient(Cliente cliente) throws Exception {
        for(Object sesion : (Set<?>)get(cliente,"limpiando")) {
            System.out.println("SESSION " + Integer.toHexString(System.identityHashCode(sesion))
                    + " socketClosed=" + ((Socket)get(sesion,"socket")).isClosed()
                    + " cleanup=" + get(sesion,"limpiezaTerminada")
                    + " latch=" + ((CountDownLatch)get(sesion,"finLimpieza")).getCount());
            for(String nombre : new String[]{"conexion","receptor","escritor","heartbeat","limpieza"}) {
                Thread hilo=(Thread)get(sesion,nombre);
                if(hilo!=null) System.out.println("  " + hilo.getName() + " alive=" + hilo.isAlive()
                        + " state=" + hilo.getState() + " stack=" + Arrays.toString(hilo.getStackTrace()));
            }
        }
    }
    static void awaitClean(Object sesion) throws Exception {
        check(((CountDownLatch)get(sesion,"finLimpieza")).await(5000,TimeUnit.MILLISECONDS),"Limpieza no finalizo");
        check((boolean)get(sesion,"limpiezaTerminada"),"Limpieza incompleta");
        Thread worker=(Thread)get(sesion,"limpieza");worker.join(1000);check(!worker.isAlive(),"Cleaner sigue activo");
    }
    static void stop(Cliente c) throws Exception {c.detenerHandlers();check(c.esperarDetenido(),"Stop cliente incompleto");}
    static void stop(Servidor s) throws Exception {s.detener();check(s.esperarDetenido(),"Stop servidor incompleto");}
    static void connect(Cliente c) throws Exception {
        Method m=Cliente.class.getDeclaredMethod("conectar");m.setAccessible(true);m.invoke(c);
    }
    static class MouseSinEntrada extends MouseHandler {
        volatile boolean remoto;
        MouseSinEntrada() throws Exception { super(x->{},()->{},()->{},new GeometriaPantalla(1366,768),null); }
        @Override public synchronized void setControlando(boolean c) { super.setControlando(c);remoto=c; }
    }
    static Cliente client(int port, MouseSinEntrada mouse, java.util.List<Cliente.Estado> states) throws Exception {
        return client(port,mouse,states,x->{});
    }
    static Cliente client(int port, MouseSinEntrada mouse, java.util.List<Cliente.Estado> states, Consumer<String> logger) throws Exception {
        Cliente c=new Cliente("127.0.0.1",port,true,new GeometriaPantalla(1366,768),logger,states::add);
        set(c,"mouseHandler",mouse);
        set(c,"keyboardHandler",new KeyboardHandler(x->{},()->{}));
        set(c,"habilitado",true);
        return c;
    }
    static class Peer implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0);
        volatile Socket active;
        volatile String mode="PONG";
        final AtomicInteger accepts=new AtomicInteger();
        final java.util.List<java.util.List<String>> received=new CopyOnWriteArrayList<>();
        Peer() throws Exception {
            Thread t=new Thread(()->{
                while(!listener.isClosed()) try {
                    Socket s=listener.accept();active=s;
                    java.util.List<String> messages=new CopyOnWriteArrayList<>();received.add(messages);accepts.incrementAndGet();
                    try(s) {
                        BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));
                        PrintWriter w=new PrintWriter(s.getOutputStream(),true);
                        String line;
                        while((line=r.readLine())!=null) {
                            messages.add(line);
                            if(mode.equals("REGRESAR")) {w.println("REGRESAR");break;}
                            if(mode.equals("EOF")) break;
                            if(line.equals("PING") && mode.equals("PONG")) w.println("PONG");
                        }
                    }
                } catch(IOException ignored) {}
            },"fake-peer");t.setDaemon(true);t.start();
        }
        public void close() throws Exception {listener.close();if(active!=null)active.close();}
    }
    static class RobotSimulado extends Robot {
        final Set<Integer> keys=ConcurrentHashMap.newKeySet(), buttons=ConcurrentHashMap.newKeySet();
        RobotSimulado() throws Exception {}
        @Override public void keyPress(int k) {keys.add(k);}
        @Override public void keyRelease(int k) {keys.remove(k);}
        @Override public void mousePress(int b) {buttons.add(b);}
        @Override public void mouseRelease(int b) {buttons.remove(b);}
        @Override public void mouseMove(int x,int y) {}
        @Override public void mouseWheel(int x) {}
    }
    static class ServidorSimulado extends Servidor {
        volatile Point posicion = new Point(100,100);
        ServidorSimulado() {this(x->{});}
        ServidorSimulado(Consumer<String> logger) {super(0,true,logger,x->{});}
        @Override protected Point posicionActual() {return new Point(posicion);}
    }
    static NativeMouseEvent move(int x) {return new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_MOVED,0,x,200,0);}
    static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted=false;
        while(true) {
            try {latch.await();break;} catch(InterruptedException e) {interrupted=true;}
        }
        if(interrupted) Thread.currentThread().interrupt();
    }
    static int port(Servidor server) throws Exception {return ((ServerSocket)get(server,"serverSocket")).getLocalPort();}
    static ExecutorService installInput(Cliente cliente) throws Exception {
        Class<?> tipo=Class.forName("Absolute_Control.core.Cliente$InputDispatcher");
        Constructor<?> constructor=tipo.getDeclaredConstructor();constructor.setAccessible(true);
        ExecutorService input=(ExecutorService)constructor.newInstance();set(cliente,"dispatcher",input);
        Method actual=tipo.getDeclaredMethod("esActual");actual.setAccessible(true);
        BooleanSupplier valid=()->{
            try {return (boolean)actual.invoke(input);} catch(Exception e) {throw new RuntimeException(e);}
        };
        ((KeyboardHandler)get(cliente,"keyboardHandler")).setEventoActual(valid);
        ((MouseHandler)get(cliente,"mouseHandler")).setEventoActual(valid);
        return input;
    }
    static void checkDelayedClientAndSingleClose() throws Exception {
        try(Peer peer=new Peer()) {
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
            AtomicInteger connections=new AtomicInteger(), readersClosed=new AtomicInteger(),writersClosed=new AtomicInteger();
            Cliente c=client(peer.listener.getLocalPort(),new MouseSinEntrada(),new CopyOnWriteArrayList<>(),msg->{
                if(msg.startsWith("Conectado a") && connections.incrementAndGet()==1) {
                    started.countDown();awaitUninterruptibly(release);
                }
            });
            Object old;
            try {
                connect(c);check(started.await(2000,TimeUnit.MILLISECONDS),"Conexion A no inicio");old=get(c,"sesion");
                BufferedReader reader=(BufferedReader)get(old,"entrada");
                PrintWriter writer=(PrintWriter)get(old,"salida");
                set(old,"entrada",new BufferedReader(reader) {
                    @Override public void close() throws IOException {readersClosed.incrementAndGet();super.close();}
                });
                set(old,"salida",new PrintWriter(writer,true) {
                    @Override public void close() {writersClosed.incrementAndGet();super.close();}
                });
                c.desconectar();connect(c);
                Thread.sleep(200);
                check(peer.accepts.get()==1 && !c.isConectado(),"B se conecto durante la limpieza de A");
                check(((Thread)get(old,"limpieza")).isAlive(),"A perdio el propietario de limpieza");
            } finally {release.countDown();}
            waitFor(c::isConectado,3000);waitFor(()->peer.accepts.get()==2,2000);awaitClean(old);
            check(readersClosed.get()==1 && writersClosed.get()==1,"Streams cerrados mas de una vez");
            stop(c);
        }
        System.out.println("PASS cliente espera limpieza anterior y cierra cada stream una sola vez");
    }
    static void checkRunningOldInput() throws Exception {
        try(Peer peer=new Peer()) {
            Cliente c=client(peer.listener.getLocalPort(),new MouseSinEntrada(),new CopyOnWriteArrayList<>());
            ExecutorService input=installInput(c);
            KeyboardHandler keyboard=(KeyboardHandler)get(c,"keyboardHandler");
            MouseHandler mouse=(MouseHandler)get(c,"mouseHandler");
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
            connect(c);waitFor(c::isConectado,2000);
            input.execute(()->{
                started.countDown();awaitUninterruptibly(release);
                keyboard.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,160,NativeKeyEvent.VC_SHIFT,NativeKeyEvent.CHAR_UNDEFINED));
                mouse.nativeMousePressed(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_PRESSED,0,100,100,1,NativeMouseEvent.BUTTON1));
                keyboard.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,27,NativeKeyEvent.VC_ESCAPE,NativeKeyEvent.CHAR_UNDEFINED));
                try {
                    // Incluso un emisor de B obtenido por el callback iniciado en A debe rechazarlo.
                    @SuppressWarnings("unchecked") Consumer<String> senderB=(Consumer<String>)get(keyboard,"onEnviar");
                    senderB.accept("K,PRESIONAR,42");
                    ((Runnable)get(keyboard,"onDesconectar")).run();
                } catch(Exception e) {throw new RuntimeException(e);}
            });
            try {
                check(started.await(2000,TimeUnit.MILLISECONDS),"Callback de A no inicio");
                c.desconectar();connect(c);waitFor(c::isConectado,2000);waitFor(()->peer.accepts.get()==2,2000);
            } finally {release.countDown();}
            input.submit(()->{}).get(2000,TimeUnit.MILLISECONDS);
            check(c.isConectado(),"Escape del callback antiguo cerro B");
            check(!(boolean)get(keyboard,"shiftActivo"),"Callback antiguo altero modificadores de B");
            input.submit(()->keyboard.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,37,NativeKeyEvent.VC_LEFT,NativeKeyEvent.CHAR_UNDEFINED))).get(2000,TimeUnit.MILLISECONDS);
            waitFor(()->peer.received.get(1).stream().anyMatch(msg->msg.startsWith("K,")),1000);
            check(peer.received.get(1).stream().filter(msg->msg.startsWith("K,")||msg.startsWith("C,")).count()==1,"Entrada antigua enviada a B");
            stop(c);
        }
        System.out.println("PASS callback ya iniciado en A: no altera, envia ni desconecta B");
    }
    static void checkDelayedServerStop() throws Exception {
        ServidorSimulado server=new ServidorSimulado();server.iniciar();
        CountDownLatch pressed=new CountDownLatch(1),release=new CountDownLatch(1);
        RobotSimulado robot=new RobotSimulado() {
            @Override public void mousePress(int button) {super.mousePress(button);pressed.countDown();awaitUninterruptibly(release);}
        };
        set(server,"robot",robot);
        Object old;
        try(Socket socket=new Socket("127.0.0.1",port(server))) {
            PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
            out.println("K,PRESIONAR,29");out.println("C,PRESIONAR,1");
            try {
                check(pressed.await(2000,TimeUnit.MILLISECONDS),"Entrada del servidor no quedo bloqueada");old=get(server,"sesion");
                long start=System.nanoTime();
                javax.swing.SwingUtilities.invokeAndWait(server::detener);
                check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000,"Parada bloqueo Swing");
                check(!server.esperarDetenido(),"Parada declaro terminado lector bloqueado");
                check(((CountDownLatch)get(old,"finLimpieza")).getCount()==1,"Timeout abandono limpieza del servidor");
                try {server.iniciar();throw new AssertionError("Reinicio durante limpieza activa");}
                catch(IllegalStateException expected) {}
            } finally {release.countDown();}
            awaitClean(old);check(server.esperarDetenido(),"Parada tardia no se revalido");
            check(robot.keys.isEmpty() && robot.buttons.isEmpty(),"Parada tardia dejo entradas presionadas");
        }
        server.iniciar();stop(server);
        System.out.println("PASS servidor: parada desde Swing, timeout, liberacion tardia, revalidacion y reinicio");
    }
    static void checkDelayedReleaseAndNextSession() throws Exception {
        ServidorSimulado server=new ServidorSimulado();server.iniciar();
        AtomicBoolean allowRelease=new AtomicBoolean();CountDownLatch attempted=new CountDownLatch(1);
        RobotSimulado robot=new RobotSimulado() {
            @Override public void keyRelease(int key) {
                if(!allowRelease.get()) {attempted.countDown();throw new IllegalStateException("Fallo transitorio simulado");}
                super.keyRelease(key);
            }
            @Override public void mouseRelease(int button) {
                if(!allowRelease.get()) throw new IllegalStateException("Fallo transitorio simulado");
                super.mouseRelease(button);
            }
        };
        set(server,"robot",robot);int port=port(server);
        Object old;
        try(Socket first=new Socket("127.0.0.1",port)) {
            first.setSoTimeout(2000);PrintWriter out=new PrintWriter(first.getOutputStream(),true);
            BufferedReader in=new BufferedReader(new InputStreamReader(first.getInputStream()));
            out.println("K,PRESIONAR,29");out.println("C,PRESIONAR,1");out.println("PING");
            check("PONG".equals(in.readLine()),"Preparacion de A fallo");old=get(server,"sesion");
        }
        try {
            check(attempted.await(2000,TimeUnit.MILLISECONDS),"No se intento liberar A");
            try(Socket next=new Socket("127.0.0.1",port)) {
                next.setSoTimeout(200);PrintWriter out=new PrintWriter(next.getOutputStream(),true);
                BufferedReader in=new BufferedReader(new InputStreamReader(next.getInputStream()));out.println("PING");
                try {in.readLine();throw new AssertionError("B fue atendida durante limpieza de A");}
                catch(SocketTimeoutException expected) {}
                check(get(server,"sesion")==old && ((Thread)get(old,"limpieza")).isAlive(),"Servidor sustituyo A antes de completar limpieza");
                check(!robot.keys.isEmpty() && !robot.buttons.isEmpty(),"Fallo de liberacion borro el registro pendiente");
                allowRelease.set(true);next.setSoTimeout(3000);
                check("PONG".equals(in.readLine()),"Servidor no admitio B despues de limpieza tardia");awaitClean(old);
                check(robot.keys.isEmpty() && robot.buttons.isEmpty(),"Entradas de A no se liberaron");
                out.println("K,PRESIONAR,29");out.println("C,PRESIONAR,1");out.println("PING");check("PONG".equals(in.readLine()),"Preparacion B fallo");
                check(get(server,"sesion")!=old && !robot.keys.isEmpty() && !robot.buttons.isEmpty(),"Limpieza A afecto entradas de B");
                stop(server);check(robot.keys.isEmpty() && robot.buttons.isEmpty(),"B retuvo entradas al detener");
            }
        } finally {allowRelease.set(true);server.detener();server.esperarDetenido();}
        System.out.println("PASS liberacion reintenta fallos transitorios; B espera fin real de A y conserva sus entradas");
    }
    static void checkDelayedDiscoveryAndBorder() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        ServidorSimulado server=new ServidorSimulado(msg->{
            if(msg.startsWith("Discovery UDP escuchando")) {started.countDown();awaitUninterruptibly(release);}
        });
        server.iniciar();
        try {
            check(started.await(2000,TimeUnit.MILLISECONDS),"Discovery no inicio");server.detener();
            check(!server.esperarDetenido(),"Parada ignoro Discovery aun activo");
        } finally {release.countDown();}
        check(server.esperarDetenido(),"Discovery tardio no completo parada");
        server.iniciar();set(server,"robot",new RobotSimulado());
        try(Socket socket=new Socket("127.0.0.1",port(server))) {
            socket.setSoTimeout(2000);PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("PING");check("PONG".equals(in.readLine()),"Sesion para borde no inicio");
            server.posicion=new Point(0,100);
            check("REGRESAR".equals(in.readLine()),"Borde de regreso cambio");check(in.readLine()==null,"Borde no cerro TCP");
        }
        stop(server);
        System.out.println("PASS Discovery tardio termina y permite reinicio; REGRESAR conserva el borde existente");
    }
    static void checkStreamFailureAndPartialPress() throws Exception {
        try(Peer peer=new Peer()) {
            Cliente c=client(peer.listener.getLocalPort(),new MouseSinEntrada(),new CopyOnWriteArrayList<>());
            connect(c);waitFor(c::isConectado,2000);Object old=get(c,"sesion");
            AtomicBoolean allowClose=new AtomicBoolean();AtomicInteger closed=new AtomicInteger();
            BufferedReader reader=(BufferedReader)get(old,"entrada");
            set(old,"entrada",new BufferedReader(reader) {
                @Override public void close() throws IOException {
                    if(!allowClose.get()) throw new IOException("Cierre transitorio simulado");
                    closed.incrementAndGet();super.close();
                }
            });
            try {
                c.detenerHandlers();check(!c.esperarDetenido(),"Stream pendiente declarado cerrado");
                check(((CountDownLatch)get(old,"finLimpieza")).getCount()==1,"Stream pendiente bajo el latch");
            } finally {allowClose.set(true);}
            check(c.esperarDetenido(),"Cierre de stream tardio no se completo");awaitClean(old);
            check(closed.get()==1,"Hubo mas de un cierre exitoso del stream");
        }
        for(boolean mouse : new boolean[]{false,true}) {
            ServidorSimulado server=new ServidorSimulado();server.iniciar();
            RobotSimulado robot=new RobotSimulado() {
                @Override public void keyPress(int key) {super.keyPress(key);throw new IllegalStateException("Presion parcial simulada");}
                @Override public void mousePress(int button) {super.mousePress(button);throw new IllegalStateException("Presion parcial simulada");}
            };
            set(server,"robot",robot);
            try(Socket socket=new Socket("127.0.0.1",port(server))) {
                socket.setSoTimeout(2000);PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
                out.println(mouse ? "C,PRESIONAR,1" : "K,PRESIONAR,29");
                check(socket.getInputStream().read()==-1,"Presion fallida no cerro sesion");
                stop(server);check(robot.keys.isEmpty() && robot.buttons.isEmpty(),"Presion parcial quedo retenida");
            }
        }
        System.out.println("PASS stream con fallo transitorio completa cierre; presion parcial libera teclado y mouse");
    }
    static void checkConcurrentCloseStress() throws Exception {
        try(Peer peer=new Peer()) {
            for(int i=0;i<30;i++) {
                MouseSinEntrada mouse=new MouseSinEntrada();
                Cliente c=client(peer.listener.getLocalPort(),mouse,new CopyOnWriteArrayList<>());
                connect(c);waitFor(c::isConectado,2000);Object old=get(c,"sesion");
                int expected=i+1;waitFor(()->peer.accepts.get()==expected,2000);
                Thread[] callers={new Thread(c::desconectar),new Thread(c::detenerHandlers),new Thread(c::desconectar)};
                for(Thread caller:callers) caller.start();
                for(Thread caller:callers) {caller.join(2000);check(!caller.isAlive(),"Solicitante de cierre bloqueado");}
                check(!c.isConectado() && !mouse.remoto && !(boolean)get(c,"pendiente"),"Cierre concurrente dejo estado activo");
                check(c.esperarDetenido(),"Cierre concurrente sin terminar en ciclo " + i);awaitClean(old);
                Thread cleaner=(Thread)get(old,"limpieza");c.detenerHandlers();c.desconectar();
                check(get(old,"limpieza")==cleaner,"Cierre repetido creo otro propietario");
                check(((Socket)get(old,"socket")).isClosed(),"Cierre concurrente retuvo TCP");
            }
        }
        System.out.println("PASS 30 ciclos de cierre concurrente y repetido: un propietario, sockets cerrados y estado local");
    }
    public static void main(String[] args) throws Exception {
        // El conector se demora mas que el join: el timeout del observador no debe abandonar la limpieza.
        try(Peer peer=new Peer()) {
            CountDownLatch conectado=new CountDownLatch(1), liberar=new CountDownLatch(1), vencio=new CountDownLatch(1);
            Cliente c=client(peer.listener.getLocalPort(),new MouseSinEntrada(),new CopyOnWriteArrayList<>(),msg->{
                if(msg.startsWith("Conectado a")) {
                    conectado.countDown();
                    try { while(!liberar.await(100,TimeUnit.MILLISECONDS)) {} }
                    catch(InterruptedException e) {
                        // Simular una operacion que necesita finalizar aun despues de interrupt.
                        boolean listo=false;
                        while(!listo) try {liberar.await();listo=true;} catch(InterruptedException ignored) {}
                    }
                }
                if(msg.contains("kvm-conectar") && msg.contains("timeout")) vencio.countDown();
            });
            connect(c);check(conectado.await(2000,TimeUnit.MILLISECONDS),"Conector no llego al bloqueo controlado");
            Thread a=new Thread(c::desconectar),b=new Thread(c::detenerHandlers);a.start();b.start();a.join();b.join();
            try {
                check(!c.esperarDetenido(),"La espera declaro terminado un conector bloqueado");
                check(vencio.await(3000,TimeUnit.MILLISECONDS),"No se observo el timeout del conector");
                Object sesion=((Set<?>)get(c,"limpiando")).iterator().next();
                check(((CountDownLatch)get(sesion,"finLimpieza")).getCount()==1,"El timeout bajo el latch de limpieza");
                check(((Thread)get(sesion,"limpieza")).isAlive(),"El propietario abandono la limpieza");
            } finally {liberar.countDown();}
            boolean terminado=c.esperarDetenido();
            if(!terminado) diagnoseClient(c);
            check(terminado,"Cierre concurrente sin terminar");
            System.out.println("PASS cierre concurrente tardio: timeout seguido de limpieza y revalidacion");
        }
        try(Peer peer=new Peer()) {
            MouseSinEntrada mouse=new MouseSinEntrada();
            java.util.List<Cliente.Estado> states=new CopyOnWriteArrayList<>();
            Cliente c=client(peer.listener.getLocalPort(),mouse,states);
            java.util.List<Thread> attempts=new ArrayList<>();
            for(int i=0;i<30;i++) {Thread t=new Thread(()->{try{connect(c);}catch(Exception e){throw new RuntimeException(e);}}); attempts.add(t);t.start();}
            for(Thread t:attempts)t.join();
            waitFor(c::isConectado,2000);Thread.sleep(1300);
            check(peer.accepts.get()==1 && mouse.remoto,"Intentos duplicados o heartbeat sano fallo");
            System.out.println("PASS conexion concurrente unica y heartbeat sano");
            c.desconectar();waitFor(()->!c.isConectado()&&!mouse.remoto,500);
            connect(c);waitFor(c::isConectado,2000);peer.mode="EOF";
            waitFor(()->!c.isConectado()&&!mouse.remoto,2500);
            System.out.println("PASS cierre normal, nueva conexion y EOF recuperan local");
            peer.mode="PONG";connect(c);waitFor(c::isConectado,2000);
            peer.active.setSoLinger(true,0);peer.active.close();
            waitFor(()->!c.isConectado()&&!mouse.remoto,1500);
            System.out.println("PASS reset TCP recupera local");
            peer.mode="REGRESAR";connect(c);waitFor(()->states.get(states.size()-1)==Cliente.Estado.LOCAL,2500);
            check(!mouse.remoto,"REGRESAR dejo remoto");
            System.out.println("PASS REGRESAR recupera local");
            peer.mode="SILENCIO";long start=System.nanoTime();connect(c);waitFor(c::isConectado,2000);
            waitFor(()->!c.isConectado()&&!mouse.remoto,4500);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            check(elapsed<4100,"Heartbeat demasiado lento: "+elapsed);
            System.out.println("PASS heartbeat silencioso recupera local en "+elapsed+" ms");
            peer.mode="PONG";connect(c);waitFor(c::isConectado,2000);
            Thread a=new Thread(c::desconectar),b=new Thread(c::detenerHandlers);a.start();b.start();a.join();b.join();
            check(!c.isConectado()&&!mouse.remoto,"Cierre concurrente fallo");
            int n=peer.accepts.get();connect(c);Thread.sleep(100);check(peer.accepts.get()==n,"Cliente detenido reconecto");
            boolean terminado=c.esperarDetenido();
            if(!terminado) diagnoseClient(c);
            check(terminado,"Cierre concurrente sin terminar");
            System.out.println("PASS cierre idempotente concurrente y cliente detenido");
        }
        try(Peer peer=new Peer()) {
            MouseSinEntrada mouse=new MouseSinEntrada();
            Cliente c=client(peer.listener.getLocalPort(),mouse,new CopyOnWriteArrayList<>());
            for(int i=0;i<20;i++) {
                peer.mode="PONG";connect(c);waitFor(c::isConectado,2000);
                c.desconectar();check(!mouse.remoto,"Repeticion retuvo remoto");
            }
            stop(c);
            System.out.println("PASS 20 conexiones/desconexiones consecutivas");
        }
        try(Peer peer=new Peer()) {
            MouseSinEntrada mouse=new MouseSinEntrada();
            Cliente c=client(peer.listener.getLocalPort(),mouse,new CopyOnWriteArrayList<>());
            connect(c);stop(c);Thread.sleep(200);
            check(!c.isConectado()&&!mouse.remoto,"Stop pendiente activo sesion tardia");
            System.out.println("PASS detener durante conexion pendiente");
        }
        // A prepara eventos reales de handlers. Se pausa ANTES de enviar; B se abre inmediatamente.
        try(Peer peer=new Peer()) {
            MouseSinEntrada mouse=new MouseSinEntrada();Cliente c=client(peer.listener.getLocalPort(),mouse,new CopyOnWriteArrayList<>());
            connect(c);waitFor(c::isConectado,2000);
            KeyboardHandler keyboard=(KeyboardHandler)get(c,"keyboardHandler");
            @SuppressWarnings("unchecked") Consumer<String> senderA=(Consumer<String>)get(keyboard,"onEnviar");
            @SuppressWarnings("unchecked") Consumer<String> mouseA=(Consumer<String>)get(mouse,"onEnviar");
            CountDownLatch prepared=new CountDownLatch(2), letSend=new CountDownLatch(1);
            set(keyboard,"onEnviar",(Consumer<String>)msg->{prepared.countDown();try{letSend.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}senderA.accept(msg);});
            set(mouse,"onEnviar",(Consumer<String>)msg->{prepared.countDown();try{letSend.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}mouseA.accept(msg);});
            Thread keyA=new Thread(()->keyboard.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,162,NativeKeyEvent.VC_CONTROL,NativeKeyEvent.CHAR_UNDEFINED)));
            Thread clickA=new Thread(()->mouse.nativeMousePressed(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_PRESSED,0,100,100,1,NativeMouseEvent.BUTTON1)));
            keyA.start();clickA.start();check(prepared.await(2000,TimeUnit.MILLISECONDS),"Eventos A no preparados");
            Object sessionA=get(c,"sesion");c.desconectar();connect(c);waitFor(c::isConectado,2000);waitFor(()->peer.accepts.get()==2,2000);
            letSend.countDown();keyA.join(1000);clickA.join(1000);
            // Escape atrasado de A tampoco puede cerrar B.
            Method close=Cliente.class.getDeclaredMethod("cerrarSesion",sessionA.getClass(),Cliente.Estado.class,String.class);close.setAccessible(true);
            close.invoke(c,sessionA,Cliente.Estado.LOCAL,"Escape antiguo");
            keyboard.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,37,NativeKeyEvent.VC_LEFT,NativeKeyEvent.CHAR_UNDEFINED));
            waitFor(()->peer.received.get(1).stream().anyMatch(msg->msg.startsWith("K,")),1000);
            // El evento nuevo tiene otro codigo de control; los de A no se transmiten.
            long keyCount=peer.received.get(1).stream().filter(msg->msg.startsWith("K,")).count();
            check(keyCount==1 && peer.received.get(1).stream().noneMatch(msg->msg.startsWith("C,")),"Eventos A contaminaron B: "+peer.received.get(1));
            check(c.isConectado(),"Escape antiguo cerro B");awaitClean(sessionA);stop(c);
            System.out.println("PASS A -> B inmediato: teclado/clic pausados y cierre antiguo descartados; B sigue activa");
        }
        int free;try(ServerSocket s=new ServerSocket(0)){free=s.getLocalPort();}
        Cliente refused=client(free,new MouseSinEntrada(),new CopyOnWriteArrayList<>());
        connect(refused);Thread.sleep(300);check(!refused.isConectado(),"Conexion rechazada quedo remota");stop(refused);
        System.out.println("PASS conexion rechazada permanece local");
        AtomicInteger crossing=new AtomicInteger();
        class MouseLocal extends MouseHandler {
            Point actual=new Point();
            MouseLocal() {super(x->{},crossing::incrementAndGet,()->{},new GeometriaPantalla(1366,768),null);}
            @Override protected Point posicionActual(){return actual;}
            void moveTo(int x, int y) {actual=new Point(x,y);nativeMouseMoved(new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_MOVED,0,x,y,0));}
        }
        MouseLocal m=new MouseLocal();m.setLado(true);
        AtomicLong clock=new AtomicLong(System.nanoTime());m.setInstanteEvento(clock::get);
        m.moveTo(1365,200);m.moveTo(1365,200);check(crossing.get()==1,"Borde repetido");
        m.setControlando(false);
        long from=(long)get(m,"aceptarDesde");
        clock.set(from-1);m.moveTo(1200,200);m.moveTo(1365,200);check(crossing.get()==1,"Residual reconecto");
        clock.set(from+1);m.moveTo(683,384);clock.addAndGet(150000000);m.moveTo(1365,200);
        check(crossing.get()==1,"Warp propio rearmo borde");
        m.actual=new Point(1365,200);m.nativeMouseMoved(move(1200));clock.addAndGet(150000000);m.moveTo(1365,200);
        check(crossing.get()==1,"Evento atrasado rearmo borde");
        m.moveTo(1200,200);clock.addAndGet(50000000);m.moveTo(1365,200);check(crossing.get()==1,"Cruce sin permanencia");
        m.moveTo(1200,200);clock.addAndGet(130000000);m.moveTo(1365,200);check(crossing.get()==2,"Nuevo cruce ignorado");
        System.out.println("PASS rearme: descarta enfriamiento, warp, posicion atrasada y exige nuevo cruce");
        Class<?> inputType=Class.forName("Absolute_Control.core.Cliente$InputDispatcher");
        Constructor<?> inputCtor=inputType.getDeclaredConstructor();inputCtor.setAccessible(true);
        ExecutorService input=(ExecutorService)inputCtor.newInstance();
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger oldEvents=new AtomicInteger(),newEvents=new AtomicInteger();
        input.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        check(started.await(1000,TimeUnit.MILLISECONDS),"Input worker no inicio");
        input.execute(oldEvents::incrementAndGet);
        Method invalidate=inputType.getDeclaredMethod("invalidar");invalidate.setAccessible(true);invalidate.invoke(input);
        input.execute(newEvents::incrementAndGet);release.countDown();
        waitFor(()->newEvents.get()==1,1000);check(oldEvents.get()==0,"Cola anterior sobrevivio");
        input.shutdownNow();check(input.awaitTermination(1000,TimeUnit.MILLISECONDS),"Input worker sigue activo");
        System.out.println("PASS generacion de entrada descarta cola de sesion anterior");
        AtomicInteger escapes=new AtomicInteger();KeyboardHandler k=new KeyboardHandler(x->{},escapes::incrementAndGet);k.setControlando(true);
        k.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,0,27,NativeKeyEvent.VC_ESCAPE,NativeKeyEvent.CHAR_UNDEFINED));
        check(escapes.get()==1,"Escape fallo");k.setControlando(false);
        System.out.println("PASS Escape usa callback central");
        Discovery d=new Discovery();for(int i=0;i<20;i++){d.iniciarResponder(12345,x->{});d.detenerResponder();}
        try(DatagramSocket udp=new DatagramSocket(8079)){}
        System.out.println("PASS Discovery inicio/parada inmediato 20 ciclos libera puerto");
        Servidor server=new ServidorSimulado();
        for(int i=0;i<10;i++) {
            server.iniciar();RobotSimulado robot=new RobotSimulado();set(server,"robot",robot);
            Field sf=Servidor.class.getDeclaredField("serverSocket");sf.setAccessible(true);int port=((ServerSocket)sf.get(server)).getLocalPort();
            Socket socket=new Socket("127.0.0.1",port);socket.setSoTimeout(2000);
            PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("PING");String respuesta=in.readLine();check("PONG".equals(respuesta),"PONG servidor fallo: " + respuesta);
            out.println("K,PRESIONAR,29");out.println("K,PRESIONAR,42");out.println("K,PRESIONAR,56");out.println("C,PRESIONAR,1");out.println("PING");check("PONG".equals(in.readLine()),"Entradas no procesadas");
            check(robot.keys.contains(KeyEvent.VK_CONTROL)&&robot.keys.contains(KeyEvent.VK_SHIFT)&&robot.keys.contains(KeyEvent.VK_ALT)&&!robot.buttons.isEmpty(),"Registro presiones fallo");
            if(i%2==0) {out.println("LIBERAR");waitFor(()->robot.keys.isEmpty()&&robot.buttons.isEmpty(),2000);}
            stop(server);check(robot.keys.isEmpty()&&robot.buttons.isEmpty(),"Entradas retenidas");
            check(in.readLine()==null,"Socket activo no cerrado");socket.close();
            try(ServerSocket reusable=new ServerSocket(port)){}
            try(DatagramSocket udp=new DatagramSocket(8079)){}
        }
        server.iniciar();RobotSimulado robot=new RobotSimulado();set(server,"robot",robot);
        Field listenField=Servidor.class.getDeclaredField("serverSocket");listenField.setAccessible(true);
        int testPort=((ServerSocket)listenField.get(server)).getLocalPort();
        try(Socket socket=new Socket("127.0.0.1",testPort)) {
            socket.setSoTimeout(6000);
            PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("K,PRESIONAR,29");out.println("PING");check("PONG".equals(in.readLine()),"idle preparacion");
            String respuesta=in.readLine();check(respuesta==null,"Sesion silenciosa no expiro: " + respuesta);
            waitFor(()->robot.keys.isEmpty(),1000);
        }
        try(Socket socket=new Socket("127.0.0.1",testPort)) {
            socket.setSoTimeout(2000);
            PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("K,PRESIONAR,42");out.println("D,invalido,0");
            check(in.readLine()==null,"Mensaje invalido no cerro sesion");
            waitFor(()->robot.keys.isEmpty(),1000);
        }
        stop(server);
        System.out.println("PASS servidor libera entradas ante timeout y mensaje invalido");
        System.out.println("PASS servidor PING/PONG, LIBERAR, stop activo, liberacion Ctrl/Shift/clic y 10 reinicios");
        checkDelayedClientAndSingleClose();
        checkRunningOldInput();
        checkDelayedServerStop();
        checkDelayedReleaseAndNextSession();
        checkDelayedDiscoveryAndBorder();
        checkStreamFailureAndPartialPress();
        checkConcurrentCloseStress();
        Thread.sleep(300);
        java.util.List<String> alive=Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().startsWith("kvm-")).map(Thread::getName).toList();
        check(alive.isEmpty(),"Hilos sobrevivientes: "+alive);
        System.out.println("PASS sin hilos kvm sobrevivientes");
    }
}
