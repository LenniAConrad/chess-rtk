package application.gui.workbench.engine;

import application.gui.workbench.board.BoardExporter;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Move;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Live gallery of finished gauntlet games.
 *
 * <p>
 * Each completed game streamed by {@code crtk engine gauntlet --stream} is shown
 * as a board thumbnail of its final position, laid out left-to-right and
 * top-to-bottom in play order. Clicking a thumbnail opens the full game in a
 * board with move-by-move replay controls, so a result is never just a number:
 * the actual game can be inspected.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class GauntletGameBrowser extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Thumbnail board edge, in pixels.
     */
    private static final int TILE_BOARD = 132;

    /**
     * Offscreen board image render size for thumbnails, in pixels.
     */
    private static final int RENDER_SIZE = 256;

    /**
     * Wrapping gallery of game thumbnails.
     */
    private final JPanel gallery = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_MD, Theme.SPACE_MD));

    /**
     * Hint shown before any game has arrived.
     */
    private final JLabel emptyHint = Ui.caption("Run a gauntlet to view and replay its games.");

    /**
     * Offscreen board reused to rasterize each thumbnail's final position.
     */
    private final transient BoardPanel renderer = readOnlyBoard(false);

    /**
     * Games added so far, kept for index-ordered insertion.
     */
    private final transient List<Integer> insertedIndices = new ArrayList<>();

    /**
     * Creates an empty game browser.
     */
    GauntletGameBrowser() {
        super(new BorderLayout(0, Theme.SPACE_SM));
        setOpaque(false);
        gallery.setOpaque(false);
        add(emptyHint, BorderLayout.NORTH);
        add(gallery, BorderLayout.CENTER);
    }

    /**
     * Clears all thumbnails, restoring the empty state.
     */
    void clear() {
        gallery.removeAll();
        insertedIndices.clear();
        emptyHint.setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Adds one finished game thumbnail, inserting it in play order.
     *
     * @param game finished game
     */
    void addGame(Game game) {
        emptyHint.setVisible(false);
        BufferedImage image = renderFinalPosition(game);
        GameTile tile = new GameTile(game, image);
        gallery.add(tile, insertionIndex(game.index()));
        gallery.revalidate();
        gallery.repaint();
    }

    /**
     * Returns the gallery insertion index that keeps tiles in game order.
     *
     * @param index new game index
     * @return insertion position
     */
    private int insertionIndex(int index) {
        int position = 0;
        while (position < insertedIndices.size() && insertedIndices.get(position) < index) {
            position++;
        }
        insertedIndices.add(position, index);
        return position;
    }

    /**
     * Rasterizes a game's final position into a thumbnail image.
     *
     * @param game finished game
     * @return rendered board image
     */
    private BufferedImage renderFinalPosition(Game game) {
        Position position = replay(game, game.moves().size());
        renderer.position(position.toString(), false);
        return BoardExporter.renderPng(renderer, RENDER_SIZE);
    }

    /**
     * Replays a game up to a ply count, returning the resulting position.
     *
     * @param game finished game
     * @param plies number of moves to apply
     * @return position after the requested plies
     */
    private static Position replay(Game game, int plies) {
        Position position = new Position(game.openingFen());
        int applied = 0;
        for (String uci : game.moves()) {
            if (applied >= plies) {
                break;
            }
            try {
                position.play(Move.parse(uci));
            } catch (IllegalArgumentException ex) {
                break;
            }
            applied++;
        }
        return position;
    }

    /**
     * Opens a modeless replay dialog for one game.
     *
     * @param game finished game
     */
    private void openReplay(Game game) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new GameReplayDialog(owner, game).setVisible(true);
    }

    /**
     * Builds a read-only board with interactive overlays disabled.
     *
     * @param notation whether to show rank/file coordinates
     * @return configured board
     */
    private static BoardPanel readOnlyBoard(boolean notation) {
        BoardPanel board = new BoardPanel();
        board.setShowLegalMovePreview(false);
        board.setShowSuggestedMoveArrow(false);
        board.setShowSpecialMoveHints(false);
        board.setShowNotation(notation);
        return board;
    }

    /**
     * Returns the result accent color for a candidate-perspective result label.
     *
     * @param result {@code win}, {@code draw}, or {@code loss}
     * @return accent color
     */
    private static Color resultColor(String result) {
        return switch (result) {
            case "win" -> Theme.STATUS_SUCCESS_BORDER;
            case "loss" -> Theme.STATUS_ERROR_BORDER;
            default -> Theme.STATUS_WARNING_BORDER;
        };
    }

    /**
     * One finished gauntlet game.
     *
     * @param index zero-based game index in play order
     * @param candidateWhite whether the candidate played White
     * @param result candidate-perspective result ({@code win}/{@code draw}/{@code loss})
     * @param openingFen opening position FEN
     * @param moves moves played, in UCI notation
     */
    record Game(int index, boolean candidateWhite, String result, String openingFen, List<String> moves) {

        /**
         * Parses one tab-separated {@code GAME} stream line.
         *
         * @param line stream line beginning with {@code GAME\t}
         * @return parsed game, or {@code null} when the line is malformed
         */
        static Game parse(String line) {
            String[] parts = line.split("\t", 6);
            if (parts.length < 6 || !"GAME".equals(parts[0])) {
                return null;
            }
            try {
                int index = Integer.parseInt(parts[1].trim());
                boolean white = "W".equals(parts[2].trim());
                String result = parts[3].trim();
                String fen = parts[4].trim();
                String moveText = parts[5].trim();
                List<String> moves = moveText.isEmpty() ? List.of() : List.of(moveText.split(" "));
                return new Game(index, white, result, fen, moves);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        /**
         * Returns the one-based game number for display.
         *
         * @return game number
         */
        int number() {
            return index + 1;
        }
    }

    /**
     * Clickable board thumbnail for one finished game.
     */
    private final class GameTile extends JComponent {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Backing game.
         */
        private final transient Game game;

        /**
         * Rendered final-position board image.
         */
        private final transient BufferedImage board;

        /**
         * Whether the pointer is hovering this tile.
         */
        private boolean hover;

        /**
         * Creates a tile.
         *
         * @param game backing game
         * @param board rendered board image
         */
        GameTile(Game game, BufferedImage board) {
            this.game = game;
            this.board = board;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Game " + game.number() + " · candidate "
                    + (game.candidateWhite() ? "White" : "Black") + " · " + game.result()
                    + " · " + game.moves().size() + " moves — click to replay");
            addMouseListener(new MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseClicked(MouseEvent event) {
                    openReplay(game);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(TILE_BOARD, TILE_BOARD + 22);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int edge = TILE_BOARD;
                g.drawImage(board, 0, 0, edge, edge, null);
                Color accent = resultColor(game.result());
                g.setStroke(new java.awt.BasicStroke(hover ? 2.5f : 1.5f));
                g.setColor(accent);
                g.drawRoundRect(1, 1, edge - 2, edge - 2, 8, 8);
                g.setFont(Theme.font(11, Font.BOLD));
                g.setColor(Theme.TEXT);
                String label = "Game " + game.number();
                g.drawString(label, 1, edge + 14);
                int dotX = edge - 9;
                g.setColor(accent);
                g.fillOval(dotX, edge + 5, 8, 8);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Modeless dialog that replays one game move by move.
     */
    private final class GameReplayDialog extends JDialog {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Replay board.
         */
        private final transient BoardPanel board = readOnlyBoard(true);

        /**
         * Position FENs for each ply (index 0 is the opening).
         */
        private final transient List<String> fens = new ArrayList<>();

        /**
         * Move applied to reach each ply (parallel to {@link #fens}, last move).
         */
        private final transient List<Short> lastMoves = new ArrayList<>();

        /**
         * Ply caption.
         */
        private final JLabel plyLabel = Ui.label("");

        /**
         * Current ply index into {@link #fens}.
         */
        private int ply;

        /**
         * Builds and lays out a replay dialog.
         *
         * @param owner owning window
         * @param game game to replay
         */
        GameReplayDialog(Window owner, Game game) {
            super(owner, "Game " + game.number() + " — candidate "
                    + (game.candidateWhite() ? "White" : "Black") + " — " + game.result());
            buildPlies(game);
            board.setWhiteDown(game.candidateWhite());
            board.setPreferredSize(new Dimension(520, 520));

            JPanel content = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
            content.setBorder(Theme.pad(Theme.SPACE_MD));
            content.setBackground(Theme.BG);
            content.add(board, BorderLayout.CENTER);
            content.add(buildControls(), BorderLayout.SOUTH);
            setContentPane(content);

            ply = fens.size() - 1;
            showPly();
            pack();
            setLocationRelativeTo(owner);
        }

        /**
         * Precomputes the position at every ply.
         *
         * @param game game to replay
         */
        private void buildPlies(Game game) {
            Position position = new Position(game.openingFen());
            fens.add(position.toString());
            lastMoves.add(Move.NO_MOVE);
            for (String uci : game.moves()) {
                short move;
                try {
                    move = Move.parse(uci);
                } catch (IllegalArgumentException ex) {
                    break;
                }
                position.play(move);
                fens.add(position.toString());
                lastMoves.add(move);
            }
        }

        /**
         * Builds the navigation control row.
         *
         * @return controls
         */
        private JComponent buildControls() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_SM, 0));
            row.setOpaque(false);
            row.add(Ui.button("⏮", false, event -> goTo(0)));
            row.add(Ui.button("◀", false, event -> goTo(ply - 1)));
            plyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            plyLabel.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_MD, 0, Theme.SPACE_MD));
            row.add(plyLabel);
            row.add(Ui.button("▶", false, event -> goTo(ply + 1)));
            row.add(Ui.button("⏭", false, event -> goTo(fens.size() - 1)));
            return row;
        }

        /**
         * Navigates to a ply, clamped into range.
         *
         * @param target requested ply
         */
        private void goTo(int target) {
            ply = Math.max(0, Math.min(fens.size() - 1, target));
            showPly();
        }

        /**
         * Renders the current ply on the board and updates the caption.
         */
        private void showPly() {
            board.setPositionInstant(new Position(fens.get(ply)), lastMoves.get(ply));
            plyLabel.setText("move " + ply + " / " + (fens.size() - 1));
        }
    }
}
