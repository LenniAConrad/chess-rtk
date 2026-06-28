package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A press-and-hold confirmation button for destructive or expensive actions.
 *
 * <p>Instead of a single click, the user holds the button; a ring fills as the
 * hold progresses and the action fires only once the hold completes. Releasing
 * early cancels. This replaces a modal "are you sure?" with an in-place gesture
 * — used for clearing the console, cleaning logs, and stopping a run.</p>
 */
public final class HoldButton extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Animation frame delay (~60fps).
     */
    private static final int FRAME_MS = 16;

    /**
     * Hold duration before the action fires.
     */
    private static final int HOLD_MS = 700;

    /**
     * Diameter of the progress ring.
     */
    private static final int RING = 14;

    /**
     * Horizontal padding inside the button.
     */
    private static final int PAD_X = 12;

    /**
     * Gap between the ring and the label.
     */
    private static final int GAP = 8;

    /**
     * Button label.
     */
    private final String label;

    /**
     * Action fired once the hold completes.
     */
    private final transient Runnable onConfirm;

    /**
     * Whether this button styles itself as a destructive action.
     */
    private final boolean danger;

    /**
     * Frame timer driving the hold progress.
     */
    private final transient Timer timer;

    /**
     * Wall-clock time the current hold started, or 0 when idle.
     */
    private long holdStartedAt;

    /**
     * Whether the pointer is currently inside the button.
     */
    private boolean hovered;

    /**
     * Current eased hold progress, 0.0..1.0.
     */
    private double progress;

    /**
     * Creates a non-destructive hold button.
     *
     * @param label button label
     * @param onConfirm action fired when the hold completes
     */
    public HoldButton(String label, Runnable onConfirm) {
        this(label, onConfirm, false);
    }

    /**
     * Creates a hold button.
     *
     * @param label button label
     * @param onConfirm action fired when the hold completes
     * @param danger true to style the button as a destructive action
     */
    public HoldButton(String label, Runnable onConfirm, boolean danger) {
        this.label = label;
        this.onConfirm = onConfirm == null ? () -> {
            // no-op
        } : onConfirm;
        this.danger = danger || Theme.destructiveActionLabel(label);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Hold to " + label.toLowerCase(java.util.Locale.ROOT));
        setFont(Theme.font(13, Font.PLAIN));
        timer = new Timer(FRAME_MS, event -> tick());
        timer.setCoalesce(true);
        MouseAdapter mouse = new MouseAdapter() {
            /**
             * Starts the hold timer when pressed.
             *
             * @param event mouse press event
             */
            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled()) {
                    beginHold();
                }
            }

            /**
             * Cancels the hold timer when released.
             *
             * @param event mouse release event
             */
            @Override
            public void mouseReleased(MouseEvent event) {
                cancelHold();
            }

            /**
             * Marks the button as hovered.
             *
             * @param event mouse enter event
             */
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            /**
             * Clears hover state and cancels any active hold.
             *
             * @param event mouse exit event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                cancelHold();
            }
        };
        addMouseListener(mouse);
    }

    /**
     * Returns the accessible context for this custom hold button.
     *
     * @return accessible context
     */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleHoldButton();
        }
        return accessibleContext;
    }

    /**
     * Updates the enabled state, swapping the pointer cursor and cancelling any
     * in-progress hold so a disabled button neither invites a click nor lets a
     * hold complete after it was disabled.
     *
     * @param enabled true to enable the button
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!enabled && timer != null) {
            cancelHold();
        }
        repaint();
    }

    /**
     * Returns the preferred size from the label, ring, and padding.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = PAD_X * 2 + RING + GAP + metrics.stringWidth(label);
        return new Dimension(width, Theme.CONTROL_HEIGHT);
    }

    /**
     * Returns the maximum size pinned to the preferred size.
     *
     * @return maximum size
     */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * Begins a hold from the current frame.
     */
    private void beginHold() {
        holdStartedAt = System.currentTimeMillis();
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    /**
     * Cancels an in-progress hold.
     */
    private void cancelHold() {
        holdStartedAt = 0;
        timer.stop();
        progress = 0.0;
        repaint();
    }

    /**
     * Advances the hold progress, firing the action when it completes.
     */
    private void tick() {
        if (holdStartedAt == 0) {
            timer.stop();
            return;
        }
        double raw = Math.min(1.0, (System.currentTimeMillis() - holdStartedAt) / (double) HOLD_MS);
        progress = Ui.easeOutCubic(raw);
        if (raw >= 1.0) {
            holdStartedAt = 0;
            timer.stop();
            progress = 0.0;
            repaint();
            onConfirm.run();
            return;
        }
        repaint();
    }

    /**
     * Stops the timer when detached.
     */
    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /**
     * Paints the button surface, progress ring, and label.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean enabled = isEnabled();
            Theme.ButtonVariant variant = danger ? Theme.ButtonVariant.DESTRUCTIVE : Theme.ButtonVariant.SECONDARY;
            Color accent = Theme.buttonText(variant);
            Color fill;
            Color border;
            Color ring;
            if (!enabled) {
                fill = Theme.BUTTON_DISABLED_BG;
                border = Theme.BUTTON_DISABLED_BORDER;
                ring = Theme.BUTTON_DISABLED_BORDER;
            } else {
                fill = progress > 0
                        ? Theme.buttonPressed(variant)
                        : hovered ? Theme.buttonHover(variant) : Theme.buttonBackground(variant);
                border = progress > 0 ? accent : Theme.buttonBorder(variant);
                ring = accent;
            }
            g.setColor(fill);
            g.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g.setColor(border);
            g.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

            int ringX = PAD_X;
            int ringY = (h - RING) / 2;
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.withAlpha(ring, 80));
            g.drawOval(ringX, ringY, RING, RING);
            if (enabled && progress > 0) {
                g.setColor(accent);
                g.drawArc(ringX, ringY, RING, RING, 90, -(int) Math.round(progress * 360));
            }

            g.setColor(enabled ? Theme.buttonText(variant) : Theme.BUTTON_DISABLED_TEXT);
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            int textX = ringX + RING + GAP;
            int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(label, textX, textY);
        } finally {
            g.dispose();
        }
    }

    /**
     * Minimal accessible peer for the custom hold-to-confirm button.
     */
    private final class AccessibleHoldButton extends AccessibleJComponent {

        /**
         * Serialization identifier for Swing accessibility compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns the interactive role exposed to assistive technologies.
         *
         * @return push-button role
         */
        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PUSH_BUTTON;
        }
    }
}
