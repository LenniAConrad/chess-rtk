package application.gui.workbench.board;

import application.gui.workbench.ui.EvalBar;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Calculates board bounds and positions board-adjacent Swing components.
 */
final class BoardPanelLayout {

    /**
     * Padding around the chessboard surface.
     */
    private static final int BOARD_MARGIN = 32;

    /**
     * Minimum rendered board size in pixels.
     */
    private static final int MIN_BOARD_SIZE = 64;

    /**
     * Width of the attached evaluation bar.
     */
    private static final int EVAL_BAR_WIDTH = 22;

    /**
     * Gap between the board and attached evaluation bar.
     */
    private static final int EVAL_BAR_GAP = 8;

    /**
     * Board panel whose size and child components are managed.
     */
    private final BoardPanel boardPanel;

    /**
     * Optional attached evaluation bar component.
     */
    private transient EvalBar evalBar;

    /**
     * Creates a layout helper for one board panel.
     *
     * @param boardPanel board panel to manage
     */
    BoardPanelLayout(BoardPanel boardPanel) {
        this.boardPanel = boardPanel;
    }

    /**
     * Returns the preferred size.
     *
     * @return preferred size
     */
    static Dimension preferredSize() {
        int basis = Math.round(620 * displayScale());
        return new Dimension(basis, basis);
    }

    /**
     * Returns the minimum size.
     *
     * @return minimum size
     */
    static Dimension minimumSize() {
        int basis = Math.round(MIN_BOARD_SIZE + BOARD_MARGIN * 2 * displayScale());
        return new Dimension(basis, basis);
    }

    /**
     * Returns the board bounds.
     *
     * @return board bounds
     */
    Rectangle boardBounds() {
        // Reserve a left strip for the eval bar so the board square never
        // slides underneath it.
        int leftReserve = evalBar != null ? EVAL_BAR_WIDTH + EVAL_BAR_GAP : 0;
        int availWidth = boardPanel.getWidth() - leftReserve;
        int size = Math.min(availWidth - BOARD_MARGIN * 2, boardPanel.getHeight() - BOARD_MARGIN * 2);
        size = Math.max(MIN_BOARD_SIZE, size - size % 8);
        int x = leftReserve + (availWidth - size) / 2;
        return new Rectangle(x, (boardPanel.getHeight() - size) / 2, size, size);
    }

    /**
     * Updates the eval bar.
     *
     * @param bar progress bar
     */
    void setEvalBar(EvalBar bar) {
        if (evalBar != null) {
            boardPanel.remove(evalBar);
        }
        evalBar = bar;
        if (bar != null) {
            boardPanel.setLayout(null);
            boardPanel.add(bar);
        }
        boardPanel.revalidate();
        boardPanel.repaint();
    }

    /**
     * Lays out the board panel layout.
     */
    void doLayout() {
        if (evalBar != null) {
            Rectangle square = boardBounds();
            int barX = Math.max(0, square.x - EVAL_BAR_GAP - EVAL_BAR_WIDTH);
            evalBar.setBounds(barX, square.y, EVAL_BAR_WIDTH, square.height);
        }
    }

    /**
     * Returns the display scale.
     *
     * @return display scale
     */
    private static float displayScale() {
        try {
            java.awt.GraphicsEnvironment env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            java.awt.GraphicsDevice device = env.getDefaultScreenDevice();
            return (float) device.getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (java.awt.HeadlessException ex) {
            return 1f;
        }
    }
}
