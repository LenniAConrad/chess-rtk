package application.gui.workbench.window;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * Compact Lichess-style main-line and variation tree beside the Analyze board.
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
    private static final int HEADER_HEIGHT = 56;

    /**
     * One inline notation line height.
     */
    private static final int LINE_HEIGHT = 24;

    /**
     * Horizontal gap between inline notation tokens.
     */
    private static final int TOKEN_GAP = 4;

    /**
     * Horizontal padding inside move chips.
     */
    private static final int TOKEN_PAD_X = 5;

    /**
     * Preferred panel width in pixels.
     */
    private static final int PREFERRED_WIDTH = 236;

    /**
     * Backing game line.
     */
    private final GameModel gameModel;

    /**
     * Navigation callback accepting visible game-table rows.
     */
    private final IntConsumer jumpToRow;

    /**
     * Creates the variation tree panel.
     *
     * @param gameModel game line model
     * @param jumpToRow callback for visible row navigation
     */
    AnalyzeVariationTreePanel(GameModel gameModel, IntConsumer jumpToRow) {
        this.gameModel = Objects.requireNonNull(gameModel, "gameModel");
        this.jumpToRow = Objects.requireNonNull(jumpToRow, "jumpToRow");
        setOpaque(true);
        setBackground(Theme.BG);
        setToolTipText("Main line and PGN variations. Click a move to inspect that branch.");
        addMouseListener(new MouseAdapter() {
            /**
             * Navigates to the clicked move row.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = rowAt(event.getX(), event.getY());
                if (row >= 0) {
                    AnalyzeVariationTreePanel.this.jumpToRow.accept(row);
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
        List<LaidToken> tokens = layoutTokens(PREFERRED_WIDTH);
        int height = tokens.isEmpty() ? 360
                : Math.max(360, tokens.get(tokens.size() - 1).bounds().y
                        + LINE_HEIGHT + Theme.SPACE_MD);
        return new Dimension(PREFERRED_WIDTH, height);
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
            List<GameModel.VisibleMoveSnapshot> rows = gameModel.visibleMoveSnapshots();
            if (rows.isEmpty()) {
                paintEmpty(g);
            } else {
                paintInlineNotation(g, layoutTokens(getWidth()));
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
        g.drawString("MOVES", Theme.SPACE_MD, Theme.SPACE_MD + titleMetrics.getAscent());

        g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        g.setColor(Theme.MUTED);
        int variations = gameModel.variationRowCount();
        String summary = gameModel.lastPly() == 0
                ? "Start position"
                : variations == 0
                        ? "Main line - " + gameModel.lastPly() + " plies"
                        : "Main line - " + gameModel.lastPly() + " plies - "
                                + variations + " variation plies";
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
        g.drawString("No notation yet", Theme.SPACE_MD, y);
        g.drawString(Ui.elide(second, metrics, getWidth() - Theme.SPACE_MD * 2),
                Theme.SPACE_MD, y + LINE_HEIGHT);
    }

    /**
     * Paints inline notation tokens.
     *
     * @param g graphics context
     * @param tokens laid-out notation tokens
     */
    private void paintInlineNotation(Graphics2D g, List<LaidToken> tokens) {
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        for (LaidToken laid : tokens) {
            Rectangle bounds = laid.bounds();
            if (bounds.y + bounds.height < clip.y) {
                continue;
            }
            if (bounds.y > clip.y + clip.height) {
                return;
            }
            paintToken(g, laid);
        }
    }

    /**
     * Paints one notation token.
     *
     * @param g graphics context
     * @param laid laid-out token
     */
    private void paintToken(Graphics2D g, LaidToken laid) {
        InlineToken token = laid.token();
        Rectangle bounds = laid.bounds();
        boolean move = token.rowIndex() >= 0;
        boolean selected = move && gameModel.currentRow() == token.rowIndex();
        Font font = Theme.font(Theme.FONT_METADATA, selected ? Font.BOLD : Font.PLAIN);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        if (move) {
            g.setColor(selected ? Theme.ACCENT
                    : token.mainline()
                            ? Theme.withAlpha(Theme.ELEVATED_SOLID, Theme.isDark() ? 130 : 190)
                            : Theme.withAlpha(Theme.STATUS_WARNING_BG, Theme.isDark() ? 85 : 155));
            g.fillRoundRect(bounds.x, bounds.y + 2, bounds.width, bounds.height - 4,
                    Theme.RADIUS, Theme.RADIUS);
        }
        g.setColor(selected ? Theme.PRIMARY_BUTTON_TEXT
                : move && token.mainline() ? Theme.TEXT : Theme.MUTED);
        int textX = bounds.x + (move ? TOKEN_PAD_X : 0);
        int baseline = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(token.text(), textX, baseline);
    }

    /**
     * Builds inline notation tokens from visible game rows.
     *
     * @return inline notation tokens
     */
    private List<InlineToken> notationTokens() {
        List<GameModel.VisibleMoveSnapshot> rows = gameModel.visibleMoveSnapshots();
        List<InlineToken> tokens = new ArrayList<>();
        GameModel.VisibleMoveSnapshot previous = null;
        int openDepth = 0;
        for (GameModel.VisibleMoveSnapshot row : rows) {
            int targetDepth = Math.max(0, row.variationDepth());
            if (targetDepth == 0) {
                openDepth = closeVariations(tokens, openDepth, 0);
            } else {
                if (openDepth == 0) {
                    tokens.add(marker("("));
                    openDepth = 1;
                }
                while (openDepth < targetDepth) {
                    tokens.add(marker("("));
                    openDepth++;
                }
                while (openDepth > targetDepth) {
                    tokens.add(marker(")"));
                    openDepth--;
                }
                if (previous != null
                        && previous.variationDepth() == targetDepth
                        && !continuesVariation(previous, row)) {
                    tokens.add(marker(")"));
                    tokens.add(marker("("));
                }
            }
            tokens.add(moveToken(row, previous));
            appendAnnotationMarker(tokens, row);
            previous = row;
        }
        closeVariations(tokens, openDepth, 0);
        return tokens;
    }

    /**
     * Appends a muted annotation marker after a main-line move that carries a
     * bound annotation (Phase 2): the free-text comment in braces, or a small
     * pencil glyph when the move only carries drawn shapes. This is what makes
     * board annotations visible in the move list, not just the raw PGN.
     *
     * @param tokens destination token list
     * @param row visible move row
     */
    private void appendAnnotationMarker(List<InlineToken> tokens, GameModel.VisibleMoveSnapshot row) {
        if (!row.mainline()) {
            return;
        }
        String comment = gameModel.commentForPly(row.pathPly());
        if (comment == null || comment.isBlank()) {
            return;
        }
        String text = application.gui.workbench.board.BoardMarkupComment.plainText(comment).trim();
        if (text.isEmpty()) {
            tokens.add(marker("✎"));
        } else {
            if (text.length() > 32) {
                text = text.substring(0, 31) + "…";
            }
            tokens.add(marker("{" + text + "}"));
        }
    }

    /**
     * Closes variation marker tokens down to a target depth.
     *
     * @param tokens destination token list
     * @param openDepth current open variation depth
     * @param targetDepth target open variation depth
     * @return resulting open variation depth
     */
    private static int closeVariations(List<InlineToken> tokens, int openDepth, int targetDepth) {
        int next = openDepth;
        while (next > targetDepth) {
            tokens.add(marker(")"));
            next--;
        }
        return next;
    }

    /**
     * Creates a marker token.
     *
     * @param text marker text
     * @return marker token
     */
    private static InlineToken marker(String text) {
        return new InlineToken(text, -1, true);
    }

    /**
     * Creates one move token.
     *
     * @param row visible move row
     * @param previous previous visible move row
     * @return move token
     */
    private static InlineToken moveToken(
            GameModel.VisibleMoveSnapshot row,
            GameModel.VisibleMoveSnapshot previous) {
        String text = moveText(row, previous);
        return new InlineToken(text, row.rowIndex(), row.mainline());
    }

    /**
     * Returns inline move notation.
     *
     * @param row visible move row
     * @param previous previous visible move row
     * @return text token
     */
    private static String moveText(GameModel.VisibleMoveSnapshot row, GameModel.VisibleMoveSnapshot previous) {
        boolean showPly = !row.ply().endsWith("...");
        return showPly ? row.ply() + " " + row.san() : row.san();
    }

    /**
     * Returns whether a row continues the previous visible variation line.
     *
     * @param previous previous row
     * @param row current row
     * @return true when the current row extends the previous path by one move
     */
    private static boolean continuesVariation(
            GameModel.VisibleMoveSnapshot previous,
            GameModel.VisibleMoveSnapshot row) {
        if (previous == null || row.pathPly() != previous.pathPly() + 1) {
            return false;
        }
        List<String> before = previous.uciPath();
        List<String> current = row.uciPath();
        if (before.size() >= current.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(current.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes token bounds for the current available width.
     *
     * @param width available component width
     * @return laid-out tokens
     */
    private List<LaidToken> layoutTokens(int width) {
        int available = width <= 0 ? PREFERRED_WIDTH : width;
        int left = Theme.SPACE_MD;
        int right = Math.max(left + 32, available - Theme.SPACE_MD);
        int x = left;
        int y = HEADER_HEIGHT + Theme.SPACE_SM;
        List<LaidToken> laid = new ArrayList<>();
        for (InlineToken token : notationTokens()) {
            boolean move = token.rowIndex() >= 0;
            Font font = Theme.font(Theme.FONT_METADATA,
                    move && gameModel.currentRow() == token.rowIndex() ? Font.BOLD : Font.PLAIN);
            FontMetrics metrics = getFontMetrics(font);
            int tokenWidth = metrics.stringWidth(token.text()) + (move ? TOKEN_PAD_X * 2 : 0);
            if (x > left && x + tokenWidth > right) {
                x = left;
                y += LINE_HEIGHT;
            }
            laid.add(new LaidToken(token, new Rectangle(x, y, tokenWidth, LINE_HEIGHT)));
            x += tokenWidth + TOKEN_GAP;
        }
        return laid;
    }

    /**
     * Returns the visible row under a pointer.
     *
     * @param x pointer x
     * @param y pointer y
     * @return visible row index, or -1
     */
    private int rowAt(int x, int y) {
        if (y < HEADER_HEIGHT) {
            return -1;
        }
        for (LaidToken token : layoutTokens(getWidth())) {
            if (token.token().rowIndex() >= 0 && token.bounds().contains(x, y)) {
                return token.token().rowIndex();
            }
        }
        return -1;
    }

    /**
     * Inline notation token.
     *
     * @param text displayed text
     * @param rowIndex visible game row, or {@code -1} for a marker
     * @param mainline true when token belongs to the mainline
     */
    private record InlineToken(String text, int rowIndex, boolean mainline) {
    }

    /**
     * Token with computed bounds.
     *
     * @param token notation token
     * @param bounds token bounds
     */
    private record LaidToken(InlineToken token, Rectangle bounds) {
    }
}
