package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A VS Code-style editor tab: a flat rectangle showing the panel name with a
 * close affordance. The active tab is lifted onto the panel surface and
 * underlined in the accent colour; every tab carries an {@code x} that hides
 * it from the strip.
 */
final class EditorTab extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Tab height.
     */
    private static final int HEIGHT = 30;

    /**
     * Horizontal text padding.
     */
    private static final int PAD = 11;

    /**
     * Size of the square close-button hit region.
     */
    private static final int CLOSE = 16;

    /**
     * Minimum pointer travel before a press turns into a tab drag.
     */
    private static final int DRAG_THRESHOLD_PX = 5;

    /**
     * Panel display name.
     */
    private final String name;

    /**
     * Whether this tab is the active one in its strip.
     */
    private boolean selected;

    /**
     * Whether this tab belongs to the active editor group.
     */
    private boolean paneActive = true;

    /**
     * Whether the pointer is over the close region.
     */
    private boolean closeHover;

    /**
     * Whether the pointer is over the tab.
     */
    private boolean hover;

    /**
     * Drag callback invoked with the live mouse point in this tab's own
     * coordinate space; null when dragging is not wired.
     */
    private transient Consumer<Point> onDrag;

    /**
     * Drop callback invoked when a drag ends.
     */
    private transient Runnable onDrop;

    /**
     * Whether a reorder drag is in progress.
     */
    private boolean dragging;

    /**
     * Press point used for drag thresholding.
     */
    private Point pressPoint;

    /**
     * Whether the current press started outside the close affordance and may
     * become a drag.
     */
    private boolean dragArmed;

    /**
     * Suppresses the click event generated after a completed drag.
     */
    private boolean suppressClick;

    /**
     * Creates a tab.
     *
     * @param name panel display name
     * @param onSelect selection callback
     * @param onClose close callback
     */
    public EditorTab(String name, Runnable onSelect, Runnable onClose) {
        this.name = name;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                if (suppressClick) {
                    suppressClick = false;
                    return;
                }
                if (closeRegion().contains(event.getPoint())) {
                    onClose.run();
                } else {
                    onSelect.run();
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                pressPoint = event.getPoint();
                dragArmed = SwingUtilities.isLeftMouseButton(event)
                        && !closeRegion().contains(event.getPoint());
                dragging = false;
                suppressClick = false;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (onDrag != null && dragArmed) {
                    if (!dragging && !dragPastThreshold(event.getPoint())) {
                        return;
                    }
                    dragging = true;
                    suppressClick = true;
                    onDrag.accept(event.getPoint());
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (dragging) {
                    dragging = false;
                    if (onDrop != null) {
                        onDrop.run();
                    }
                }
                dragArmed = false;
                pressPoint = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(true, closeRegion().contains(event.getPoint()));
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                updateHover(true, closeRegion().contains(event.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(false, false);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText(name + " - right-click for tab actions");
    }

    /**
     * Returns whether a pointer has moved far enough to start a drag.
     *
     * @param point current pointer
     * @return true when the drag threshold is crossed
     */
    private boolean dragPastThreshold(Point point) {
        if (pressPoint == null || point == null) {
            return false;
        }
        int dx = point.x - pressPoint.x;
        int dy = point.y - pressPoint.y;
        return dx * dx + dy * dy >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
    }

    /**
     * Wires drag handling. The {@code onDrag} callback receives the live mouse
     * point in this tab's coordinate space; {@code onDrop} fires once when the
     * drag ends.
     *
     * @param onDrag live drag callback
     * @param onDrop drag-end callback
     */
    public void setDragHandler(Consumer<Point> onDrag, Runnable onDrop) {
        this.onDrag = onDrag;
        this.onDrop = onDrop;
    }

    /**
     * Sets the tab's active state.
     *
     * @param value true when this tab is active
     */
    public void setSelected(boolean value) {
        if (selected != value) {
            selected = value;
            repaint();
        }
    }

    /**
     * Sets whether the owning pane is the active editor group.
     *
     * @param value true when this tab's pane is active
     */
    public void setPaneActive(boolean value) {
        if (paneActive != value) {
            paneActive = value;
            repaint();
        }
    }

    /**
     * Updates hover state.
     *
     * @param overTab true when the pointer is over the tab
     * @param overClose true when the pointer is over the close region
     */
    private void updateHover(boolean overTab, boolean overClose) {
        if (hover != overTab || closeHover != overClose) {
            hover = overTab;
            closeHover = overClose;
            repaint();
        }
    }

    /**
     * Returns the close-button hit region.
     *
     * @return close region rectangle
     */
    private Rectangle closeRegion() {
    return new Rectangle(getWidth() - PAD - CLOSE, (HEIGHT - CLOSE) / 2, CLOSE, CLOSE);
    }

    /**
     * Returns the preferred tab size based on its text and close affordance.
     *
     * @return preferred tab size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.font(12, Font.PLAIN));
        int width = PAD + fm.stringWidth(name) + 8 + CLOSE + PAD;
    return new Dimension(width, HEIGHT);
    }

    /**
     * Returns the maximum tab size, fixed to the preferred size so tab strips
     * stay stable during hover and drag state changes.
     *
     * @return maximum tab size
     */
    @Override
    public Dimension getMaximumSize() {
    return getPreferredSize();
    }

    /**
     * Returns the minimum tab size, fixed to the preferred size so tab strips
     * stay stable during hover and drag state changes.
     *
     * @return minimum tab size
     */
    @Override
    public Dimension getMinimumSize() {
    return getPreferredSize();
    }

    /**
     * Paints the flat tab body, active underline, label, separator, and close
     * affordance.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color background = selected ? (paneActive ? Theme.PANEL_SOLID : Theme.ELEVATED_SOLID)
                    : hover ? Theme.TAB_HOVER : Theme.BG;
            g.setColor(background);
            g.fillRect(0, 0, w, h);
            // Trailing separator between tabs.
            g.setColor(Theme.LINE);
            g.drawLine(w - 1, 4, w - 1, h - 4);
            // Accent underline marks the active tab.
            if (selected) {
                g.setColor(paneActive ? Theme.ACCENT : Theme.LINE);
                g.fillRect(0, h - 2, w, 2);
            }
            g.setFont(Theme.font(12, selected ? Font.BOLD : Font.PLAIN));
            g.setColor(selected && paneActive ? Theme.TEXT : Theme.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(name, PAD, (h + fm.getAscent() - fm.getDescent()) / 2);
            paintClose(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the close glyph, with a soft circle when hovered.
     *
     * @param g graphics
     */
    private void paintClose(Graphics2D g) {
        Rectangle region = closeRegion();
        if (closeHover) {
            g.setColor(Theme.SECONDARY_BUTTON_PRESSED);
            g.fillRoundRect(region.x, region.y, region.width, region.height, 6, 6);
        }
        if (!selected && !hover) {
            return;
        }
        g.setColor(closeHover ? Theme.TEXT : Theme.MUTED);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = region.x + region.width / 2;
        int cy = region.y + region.height / 2;
        int r = 3;
        g.drawLine(cx - r, cy - r, cx + r, cy + r);
        g.drawLine(cx - r, cy + r, cx + r, cy - r);
    }
}
