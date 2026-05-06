package application.gui.workbench;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JCheckBox;

/**
 * Compact switch-style checkbox for workbench boolean options.
 */
final class WorkbenchToggleBox extends JCheckBox {

    /**
     * Serialization identifier for Swing checkbox compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Outer horizontal padding.
     */
    private static final int PAD_X = 10;

    /**
     * Outer vertical padding.
     */
    private static final int PAD_Y = 5;

    /**
     * Gap between label and switch.
     */
    private static final int LABEL_GAP = 9;

    /**
     * Switch track width.
     */
    private static final int TRACK_WIDTH = 38;

    /**
     * Switch track height.
     */
    private static final int TRACK_HEIGHT = 20;

    /**
     * Switch thumb size.
     */
    private static final int THUMB_SIZE = 14;

    /**
     * Component height.
     */
    private static final int HEIGHT = 32;

    /**
     * Minimum row width that keeps settings labels readable.
     */
    private static final int MIN_WIDTH = 320;

    /**
     * Creates a toggle checkbox.
     *
     * @param text toggle label
     */
    WorkbenchToggleBox(String text) {
        super(text);
        setOpaque(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setForeground(WorkbenchTheme.TEXT);
        setFont(WorkbenchTheme.font(13, Font.PLAIN));
        setBorder(WorkbenchTheme.pad(0, 0, 0, 0));
    }

    /**
     * Returns the preferred switch size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = PAD_X * 2 + metrics.stringWidth(getText()) + LABEL_GAP + TRACK_WIDTH;
        return new Dimension(Math.max(MIN_WIDTH, width), HEIGHT);
    }

    /**
     * Paints the row, label, and switch.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintRow(g);
            paintLabel(g);
            paintTrack(g);
            if (isFocusOwner()) {
                paintFocus(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the row background.
     *
     * @param g graphics context
     */
    private void paintRow(Graphics2D g) {
        if (getModel().isRollover() || isFocusOwner()) {
            g.setColor(isEnabled() ? WorkbenchTheme.SECONDARY_BUTTON_HOVER : WorkbenchTheme.BUTTON_DISABLED_BG);
            g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 6, 6);
        }
    }

    /**
     * Paints the toggle label.
     *
     * @param g graphics context
     */
    private void paintLabel(Graphics2D g) {
        FontMetrics metrics = g.getFontMetrics(getFont());
        int trackX = trackX();
        int labelWidth = Math.max(0, trackX - LABEL_GAP - PAD_X);
        String label = WorkbenchUi.elide(getText(), metrics, labelWidth);
        g.setFont(getFont());
        g.setColor(isEnabled() ? WorkbenchTheme.TEXT : WorkbenchTheme.BUTTON_DISABLED_TEXT);
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
        g.drawString(label, PAD_X, baseline);
    }

    /**
     * Paints the switch track and thumb.
     *
     * @param g graphics context
     */
    private void paintTrack(Graphics2D g) {
        int x = trackX();
        int y = Math.max(0, (getHeight() - TRACK_HEIGHT) / 2 - 1);
        Color track = isSelected() ? WorkbenchTheme.TOGGLE_ON_TRACK : WorkbenchTheme.TOGGLE_TRACK;
        if (!isEnabled()) {
            track = WorkbenchTheme.BUTTON_DISABLED_BORDER;
        }
        g.setColor(track);
        g.fillRoundRect(x, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        int travel = TRACK_WIDTH - THUMB_SIZE - 6;
        int thumbX = x + 3 + (isSelected() ? travel : 0);
        int thumbY = y + 3;
        g.setColor(WorkbenchTheme.TOGGLE_THUMB);
        g.fillOval(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);
    }

    /**
     * Paints a focus ring.
     *
     * @param g graphics context
     */
    private void paintFocus(Graphics2D g) {
        g.setColor(WorkbenchTheme.TOGGLE_FOCUS);
        g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5), 6, 6);
    }

    /**
     * Returns the switch track x coordinate.
     *
     * @return track x coordinate
     */
    private int trackX() {
        return Math.max(PAD_X, getWidth() - PAD_X - TRACK_WIDTH);
    }

}
