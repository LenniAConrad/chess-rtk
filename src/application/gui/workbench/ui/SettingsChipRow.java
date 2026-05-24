package application.gui.workbench.ui;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Compact settings row that replaces checkbox-style toggles with an explicit
 * off/on chip selector.
 */
public final class SettingsChipRow extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum readable row width.
     */
    private static final int MIN_WIDTH = 330;

    /**
     * Fixed row height.
     */
    private static final int ROW_HEIGHT = 48;

    /**
     * Chip selector for the boolean value.
     */
    private final transient ChipGroup chips = new ChipGroup(List.of("Off", "On"));

    /**
     * Creates a settings chip row.
     *
     * @param text label text
     * @param tooltip tooltip text
     * @param selected selected state
     * @param onChange callback for changed state
     */
    public SettingsChipRow(String text, String tooltip, boolean selected, Consumer<Boolean> onChange) {
        super(new BorderLayout(14, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JPanel copy = new JPanel(new BorderLayout(0, 2));
        copy.setOpaque(false);
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        label.setFont(Theme.font(13, Font.BOLD));
        label.setToolTipText(tooltip);
        JLabel detail = new JLabel(tooltip == null ? "" : tooltip);
        Theme.foreground(detail, Theme.ForegroundRole.MUTED);
        detail.setFont(Theme.font(12, Font.PLAIN));
        detail.setToolTipText(tooltip);
        chips.setToolTipText(tooltip);
        chips.setSelectedIndex(selected ? 1 : 0);
        chips.setOnSelect(index -> {
            SoundService.play(SoundCue.UI_CLICK);
            onChange.accept(index == 1);
        });
        copy.add(label, BorderLayout.NORTH);
        copy.add(detail, BorderLayout.CENTER);
        add(copy, BorderLayout.CENTER);
        add(chips, BorderLayout.EAST);
    }

    /**
     * Returns whether the row is currently selected.
     *
     * @return true when selected
     */
    public boolean isSelected() {
        return chips.getSelectedIndex() == 1;
    }

    /**
     * Updates the selected state without firing a change callback.
     *
     * @param selected selected state
     */
    public void setSelected(boolean selected) {
        chips.setSelectedIndex(selected ? 1 : 0);
    }

    /**
     * Returns the preferred readable row size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MIN_WIDTH, ROW_HEIGHT);
    }

    /**
     * Returns the minimum readable row size.
     *
     * @return minimum size
     */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }
}
