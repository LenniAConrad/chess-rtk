package application.gui.history.window;

import application.gui.GuiTheme;
import application.gui.model.TabLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;

/**
 * Minimal context required by {@link HistoryTabController} and {@link application.gui.history.status.HistoryStatusBar}
 * to drive the sidebar tabs and status bar.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public interface HistoryTabDependencies {

    /**
     * rightTabs method.
     *
     * @return return value.
     */
    JTabbedPane rightTabs();

    /**
     * tabLabels method.
     *
     * @return return value.
     */
    List<TabLabel> tabLabels();

    /**
     * activityButtons method.
     *
     * @return return value.
     */
    List<JButton> activityButtons();

    /**
     * sidebarHeaderLabel method.
     *
     * @return return value.
     */
    JLabel sidebarHeaderLabel();

    /**
     * statusTabPicker method.
     *
     * @return return value.
     */
    JComboBox<String> statusTabPicker();

    /**
     * theme method.
     *
     * @return return value.
     */
    GuiTheme theme();

    /**
     * scaleFont method.
     *
     * @param base parameter.
     * @return return value.
     */
    Font scaleFont(Font base);

    /**
     * blend method.
     *
     * @param base parameter.
     * @param overlay parameter.
     * @param amount parameter.
     * @return return value.
     */
    Color blend(Color base, Color overlay, float amount);

    /**
     * formatTitle method.
     *
     * @param text parameter.
     * @return return value.
     */
    String formatTitle(String text);

    /**
     * statusTabUpdating method.
     *
     * @return return value.
     */
    boolean statusTabUpdating();

    /**
     * setStatusTabUpdating method.
     *
     * @param updating parameter.
     */
    void setStatusTabUpdating(boolean updating);

    /**
     * scaleDimension method.
     *
     * @param base parameter.
     * @return return value.
     */
    Dimension scaleDimension(Dimension base);

    /**
     * isLightMode method.
     *
     * @return return value.
     */
    boolean isLightMode();

    /**
     * toggleTheme method.
     */
    void toggleTheme();

    /**
     * panelVisible method.
     *
     * @return return value.
     */
    boolean panelVisible();

    /**
     * togglePanel method.
     */
    void togglePanel();

    /**
     * setPanelVisible method.
     *
     * @param visible parameter.
     * @param persist parameter.
     */
    void setPanelVisible(boolean visible, boolean persist);

    /**
     * openPanelTab method.
     *
     * @param title parameter.
     */
    void openPanelTab(String title);

    /**
     * toggleEnginePower method.
     */
    void toggleEnginePower();

    /**
     * selectRightTab method.
     *
     * @param index parameter.
     */
    void selectRightTab(int index);

    /**
     * registerCombo method.
     *
     * @param combo parameter.
     */
    void registerCombo(JComboBox<?> combo);
}
