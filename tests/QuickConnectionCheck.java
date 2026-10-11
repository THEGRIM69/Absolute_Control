import Absolute_Control.equipos.EquipoGuardado;
import Absolute_Control.equipos.RepositorioEquiposJson;
import Absolute_Control.equipos.ServicioEquipos;
import Absolute_Control.ui.ConnectionPanel;
import Absolute_Control.ui.NavigationPanel;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class QuickConnectionCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("AbsoluteControl-ui53a-");
        Path store = root.resolve("equipos.json");
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T21:00:00Z"), ZoneOffset.UTC);
        ServicioEquipos service = new ServicioEquipos(new RepositorioEquiposJson(store), clock);
        EquipoGuardado laptop = service.guardar("Laptop", "192.168.1.198", 8080);
        EquipoGuardado office = service.guardar("PC Oficina", "10.0.0.40", 9000);
        byte[] jsonBeforeConnect = digest(store);

        FakeControl control = new FakeControl();
        ConnectionPanel[] panelRef = new ConnectionPanel[1];
        SwingUtilities.invokeAndWait(() -> panelRef[0] = new ConnectionPanel(
                service, (parent, equipment) -> true, control));
        ConnectionPanel panel = panelRef[0];
        JButton connect = named(panel, "connection.connectButton", JButton.class);
        check(!connect.isEnabled(), "CONECTAR inicia deshabilitado sin selección");

        JButton selectLaptop = named(
                panel, "connection.select." + laptop.getId(), JButton.class);
        SwingUtilities.invokeAndWait(selectLaptop::doClick);
        check(connect.isEnabled(), "seleccionar equipo habilita CONECTAR");
        JLabel selected = named(panel, "connection.selectedLabel", JLabel.class);
        check(selected.getText().contains("Laptop")
                        && selected.getText().contains("192.168.1.198:8080"),
                "selección muestra nombre, IP y puerto");

        SwingUtilities.invokeAndWait(connect::doClick);
        check(control.connectCalls == 1
                        && control.lastEquipment.getId().equals(laptop.getId())
                        && control.lastEquipment.getIp().equals("192.168.1.198")
                        && control.lastEquipment.getPuerto() == 8080,
                "intención usa exactamente IP y puerto guardados");
        check(!connect.isEnabled(), "solicitud en inicio bloquea conexión duplicada");
        SwingUtilities.invokeAndWait(connect::doClick);
        check(control.connectCalls == 1, "segunda pulsación no crea otra solicitud");
        check(MessageDigest.isEqual(jsonBeforeConnect, digest(store))
                        && service.listar().get(0).getNombre().equals("Laptop"),
                "conectar no guarda ni cambia el nombre personalizado");

        SwingUtilities.invokeAndWait(() -> panel.actualizarEstadoConexion(
                ConnectionPanel.EstadoConexionRapida.ACTIVO_LOCAL,
                "MODO REY ACTIVO — LISTO PARA CRUZAR EL BORDE"));
        JButton selectOffice = named(
                panel, "connection.select." + office.getId(), JButton.class);
        SwingUtilities.invokeAndWait(selectOffice::doClick);
        check(connect.getText().equals("DETENER") && connect.isEnabled(),
                "sesión activa ofrece DETENER y no reemplazo");
        SwingUtilities.invokeAndWait(connect::doClick);
        check(control.connectCalls == 1 && control.stopCalls == 1,
                "sesión activa no inicia otro destino");

        SwingUtilities.invokeAndWait(() -> panel.actualizarEstadoConexion(
                ConnectionPanel.EstadoConexionRapida.DISPONIBLE,
                "DISPONIBLE PARA CONECTAR"));
        JButton editOffice = named(panel, "connection.edit." + office.getId(), JButton.class);
        JTextField name = named(panel, "connection.nameField", JTextField.class);
        JTextField ip = named(panel, "connection.ipField", JTextField.class);
        JTextField port = named(panel, "connection.portField", JTextField.class);
        JButton save = named(panel, "connection.saveButton", JButton.class);
        SwingUtilities.invokeAndWait(() -> {
            editOffice.doClick();
            name.setText("Oficina norte");
            ip.setText("10.0.0.41");
            port.setText("9001");
            save.doClick();
        });
        await(() -> save.isEnabled() && selected.getText().contains("Oficina norte")
                        && selected.getText().contains("10.0.0.41:9001"),
                "editar actualiza coherentemente el destino seleccionado");

        EquipoGuardado editedOffice = service.listar().stream()
                .filter(item -> item.getId().equals(office.getId())).findFirst().orElseThrow();
        JButton deleteOffice = named(
                panel, "connection.delete." + editedOffice.getId(), JButton.class);
        SwingUtilities.invokeAndWait(deleteOffice::doClick);
        await(() -> service.listar().size() == 1 && selected.getText().contains("NINGÚN"),
                "eliminar limpia la selección válida");
        check(!connect.isEnabled(), "equipo eliminado no puede conectarse");

        SwingUtilities.invokeAndWait(() -> panel.actualizarEstadoConexion(
                ConnectionPanel.EstadoConexionRapida.ROL_NO_PERMITIDO,
                "DISPONIBLE SÓLO EN ROL REY / CLIENTE"));
        JButton remainingSelect = named(
                panel, "connection.select." + laptop.getId(), JButton.class);
        SwingUtilities.invokeAndWait(remainingSelect::doClick);
        check(!connect.isEnabled(), "rol no permitido bloquea conexión rápida");

        SwingUtilities.invokeAndWait(() -> panel.actualizarEstadoConexion(
                ConnectionPanel.EstadoConexionRapida.ERROR_ACTIVO,
                "DESCONECTADO/ERROR - CONTROL LOCAL; VUELVE AL BORDE"));
        JLabel connectionStatus = named(
                panel, "connection.connectionStatusLabel", JLabel.class);
        check(connectionStatus.getText().contains("ERROR")
                        && connect.isEnabled() && connect.getText().equals("DETENER"),
                "IP no disponible muestra error controlado y permite detener");

        int stopsBeforeNavigation = control.stopCalls;
        CardLayout cards = new CardLayout();
        JPanel host = new JPanel(cards);
        NavigationPanel[] navigationRef = new NavigationPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            host.add(new JPanel(), NavigationPanel.Page.INICIO.name());
            host.add(panel, NavigationPanel.Page.CONEXION.name());
            navigationRef[0] = new NavigationPanel(page -> cards.show(host, page.name()));
            cards.show(host, NavigationPanel.Page.CONEXION.name());
            buttonByText(navigationRef[0], "INICIO").doClick();
            buttonByText(navigationRef[0], "CONEXIÓN").doClick();
        });
        check(control.stopCalls == stopsBeforeNavigation
                        && connectionStatus.getText().contains("ERROR"),
                "navegar no desconecta ni altera el estado de sesión");

        assertSingleClientConstruction();
        System.out.println("PASS " + checks + " comprobaciones de conexión rápida sin red externa");
        System.out.println("Directorio temporal: " + root);
    }

    private static void assertSingleClientConstruction() throws Exception {
        String projectRoot = System.getProperty("project.root");
        String main = Files.readString(Path.of(projectRoot, "src/Absolute_Control/Main.java"));
        String panel = Files.readString(Path.of(
                projectRoot, "src/Absolute_Control/ui/ConnectionPanel.java"));
        check(occurrences(main, "new Cliente(") == 1 && !panel.contains("new Cliente("),
                "Cliente se construye sólo en el flujo central existente de Main");
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) count++;
        return count;
    }

    private static byte[] digest(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    private static void await(Check condition, String description) throws Exception {
        long limit = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limit) {
            SwingUtilities.invokeAndWait(() -> {});
            if (condition.ok()) {
                check(true, description);
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("FAIL timeout: " + description);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAIL " + description);
        checks++;
        System.out.println("PASS " + description);
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
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
        }
        throw new AssertionError("No se encontró componente: " + name);
    }

    private static List<Component> all(Container root) {
        List<Component> components = new ArrayList<>();
        for (Component component : root.getComponents()) {
            components.add(component);
            if (component instanceof Container) components.addAll(all((Container) component));
        }
        return components;
    }

    private static final class FakeControl implements ConnectionPanel.ControlConexion {
        int connectCalls;
        int stopCalls;
        EquipoGuardado lastEquipment;

        @Override public ConnectionPanel.ResultadoSolicitud conectar(EquipoGuardado equipment) {
            connectCalls++;
            lastEquipment = equipment;
            return ConnectionPanel.ResultadoSolicitud.aceptada("ACEPTADA");
        }

        @Override public ConnectionPanel.ResultadoSolicitud detener() {
            stopCalls++;
            return ConnectionPanel.ResultadoSolicitud.aceptada("DETENIENDO");
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ok() throws Exception;
    }
}
