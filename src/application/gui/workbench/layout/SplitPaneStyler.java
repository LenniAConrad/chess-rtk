package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Shared VS Code-style split pane styling.
 */
public final class SplitPaneStyler {

    /**
     * VS Code's sash uses a compact four-pixel interaction strip.
     */
    private static final int SASH_SIZE = 4;

    /**
     * Idle separator alpha. Kept barely visible so the divider remains
     * discoverable in Swing while still reading as a VS Code-style sash.
     */
    private static final int IDLE_SEPARATOR_ALPHA = 95;

    /**
     * Hover separator alpha.
     */
    private static final int HOVER_SEPARATOR_ALPHA = 205;

    /**
     * Animation frame cadence for sash hover/drag feedback.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Hover/drag transition duration in milliseconds.
     */
    private static final int SASH_TRANSITION_MS = 110;

    /**
     * Prevents instantiation.
     */
    private SplitPaneStyler() {
        // utility
    }

    /**
     * Styles a split pane as a quiet resizable VS Code-style workbench divider.
     *
     * @param pane split pane
     */
    public static void style(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            /**
             * {@inheritDoc}
             */
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
    return new SashDivider(this);
            }
        });
        pane.setOneTouchExpandable(false);
        pane.setDividerSize(SASH_SIZE);
        pane.setContinuousLayout(true);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(Theme.BG);
    }

    /**
     * Builds a horizontal split pane wired with the workbench sash and the
     * given resize weight (used as both the resize weight and the initial
     * divider location). Centralises the split-pane boilerplate that was copied
     * across every board/analysis/tool tab.
     *
     * @param left leading component
     * @param right trailing component
     * @param weight resize weight and initial divider location (0..1)
     * @return the styled split pane
     */
    public static JSplitPane styledHorizontalSplit(java.awt.Component left, java.awt.Component right,
            double weight) {
        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        pane.setOpaque(false);
        pane.setResizeWeight(weight);
        pane.setDividerLocation(weight);
        SplitPaneStyler.style(pane);
        return pane;
    }

    /**
     * Builds a vertical split pane wired with the workbench sash, for stacking
     * two independently scrollable regions (e.g. a setup form above a list).
     *
     * @param top leading component
     * @param bottom trailing component
     * @param weight resize weight and initial divider location (0..1)
     * @return the styled split pane
     */
    public static JSplitPane styledVerticalSplit(java.awt.Component top, java.awt.Component bottom,
            double weight) {
        JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        pane.setOpaque(false);
        pane.setResizeWeight(weight);
        pane.setDividerLocation(weight);
        SplitPaneStyler.style(pane);
        return pane;
    }

    /**
     * Thin split-pane divider modelled after VS Code's transparent sash: no
     * grip, quiet at rest, accent colored only on hover/drag.
     */
    private static final class SashDivider extends BasicSplitPaneDivider {

        /**
         * Serialization identifier for Swing divider compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Whether the pointer is hovering over the sash.
         */
        private boolean hover;

        /**
         * Whether the sash is currently being dragged.
         */
        private boolean active;

        /**
         * Current visual progress from idle to active.
         */
        private double visualProgress;

        /**
         * Visual progress at the beginning of the active transition.
         */
        private double transitionStartProgress;

        /**
         * Target visual progress for the active transition.
         */
        private double transitionTargetProgress;

        /**
         * Wall-clock start time for the active sash transition.
         */
        private long transitionStartedAt;

        /**
         * Timer driving the sash transition.
         */
        private final Timer transitionTimer = new Timer(ANIMATION_DELAY_MS, event -> tickTransition());

        /**
         * Creates a sash divider.
         *
         * @param ui owning split pane UI
         */
        SashDivider(BasicSplitPaneUI ui) {
            super(ui);
            setBorder(BorderFactory.createEmptyBorder());
            transitionTimer.setCoalesce(true);
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    setHover(true);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    setHover(false);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    setActive(true);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseReleased(MouseEvent event) {
                    setActive(false);
                }
            });
        }

        /**
         * Updates hover state.
         *
         * @param value true when hovered
         */
        private void setHover(boolean value) {
            if (hover != value) {
                hover = value;
                animateToTarget();
            }
        }

        /**
         * Updates drag state.
         *
         * @param value true while dragging
         */
        private void setActive(boolean value) {
            if (active != value) {
                active = value;
                animateToTarget();
            }
        }

        /**
         * Stops the transition timer when the divider leaves the component tree.
         */
        @Override
        public void removeNotify() {
            transitionTimer.stop();
            super.removeNotify();
        }

        /**
         * Starts a transition toward the current hover/drag target.
         */
        private void animateToTarget() {
            transitionStartProgress = visualProgress;
            transitionTargetProgress = active || hover ? 1.0d : 0.0d;
            transitionStartedAt = System.currentTimeMillis();
            if (Math.abs(transitionStartProgress - transitionTargetProgress) < 0.001d) {
                visualProgress = transitionTargetProgress;
                transitionTimer.stop();
            } else if (!transitionTimer.isRunning()) {
                transitionTimer.start();
            }
            repaint();
        }

        /**
         * Advances the hover/drag transition.
         */
        private void tickTransition() {
            double progress = Math.min(1.0d,
                    (System.currentTimeMillis() - transitionStartedAt)
                            / (double) SASH_TRANSITION_MS);
            visualProgress = transitionStartProgress
                    + (transitionTargetProgress - transitionStartProgress) * Ui.easeOutCubic(progress);
            if (progress >= 1.0d) {
                visualProgress = transitionTargetProgress;
                transitionTimer.stop();
            }
            repaint();
        }

        /**
         * Paints a transparent VS Code-style sash.
         *
         * @param graphics graphics context
         */
        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int alpha = interpolate(IDLE_SEPARATOR_ALPHA, HOVER_SEPARATOR_ALPHA, visualProgress);
                Color base = blend(Theme.LINE, Theme.ACCENT, visualProgress);
                Color color = Theme.withAlpha(base, alpha);
                g.setColor(color);
                if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    int x = Math.max(0, (getWidth() - 1) / 2);
                    g.drawLine(x, 0, x, getHeight());
                } else {
                    int y = Math.max(0, (getHeight() - 1) / 2);
                    g.drawLine(0, y, getWidth(), y);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Interpolates an integer value.
         *
         * @param from start value
         * @param to target value
         * @param progress progress from 0 to 1
         * @return interpolated value
         */
        private static int interpolate(int from, int to, double progress) {
            return (int) Math.round(from + (to - from) * progress);
        }

        /**
         * Blends two colors.
         *
         * @param from start color
         * @param to target color
         * @param progress progress from 0 to 1
         * @return blended color
         */
        private static Color blend(Color from, Color to, double progress) {
            double amount = Math.max(0.0d, Math.min(1.0d, progress));
            return new Color(
                    interpolate(from.getRed(), to.getRed(), amount),
                    interpolate(from.getGreen(), to.getGreen(), amount),
                    interpolate(from.getBlue(), to.getBlue(), amount));
        }
    }
}
