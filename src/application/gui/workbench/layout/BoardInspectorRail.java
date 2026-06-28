package application.gui.workbench.layout;

import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * Reusable right-hand inspector rail shared by the board-centric workbench
 * surfaces (Play, Draw, Relations, and Analyze; the merged Study surface next).
 * A rail stacks an optional status header above the mode's own inspector above
 * an optional shared move list, with the inspector / move-list boundary filling
 * the rail's full height.
 *
 * <p>This is the consolidation seam: modes supply only their mode-unique
 * controls and the board-common chrome — the move list, the height behaviour,
 * the fixed column width — lives here once. It replaces the per-mode
 * hand-rolled rail that pinned the move list to a fixed {@code 300px} south slot
 * inside a fixed {@code 360x560} block, which stranded a dead gap on tall
 * windows because the form was top-anchored and nothing absorbed the slack.</p>
 *
 * <p>The surface is configurable so Analyze can keep its intentionally lighter
 * {@code PANEL} document tone while Play/Draw/Relations sit on the darker board
 * {@code BACKDROP} wash.</p>
 */
public final class BoardInspectorRail extends SurfacePanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred rail width; the height tracks the canvas via the hosting split.
     */
    public static final int RAIL_WIDTH = 360;

    /**
     * Scroll pane wrapping the mode inspector, retained so callers can return it
     * to its first section after a mode (re)activates.
     */
    private final transient JScrollPane inspectorScroll;

    /**
     * Builds a rail on the board {@code BACKDROP} wash.
     *
     * @param header optional status/position header docked at the top, or null
     * @param inspector mode-unique controls (scrolled, given its natural height)
     * @param moveList optional shared move list that fills the remaining height,
     *     or null to let the inspector fill the rail alone
     */
    public BoardInspectorRail(JComponent header, JComponent inspector, JComponent moveList) {
        this(Theme.Surface.BACKDROP, header, inspector, moveList);
    }

    /**
     * Builds a rail on the given surface.
     *
     * @param surface rail surface tone ({@code BACKDROP} for the board modes,
     *     {@code PANEL} for the lighter Analyze document tone)
     * @param header optional status/position header docked at the top, or null
     * @param inspector mode-unique controls (scrolled, given its natural height)
     * @param moveList optional shared move list that fills the remaining height,
     *     or null to let the inspector fill the rail alone
     */
    public BoardInspectorRail(Theme.Surface surface, JComponent header, JComponent inspector,
            JComponent moveList) {
        super(new BorderLayout(0, Theme.SPACE_MD), surface);
        if (header != null) {
            add(header, BorderLayout.NORTH);
        }
        inspectorScroll = surface == Theme.Surface.PANEL
                ? Ui.scroll(Ui.fillViewport(inspector), () -> Theme.PANEL_SOLID)
                : Ui.scroll(Ui.fillViewport(inspector));
        inspectorScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inspectorScroll.setBorder(BorderFactory.createEmptyBorder());
        if (moveList == null) {
            add(inspectorScroll, BorderLayout.CENTER);
        } else {
            // A vertical split, not a fixed south slot: resizeWeight 0 hands
            // every extra pixel to the move list, so the column grows with the
            // canvas instead of leaving a gap. The divider must sit at the form's
            // natural height — a JScrollPane's own preferred height is too small
            // to drive it, so the form is seeded with that height and the
            // boundary is (re)seated once the split is genuinely tall enough
            // (early layout passes report a sliver, which would strand the form).
            int natural = Math.max(220, inspector.getPreferredSize().height);
            inspectorScroll.setPreferredSize(new Dimension(RAIL_WIDTH, natural));
            inspectorScroll.setMinimumSize(new Dimension(0, 160));
            moveList.setMinimumSize(new Dimension(0, 90));
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inspectorScroll, moveList);
            split.setResizeWeight(0.0d);
            split.setOpaque(false);
            split.setBorder(BorderFactory.createEmptyBorder());
            SplitPaneStyler.style(split);
            split.addComponentListener(new ComponentAdapter() {
                /**
                 * Whether the divider has settled at the form's natural height.
                 */
                private boolean seated;

                /**
                 * Seats the divider at the inspector's natural height once the
                 * split has room to show the whole form; before then it keeps a
                 * floor under the move list so neither side collapses.
                 *
                 * @param event component resize event
                 */
                @Override
                public void componentResized(ComponentEvent event) {
                    if (seated) {
                        return;
                    }
                    int height = split.getHeight();
                    if (height <= 0) {
                        return;
                    }
                    if (height >= natural + 120) {
                        seated = true;
                        split.setDividerLocation(natural);
                    } else {
                        split.setDividerLocation(Math.max(120, height - 120));
                    }
                }
            });
            add(split, BorderLayout.CENTER);
        }
        setPreferredSize(new Dimension(RAIL_WIDTH, 0));
    }

    /**
     * Returns the mode inspector to its first section (top of the scroll). Used
     * when a mode reactivates and should not inherit a prior scroll offset.
     */
    public void scrollInspectorToTop() {
        inspectorScroll.getVerticalScrollBar().setValue(0);
    }
}
