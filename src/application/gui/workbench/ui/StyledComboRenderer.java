package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/**
 * Combo-box renderer that avoids platform-default blue or gray row flashes.
 */
final class StyledComboRenderer extends DefaultListCellRenderer {

    /**
     * Serialization identifier for Swing renderer compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Combo that owns this renderer, used to mirror disabled state while painting
     * the selected value outside the popup list.
     */
    private final JComboBox<?> owner;

    /**
     * Creates a combo renderer.
     *
     * @param owner owning combo box
     */
    StyledComboRenderer(JComboBox<?> owner) {
        this.owner = owner;
    }

    /**
     * Returns the rendered combo row.
     *
     * @param list source list
     * @param value row value
     * @param index row index
     * @param selected whether selected
     * @param focused whether focused
     * @return renderer component
     */
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
            boolean focused) {
        super.getListCellRendererComponent(list, value, index, selected, focused);
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        setFont(Theme.font(13, Font.PLAIN));
        boolean enabled = owner == null || owner.isEnabled();
        setForeground(enabled ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
        if (!enabled) {
            setBackground(Theme.INPUT_DISABLED);
        } else {
            setBackground(selected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
        }
        return this;
    }
}
