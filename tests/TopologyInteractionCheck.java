import Absolute_Control.ui.NavigationPanel;
import Absolute_Control.ui.RetroPalette;
import Absolute_Control.ui.TopologyPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TopologyInteractionCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        TopologyPanel[] panelRef = new TopologyPanel[1];
        List<Boolean> changes = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> panelRef[0] = new TopologyPanel(false, right -> {
            changes.add(right);
            panelRef[0].setPc1Right(right);
        }));
        TopologyPanel panel = panelRef[0];

        check(order(panel).equals(List.of("PC1", "REY", "PC2")) && !panel.isPc1Right(),
                "PC1 izquierda representa PC1 | REY | PC2");
        checkPc2Disabled(panel, "topology.rightNode");
        check(centerTitle(panel).getForeground().equals(RetroPalette.NEON_GREEN),
                "REY permanece resaltado en verde");

        AbstractButton right = named(panel, "topology.rightButton", AbstractButton.class);
        SwingUtilities.invokeAndWait(right::doClick);
        check(changes.equals(List.of(true)) && panel.isPc1Right()
                        && order(panel).equals(List.of("PC2", "REY", "PC1")),
                "PC1 derecha representa PC2 | REY | PC1");
        checkPc2Disabled(panel, "topology.leftNode");
        check(named(panel, "topology.rightButton", AbstractButton.class).isSelected(),
                "opción visual coincide con la posición real");

        AbstractButton left = named(panel, "topology.leftButton", AbstractButton.class);
        SwingUtilities.invokeAndWait(() -> panel.setPositionSelectionEnabled(false));
        int changesBeforeBlockedClick = changes.size();
        SwingUtilities.invokeAndWait(left::doClick);
        JLabel availability = named(panel, "topology.availabilityLabel", JLabel.class);
        check(changes.size() == changesBeforeBlockedClick && panel.isPc1Right()
                        && !panel.isPositionSelectionEnabled()
                        && availability.getText().contains("BLOQUEADO"),
                "sesión activa bloquea cambios y explica la restricción");

        CardLayout cards = new CardLayout();
        JPanel host = new JPanel(cards);
        NavigationPanel[] navigationRef = new NavigationPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            host.add(panel, NavigationPanel.Page.INICIO.name());
            host.add(new JPanel(), NavigationPanel.Page.CONEXION.name());
            navigationRef[0] = new NavigationPanel(page -> cards.show(host, page.name()));
            buttonByText(navigationRef[0], "CONEXIÓN").doClick();
            buttonByText(navigationRef[0], "INICIO").doClick();
        });
        check(panel.isPc1Right() && order(panel).equals(List.of("PC2", "REY", "PC1")),
                "navegación conserva posición y estado visual");

        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1039, 122);
            layoutChildren(panel);
            BufferedImage image = new BufferedImage(1039, 122, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            panel.printAll(graphics);
            graphics.dispose();
            try {
                ImageIOBridge.write(image, Path.of(System.getProperty("java.io.tmpdir"),
                        "AbsoluteControl-topology-preview.png"));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        check(allInside(panel), "topología completa visible dentro de 1039×122 sin recortes");

        assertSingleClientConstruction();
        System.out.println("PASS " + checks + " comprobaciones de topología interactiva");
    }

    private static List<String> order(TopologyPanel panel) {
        return List.of(
                named(panel, "topology.leftNode.title", JLabel.class).getText(),
                named(panel, "topology.centerNode.title", JLabel.class).getText(),
                named(panel, "topology.rightNode.title", JLabel.class).getText());
    }

    private static JLabel centerTitle(TopologyPanel panel) {
        return named(panel, "topology.centerNode.title", JLabel.class);
    }

    private static void checkPc2Disabled(TopologyPanel panel, String nodeName) {
        JPanel node = named(panel, nodeName, JPanel.class);
        JLabel title = named(panel, nodeName + ".title", JLabel.class);
        JLabel subtitle = named(panel, nodeName + ".subtitle", JLabel.class);
        check(!node.isEnabled() && !title.isEnabled() && !subtitle.isEnabled()
                        && title.getText().equals("PC2")
                        && subtitle.getText().equals("PRÓXIMAMENTE")
                        && title.getForeground().equals(RetroPalette.DISABLED),
                "PC2 permanece deshabilitada y marcada PRÓXIMAMENTE");
    }

    private static boolean allInside(Container root) {
        for (Component component : root.getComponents()) {
            Rectangle bounds = component.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0
                    || bounds.x < 0 || bounds.y < 0
                    || bounds.x + bounds.width > root.getWidth()
                    || bounds.y + bounds.height > root.getHeight()) return false;
        }
        return true;
    }

    private static void assertSingleClientConstruction() throws Exception {
        Path root = Path.of(System.getProperty("project.root"));
        String main = Files.readString(root.resolve("src/Absolute_Control/Main.java"));
        check(occurrences(main, "new Cliente(") == 1,
                "topología no crea otro Cliente");
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) count++;
        return count;
    }

    private static void layoutChildren(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) layoutChildren((Container) component);
        }
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

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAIL " + description);
        checks++;
        System.out.println("PASS " + description);
    }

    private static final class ImageIOBridge {
        private static void write(BufferedImage image, Path path) throws Exception {
            javax.imageio.ImageIO.write(image, "png", path.toFile());
        }
    }
}
