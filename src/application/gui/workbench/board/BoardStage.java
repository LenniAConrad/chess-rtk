package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import javax.swing.JPanel;

/**
 * Reusable board stage: a {@link BoardPanel} flanked by lichess-style
 * {@link MaterialStrip}s above and below (each showing captured pieces and a
 * {@code +N} material edge), wired so the strips refresh with the board.
 *
 * <p>This is the shared primitive behind every board-centric surface (Analyze,
 * Play, Relations, and the merged Board/Engine workspaces in the workbench UX
 * redesign). Hosting the strips here — instead of each tab re-assembling the
 * same {@code top + board + bottom} BorderLayout — keeps a single board surface
 * definition that the inspector docks compose around. The stage is transparent
 * so it floats on the page wash like the rest of the workbench cards.</p>
 */
public final class BoardStage extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Board hosted at the centre of the stage.
     */
    private final transient BoardPanel board;

    /**
     * Material strip painted above the board (top side's captures).
     */
    private final MaterialStrip topStrip;

    /**
     * Material strip painted below the board (bottom side's captures).
     */
    private final MaterialStrip bottomStrip;

    /**
     * Builds a board stage around the supplied board.
     *
     * @param board board to host and observe
     */
    public BoardStage(BoardPanel board) {
        super(new BorderLayout());
        // Match Ui.transparentPanel: a non-opaque panel on the BG wash, so the
        // stage reads identically to the hand-rolled material-board stage it
        // replaces.
        setOpaque(false);
        setBackground(Theme.BG);
        this.board = board;
        topStrip = new MaterialStrip(board);
        bottomStrip = new MaterialStrip(board);
        board.setMaterialStrips(topStrip, bottomStrip);
        add(topStrip, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);
        add(bottomStrip, BorderLayout.SOUTH);
    }

    /**
     * Returns the board hosted by this stage.
     *
     * @return hosted board
     */
    public BoardPanel board() {
        return board;
    }
}
