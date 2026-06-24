package application.gui.workbench.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.border.Border;

/**
 * Border factories behind the stable {@link Theme} facade.
 */
final class ThemeBorders {

    /**
     * Utility class; prevent instantiation.
     */
    private ThemeBorders() {
        // utility
    }

    /**
     * Creates equal empty padding on every side.
     *
     * @param all padding in pixels
     * @return empty border
     */
    static Border pad(int all) {
        return BorderFactory.createEmptyBorder(all, all, all, all);
    }

    /**
     * Creates symmetric vertical and horizontal empty padding.
     *
     * @param vertical top and bottom padding in pixels
     * @param horizontal left and right padding in pixels
     * @return empty border
     */
    static Border pad(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Creates empty padding with explicit side values.
     *
     * @param top top padding in pixels
     * @param left left padding in pixels
     * @param bottom bottom padding in pixels
     * @param right right padding in pixels
     * @return empty border
     */
    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Creates a one-pixel line border.
     *
     * @param color display color
     * @return line border using the color
     */
    static Border lineBorder(Color color) {
        return BorderFactory.createLineBorder(color);
    }

    /**
     * Creates a line border with explicit thickness.
     *
     * @param color display color
     * @param thickness border thickness in pixels
     * @return line border using the color and thickness
     */
    static Border lineBorder(Color color, int thickness) {
        return BorderFactory.createLineBorder(color, thickness);
    }
}
