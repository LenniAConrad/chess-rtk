package application.gui.layout.tab;

import java.awt.Point;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import application.gui.window.PgnNode;
import application.gui.ui.RoundedPanel;

/**
 * ReportTabContext interface.
 *
 * Provides interface behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public interface ReportTabContext {
    /**
     * mutedLabel method.
     *
     * @param text parameter.
     * @return return value.
     */
    JLabel mutedLabel(String text);
    /**
     * themedButton method.
     *
     * @param text parameter.
     * @param action parameter.
     * @return return value.
     */
    JButton themedButton(String text, ActionListener action);
    /**
     * registerFlatCard method.
     *
     * @param card parameter.
     */
    void registerFlatCard(RoundedPanel card);
    /**
     * registerList method.
     *
     * @param list parameter.
     */
    void registerList(JList<?> list);
    /**
     * registerScrollPane method.
     *
     * @param scroll parameter.
     */
    void registerScrollPane(JScrollPane scroll);
    /**
     * registerButton method.
     *
     * @param button parameter.
     */
    void registerButton(JButton button);
    /**
     * previewNode method.
     *
     * @param node parameter.
     * @param screenPoint parameter.
     */
    void previewNode(PgnNode node, Point screenPoint);
    /**
     * clearHoverPreviews method.
     */
    void clearHoverPreviews();
    /**
     * applyPgnNode method.
     *
     * @param node parameter.
     */
    void applyPgnNode(PgnNode node);
    /**
     * scaledRowHeight method.
     *
     * @param base parameter.
     * @return return value.
     */
    int scaledRowHeight(int base);
}
