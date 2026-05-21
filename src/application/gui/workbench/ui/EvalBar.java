package application.gui.workbench.ui;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.window.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Smooth vertical engine-evaluation bar for the board view.
 */
public final class EvalBar extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred bar width.
     */
    private static final int BAR_WIDTH = 38;

    /**
     * Minimum bar height.
     */
    private static final int BAR_HEIGHT = 520;

    /**
     * Animation frame delay.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Score transition duration.
     */
    private static final int ANIMATION_DURATION_MS = 140;

    /**
     * Mate display centipawn equivalent.
     */
    private static final int MATE_CP = 3000;

    /**
     * Centipawn scale for tanh mapping.
     */
    private static final double CP_SCALE = 650.0;

    /**
     * Dark side fill.
     */
    private static final Color BLACK_FILL = Theme.EVAL_BLACK;

    /**
     * Light side fill.
     */
    private static final Color WHITE_FILL = Theme.EVAL_WHITE;

    /**
     * Bar frame color.
     */
    private static final Color FRAME = Theme.EVAL_FRAME;

    /**
     * Neutral divider color.
     */
    private static final Color DIVIDER = Theme.EVAL_DIVIDER;

    /**
     * Animated visible white share.
     */
    private double displayedWhiteShare = 0.5;

    /**
     * Target white share.
     */
    private double targetWhiteShare = 0.5;

    /**
     * White share at the start of the current transition.
     */
    private double animationStartShare = 0.5;

    /**
     * Start time of the current transition.
     */
    private long animationStartedAt;

    /**
     * Current score label.
     */
    private String label = "0.00";

    /**
     * Animation timer.
     */
    private final Timer timer = new Timer(ANIMATION_DELAY_MS, event -> tick());

    /**
     * Cached frame stroke.
     */
    private static final BasicStroke FRAME_STROKE = new BasicStroke(1.2f);

    /**
     * Reusable clip rectangle (mutated each frame).
     */
    private final RoundRectangle2D.Double clipRect = new RoundRectangle2D.Double();

    /**
     * Creates the eval bar.
     */
    public EvalBar() {
        setOpaque(true);
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(BAR_WIDTH, BAR_HEIGHT));
        setMinimumSize(new Dimension(BAR_WIDTH, 260));
        timer.setCoalesce(true);
        setToolTipText("Engine evaluation bar (positive = White advantage in pawns; '#n' = mate in n)");
        javax.accessibility.AccessibleContext context = getAccessibleContext();
        if (context != null) {
            context.setAccessibleName("Engine evaluation bar");
        }
    }

    /**
     * Stops the animation timer when the bar is detached so a recreated bar
     * does not leak via the ticker's ActionListener.
     */
    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /**
     * Marks the bar as waiting for engine output.
     */
    public void setThinking() {
        label = "";
        targetWhiteShare = displayedWhiteShare;
        animationStartedAt = 0L;
        timer.stop();
        repaint();
    }

    /**
     * Shows an unavailable state.
     *
     * @param message status text
     */
    public void setUnavailable(String message) {
        label = message == null || message.isBlank() ? "n/a" : message;
        setTargetWhiteShare(0.5);
    }

    /**
     * Applies a centipawn score from White's perspective.
     *
     * @param whiteCentipawns centipawns from White's perspective
     */
    public void setCentipawns(int whiteCentipawns) {
        label = formatCentipawns(whiteCentipawns);
        setTargetWhiteShare(whiteShareForCentipawns(whiteCentipawns));
    }

    /**
     * Applies a mate score from White's perspective.
     *
     * @param whiteMate signed mate distance from White's perspective
     */
    public void setMate(int whiteMate) {
        // whiteMate == 0 is ambiguous from this signature alone (engines emit
        // "mate 0" to mean "side to move has just been mated"); render the bar
        // centered rather than guessing white wins. Callers that know who got
        // mated can call setMateDelivered(boolean) instead.
        int cp;
        if (whiteMate > 0) {
            cp = MATE_CP;
        } else if (whiteMate < 0) {
            cp = -MATE_CP;
        } else {
            cp = 0;
        }
        label = "#" + whiteMate;
        setTargetWhiteShare(whiteShareForCentipawns(cp));
    }

    /**
     * Applies an already-delivered mate ("mate 0" from the side-to-move).
     *
     * @param whiteWasMated true when White is the side that has been mated
     */
    public void setMateDelivered(boolean whiteWasMated) {
        label = "#0";
        setTargetWhiteShare(whiteShareForCentipawns(whiteWasMated ? -MATE_CP : MATE_CP));
    }

    /**
     * Sets a target white share and starts animation.
     *
     * @param value target white share
     */
    private void setTargetWhiteShare(double value) {
        double next = clamp(value, 0.02, 0.98);
        if (Math.abs(next - displayedWhiteShare) < 0.0001) {
            displayedWhiteShare = next;
            targetWhiteShare = next;
            animationStartedAt = 0L;
            timer.stop();
            repaint();
            return;
        }
        animationStartShare = displayedWhiteShare;
        animationStartedAt = System.currentTimeMillis();
        targetWhiteShare = next;
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint();
    }

    /**
     * Advances the smooth animation.
     */
    private void tick() {
        if (animationStartedAt == 0L) {
            displayedWhiteShare = targetWhiteShare;
            timer.stop();
        } else {
            double progress = clamp((System.currentTimeMillis() - animationStartedAt)
                    / (double) ANIMATION_DURATION_MS, 0.0, 1.0);
            double eased = easeInOutCubic(progress);
            displayedWhiteShare = animationStartShare + (targetWhiteShare - animationStartShare) * eased;
            if (progress >= 1.0) {
                displayedWhiteShare = targetWhiteShare;
                animationStartedAt = 0L;
                timer.stop();
            }
        }
        repaint();
    }

    /**
     * Paints the evaluation bar.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Fill the whole component so the visible bar matches the board
            // square height exactly; the board stage sizes us to it.
            int x = 0;
            int y = 0;
            int w = Math.max(12, getWidth());
            int h = Math.max(40, getHeight());
            int whiteHeight = (int) Math.round(h * displayedWhiteShare);
            int split = y + h - whiteHeight;

            Shape oldClip = g.getClip();
            clipRect.setRoundRect(x, y, w, h, 3, 3);
            g.clip(clipRect);
            paintBarFill(g, x, y, w, h, split);
            g.setClip(oldClip);
            g.setStroke(FRAME_STROKE);
            g.setColor(FRAME);
            g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);
            paintLabel(g, x, y, w, h);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the clipped two-color bar fill without a rounded middle seam.
     *
     * @param g graphics context
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param split split y coordinate
     */
    private void paintBarFill(Graphics2D g, int x, int y, int w, int h, int split) {
        g.setColor(BLACK_FILL);
        g.fillRect(x, y, w, h);
        g.setColor(WHITE_FILL);
        g.fillRect(x, split, w, y + h - split);
        g.setColor(DIVIDER);
        g.drawLine(x + 2, split, x + w - 3, split);
    }

    /**
     * Paints the score label.
     *
     * @param g graphics context
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintLabel(Graphics2D g, int x, int y, int w, int h) {
        if (label.isBlank()) {
            return;
        }
        g.setFont(Theme.font(11, Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();
        String text = Ui.elide(label, metrics, Math.max(16, w - 5));
        int tx = x + (w - metrics.stringWidth(text)) / 2;
        int ty = y + h / 2 + metrics.getAscent() / 2 - 2;
        Color fill = displayedWhiteShare > 0.5 ? BLACK_FILL : WHITE_FILL;
        Color halo = displayedWhiteShare > 0.5 ? WHITE_FILL : BLACK_FILL;
        // Halo keeps the label legible while the seam crosses through it
        // during the animation between black-favoured and white-favoured states.
        g.setColor(halo);
        g.drawString(text, tx - 1, ty);
        g.drawString(text, tx + 1, ty);
        g.drawString(text, tx, ty - 1);
        g.drawString(text, tx, ty + 1);
        g.setColor(fill);
        g.drawString(text, tx, ty);
    }

    /**
     * Maps centipawns to the white-side share of the bar.
     *
     * @param whiteCentipawns centipawns from White's perspective
     * @return white share
     */
    public static double whiteShareForCentipawns(int whiteCentipawns) {
        return 0.5 + Math.tanh(whiteCentipawns / CP_SCALE) * 0.45;
    }

    /**
     * Formats a centipawn value as pawns.
     *
     * @param cp centipawns
     * @return label
     */
    public static String formatCentipawns(int cp) {
        return String.format(java.util.Locale.ROOT, "%+.2f", cp / 100.0);
    }

    /**
     * Applies a smooth symmetric cubic curve to animation progress.
     *
     * @param value linear progress
     * @return eased progress
     */
    public static double easeInOutCubic(double value) {
        double progress = clamp(value, 0.0, 1.0);
        if (progress < 0.5) {
            return 4.0 * progress * progress * progress;
        }
        return 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0;
    }

    /**
     * Clamps a value.
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
