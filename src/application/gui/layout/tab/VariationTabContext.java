package application.gui.layout.tab;

import java.awt.Point;

import javax.swing.JScrollPane;
import javax.swing.JTable;

import application.gui.window.PgnNode;
import application.gui.ui.RoundedPanel;

/**
 * VariationTabContext interface.
 *
 * Provides interface behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public interface VariationTabContext {
    /**
     * createFlatCard method.
     *
     * @param title parameter.
     * @param showTitle parameter.
     * @return return value.
     */
    RoundedPanel createFlatCard(String title, boolean showTitle);
    /**
     * registerFlatCard method.
     *
     * @param card parameter.
     */
    void registerFlatCard(RoundedPanel card);
    /**
     * registerTable method.
     *
     * @param table parameter.
     */
    void registerTable(JTable table);
    /**
     * registerScrollPane method.
     *
     * @param scroll parameter.
     */
    void registerScrollPane(JScrollPane scroll);
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
