package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Inline collapsible section for optional information strips.
 */
final class CollapsibleSection extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Animation frame cadence for expand/collapse transitions.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Collapsible-section expand/collapse transition duration.
     */
    private static final int COLLAPSE_ANIMATION_MS = 145;

    /**
     * Section title.
     */
    private final String title;

    /**
     * Toggle button.
     */
    private final JButton toggle;

    /**
     * Collapsible content.
     */
    private final JComponent content;

    /**
     * Holder that gives expanded content consistent spacing below the
     * disclosure header.
     */
    private final JPanel contentHolder;

    /**
     * Current expansion state.
     */
    private boolean expanded;

    /**
     * Current animated content height.
     */
    private int animatedContentHeight;

    /**
     * Content height at the beginning of the active transition.
     */
    private int animationStartHeight;

    /**
     * Content height target for the active transition.
     */
    private int animationTargetHeight;

    /**
     * Wall-clock start time for the active expansion transition.
     */
    private long expansionAnimationStartedAt;

    /**
     * Whether the constructor has applied the initial state.
     */
    private boolean initialized;

    /**
     * Scroll-pane policy snapshots hidden during active expand/collapse
     * transitions.
     */
    private final transient List<ScrollPolicySnapshot> suspendedScrollPanes = new ArrayList<>();

    /**
     * Whether nested scroll bars are currently suspended for the transition.
     */
    private boolean scrollBarsSuspended;

    /**
     * Timer driving visible expand/collapse transitions.
     */
    private final Timer expansionTimer = new Timer(ANIMATION_DELAY_MS, event -> tickExpansionAnimation());

    /**
     * Creates an inline collapsible section.
     *
     * @param title section title
     * @param content collapsible content
     * @param expanded initial expansion state
     */
    CollapsibleSection(String title, JComponent content, boolean expanded) {
        super(new BorderLayout(0, 0));
        this.title = title == null || title.isBlank() ? "Info" : title;
        this.content = content;
        toggle = new DisclosureButton();
        toggle.addActionListener(event -> setExpanded(!this.expanded));
        expansionTimer.setCoalesce(true);
        // The holder always lays its content out at the content's FULL height
        // and simply clips it to the animated holder height (see ClipLayout).
        // A plain BorderLayout would instead squish the content to the partial
        // height each frame, so the text only reflowed to its real layout once
        // the section reached full size — the visible glitch this avoids.
        contentHolder = Ui.transparentPanel(new ClipLayout());
        contentHolder.setBorder(Theme.pad(Theme.SPACE_SM, 0, Theme.SPACE_SM, 0));
        contentHolder.add(content);
        setOpaque(false);
        setBackground(Theme.BG);
        setAlignmentX(LEFT_ALIGNMENT);
        add(toggle, BorderLayout.NORTH);
        add(contentHolder, BorderLayout.CENTER);
        setExpanded(expanded, false);
    }

    /**
     * Updates the expansion state.
     *
     * @param value true when expanded
     */
    void setExpanded(boolean value) {
        setExpanded(value, isShowing());
    }

    /**
     * Updates the expansion state, optionally animating visible sections.
     *
     * @param value true when expanded
     * @param animate true to animate the content height
     */
    private void setExpanded(boolean value, boolean animate) {
        if (initialized && expanded == value && !expansionTimer.isRunning()) {
            updateDisclosureText(value);
            return;
        }
        expanded = value;
        int targetHeight = value ? expandedContentHeight() : 0;
        updateDisclosureText(value);
        if (value) {
            contentHolder.setVisible(true);
            content.setVisible(true);
        }
        if (!animate) {
            expansionTimer.stop();
            animatedContentHeight = targetHeight;
            applyAnimatedContentHeight(targetHeight);
            finishExpansion(value);
            initialized = true;
            return;
        }
        animationStartHeight = currentAnimatedContentHeight();
        animationTargetHeight = targetHeight;
        expansionAnimationStartedAt = System.currentTimeMillis();
        if (animationStartHeight == animationTargetHeight) {
            animatedContentHeight = animationTargetHeight;
            finishExpansion(value);
            return;
        }
        suspendNestedScrollBars();
        if (!expansionTimer.isRunning()) {
            expansionTimer.start();
        }
        applyAnimatedContentHeight(animationStartHeight);
        initialized = true;
    }

    /**
     * Stops animation when the section leaves the component tree.
     */
    @Override
    public void removeNotify() {
        expansionTimer.stop();
        restoreNestedScrollBars();
        super.removeNotify();
    }

    /**
     * Advances one expand/collapse animation frame.
     */
    private void tickExpansionAnimation() {
        double progress = Math.min(1.0d,
                (System.currentTimeMillis() - expansionAnimationStartedAt)
                        / (double) COLLAPSE_ANIMATION_MS);
        double eased = Ui.easeOutCubic(progress);
        animatedContentHeight = (int) Math.round(animationStartHeight
                + (animationTargetHeight - animationStartHeight) * eased);
        applyAnimatedContentHeight(animatedContentHeight);
        if (progress >= 1.0d) {
            expansionTimer.stop();
            animatedContentHeight = animationTargetHeight;
            finishExpansion(expanded);
        }
    }

    /**
     * Updates text and tooltip for the disclosure row.
     *
     * @param value true when expanded
     */
    private void updateDisclosureText(boolean value) {
        toggle.setText(title);
        toggle.setToolTipText(value ? "Collapse " + title : "Expand " + title);
    }

    /**
     * Returns the target expanded content height.
     *
     * @return content height including holder insets
     */
    private int expandedContentHeight() {
        Insets insets = contentHolder.getInsets();
        return Math.max(0, content.getPreferredSize().height + insets.top + insets.bottom);
    }

    /**
     * Returns the current height used as animation start.
     *
     * @return current visible content height
     */
    private int currentAnimatedContentHeight() {
        if (!contentHolder.isVisible()) {
            return 0;
        }
        if (animatedContentHeight > 0) {
            return animatedContentHeight;
        }
        return Math.max(0, contentHolder.getHeight());
    }

    /**
     * Applies a clipped content-holder height and matching disclosure glyph
     * progress.
     *
     * @param height target holder height
     */
    private void applyAnimatedContentHeight(int height) {
        int safeHeight = Math.max(0, height);
        Insets insets = contentHolder.getInsets();
        int preferredWidth = Math.max(1, content.getPreferredSize().width
                + insets.left + insets.right);
        int expandedHeight = expandedContentHeight();
        Dimension size = new Dimension(preferredWidth, safeHeight);
        contentHolder.setPreferredSize(size);
        contentHolder.setMinimumSize(new Dimension(0, safeHeight));
        contentHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, safeHeight));
        contentHolder.setVisible(safeHeight > 0 || expanded);
        content.setVisible(safeHeight > 0 || expanded);
        applyDisclosureProgress(expandedHeight == 0
                ? (expanded ? 1.0d : 0.0d)
                : safeHeight / (double) expandedHeight);
        revalidate();
        repaint();
    }

    /**
     * Finalizes the section state after an animation or immediate update.
     *
     * @param value true when expanded
     */
    private void finishExpansion(boolean value) {
        if (value) {
            contentHolder.setPreferredSize(null);
            contentHolder.setMinimumSize(null);
            contentHolder.setMaximumSize(null);
            contentHolder.setVisible(true);
            content.setVisible(true);
            applyDisclosureProgress(1.0d);
            restoreNestedScrollBars();
        } else {
            applyAnimatedContentHeight(0);
            content.setVisible(false);
            contentHolder.setVisible(false);
            applyDisclosureProgress(0.0d);
            restoreNestedScrollBars();
        }
        revalidate();
        repaint();
    }

    /**
     * Temporarily hides nested scroll bars so partial-height animation frames do
     * not show transient scroll handles.
     */
    private void suspendNestedScrollBars() {
        if (scrollBarsSuspended) {
            return;
        }
        suspendedScrollPanes.clear();
        collectScrollPanes(contentHolder);
        for (ScrollPolicySnapshot snapshot : suspendedScrollPanes) {
            snapshot.scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            snapshot.scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        }
        scrollBarsSuspended = !suspendedScrollPanes.isEmpty();
    }

    /**
     * Restores nested scroll bars to their caller-selected policies.
     */
    private void restoreNestedScrollBars() {
        if (!scrollBarsSuspended) {
            return;
        }
        for (ScrollPolicySnapshot snapshot : suspendedScrollPanes) {
            snapshot.scrollPane.setVerticalScrollBarPolicy(snapshot.verticalPolicy);
            snapshot.scrollPane.setHorizontalScrollBarPolicy(snapshot.horizontalPolicy);
        }
        suspendedScrollPanes.clear();
        scrollBarsSuspended = false;
    }

    /**
     * Collects nested scroll panes whose bars should be hidden during animation.
     *
     * @param component component tree root
     */
    private void collectScrollPanes(Component component) {
        if (component instanceof JScrollPane pane) {
            suspendedScrollPanes.add(new ScrollPolicySnapshot(pane,
                    pane.getVerticalScrollBarPolicy(), pane.getHorizontalScrollBarPolicy()));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectScrollPanes(child);
            }
        }
    }

    /**
     * Original scroll-bar policies for one nested scroll pane.
     *
     * @param scrollPane scroll pane
     * @param verticalPolicy vertical scroll-bar policy
     * @param horizontalPolicy horizontal scroll-bar policy
     */
    private record ScrollPolicySnapshot(
            JScrollPane scrollPane,
            int verticalPolicy,
            int horizontalPolicy) {
        // data carrier
    }

    /**
     * Lays the single child out at its full preferred height, top-anchored,
     * regardless of the (animating) holder height — so the holder clips the
     * content rather than squishing it. The content keeps one stable layout for
     * the whole transition, so its text never reflows mid-animation.
     */
    private static final class ClipLayout implements LayoutManager {

        /**
         * Accepts a child without named constraints.
         *
         * @param name ignored constraint
         * @param component child
         */
        @Override
        public void addLayoutComponent(String name, Component component) {
            // no named constraints
        }

        /**
         * Removes a child.
         *
         * @param component child
         */
        @Override
        public void removeLayoutComponent(Component component) {
            // no cached state
        }

        /**
         * Returns the full content size plus holder insets.
         *
         * @param parent holder container
         * @return preferred size
         */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            Component child = child(parent);
            Dimension size = child == null ? new Dimension() : child.getPreferredSize();
            return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
        }

        /**
         * Returns only the holder insets so the holder may clip to any height.
         *
         * @param parent holder container
         * @return minimum size
         */
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
        }

        /**
         * Lays the child out at full preferred height, top-anchored.
         *
         * @param parent holder container
         */
        @Override
        public void layoutContainer(Container parent) {
            Component child = child(parent);
            if (child == null) {
                return;
            }
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
            child.setBounds(insets.left, insets.top, width, child.getPreferredSize().height);
        }

        /**
         * Returns the holder's single content child.
         *
         * @param parent holder container
         * @return content child, or null when empty
         */
        private static Component child(Container parent) {
            return parent.getComponentCount() > 0 ? parent.getComponent(0) : null;
        }
    }

    /**
     * Updates the disclosure glyph progress.
     *
     * @param progress expansion progress from 0 to 1
     */
    private void applyDisclosureProgress(double progress) {
        if (toggle instanceof DisclosureButton disclosure) {
            disclosure.setExpansionProgress(progress);
        }
    }

    /**
     * Flat disclosure header that reads like a VS Code section strip instead
     * of another nested card.
     */
    private static final class DisclosureButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Visual glyph progress from right-facing to down-facing.
         */
        private double expansionProgress;

        /**
         * Creates the disclosure header.
         */
        DisclosureButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setRolloverEnabled(true);
            setMargin(new Insets(0, 0, 0, 0));
            setBorder(Theme.pad(Theme.SPACE_XS, 20, Theme.SPACE_XS, 0));
            setPreferredSize(new Dimension(120, Theme.CONTROL_HEIGHT));
        }

        /**
         * Updates the visual disclosure glyph progress.
         *
         * @param value progress from 0.0 collapsed to 1.0 expanded
         */
        private void setExpansionProgress(double value) {
            expansionProgress = Math.max(0.0d, Math.min(1.0d, value));
            repaint();
        }

        /**
         * Paints a full-width disclosure row.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            setFont(Theme.font(11, java.awt.Font.BOLD));
            setForeground(isEnabled() ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                if (getModel().isRollover() || isFocusOwner()) {
                    g.setColor(Theme.SECONDARY_BUTTON_HOVER);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isEnabled() ? Theme.MUTED : Theme.BUTTON_DISABLED_TEXT);
                double cy = getHeight() / 2.0d;
                AffineTransform oldTransform = g.getTransform();
                g.rotate(expansionProgress * Math.PI / 2.0d, 9.0d, cy);
                Path2D chevron = new Path2D.Double();
                chevron.moveTo(7.0d, cy - 4.0d);
                chevron.lineTo(11.0d, cy);
                chevron.lineTo(7.0d, cy + 4.0d);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(chevron);
                g.setTransform(oldTransform);
                g.setColor(Theme.LINE);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
