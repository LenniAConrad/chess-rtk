package application.gui.workbench.ui;

/**
 * Animation math shared by small Workbench UI components.
 */
final class UiMotion {

    /**
     * Prevents instantiation.
     */
    private UiMotion() {
        // utility
    }

    /**
     * Applies an ease-out cubic animation curve after clamping input progress to
     * the legal animation range.
     *
     * @param value linear progress, usually from 0.0 to 1.0
     * @return eased progress in the 0.0 to 1.0 range
     */
    static double easeOutCubic(double value) {
        double progress = clamp(value, 0.0, 1.0);
        return 1.0 - Math.pow(1.0 - progress, 3.0);
    }

    /**
     * Clamps a value to an inclusive range.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
