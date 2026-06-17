package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Color math and shared decorative painting behind the public {@link Theme} facade.
 */
final class ThemeColors {

    /**
     * Prevents instantiation.
     */
    private ThemeColors() {
        // utility
    }

    static Color withAlpha(Color color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
    }

    static Color lerp(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    static void paintElevatedCard(Graphics2D g, int width, int height, int arc, float hover) {
        float h = Math.max(0f, Math.min(1f, hover));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int shadow = 3;
        int w = Math.max(0, width - 1);
        int surfaceHeight = Math.max(0, height - 1 - shadow);
        int baseShadowAlpha = Theme.isDark() ? 48 : 26;
        int shadowAlpha = Math.round(baseShadowAlpha * (0.75f + 0.45f * h));
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha))));
        g.fillRoundRect(2, shadow, Math.max(0, w - 3), surfaceHeight, arc, arc);
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha / 2))));
        g.fillRoundRect(1, Math.max(0, shadow - 1), Math.max(0, w - 1), surfaceHeight, arc, arc);
        g.setColor(Theme.CARD);
        g.fillRoundRect(0, 0, w, surfaceHeight, arc, arc);
        g.setColor(Theme.GLASS_HIGHLIGHT);
        g.drawLine(arc / 2, 1, Math.max(arc / 2, w - arc / 2), 1);
        g.setColor(lerp(Theme.CARD_BORDER, Theme.ACCENT, h * 0.85f));
        g.drawRoundRect(0, 0, w, surfaceHeight, arc, arc);
    }

    static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    static Color blendOver(Color foreground, Color background) {
        double alpha = foreground.getAlpha() / 255.0;
        int red = blendChannel(foreground.getRed(), background.getRed(), alpha);
        int green = blendChannel(foreground.getGreen(), background.getGreen(), alpha);
        int blue = blendChannel(foreground.getBlue(), background.getBlue(), alpha);
        return new Color(red, green, blue);
    }

    private static int blendChannel(int foreground, int background, double alpha) {
        return (int) Math.round(foreground * alpha + background * (1.0 - alpha));
    }
}
