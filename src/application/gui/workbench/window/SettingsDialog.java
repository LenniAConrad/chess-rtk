package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.ModalOverlay;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Single settings home for the workbench, laid out like the VS Code settings
 * editor: a left category rail with an accent active-marker (the network
 * inspector shell language) and a scrollable content pane on the right, so a
 * long section is always reachable instead of overflowing off the dialog.
 *
 * <p>The content lives inside the main workbench window via {@link ModalOverlay};
 * opening Settings no longer spawns a separate OS window.</p>
 */
public final class SettingsDialog extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred overlay width.
     */
    private static final int PREFERRED_WIDTH = 840;

    /**
     * Preferred overlay height.
     */
    private static final int PREFERRED_HEIGHT = 580;

    /**
     * Fixed width of the left category rail.
     */
    private static final int NAV_WIDTH = 196;

    /**
     * Overlay backing the in-workbench presentation.
     */
    private final ModalOverlay overlay;

    /**
     * Left category rail.
     */
    private final JPanel nav;

    /**
     * Card-swapped content host on the right.
     */
    private final JPanel content;

    /**
     * Card layout switching the active section.
     */
    private final CardLayout contentLayout = new CardLayout();

    /**
     * Registered category rail items, in insertion order.
     */
    private final transient List<NavItem> navItems = new ArrayList<>();

    /**
     * Creates the settings dialog.
     *
     * @param overlay workbench overlay to mount into
     */
    public SettingsDialog(ModalOverlay overlay) {
        super(new BorderLayout());
        this.overlay = overlay;
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(Theme.pad(Theme.SPACE_MD));

        add(buildHeader(), BorderLayout.NORTH);

        nav = Ui.transparentPanel(null);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(Theme.pad(Theme.SPACE_SM, 0, 0, 0));
        JPanel navColumn = Ui.transparentPanel(new BorderLayout());
        navColumn.setPreferredSize(new Dimension(NAV_WIDTH, 0));
        navColumn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE),
                Theme.pad(0, 0, 0, Theme.SPACE_SM)));
        navColumn.add(nav, BorderLayout.NORTH);

        content = new JPanel(contentLayout);
        content.setOpaque(false);

        JPanel body = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        body.add(navColumn, BorderLayout.WEST);
        body.add(content, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    /**
     * Builds the title band with the close affordance.
     *
     * @return header component
     */
    private JComponent buildHeader() {
        JPanel header = Ui.transparentPanel(new BorderLayout());
        JPanel titleColumn = Ui.transparentPanel(new BorderLayout(0, 2));
        JLabel title = new JLabel("Settings");
        title.setFont(Theme.font(Theme.FONT_TITLE, Font.BOLD));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Workbench appearance, board, sound, and engine options");
        subtitle.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(subtitle, Theme.ForegroundRole.MUTED);
        titleColumn.add(title, BorderLayout.NORTH);
        titleColumn.add(subtitle, BorderLayout.CENTER);
        header.add(titleColumn, BorderLayout.WEST);
        JButton close = Ui.button("Close", false, event -> overlay.hide());
        JPanel closeWrap = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.add(close);
        header.add(closeWrap, BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(0, 0, Theme.SPACE_SM, 0)));
        return header;
    }

    /**
     * Re-applies the active theme to the dialog and its sections. The theme
     * toggle lives inside this dialog (Display &gt; Appearance), so toggling it
     * must refresh the dialog itself — otherwise its background and section chrome
     * keep the previous theme's colours until the dialog is reopened.
     */
    public void refreshTheme() {
        setBackground(Theme.BG);
        Theme.refreshComponentTree(this);
    }

    /**
     * Registers one settings section: a category rail entry plus its content,
     * wrapped in a scroll pane so a tall section is always reachable.
     *
     * @param sectionTitle section title shown on the rail
     * @param sectionBody section content
     */
    public void addSection(String sectionTitle, JComponent sectionBody) {
        JScrollPane scroll = Ui.scroll(Ui.fillViewport(wrap(sectionBody)));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        content.add(scroll, sectionTitle);

        NavItem item = new NavItem(sectionTitle);
        item.addActionListener(event -> selectSection(sectionTitle));
        nav.add(item);
        nav.add(Box.createVerticalStrut(2));
        navItems.add(item);
        if (navItems.size() == 1) {
            selectSection(sectionTitle);
        }
    }

    /**
     * Wraps a section body so its natural width is preserved while the scroll
     * pane owns the height.
     *
     * @param body section body
     * @return wrapped body
     */
    private static JComponent wrap(JComponent body) {
        JPanel holder = Ui.transparentPanel(new BorderLayout());
        holder.add(body, BorderLayout.NORTH);
        return holder;
    }

    /**
     * Selects the section with the supplied title when present.
     *
     * @param sectionTitle section title
     */
    public void selectSection(String sectionTitle) {
        contentLayout.show(content, sectionTitle);
        for (NavItem item : navItems) {
            item.setActive(item.title.equals(sectionTitle));
        }
    }

    /**
     * Shows the settings panel as a centred overlay on the workbench. The sheet
     * scales with the host window — a roomy editor-style surface on a large
     * window, a sensible floor on a small one — so a tall section reads as a
     * full settings editor and scrolls inside the rail instead of being clipped
     * by a cramped fixed-size box.
     */
    public void showCentered() {
        int hostW = overlay.hostWidth();
        int hostH = overlay.hostHeight();
        int width = hostW > 0
                ? clamp(Math.round(hostW * 0.72f), PREFERRED_WIDTH, 1040)
                : PREFERRED_WIDTH;
        int height = hostH > 0
                ? clamp(Math.round(hostH * 0.84f), PREFERRED_HEIGHT, 940)
                : PREFERRED_HEIGHT;
        overlay.show(this, width, height);
    }

    /**
     * Clamps a value into an inclusive range.
     *
     * @param value value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return clamped value
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * One left-rail category entry. Reads like a VS Code settings list row: a
     * full-width target that, when active, carries the accent left-stripe of the
     * network inspector shells over a quiet selected background.
     */
    private static final class NavItem extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Width of the accent active-marker stripe.
         */
        private static final int STRIPE = 3;

        /**
         * Section title this entry selects.
         */
        private final String title;

        /**
         * Whether this entry is the active section.
         */
        private boolean active;

        /**
         * Creates a rail entry.
         *
         * @param title section title
         */
        NavItem(String title) {
            super(title);
            this.title = title;
            setHorizontalAlignment(SwingConstants.LEFT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(Theme.pad(7, 12, 7, 10));
            setFont(Theme.font(13, Font.PLAIN));
            setForeground(Theme.MUTED);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT_TALL));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        /**
         * Sets the active state.
         *
         * @param value true when this is the selected section
         */
        void setActive(boolean value) {
            this.active = value;
            setForeground(value ? Theme.TEXT : Theme.MUTED);
            setFont(Theme.font(13, value ? Font.BOLD : Font.PLAIN));
            repaint();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g.setColor(Theme.SELECTION_SOLID);
                    g.fillRoundRect(STRIPE, 0, getWidth() - STRIPE - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(0, 0, STRIPE + Theme.RADIUS, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                    g.setClip(STRIPE, 0, getWidth(), getHeight());
                } else if (getModel().isRollover()) {
                    g.setColor(Theme.SECONDARY_BUTTON_HOVER);
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                            Theme.RADIUS, Theme.RADIUS);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
