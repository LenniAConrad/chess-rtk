package testing;

import static testing.WorkbenchComponentDebugFormat.alignment;
import static testing.WorkbenchComponentDebugFormat.className;
import static testing.WorkbenchComponentDebugFormat.clean;
import static testing.WorkbenchComponentDebugFormat.color;
import static testing.WorkbenchComponentDebugFormat.dim;
import static testing.WorkbenchComponentDebugFormat.fontStyle;
import static testing.WorkbenchComponentDebugFormat.icon;
import static testing.WorkbenchComponentDebugFormat.quote;
import static testing.WorkbenchComponentDebugFormat.rect;
import static testing.WorkbenchComponentDebugFormat.sample;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.LayoutManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

/**
 * Produces detailed, text-based Swing component-tree diagnostics for Workbench
 * visual debugging.
 */
public final class WorkbenchComponentDebug {

    /**
     * Prevents instantiation.
     */
    private WorkbenchComponentDebug() {
        // utility
    }

    /**
     * Writes a component tree dump to disk as UTF-8 text.
     *
     * @param root root component
     * @param label human-readable dump label
     * @param output output file
     * @throws IOException if the dump cannot be written
     */
    public static void write(Component root, String label, Path output) throws IOException {
        if (output == null) {
            return;
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, dump(root, label), StandardCharsets.UTF_8);
    }

    /**
     * Builds a detailed component tree dump.
     *
     * @param root root component
     * @param label human-readable dump label
     * @return formatted diagnostic text
     */
    public static String dump(Component root, String label) {
        WorkbenchComponentStats stats = WorkbenchComponentStats.collect(root);

        StringBuilder out = new StringBuilder(16_384);
        out.append("Workbench UI component dump\n");
        out.append("label: ").append(clean(label)).append('\n');
        out.append("root: ").append(root == null ? "<null>" : root.getClass().getName()).append('\n');
        out.append("java.version: ").append(System.getProperty("java.version", "")).append('\n');
        out.append("os.name: ").append(System.getProperty("os.name", "")).append('\n');
        out.append('\n');

        out.append("Summary\n");
        out.append("total.components: ").append(stats.total()).append('\n');
        out.append("max.depth: ").append(stats.maxDepth()).append('\n');
        out.append("visible.components: ").append(stats.visible()).append('\n');
        out.append("showing.components: ").append(stats.showing()).append('\n');
        out.append("displayable.components: ").append(stats.displayable()).append('\n');
        out.append("enabled.components: ").append(stats.enabled()).append('\n');
        out.append("focusable.components: ").append(stats.focusable()).append('\n');
        out.append("opaque.swing.components: ").append(stats.opaque()).append('\n');
        out.append('\n');

        out.append("Counts by class\n");
        for (Map.Entry<String, Integer> entry : stats.byClass().entrySet()) {
            out.append(entry.getValue()).append('\t').append(entry.getKey()).append('\n');
        }
        out.append('\n');

        out.append("Component tree\n");
        int[] index = {0};
        appendComponent(root, 0, "root", out, index);
        return out.toString();
    }

    /**
     * Appends one component and all descendants.
     *
     * @param component Swing component
     * @param depth tree depth
     * @param path logical child path
     * @param out output buffer
     * @param index monotonic component index
     */
    private static void appendComponent(Component component, int depth, String path, StringBuilder out, int[] index) {
        if (component == null) {
            return;
        }
        String indent = "  ".repeat(depth);
        int id = index[0]++;
        out.append(indent).append('#').append(String.format(Locale.ROOT, "%04d", id))
                .append(" path=").append(path)
                .append(" class=").append(component.getClass().getName()).append('\n');
        appendIdentity(component, indent, out);
        appendState(component, indent, out);
        appendGeometry(component, indent, out);
        appendVisual(component, indent, out);
        appendContent(component, indent, out);
        WorkbenchComponentSpecializedDebug.append(component, indent, out);
        appendContainer(component, indent, out);

        if (component instanceof Container container) {
            Component[] children = container.getComponents();
            for (int i = 0; i < children.length; i++) {
                appendComponent(children[i], depth + 1, path + "/" + i, out, index);
            }
        }
    }

    /**
     * Appends identity and accessibility details.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendIdentity(Component component, String indent, StringBuilder out) {
        AccessibleContext accessible = component.getAccessibleContext();
        out.append(indent).append("  identity: name=").append(quote(component.getName()))
                .append(" accessibleName=")
                .append(quote(accessible == null ? null : accessible.getAccessibleName()))
                .append(" accessibleRole=")
                .append(accessible == null || accessible.getAccessibleRole() == null
                        ? "<null>" : accessible.getAccessibleRole().toDisplayString())
                .append(" accessibleDescription=")
                .append(quote(accessible == null ? null : accessible.getAccessibleDescription()))
                .append('\n');
    }

    /**
     * Appends visibility, focus, and validation details.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendState(Component component, String indent, StringBuilder out) {
        out.append(indent).append("  state: visible=").append(component.isVisible())
                .append(" showing=").append(component.isShowing())
                .append(" displayable=").append(component.isDisplayable())
                .append(" enabled=").append(component.isEnabled())
                .append(" focusable=").append(component.isFocusable())
                .append(" focusOwner=").append(component.isFocusOwner())
                .append(" valid=").append(component.isValid());
        if (component instanceof JComponent swing) {
            out.append(" opaque=").append(swing.isOpaque())
                    .append(" doubleBuffered=").append(swing.isDoubleBuffered());
        }
        out.append('\n');
    }

    /**
     * Appends current and preferred geometry.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendGeometry(Component component, String indent, StringBuilder out) {
        out.append(indent).append("  bounds: current=")
                .append(rect(component.getBounds()))
                .append(" size=").append(dim(component.getSize()))
                .append(" preferred=").append(dim(component.getPreferredSize()))
                .append(" minimum=").append(dim(component.getMinimumSize()))
                .append(" maximum=").append(dim(component.getMaximumSize()))
                .append('\n');
    }

    /**
     * Appends visual styling details.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendVisual(Component component, String indent, StringBuilder out) {
        Font font = component.getFont();
        out.append(indent).append("  visual: background=")
                .append(color(component.getBackground()))
                .append(" foreground=").append(color(component.getForeground()))
                .append(" font=").append(font == null ? "<null>" : font.getFamily()
                        + "." + fontStyle(font) + "." + font.getSize());
        if (component instanceof JComponent swing) {
            out.append(" border=").append(className(swing.getBorder()))
                    .append(" ui=").append(className(swing.getUI()))
                    .append(" tooltip=").append(quote(swing.getToolTipText()));
        }
        out.append('\n');
    }

    /**
     * Appends common text and icon content.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendContent(Component component, String indent, StringBuilder out) {
        if (component instanceof AbstractButton button) {
            out.append(indent).append("  button: text=").append(quote(button.getText()))
                    .append(" actionCommand=").append(quote(button.getActionCommand()))
                    .append(" selected=").append(button.isSelected())
                    .append(" armed=").append(button.getModel().isArmed())
                    .append(" pressed=").append(button.getModel().isPressed())
                    .append(" rollover=").append(button.getModel().isRollover())
                    .append(" icon=").append(icon(button.getIcon()))
                    .append('\n');
        } else if (component instanceof JLabel label) {
            out.append(indent).append("  label: text=").append(quote(label.getText()))
                    .append(" icon=").append(icon(label.getIcon()))
                    .append(" horizontalAlignment=").append(alignment(label.getHorizontalAlignment()))
                    .append(" verticalAlignment=").append(alignment(label.getVerticalAlignment()))
                    .append('\n');
        }

        if (component instanceof JTextComponent text) {
            String value = text.getText();
            out.append(indent).append("  text: length=").append(value == null ? 0 : value.length())
                    .append(" sample=").append(quote(sample(value)))
                    .append(" editable=").append(text.isEditable())
                    .append(" caret=").append(text.getCaretPosition())
                    .append(" selectionStart=").append(text.getSelectionStart())
                    .append(" selectionEnd=").append(text.getSelectionEnd())
                    .append('\n');
        }
    }

    /**
     * Appends layout and child-count details.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendContainer(Component component, String indent, StringBuilder out) {
        if (component instanceof Container container) {
            LayoutManager layout = container.getLayout();
            out.append(indent).append("  container: childCount=").append(container.getComponentCount())
                    .append(" layout=").append(layout == null ? "<null>" : layout.getClass().getName())
                    .append('\n');
        }
    }
}
