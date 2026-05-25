package application.gui.workbench.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * In-workbench modal overlay. Mounts a content panel onto the host frame's
 * {@link JLayeredPane}, dims everything behind it, and traps focus and
 * escape until dismissed.
 *
 * <p>Replaces the previous "open a separate JDialog" pattern for things
 * like Settings or feature inspectors. Detached top-level OS windows are
 * jarring inside an editor-style workbench; an overlay keeps the user in
 * the same window context, which is how modern editor apps (VS Code,
 * Cursor, Atom) handle preference panes and similar transient surfaces.</p>
 */
public final class ModalOverlay {

    /**
     * Backdrop alpha when the overlay is active.
     */
    private static final float BACKDROP_ALPHA = 0.55f;

    /**
     * Backdrop fill colour.
     */
    private static final Color BACKDROP = new Color(0, 0, 0);

    /**
     * Host frame whose layered pane carries the overlay.
     */
    private final JFrame host;

    /**
     * Backdrop layer (dimmed clickable surface).
     */
    private final BackdropLayer backdropLayer = new BackdropLayer();

    /**
     * Centred wrapper that owns the content panel.
     */
    private final JPanel chrome = new JPanel(new BorderLayout());

    /**
     * Whether the overlay is currently mounted.
     */
    private boolean visible;

    /**
     * Last content panel installed in the chrome wrapper.
     */
    private JComponent content;

    /**
     * Last focus owner before the overlay opened, restored on dismissal.
     */
    private Component priorFocusOwner;

    /**
     * Client property under which a frame stores its shared overlay.
     */
    private static final String OVERLAY_CLIENT_KEY = "ChessRTK.ModalOverlay";

    /**
     * Returns the workbench overlay associated with the frame containing the
     * supplied component, creating it on first use. Lets components anywhere
     * in the tree open the in-window overlay without depending on a concrete
     * workbench class.
     *
     * @param near any component currently mounted in the workbench frame
     * @return shared overlay, or null when no JFrame ancestor exists
     */
    public static ModalOverlay forComponent(Component near) {
        if (near == null) {
            return null;
        }
        Component ancestor = near;
        while (ancestor != null && !(ancestor instanceof JFrame)) {
            ancestor = ancestor.getParent();
            if (ancestor == null && near instanceof JComponent jc) {
                ancestor = SwingUtilities.getWindowAncestor(jc);
            }
        }
        if (!(ancestor instanceof JFrame frame)) {
            return null;
        }
        JRootPane rootPane = frame.getRootPane();
        Object cached = rootPane.getClientProperty(OVERLAY_CLIENT_KEY);
        if (cached instanceof ModalOverlay overlay) {
            return overlay;
        }
        ModalOverlay overlay = new ModalOverlay(frame);
        rootPane.putClientProperty(OVERLAY_CLIENT_KEY, overlay);
        return overlay;
    }

    /**
     * Creates an overlay bound to one workbench frame.
     *
     * @param host workbench window
     */
    public ModalOverlay(JFrame host) {
        this.host = host;
        chrome.setOpaque(true);
        chrome.setBackground(Theme.PANEL_SOLID);
        chrome.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.LINE));
        backdropLayer.setLayout(new GridBagLayout());
        backdropLayer.setOpaque(false);
        backdropLayer.add(chrome, new GridBagConstraints());
        backdropLayer.addMouseListener(new MouseAdapter() {
            /**
             * Dismisses the overlay when the backdrop itself is pressed.
             *
             * @param event mouse event
             */
            @Override
            public void mousePressed(MouseEvent event) {
                // Click outside the content chrome dismisses the overlay,
                // matching VS Code's quick-pick / modal interaction.
                if (event.getComponent() == backdropLayer) {
                    hide();
                }
            }
        });
        backdropLayer.addKeyListener(new KeyAdapter() {
            /**
             * Dismisses the overlay when Escape is pressed.
             *
             * @param event key event
             */
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hide();
                }
            }
        });
        backdropLayer.setFocusable(true);
    }

    /**
     * Shows a content panel centred on the workbench. Replaces any
     * previously visible overlay content.
     *
     * @param body content component
     * @param preferredWidth preferred width in pixels
     * @param preferredHeight preferred height in pixels
     */
    public void show(JComponent body, int preferredWidth, int preferredHeight) {
        if (content != null) {
            chrome.remove(content);
        }
        content = body;
        body.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        chrome.add(body, BorderLayout.CENTER);
        chrome.revalidate();
        chrome.repaint();
        if (!visible) {
            JRootPane rootPane = host.getRootPane();
            JLayeredPane layered = rootPane.getLayeredPane();
            backdropLayer.setBounds(0, 0, rootPane.getWidth(), rootPane.getHeight());
            layered.add(backdropLayer, JLayeredPane.MODAL_LAYER);
            rootPane.revalidate();
            rootPane.repaint();
            priorFocusOwner = host.getFocusOwner();
            visible = true;
        }
        // Always re-size the backdrop on subsequent shows so window resizes
        // between opens still produce a correctly-sized dimmer.
        JRootPane rootPane = host.getRootPane();
        backdropLayer.setBounds(0, 0, rootPane.getWidth(), rootPane.getHeight());
        SwingUtilities.invokeLater(backdropLayer::requestFocusInWindow);
    }

    /**
     * Hides the overlay.
     */
    public void hide() {
        if (!visible) {
            return;
        }
        host.getRootPane().getLayeredPane().remove(backdropLayer);
        host.getRootPane().revalidate();
        host.getRootPane().repaint();
        visible = false;
        if (priorFocusOwner != null) {
            priorFocusOwner.requestFocusInWindow();
            priorFocusOwner = null;
        }
    }

    /**
     * Returns whether the overlay is currently displayed.
     *
     * @return true when visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Updates the backdrop bounds to follow the host frame size. Wired by
     * the host on component-resized events when the overlay is visible.
     */
    public void revalidateBounds() {
        if (!visible) {
            return;
        }
        JRootPane rootPane = host.getRootPane();
        backdropLayer.setBounds(0, 0, rootPane.getWidth(), rootPane.getHeight());
    }

    /**
     * Dimmed backdrop layer. Paints a semi-transparent black wash so the
     * underlying workbench reads as inactive while the overlay is up.
     */
    private static final class BackdropLayer extends JPanel {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Paints the inactive-workbench backdrop.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKDROP_ALPHA));
                g.setColor(BACKDROP);
                g.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns true for all points so the backdrop consumes mouse input.
         *
         * @param x x coordinate
         * @param y y coordinate
         * @return always true
         */
        @Override
        public boolean contains(int x, int y) {
            // The backdrop swallows all clicks within its bounds, so the
            // workbench behind cannot receive input while the overlay is up.
            return true;
        }
    }
}
