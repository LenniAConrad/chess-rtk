package testing;

import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import static testing.TestSupport.assertTrue;

/**
 * Headless component gallery for representative shared Workbench controls.
 */
public final class GuiComponentGallery {

    /**
     * Gallery render width.
     */
    private static final int WIDTH = 900;

    /**
     * Gallery render height.
     */
    private static final int HEIGHT = 560;

    /**
     * Prevents instantiation.
     */
    private GuiComponentGallery() {
        // utility
    }

    /**
     * Builds and paints the gallery in every supported color mode.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        for (Theme.Mode mode : Theme.Mode.values()) {
            assertTrue(hasPaintedContent(render(mode)), mode.label() + " gallery paints visible content");
        }
        System.out.println("GuiComponentGallery: all checks passed");
    }

    /**
     * Creates the gallery component.
     *
     * @return gallery component
     */
    public static JComponent create() {
        JPanel content = Ui.transparentPanel(new GridLayout(0, 2, Theme.SPACE_MD, Theme.SPACE_MD));
        content.add(Ui.card("Buttons", buttonPanel()));
        content.add(Ui.card("Inputs", inputPanel()));
        content.add(Ui.card("Status", statusPanel()));
        content.add(Ui.card("Command", Ui.commandBlock("crtk engine bestmove --fen <current>")));

        SurfacePanel surface = new SurfacePanel(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
        surface.setBorder(Theme.pad(Theme.SPACE_MD));
        surface.add(Ui.sectionHeader("Component gallery", "shared controls", Ui.spinner()), BorderLayout.NORTH);
        surface.add(content, BorderLayout.CENTER);
        surface.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        return surface;
    }

    /**
     * Renders the gallery for one theme mode.
     *
     * @param mode theme mode
     * @return rendered image
     */
    public static BufferedImage render(Theme.Mode mode) {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        runOnEdt(() -> {
            Theme.Mode previous = Theme.mode();
            try {
                Theme.setMode(mode);
                Theme.install();
                JComponent gallery = create();
                gallery.setBounds(0, 0, WIDTH, HEIGHT);
                layout(gallery);
                BufferedImage rendered = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = rendered.createGraphics();
                try {
                    gallery.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                image.set(rendered);
            } finally {
                Theme.setMode(previous);
                Theme.install();
            }
        });
        return image.get();
    }

    /**
     * Creates button examples.
     *
     * @return button panel
     */
    private static JComponent buttonPanel() {
        JPanel panel = Ui.transparentPanel(new GridLayout(0, 2, Theme.SPACE_SM, Theme.SPACE_SM));
        panel.add(Ui.button("Run", true, event -> {
            // gallery
        }));
        panel.add(Ui.ghostButton("Preview", event -> {
            // gallery
        }));
        panel.add(Ui.destructiveButton("Clear", event -> {
            // gallery
        }));
        panel.add(Ui.iconButton("More", event -> {
            // gallery
        }));
        return panel;
    }

    /**
     * Creates input examples.
     *
     * @return input panel
     */
    private static JComponent inputPanel() {
        JPanel panel = Ui.transparentPanel(new GridLayout(0, 1, Theme.SPACE_SM, Theme.SPACE_SM));
        JTextField field = new JTextField("current.fen");
        JComboBox<String> combo = new JComboBox<>(new String[] { "Depth", "Nodes", "Time" });
        JSpinner spinner = new JSpinner();
        JSlider slider = new JSlider(0, 100, 42);
        JCheckBox checkBox = new JCheckBox("Bounded");
        Ui.styleFields(field);
        Ui.styleCombo(combo);
        Ui.styleSpinner(spinner);
        Ui.styleSlider(slider);
        Ui.styleCheckBox(checkBox);
        panel.add(Ui.fieldRow("Path", field, 72));
        panel.add(Ui.fieldRow("Mode", combo, 72));
        panel.add(Ui.fieldRow("Limit", spinner, 72));
        panel.add(Ui.fieldRow("Scale", slider, 72));
        panel.add(checkBox);
        return panel;
    }

    /**
     * Creates status examples.
     *
     * @return status panel
     */
    private static JComponent statusPanel() {
        JPanel panel = Ui.transparentPanel(new GridLayout(0, 1, Theme.SPACE_SM, Theme.SPACE_SM));
        SegmentedSwitcher switcher = Ui.segmentedControl("Build", "Run", "Publish");
        switcher.setSelectedIndex(1);
        panel.add(switcher);
        StatusBadge ready = new StatusBadge();
        ready.success("Ready");
        StatusBadge warning = new StatusBadge();
        warning.warning("Needs input");
        StatusBadge failed = new StatusBadge();
        failed.error("Failed");
        panel.add(ready);
        panel.add(warning);
        panel.add(failed);
        JButton action = Ui.button("Apply", false, event -> {
            // gallery
        });
        panel.add(Ui.emptyState("No run selected", "Choose a command template.", action));
        return panel;
    }

    /**
     * Recursively lays out a component tree.
     *
     * @param component component to lay out
     */
    private static void layout(JComponent component) {
        component.doLayout();
        for (java.awt.Component child : component.getComponents()) {
            if (child instanceof JComponent childComponent) {
                Dimension size = childComponent.getPreferredSize();
                if (childComponent.getWidth() <= 0 || childComponent.getHeight() <= 0) {
                    childComponent.setSize(size);
                }
                layout(childComponent);
            }
        }
    }

    /**
     * Returns whether the rendered image contains more than a flat fill.
     *
     * @param image rendered image
     * @return true when at least two visible colors are present
     */
    private static boolean hasPaintedContent(BufferedImage image) {
        int first = image.getRGB(0, 0);
        int differentPixels = 0;
        for (int y = 0; y < image.getHeight(); y += 8) {
            for (int x = 0; x < image.getWidth(); x += 8) {
                int rgb = image.getRGB(x, y);
                if (((rgb >>> 24) & 0xff) > 0 && rgb != first) {
                    differentPixels++;
                }
            }
        }
        return differentPixels > 12;
    }

    /**
     * Runs an action on Swing's event-dispatch thread.
     *
     * @param action action to run
     */
    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("gallery render interrupted", ex);
        } catch (InvocationTargetException ex) {
            throw new AssertionError("gallery render failed", ex.getCause());
        }
    }
}
