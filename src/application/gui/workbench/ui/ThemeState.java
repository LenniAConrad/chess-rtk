package application.gui.workbench.ui;

/**
 * Active mode and density state behind the stable {@link Theme} facade.
 */
final class ThemeState {

    private static Theme.Mode mode = Theme.Mode.LIGHT;
    private static Theme.Density density = Theme.Density.DENSE;

    private ThemeState() {
        // utility
    }

    static Theme.Mode mode() {
        return mode;
    }

    static boolean isDark() {
        return mode == Theme.Mode.DARK;
    }

    static Theme.Density density() {
        return density;
    }

    static double setDensity(Theme.Density value) {
        Theme.Density next = value == null ? Theme.Density.DENSE : value;
        double ratio = next.fontScale() / density.fontScale();
        if (next != density) {
            density = next;
            ThemeFonts.clearCaches();
        }
        return ratio;
    }

    static void setMode(Theme.Mode value) {
        mode = value == null ? Theme.Mode.LIGHT : value;
        if (isDark()) {
            ThemePalette.applyDark();
        } else {
            ThemePalette.applyLight();
        }
    }
}
