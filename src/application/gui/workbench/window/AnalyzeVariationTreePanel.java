package application.gui.workbench.window;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * Compact main-line tree painted beside the Analyze board.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class AnalyzeVariationTreePanel extends JComponent {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Header height in pixels.
     */
    private static final int HEADER_HEIGHT = 52;

    /**
     * One move-pair row height.
     */
    private static final int ROW_HEIGHT = 24;

    /**
     * Backing game line.
     */
    private final GameModel gameModel;

    /**
     * Navigation callback accepting one-based plies.
     */
    private final IntConsumer jumpToPly;

    /**
     * Creates the variation tree panel.
     *
     * @param gameModel game line model
     * @param jumpToPly callback for one-based ply navigation
     */
    AnalyzeVariationTreePanel(GameModel gameModel, IntConsumer jumpToPly) {
        this.gameModel = Objects.requireNonNull(gameModel, "gameModel");
        this.jumpToPly = Objects.requireNonNull(jumpToPly, "jumpToPly");
        setOpaque(true);
        setBackground(Theme.BG);
        setToolTipText("Main line. Click a move to jump the board to that ply.");
        addMouseListener(new MouseAdapter() {
            /**
             * Navigates to the clicked move row.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                int ply = plyAt(event.getX(), event.getY());
                if (ply > 0 && ply <= AnalyzeVariationTreePanel.this.gameModel.lastPly()) {
                    AnalyzeVariationTreePanel.this.jumpToPly.accept(ply);
                }
            }
        });
    }

    /**
     * Returns the preferred size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(184, 360);
    }

    /**
     * Paints the variation tree.
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
            paintBackground(g);
            paintHeader(g);
            int count = gameModel.lastPly();
            if (count <= 0) {
                paintEmpty(g);
            } else {
                paintMoveRows(g, count);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints panel background and edge.
     *
     * @param g graphics context
     */
    private void paintBackground(Graphics2D g) {
        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.LINE);
        g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
    }

    /**
     * Paints the tree heading.
     *
     * @param g graphics context
     */
    private void paintHeader(Graphics2D g) {
        g.setFont(Theme.font(Theme.FONT_MICRO, Font.BOLD));
        g.setColor(Theme.STATUS_INFO_TEXT);
        FontMetrics titleMetrics = g.getFontMetrics();
        g.drawString("VARIATION TREE", Theme.SPACE_MD, Theme.SPACE_MD + titleMetrics.getAscent());

        g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String summary = gameModel.lastPly() == 0
                ? "Start position"
                : "Main line - " + gameModel.lastPly() + " plies";
        g.drawString(summary, Theme.SPACE_MD, HEADER_HEIGHT - Theme.SPACE_SM);

        g.setColor(Theme.LINE);
        g.drawLine(0, HEADER_HEIGHT - 1, getWidth(), HEADER_HEIGHT - 1);
    }

    /**
     * Paints an empty-line hint.
     *
     * @param g graphics context
     */
    private void paintEmpty(Graphics2D g) {
        g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        g.setColor(Theme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        String second = "Load PGN or play on the board.";
        int y = HEADER_HEIGHT + Theme.SPACE_LG + metrics.getAscent();
        g.drawString("No moves yet", Theme.SPACE_MD, y);
        g.drawString(Ui.elide(second, metrics, getWidth() - Theme.SPACE_MD * 2),
                Theme.SPACE_MD, y + ROW_HEIGHT);
    }

    /**
     * Paints move-pair rows.
     *
     * @param g graphics context
     * @param count move row count
     */
    private void paintMoveRows(Graphics2D g, int count) {
        int rows = (count + 1) / 2;
        for (int pair = 0; pair < rows; pair++) {
            int y = HEADER_HEIGHT + pair * ROW_HEIGHT;
            if (y > getHeight()) {
                return;
            }
            paintMovePair(g, pair, y, count);
        }
    }

    /**
     * Paints one move-pair row.
     *
     * @param g graphics context
     * @param pair pair index
     * @param y row y
     * @param count row count
     */
    private void paintMovePair(Graphics2D g, int pair, int y, int count) {
        int whitePly = pair * 2 + 1;
        int blackPly = whitePly + 1;
        int current = gameModel.currentPly();
        boolean selected = current == whitePly || current == blackPly;
        if (selected) {
            g.setColor(Theme.SELECTION_SOLID);
            g.fillRect(0, y, getWidth() - 1, ROW_HEIGHT);
        } else if (pair % 2 == 1) {
            g.setColor(Theme.withAlpha(Theme.ELEVATED_SOLID, Theme.isDark() ? 105 : 150));
            g.fillRect(0, y, getWidth() - 1, ROW_HEIGHT);
        }

        g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        int baseline = y + (ROW_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(Theme.MUTED);
        g.drawString(Integer.toString(pair + 1) + ".", Theme.SPACE_MD, baseline);

        int whiteX = 45;
        int blackX = Math.max(whiteX + 62, getWidth() / 2 + Theme.SPACE_XS);
        int whiteW = Math.max(24, blackX - whiteX - Theme.SPACE_SM);
        int blackW = Math.max(24, getWidth() - blackX - Theme.SPACE_MD);
        paintMoveCell(g, whitePly, whiteX, whiteW, baseline, current == whitePly);
        if (blackPly <= count) {
            paintMoveCell(g, blackPly, blackX, blackW, baseline, current == blackPly);
        }
    }

    /**
     * Paints one SAN cell.
     *
     * @param g graphics context
     * @param ply one-based ply
     * @param x x coordinate
     * @param width text width
     * @param baseline text baseline
     * @param selected true when selected
     */
    private void paintMoveCell(Graphics2D g, int ply, int x, int width, int baseline, boolean selected) {
        String san = sanForPly(ply);
        g.setFont(Theme.font(Theme.FONT_METADATA, selected ? Font.BOLD : Font.PLAIN));
        g.setColor(selected ? Theme.TEXT : Theme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(Ui.elide(san, metrics, width), x, baseline);
    }

    /**
     * Returns the SAN text for one main-line ply.
     *
     * @param ply one-based ply
     * @return SAN text
     */
    private String sanForPly(int ply) {
        List<GameModel.PlySnapshot> snapshots = gameModel.mainlineSnapshots();
        return ply > 0 && ply <= snapshots.size() ? snapshots.get(ply - 1).san() : "";
    }

    /**
     * Returns the move ply under a pointer.
     *
     * @param x pointer x
     * @param y pointer y
     * @return one-based ply, or -1
     */
    private int plyAt(int x, int y) {
        if (y < HEADER_HEIGHT) {
            return -1;
        }
        int pair = (y - HEADER_HEIGHT) / ROW_HEIGHT;
        int blackX = Math.max(45 + 62, getWidth() / 2 + Theme.SPACE_XS);
        return pair * 2 + (x >= blackX ? 2 : 1);
    }
}
