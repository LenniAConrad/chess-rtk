package application.gui.workbench;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * Lays out the board stage: the chessboard fills the stage and the engine
 * evaluation bar is pinned flush against the left edge of the board's rendered
 * square, matched exactly to the board's height.
 *
 * <p>A plain {@code BorderLayout.WEST} placement stretched the eval bar over
 * the whole stage height and left it floating far from the board whenever the
 * board letterboxed itself into a centred square. This layout instead reads the
 * board's current square from {@link WorkbenchBoardPanel#currentBoardBounds()}
 * after sizing it, then positions the bar directly beside it.</p>
 */
final class WorkbenchBoardStageLayout implements LayoutManager {

    /**
     * Width reserved for the evaluation bar.
     */
    private static final int BAR_WIDTH = 36;

    /**
     * Gap between the eval bar and the board square.
     */
    private static final int GAP = 12;

    /**
     * The chessboard panel.
     */
    private final WorkbenchBoardPanel board;

    /**
     * The engine evaluation bar.
     */
    private final WorkbenchEvalBar evalBar;

    /**
     * Creates the layout.
     *
     * @param board the chessboard panel
     * @param evalBar the engine evaluation bar
     */
    WorkbenchBoardStageLayout(WorkbenchBoardPanel board, WorkbenchEvalBar evalBar) {
        this.board = board;
        this.evalBar = evalBar;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // Components are supplied through the constructor.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Components are supplied through the constructor.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Dimension d = board.getPreferredSize();
        return new Dimension(d.width + BAR_WIDTH + GAP, d.height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Dimension d = board.getMinimumSize();
        return new Dimension(d.width + BAR_WIDTH + GAP, d.height);
    }

    @Override
    public void layoutContainer(Container parent) {
        int width = parent.getWidth();
        int height = parent.getHeight();
        int reserved = BAR_WIDTH + GAP;
        // The board takes the whole stage minus the strip reserved on the left.
        board.setBounds(reserved, 0, Math.max(0, width - reserved), height);
        board.doLayout();
        Rectangle square = board.currentBoardBounds();
        // square is in the board's coordinate space; the board itself is offset
        // by `reserved`, so the bar lands GAP pixels left of the square edge.
        int barX = Math.max(0, square.x);
        int barY = Math.max(0, square.y);
        int barHeight = Math.max(0, Math.min(height - barY, square.height));
        evalBar.setBounds(barX, barY, BAR_WIDTH, barHeight);
    }
}
