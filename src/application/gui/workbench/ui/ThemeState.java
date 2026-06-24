package application.gui.workbench.ui;

/**
 * Active mode and density state behind the stable {@link Theme} facade.
 */
final class ThemeState {

    /**
     * Active light/dark theme mode.
     */
    private static Theme.Mode mode = Theme.Mode.LIGHT;
    /**
     * Active UI density scale.
     */
    private static Theme.Density density = Theme.Density.DENSE;

    /**
     * Utility class; prevent instantiation.
     */
    private ThemeState() {
        // utility
    }

    /**
     * Returns the active light/dark mode.
     *
     * @return theme mode
     */
    static Theme.Mode mode() {
        return mode;
    }

    /**
     * Returns whether the active theme mode is dark.
     *
     * @return true in dark mode
     */
    static boolean isDark() {
        return mode == Theme.Mode.DARK;
    }

    /**
     * Returns the active UI density.
     *
     * @return density scale
     */
    static Theme.Density density() {
        return density;
    }

    /**
     * Updates the density.
     *
     * @param value candidate value
     * @return density
     */
    static double setDensity(Theme.Density value) {
        Theme.Density next = value == null ? Theme.Density.DENSE : value;
        double ratio = next.fontScale() / density.fontScale();
        if (next != density) {
            density = next;
            ThemeFonts.clearCaches();
        }
        return ratio;
    }

    /**
     * Updates the mode.
     *
     * @param value candidate value
     */
    static void setMode(Theme.Mode value) {
        mode = value == null ? Theme.Mode.LIGHT : value;
        if (isDark()) {
            ThemePalette.applyDark();
        } else {
            ThemePalette.applyLight();
        }
    }
}
