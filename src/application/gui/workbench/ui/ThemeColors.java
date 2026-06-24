package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
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

    /**
     * Returns a copy of a color with a clamped alpha channel.
     *
     * @param color display color
     * @param alpha requested alpha in the range {@code 0..255}
     * @return color with the requested alpha
     */
    static Color withAlpha(Color color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
    }

    /**
     * Linearly blends two colors, including alpha.
     *
     * @param from start color
     * @param to end color
     * @param amount blend amount, clamped to {@code 0.0..1.0}
     * @return blended color
     */
    static Color lerp(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    /**
     * Draws the paint elevated card.
     *
     * @param g graphics context
     * @param width width in pixels
     * @param height height in pixels
     * @param arc corner arc size
     * @param hover hover color
     */
    static void paintElevatedCard(Graphics2D g, int width, int height, int arc, float hover) {
        float h = Math.max(0f, Math.min(1f, hover));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int shadow = Theme.isDark() ? 3 : 4;
        int w = Math.max(0, width - 1);
        int surfaceHeight = Math.max(0, height - 1 - shadow);
        int baseShadowAlpha = Theme.isDark() ? 42 : 20;
        int shadowAlpha = Math.round(baseShadowAlpha * (0.72f + 0.50f * h));
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha))));
        g.fillRoundRect(2, shadow, Math.max(0, w - 3), surfaceHeight, arc, arc);
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha / 2))));
        g.fillRoundRect(1, Math.max(0, shadow - 1), Math.max(0, w - 1), surfaceHeight, arc, arc);
        Color top = lerp(Theme.CARD, Theme.isDark() ? Color.WHITE : Color.WHITE, Theme.isDark() ? 0.035f : 0.22f);
        Color bottom = lerp(Theme.CARD, Theme.isDark() ? Color.BLACK : Theme.BG, Theme.isDark() ? 0.06f : 0.05f);
        Paint previousPaint = g.getPaint();
        g.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, surfaceHeight), bottom));
        g.fillRoundRect(0, 0, w, surfaceHeight, arc, arc);
        g.setPaint(previousPaint);
        g.setColor(withAlpha(Color.WHITE, Theme.isDark() ? 18 : 145));
        g.drawLine(arc / 2, 1, Math.max(arc / 2, w - arc / 2), 1);
        g.setColor(lerp(Theme.CARD_BORDER, Theme.ACCENT, h * 0.65f));
        g.drawRoundRect(0, 0, w, surfaceHeight, arc, arc);
    }

    /**
     * Serializes a color as a CSS hexadecimal RGB literal.
     *
     * @param color display color
     * @return {@code #rrggbb} text
     */
    static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Returns the blend over.
     *
     * @param foreground foreground color
     * @param background background color
     * @return blend over
     */
    static Color blendOver(Color foreground, Color background) {
        double alpha = foreground.getAlpha() / 255.0;
        int red = blendChannel(foreground.getRed(), background.getRed(), alpha);
        int green = blendChannel(foreground.getGreen(), background.getGreen(), alpha);
        int blue = blendChannel(foreground.getBlue(), background.getBlue(), alpha);
        return new Color(red, green, blue);
    }

    /**
     * Returns the blend channel.
     *
     * @param foreground foreground color
     * @param background background color
     * @param alpha opacity value
     * @return blend channel
     */
    private static int blendChannel(int foreground, int background, double alpha) {
        return (int) Math.round(foreground * alpha + background * (1.0 - alpha));
    }
}
