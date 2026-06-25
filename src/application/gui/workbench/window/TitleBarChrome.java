package application.gui.workbench.window;

import application.gui.workbench.session.Job;
import application.gui.workbench.session.Session;
import application.gui.workbench.ui.Theme;
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
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Custom-painted title-bar controls for the editor-style Workbench shell.
 */
final class TitleBarChrome {

    /**
     * Prevents instantiation.
     */
    private TitleBarChrome() {
        // utility
    }

    /**
     * Creates a compact live runs affordance.
     *
     * @param session session model
     * @param openRuns callback for opening run output
     * @return runs component
     */
    static JComponent runs(Session session, Runnable openRuns) {
        Runs component = new Runs(session, openRuns);
        if (session != null) {
            session.jobs().addListener(() -> SwingUtilities.invokeLater(component::repaint));
        }
        return component;
    }

    /**
     * Base class for clickable title-bar paint components.
     */
    private abstract static class ChromeButton extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Click action.
         */
        private final Runnable action;

        /**
         * Whether the pointer is over the component.
         */
        protected boolean hovered;

        /**
         * Creates one chrome button.
         *
         * @param tooltip tooltip text
         * @param action click action
         */
        ChromeButton(String tooltip, Runnable action) {
            this.action = action == null ? () -> { } : action;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tooltip);
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        ChromeButton.this.action.run();
                    }
                }
            });
        }

        /**
         * Returns accessibility metadata for the custom-painted chrome button.
         *
         * @return accessible context
         */
        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    /**
                     * Serialization identifier for Swing compatibility.
                     */
                    private static final long serialVersionUID = 1L;
                };
            }
            return accessibleContext;
        }

        /**
         * Applies common text/rendering hints.
         *
         * @param graphics graphics context
         * @return configured graphics copy
         */
        protected Graphics2D graphics(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            return g;
        }
    }

    /**
     * Live run-count title-bar component.
     */
    private static final class Runs extends ChromeButton {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Session model backing the run count.
         */
        private final Session session;

        /**
         * Creates the runs pill.
         *
         * @param session session model
         * @param openRuns callback for opening run output
         */
        Runs(Session session, Runnable openRuns) {
            super("Open Run output", openRuns);
            this.session = session;
            getAccessibleContext().setAccessibleName("Run center status");
        }

        /**
         * Returns the preferred title-bar footprint.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(78, 26);
        }

        /**
         * Returns the maximum title-bar footprint.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        /**
         * Paints the run-status pill.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = graphics(graphics);
            try {
                int w = getWidth();
                int h = getHeight();
                if (hovered) {
                    g.setColor(Theme.TAB_HOVER);
                    g.fillRoundRect(0, 1, w - 1, h - 2, Theme.RADIUS, Theme.RADIUS);
                }
                g.setFont(Theme.font(11, Font.BOLD));
                FontMetrics metrics = g.getFontMetrics();
                int baseline = (h + metrics.getAscent() - metrics.getDescent()) / 2;
                g.setColor(Theme.TEXT);
                g.drawString("Runs", 8, baseline);
                paintBadge(g, w - 28, h / 2, runCount(), activeRunCount() > 0);
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns the bounded run history count.
         *
         * @return count for display
         */
        private int runCount() {
            return session == null ? 0 : Math.min(99, session.jobs().size());
        }

        /**
         * Returns the number of non-terminal jobs.
         *
         * @return active job count
         */
        private int activeRunCount() {
            if (session == null) {
                return 0;
            }
            int count = 0;
            for (Job job : session.jobs().recent()) {
                if (job != null && !job.status().isTerminal()) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Paints a numeric badge.
         *
         * @param g graphics context
         * @param cx center x
         * @param cy center y
         * @param value count value
         * @param active true when a run is active
         */
        private void paintBadge(Graphics2D g, int cx, int cy, int value, boolean active) {
            String text = Integer.toString(value);
            g.setFont(Theme.font(10, Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            int width = Math.max(18, metrics.stringWidth(text) + 8);
            int height = 18;
            g.setColor(active ? Theme.STATUS_RUNNING_TEXT : Theme.ACCENT);
            g.fillRoundRect(cx - width / 2, cy - height / 2, width, height, height, height);
            g.setColor(Theme.PRIMARY_BUTTON_TEXT);
            int x = cx - metrics.stringWidth(text) / 2;
            int baseline = cy + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(text, x, baseline);
        }
    }
}
