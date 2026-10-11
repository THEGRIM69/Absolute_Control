import Absolute_Control.equipos.EquipoGuardado;
import Absolute_Control.equipos.RepositorioEquiposJson;
import Absolute_Control.equipos.ServicioEquipos;
import Absolute_Control.historial.*;
import Absolute_Control.ui.EventsPanel;
import Absolute_Control.ui.NavigationPanel;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HistoryPersistenceCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("AbsoluteControl-history-");
        Path file = root.resolve("history/historial.json");
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        HistorialAsync async = new HistorialAsync(
                () -> new ServicioHistorial(new RepositorioHistorialJson(file)), errors::add);
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T22:00:00Z"), ZoneOffset.UTC);
        RegistroSesiones sessions = new RegistroSesiones(async::registrar, clock);

        List<EventoHistorial> manualEvents = new ArrayList<>();
        RegistroSesiones manualSession = new RegistroSesiones(manualEvents::add, clock);
        manualSession.prepararDestino("CONEXIÓN MANUAL", "127.0.0.1", 8080);
        manualSession.conectando();
        manualSession.conexionConfirmada();
        manualSession.regresoLocal();
        check(manualEvents.size() == 2 && manualEvents.stream().allMatch(
                        event -> event.getNombreEquipo().equals("CONEXIÓN MANUAL")),
                "conexión del dashboard usa identificación histórica manual");

        async.esperarPendientes(Duration.ofSeconds(2));
        check(new ServicioHistorial(new RepositorioHistorialJson(file)).listar().isEmpty(),
                "historial inicialmente vacío y archivo inexistente");

        sessions.prepararDestino("Laptop", "192.168.1.198", 8080);
        async.esperarPendientes(Duration.ofSeconds(2));
        check(new ServicioHistorial(new RepositorioHistorialJson(file)).listar().isEmpty(),
                "pulsar CONECTAR/preparar destino no registra éxito");

        sessions.conectando();
        sessions.conexionConfirmada();
        sessions.conexionConfirmada();
        sessions.regresoLocal();
        sessions.regresoLocal();
        sessions.conectando();
        sessions.error();
        check(async.esperarPendientes(Duration.ofSeconds(3)), "cola histórica procesa eventos");
        List<EventoHistorial> realEvents =
                new ServicioHistorial(new RepositorioHistorialJson(file)).listar();
        check(realEvents.size() == 3, "sin eventos duplicados por callbacks repetidos");
        check(count(realEvents, EventoHistorial.Tipo.CONEXION_ESTABLECIDA) == 1,
                "conexión TCP confirmada registrada una vez");
        check(count(realEvents, EventoHistorial.Tipo.CONEXION_FALLIDA) == 1,
                "fallo real de conexión registrado");
        check(count(realEvents, EventoHistorial.Tipo.SESION_FINALIZADA) == 1,
                "regreso/cierre de sesión registrado");

        Path equipmentFile = root.resolve("equipment/equipos.json");
        ServicioEquipos equipmentService = new ServicioEquipos(
                new RepositorioEquiposJson(equipmentFile), clock);
        EquipoGuardado equipment = equipmentService.guardar("Nombre histórico", "10.0.0.20", 9000);
        sessions.prepararDestino(equipment.getNombre(), equipment.getIp(), equipment.getPuerto());
        sessions.conectando();
        sessions.conexionConfirmada();
        equipmentService.editar(equipment.getId(), "Nombre nuevo", "10.0.0.21", 9001);
        equipmentService.eliminar(equipment.getId());
        sessions.regresoLocal();
        async.esperarPendientes(Duration.ofSeconds(3));
        List<EventoHistorial> afterDelete =
                new ServicioHistorial(new RepositorioHistorialJson(file)).listar();
        check(afterDelete.stream().filter(event -> event.getNombreEquipo().equals("Nombre histórico"))
                        .count() == 2,
                "nombre histórico sobrevive renombrado y eliminación del equipo");

        ServicioHistorial restarted = new ServicioHistorial(new RepositorioHistorialJson(file));
        check(restarted.listar().size() == 5, "historial persiste entre reinicios");
        check(isDescending(restarted.listar()), "presentación ordenada por fecha descendente");

        MemoryRepository memory = new MemoryRepository();
        ServicioHistorial limited = new ServicioHistorial(memory);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 505; i++) {
            limited.registrar(event(start.plusSeconds(i), "Equipo " + i));
        }
        check(limited.listar().size() == 500
                        && limited.listar().get(0).getFecha().equals(start.plusSeconds(504))
                        && limited.listar().get(499).getFecha().equals(start.plusSeconds(5)),
                "límite conserva exactamente los 500 eventos más recientes");

        Path corrupt = root.resolve("corrupt/historial.json");
        Files.createDirectories(corrupt.getParent());
        String broken = "{ historial roto";
        Files.writeString(corrupt, broken, StandardCharsets.UTF_8);
        expectHistoryError(() -> new ServicioHistorial(new RepositorioHistorialJson(corrupt)),
                "JSON corrupto se rechaza");
        check(Files.readString(corrupt, StandardCharsets.UTF_8).equals(broken),
                "JSON corrupto permanece intacto");

        List<Throwable> writeErrors = new CopyOnWriteArrayList<>();
        HistorialAsync failing = new HistorialAsync(
                () -> new ServicioHistorial(new FailingRepository()), writeErrors::add);
        RegistroSesiones failingSessions = new RegistroSesiones(failing::registrar, clock);
        failingSessions.prepararDestino("Inalcanzable", "127.0.0.1", 8080);
        failingSessions.conectando();
        failingSessions.error();
        failing.esperarPendientes(Duration.ofSeconds(2));
        check(!writeErrors.isEmpty(), "error de escritura se reporta sin lanzar al flujo KVM");
        failing.cerrar(Duration.ofSeconds(1));

        TrackingRepository tracking = new TrackingRepository();
        HistorialAsync threaded = new HistorialAsync(
                () -> new ServicioHistorial(tracking), errors::add);
        RegistroSesiones threadedSessions = new RegistroSesiones(threaded::registrar, clock);
        Thread critical = new Thread(() -> {
            threadedSessions.prepararDestino("Crítico", "127.0.0.1", 8080);
            threadedSessions.conectando();
            threadedSessions.conexionConfirmada();
        }, "kvm-test-critical");
        critical.start();
        critical.join();
        SwingUtilities.invokeAndWait(threadedSessions::regresoLocal);
        threaded.esperarPendientes(Duration.ofSeconds(2));
        check(tracking.writeThreads.stream().allMatch("historial-persistencia"::equals),
                "ninguna escritura ocurre en hilo KVM ni Swing EDT");
        check(threaded.cerrar(Duration.ofSeconds(2)),
                "cierre controlado completa tareas pendientes");

        testEventsUi(async);
        check(errors.isEmpty(), "persistencia normal no produjo errores silenciosos");
        check(async.cerrar(Duration.ofSeconds(2)), "servicio principal cierra de forma acotada");
        System.out.println("PASS " + checks + " comprobaciones de historial aislado");
        System.out.println("Directorio temporal: " + root);
    }

    private static void testEventsUi(HistorialAsync async) throws Exception {
        EventsPanel[] panelRef = new EventsPanel[1];
        List<NavigationPanel.Page> pages = new ArrayList<>();
        NavigationPanel[] navigationRef = new NavigationPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panelRef[0] = new EventsPanel(async);
            navigationRef[0] = new NavigationPanel(pages::add);
            buttonByText(navigationRef[0], "INICIO").doClick();
            buttonByText(navigationRef[0], "CONEXIÓN").doClick();
            buttonByText(navigationRef[0], "EVENTOS").doClick();
            panelRef[0].actualizar();
        });
        JLabel status = named(panelRef[0], "events.statusLabel", JLabel.class);
        await(() -> status.getText().endsWith("EVENTO(S)"), "pantalla EVENTOS recarga historial");
        check(pages.equals(List.of(NavigationPanel.Page.INICIO,
                        NavigationPanel.Page.CONEXION, NavigationPanel.Page.EVENTOS)),
                "navegación INICIO ↔ CONEXIÓN ↔ EVENTOS");
    }

    private static long count(List<EventoHistorial> events, EventoHistorial.Tipo type) {
        return events.stream().filter(event -> event.getTipo() == type).count();
    }

    private static boolean isDescending(List<EventoHistorial> events) {
        for (int i = 1; i < events.size(); i++) {
            if (events.get(i - 1).getFecha().isBefore(events.get(i).getFecha())) return false;
        }
        return true;
    }

    private static EventoHistorial event(Instant instant, String name) {
        return new EventoHistorial(UUID.randomUUID(), instant, name, "127.0.0.1", 8080,
                EventoHistorial.Tipo.CONEXION_ESTABLECIDA,
                EventoHistorial.Resultado.EXITO, "Confirmada");
    }

    private static void expectHistoryError(Checked action, String description) throws Exception {
        try { action.run(); throw new AssertionError("FAIL " + description); }
        catch (HistorialException expected) { check(true, description); }
    }

    private static void await(Check condition, String description) throws Exception {
        long limit = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limit) {
            SwingUtilities.invokeAndWait(() -> {});
            if (condition.ok()) { check(true, description); return; }
            Thread.sleep(20);
        }
        throw new AssertionError("FAIL timeout: " + description);
    }

    private static JButton buttonByText(Container root, String text) {
        for (Component component : all(root)) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
        }
        throw new AssertionError("No se encontró botón: " + text);
    }

    private static <T extends Component> T named(Container root, String name, Class<T> type) {
        for (Component component : all(root)) {
            if (type.isInstance(component) && name.equals(component.getName())) return type.cast(component);
        }
        throw new AssertionError("No se encontró componente: " + name);
    }

    private static List<Component> all(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component component : root.getComponents()) {
            result.add(component);
            if (component instanceof Container) result.addAll(all((Container) component));
        }
        return result;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAIL " + description);
        checks++;
        System.out.println("PASS " + description);
    }

    private static class MemoryRepository implements RepositorioHistorial {
        List<EventoHistorial> events = new ArrayList<>();
        @Override public List<EventoHistorial> cargar() throws HistorialException {
            return List.copyOf(events);
        }
        @Override public void guardar(List<EventoHistorial> values) throws HistorialException {
            events = new ArrayList<>(values);
        }
    }

    private static final class FailingRepository extends MemoryRepository {
        @Override public void guardar(List<EventoHistorial> values) throws HistorialException {
            throw new HistorialException("Fallo de escritura simulado");
        }
    }

    private static final class TrackingRepository extends MemoryRepository {
        final List<String> writeThreads = new CopyOnWriteArrayList<>();
        @Override public void guardar(List<EventoHistorial> values) throws HistorialException {
            writeThreads.add(Thread.currentThread().getName());
            super.guardar(values);
        }
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
    @FunctionalInterface private interface Check { boolean ok() throws Exception; }
}
