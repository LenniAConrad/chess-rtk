package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact circular activity indicator — a continuously rotating accent arc over
 * a faint track. It replaces the dated sliding indeterminate {@link
 * javax.swing.JProgressBar} for work whose total is unknown (e.g. a streaming
 * dataset scan), reading as a modern spinner rather than a marquee bar.
 *
 * <p>The component is transparent and follows the workbench animation
 * conventions: a coalescing 16&nbsp;ms {@link Timer} driven by wall-clock time
 * (resume-safe across repaints) that is stopped on detach and whenever the
 * spinner is idle, so it costs nothing while not spinning.</p>
 */
public final class Spinner extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Animation frame cadence, matching the workbench's other timers.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Milliseconds per full revolution.
     */
    private static final int REVOLUTION_MS = 1100;

    /**
     * Default edge length for the square spinner.
     */
    private static final int DEFAULT_SIZE = 18;

    /**
     * Angular length of the sweeping arc in degrees.
     */
    private static final double ARC_DEGREES = 290.0d;

    /**
     * Edge length used for preferred / minimum sizing.
     */
    private final int size;

    /**
     * Whether the spinner is currently animating.
     */
    private boolean spinning;

    /**
     * Wall-clock start time of the current spin, used to derive rotation.
     */
    private long spinStartedAt;

    /**
     * Timer advancing the rotation.
     */
    private final Timer timer = new Timer(ANIMATION_DELAY_MS, event -> repaint());

    /**
     * Creates a default-sized spinner, initially hidden and idle.
     */
    public Spinner() {
        this(DEFAULT_SIZE);
    }

    /**
     * Creates a spinner of the given square edge length, initially hidden.
     *
     * @param size square edge length in pixels
     */
    public Spinner(int size) {
        this.size = Math.max(12, size);
        setOpaque(false);
        setVisible(false);
        timer.setCoalesce(true);
    }

    /**
     * Starts or stops the spinner. While stopped it is hidden and consumes no
     * timer ticks; while started it is shown and rotates.
     *
     * @param on true to spin, false to stop and hide
     */
    public void setSpinning(boolean on) {
        if (on == spinning) {
            setVisible(on);
            return;
        }
        spinning = on;
        setVisible(on);
        if (on) {
            spinStartedAt = System.currentTimeMillis();
            if (!timer.isRunning()) {
                timer.start();
            }
        } else {
            timer.stop();
        }
        repaint();
    }

    /**
     * Stops the timer when the component is detached.
     */
    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /**
     * Returns the preferred square size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    /**
     * Returns the minimum square size.
     *
     * @return minimum size
     */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(size, size);
    }

    /**
     * Paints the track and the rotating accent arc.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        if (!spinning) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int diameter = Math.min(getWidth(), getHeight());
            float thickness = Math.max(2.0f, diameter * 0.14f);
            double inset = thickness / 2.0d + 1.0d;
            double box = diameter - 2 * inset;
            double x = (getWidth() - diameter) / 2.0d + inset;
            double y = (getHeight() - diameter) / 2.0d + inset;

            g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.withAlpha(Theme.NN_NEUTRAL, 140));
            g.draw(new Arc2D.Double(x, y, box, box, 0.0d, 360.0d, Arc2D.OPEN));

            double turns = (System.currentTimeMillis() - spinStartedAt) / (double) REVOLUTION_MS;
            double start = 90.0d - turns * 360.0d;
            g.setColor(Theme.ACCENT);
            g.draw(new Arc2D.Double(x, y, box, box, start, -ARC_DEGREES, Arc2D.OPEN));
        } finally {
            g.dispose();
        }
    }
}
