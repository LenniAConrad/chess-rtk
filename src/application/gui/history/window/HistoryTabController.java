package application.gui.history.window;

import application.gui.GuiTheme;
import application.gui.model.TabLabel;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;

/**
 * Drives sidebar tab styling and selection logic outside the main window class.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryTabController {

    /**
     * deps field.
     */
    private final HistoryTabDependencies deps;

    /**
     * HistoryTabController method.
     *
     * @param deps parameter.
     */
    public HistoryTabController(HistoryTabDependencies deps) {
        this.deps = deps;
    }

    /**
     * installTabLabels method.
     */
    public void installTabLabels() {
        JTabbedPane tabs = deps.rightTabs();
        if (tabs == null) {
            return;
        }
        List<TabLabel> labels = deps.tabLabels();
        labels.clear();
        setTabLabel(0, "◉", "Analysis");
        setTabLabel(1, "♟", "Explorer");
        setTabLabel(2, "✎", "Annotate");
        setTabLabel(3, "≡", "Report");
        setTabLabel(4, "⋯", "Variations");
        setTabLabel(5, "Δ", "Ablation");
        setTabLabel(6, "⚙", "Controls");
        setTabLabel(7, "⌘", "Commands");
        setTabLabel(8, "ℹ", "Info");
        refreshStatusTabPicker();
        updateTabLabelStyles();
    }

    /**
     * updateTabLabelStyles method.
     */
    public void updateTabLabelStyles() {
        JTabbedPane tabs = deps.rightTabs();
        List<TabLabel> labels = deps.tabLabels();
        if (tabs == null || labels.isEmpty()) {
            return;
        }
        int selected = tabs.getSelectedIndex();
        GuiTheme theme = deps.theme();
        if (theme == null) {
            return;
        }
        Font base = deps.scaleFont(theme.bodyFont());
        Font labelFont = base.deriveFont(Font.BOLD, Math.max(10f, base.getSize2D() * 0.9f));
        Font iconFont = base.deriveFont(Font.BOLD, Math.max(13f, base.getSize2D() * 1.08f));
        for (TabLabel label : labels) {
            boolean active = label.index() == selected;
            Color textColor = active ? deps.theme().textStrong() : deps.theme().textMuted();
            Color iconColor = active ? deps.theme().accent() : deps.theme().textMuted();
            label.text().setFont(labelFont);
            label.text().setForeground(textColor);
            label.icon().setFont(iconFont);
            label.icon().setForeground(iconColor);
        }
        JLabel header = deps.sidebarHeaderLabel();
        if (header != null) {
            header.setText(deps.formatTitle(tabs.getTitleAt(selected)));
        }
        updateActivitySelection();
        updateStatusTabPickerSelection();
    }

    /**
     * refreshStatusTabPicker method.
     */
    public void refreshStatusTabPicker() {
        JComboBox<String> picker = deps.statusTabPicker();
        JTabbedPane tabs = deps.rightTabs();
        if (picker == null || tabs == null) {
            return;
        }
        deps.setStatusTabUpdating(true);
        try {
            picker.removeAllItems();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                picker.addItem(tabs.getTitleAt(i));
            }
        } finally {
            deps.setStatusTabUpdating(false);
        }
        updateStatusTabPickerSelection();
    }

    /**
     * updateStatusTabPickerSelection method.
     */
    public void updateStatusTabPickerSelection() {
        JComboBox<String> picker = deps.statusTabPicker();
        JTabbedPane tabs = deps.rightTabs();
        if (picker == null || tabs == null) {
            return;
        }
        int idx = tabs.getSelectedIndex();
        if (idx >= 0 && idx < picker.getItemCount()) {
            deps.setStatusTabUpdating(true);
            try {
                picker.setSelectedIndex(idx);
            } finally {
                deps.setStatusTabUpdating(false);
            }
        }
    }

    /**
     * updateActivitySelection method.
     */
    public void updateActivitySelection() {
        List<JButton> buttons = deps.activityButtons();
        JTabbedPane tabs = deps.rightTabs();
        if (buttons.isEmpty() || tabs == null) {
            return;
        }
        int selected = tabs.getSelectedIndex();
        for (JButton button : buttons) {
            Object idxObj = button.getClientProperty("activityTabIndex");
            int idx = idxObj instanceof Integer ? (Integer) idxObj : -1;
            boolean active = idx == selected;
            boolean hover = Boolean.TRUE.equals(button.getClientProperty("activityHover"));
            Color stripe = active ? deps.theme().accent() : (hover ? deps.theme().border() : deps.theme().activityBar());
            Color base = deps.theme().activityBar();
            Color hoverBg = deps.blend(deps.theme().accent(), base, 0.18f);
            Color activeBg = deps.blend(deps.theme().accent(), base, 0.28f);
            button.setBackground(active ? activeBg : (hover ? hoverBg : base));
            button.setForeground(active || hover ? deps.theme().textStrong() : deps.theme().textMuted());
            button.setBorder(new javax.swing.border.CompoundBorder(
                    new javax.swing.border.MatteBorder(0, 1, 0, 0, stripe),
                    new javax.swing.border.EmptyBorder(0, 0, 0, 0)));
            Object baseLabel = button.getClientProperty("activityBaseLabel");
            String label = baseLabel instanceof String ? (String) baseLabel : button.getText();
            button.setText(label);
        }
    }

    /**
     * setTabLabel method.
     *
     * @param index parameter.
     * @param icon parameter.
     * @param text parameter.
     */
    private void setTabLabel(int index, String icon, String text) {
        JTabbedPane tabs = deps.rightTabs();
        if (tabs == null || index < 0 || index >= tabs.getTabCount()) {
            return;
        }
        JLabel iconLabel = new JLabel(icon);
        JLabel textLabel = new JLabel(text);
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.X_AXIS));
        panel.add(iconLabel);
        panel.add(javax.swing.Box.createHorizontalStrut(8));
        panel.add(textLabel);
        tabs.setTabComponentAt(index, panel);
        deps.tabLabels().add(new TabLabel(index, iconLabel, textLabel));
    }
}
