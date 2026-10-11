import Absolute_Control.equipos.EquipoGuardado;
import Absolute_Control.equipos.RepositorioEquiposJson;
import Absolute_Control.equipos.ServicioEquipos;
import Absolute_Control.ui.ConnectionPanel;
import Absolute_Control.ui.NavigationPanel;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UiEquipmentCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("AbsoluteControl-ui52-");
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T20:00:00Z"), ZoneOffset.UTC);

        checkNavigation();

        Path store = root.resolve("ui/equipos.json");
        ServicioEquipos service = new ServicioEquipos(new RepositorioEquiposJson(store), clock);
        AtomicBoolean confirmDelete = new AtomicBoolean(false);
        ConnectionPanel[] panelRef = new ConnectionPanel[1];
        SwingUtilities.invokeAndWait(() -> panelRef[0] = new ConnectionPanel(
                service, (parent, equipment) -> confirmDelete.get()));
        ConnectionPanel panel = panelRef[0];

        JTextField name = componentByName(panel, "connection.nameField", JTextField.class);
        JTextField ip = componentByName(panel, "connection.ipField", JTextField.class);
        JTextField port = componentByName(panel, "connection.portField", JTextField.class);
        JButton save = componentByName(panel, "connection.saveButton", JButton.class);

        SwingUtilities.invokeAndWait(() -> {
            name.setText("Laptop");
            ip.setText("192.168.1.198");
            port.setText("8080");
        });
        check(service.listar().isEmpty(), "escribir campos no guarda automáticamente");
        SwingUtilities.invokeAndWait(save::doClick);
        await(() -> service.listar().size() == 1 && save.isEnabled()
                        && componentExists(panel, "connection.edit." + service.listar().get(0).getId()),
                "guardado manual desde GUARDAR");

        EquipoGuardado created = service.listar().get(0);
        ServicioEquipos reloaded = new ServicioEquipos(new RepositorioEquiposJson(store), clock);
        check(reloaded.listar().size() == 1
                        && reloaded.listar().get(0).getId().equals(created.getId()),
                "datos persisten después de reiniciar el servicio");

        JButton edit = componentByName(panel, "connection.edit." + created.getId(), JButton.class);
        SwingUtilities.invokeAndWait(() -> {
            edit.doClick();
            name.setText("Laptop oficina");
            ip.setText("10.0.0.40");
            port.setText("9000");
            save.doClick();
        });
        await(() -> service.listar().get(0).getNombre().equals("Laptop oficina")
                        && save.isEnabled(),
                "edición desde UI persiste");
        EquipoGuardado edited = service.listar().get(0);
        check(edited.getId().equals(created.getId())
                        && edited.getIp().equals("10.0.0.40") && edited.getPuerto() == 9000,
                "edición conserva UUID y actualiza IP/puerto");

        JButton delete = componentByName(panel, "connection.delete." + created.getId(), JButton.class);
        SwingUtilities.invokeAndWait(delete::doClick);
        Thread.sleep(120);
        flushEdt();
        check(service.listar().size() == 1, "cancelar confirmación conserva el equipo");

        confirmDelete.set(true);
        SwingUtilities.invokeAndWait(delete::doClick);
        await(() -> service.listar().isEmpty() && save.isEnabled(),
                "eliminación confirmada actualiza la lista");

        SwingUtilities.invokeAndWait(() -> {
            name.setText("Equipo inválido");
            ip.setText("300.1.2.3");
            port.setText("8080");
            save.doClick();
        });
        JLabel status = componentByName(panel, "connection.statusLabel", JLabel.class);
        await(() -> status.getText().startsWith("ERROR:"), "error de validación visible");
        check(service.listar().isEmpty(), "validación fallida no crea registros");

        CardLayout cards = new CardLayout();
        JPanel host = new JPanel(cards);
        JPanel home = new JPanel();
        SwingUtilities.invokeAndWait(() -> {
            name.setText("Estado visual");
            host.add(home, "INICIO");
            host.add(panel, "CONEXION");
            cards.show(host, "CONEXION");
            cards.show(host, "INICIO");
            cards.show(host, "CONEXION");
        });
        check(name.getText().equals("Estado visual"),
                "volver a INICIO y CONEXIÓN conserva el estado visual");

        Path corrupt = root.resolve("corrupt/equipos.json");
        Files.createDirectories(corrupt.getParent());
        Files.writeString(corrupt, "{json roto", StandardCharsets.UTF_8);
        ConnectionPanel[] corruptPanelRef = new ConnectionPanel[1];
        SwingUtilities.invokeAndWait(() -> corruptPanelRef[0] = new ConnectionPanel(
                () -> new ServicioEquipos(new RepositorioEquiposJson(corrupt), clock),
                (parent, equipment) -> true));
        JLabel corruptStatus = componentByName(
                corruptPanelRef[0], "connection.statusLabel", JLabel.class);
        JButton corruptSave = componentByName(
                corruptPanelRef[0], "connection.saveButton", JButton.class);
        await(() -> corruptStatus.getText().startsWith("ERROR DE PERSISTENCIA:"),
                "JSON corrupto se informa en la pantalla");
        check(!corruptSave.isEnabled()
                        && Files.readString(corrupt, StandardCharsets.UTF_8).equals("{json roto"),
                "JSON corrupto permanece intacto y bloquea guardado");

        System.out.println("PASS " + checks + " comprobaciones Swing headless de UI-5.2");
        System.out.println("Directorio temporal: " + root);
    }

    private static void checkNavigation() throws Exception {
        List<NavigationPanel.Page> events = new ArrayList<>();
        NavigationPanel[] navigationRef = new NavigationPanel[1];
        SwingUtilities.invokeAndWait(() -> navigationRef[0] = new NavigationPanel(events::add));
        NavigationPanel navigation = navigationRef[0];
        JButton connection = buttonByText(navigation, "CONEXIÓN");
        JButton home = buttonByText(navigation, "INICIO");
        SwingUtilities.invokeAndWait(() -> {
            connection.doClick();
            navigation.setActivePage(NavigationPanel.Page.CONEXION);
            home.doClick();
            navigation.setActivePage(NavigationPanel.Page.INICIO);
        });
        check(events.equals(List.of(NavigationPanel.Page.CONEXION, NavigationPanel.Page.INICIO))
                        && navigation.getActivePage() == NavigationPanel.Page.INICIO,
                "navegación INICIO ↔ CONEXIÓN sin listeners duplicados");
    }

    private static void await(Check condition, String description) throws Exception {
        long limit = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limit) {
            flushEdt();
            if (condition.ok()) {
                check(true, description);
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("FAIL timeout: " + description);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAIL " + description);
        checks++;
        System.out.println("PASS " + description);
    }

    private static JButton buttonByText(Container root, String text) {
        for (Component component : allComponents(root)) {
            if (component instanceof JButton
                    && text.equals(((JButton) component).getText())) return (JButton) component;
        }
        throw new AssertionError("No se encontró botón: " + text);
    }

    private static <T extends Component> T componentByName(
            Container root, String name, Class<T> type) {
        for (Component component : allComponents(root)) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
        }
        throw new AssertionError("No se encontró componente: " + name);
    }

    private static boolean componentExists(Container root, String name) {
        for (Component component : allComponents(root)) {
            if (name.equals(component.getName())) return true;
        }
        return false;
    }

    private static List<Component> allComponents(Container root) {
        List<Component> components = new ArrayList<>();
        for (Component component : root.getComponents()) {
            components.add(component);
            if (component instanceof Container) {
                components.addAll(allComponents((Container) component));
            }
        }
        return components;
    }

    @FunctionalInterface
    private interface Check {
        boolean ok() throws Exception;
    }
}
