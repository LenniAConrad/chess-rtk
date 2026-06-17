package application.gui.workbench.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.border.Border;

/**
 * Border factories behind the stable {@link Theme} facade.
 */
final class ThemeBorders {

    private ThemeBorders() {
        // utility
    }

    static Border pad(int all) {
        return BorderFactory.createEmptyBorder(all, all, all, all);
    }

    static Border pad(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static Border lineBorder(Color color) {
        return BorderFactory.createLineBorder(color);
    }

    static Border lineBorder(Color color, int thickness) {
        return BorderFactory.createLineBorder(color, thickness);
    }
}
