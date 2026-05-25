package testing;

import static testing.PuzzleRatingCsvTool.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import application.cli.RecordIO;
import chess.book.collection.Builder;
import chess.book.render.MoveText;
import chess.book.render.NotationPieceSvg;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.images.render.Render;
import chess.pdf.document.Canvas;
import chess.pdf.document.Font;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Puzzle snapshot, source-tree, board, and notation rendering helpers.
 */
final class PuzzleRatingSnapshot {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleRatingSnapshot() {
        // utility
    }

    static void drawPuzzleSnapshotPanel(Canvas canvas, PuzzleRatingRow row, PuzzleRatingDetail detail, double x, double y,
            double width, double height) {
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.7, REPORT_TEXT,
                "Hardest Puzzle Snapshot");
        if (row == null) {
            canvas.drawWrappedText(x + 8.0, y + 24.0, width - 16.0, REPORT_BODY_FONT, 5.6, 6.6,
                    REPORT_MUTED, "No puzzle rows available.");
            return;
        }

        canvas.drawText(x + 8.0, y + 21.0, REPORT_BODY_FONT, 5.6, REPORT_MUTED,
                "Rating " + row.rating() + " / " + row.label().replace('_', ' ')
                        + "; " + row.plies() + " plies, " + row.nodes() + " nodes");
        double boardSize = Math.min(width - 18.0, height - 132.0);
        boardSize = Math.max(82.0, boardSize);
        double boardX = x + (width - boardSize) / 2.0;
        double boardY = y + 43.0;
        canvas.drawSvg(renderReportBoardSvg(row), boardX, boardY, boardSize, boardSize);

        double infoY = boardY + boardSize + 9.0;
        canvas.drawText(x + 8.0, infoY, REPORT_DATA_BOLD_FONT, 5.8, REPORT_ACCENT,
                sideToMoveLabel(row.fen()).toUpperCase(Locale.ROOT) + " TO MOVE");
        double cursor = infoY + 11.0;
        PuzzleRatingDetail shown = detail == null ? csvOnlyPuzzleDetail(row) : detail;
        canvas.drawText(x + 8.0, cursor, REPORT_DATA_BOLD_FONT, 4.9, REPORT_ACCENT,
                shown.sourceTree() ? "PUZZLE SOLUTION TREE" : "KEY MOVE FROM CSV");
        cursor += 8.0;
        double moveLeading = shown.sourceTree() ? 5.6 : 6.7;
        int maxMoveLines = Math.max(3, Math.min(8, (int) Math.floor((y + height - cursor - 35.0) / moveLeading)));
        cursor += drawWrappedNotationText(canvas, x + 8.0, cursor, width - 16.0, REPORT_BODY_BOLD_FONT,
                shown.sourceTree() ? 4.7 : 5.8, moveLeading, REPORT_TEXT,
                MoveText.figurine(shown.movetext()), maxMoveLines);
        cursor += 4.0;
        cursor += canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.4, 6.2,
                REPORT_MUTED,
                "Goal " + row.goal() + "; best-move rank " + row.cheapRank() + ".");
        cursor += 4.0;
        String evidence = shown.sourceTree()
                ? "Explicit source tree; CSV evidence: " + row.plies() + " plies, "
                        + row.replies() + " root replies, " + row.nodes() + " nodes."
                : "CSV stores only the key; tree evidence still reports " + row.plies()
                        + " solver plies across " + row.nodes() + " nodes.";
        canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.1, 5.9, REPORT_MUTED,
                evidence);
    }

    /**
     * Draws wrapped chess notation with SVG figurines in place of SAN piece
     * letters.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width wrap width in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param leading line advance
     * @param color text fill color
     * @param text figurine-formatted movetext
     * @return consumed vertical space in PDF points
     */
    private static double drawWrappedNotationText(Canvas canvas, double x, double y, double width, Font font,
            double fontSize, double leading, Color color, String text) {
        return drawWrappedNotationText(canvas, x, y, width, font, fontSize, leading, color, text, Integer.MAX_VALUE);
    }

    /**
     * Draws wrapped chess notation with a maximum line count.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width wrap width in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param leading line advance
     * @param color text fill color
     * @param text figurine-formatted movetext
     * @param maxLines maximum lines to draw
     * @return consumed vertical space in PDF points
     */
    private static double drawWrappedNotationText(Canvas canvas, double x, double y, double width, Font font,
            double fontSize, double leading, Color color, String text, int maxLines) {
        List<String> lines = wrapNotationLines(text, font, fontSize, width);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, Math.max(1, maxLines)));
            int last = lines.size() - 1;
            lines.set(last, fitNotationEllipsis(lines.get(last), font, fontSize, width));
        }
        double cursorY = y;
        for (String line : lines) {
            if (!line.isBlank()) {
                drawNotationText(canvas, x, cursorY, font, fontSize, color, line);
            }
            cursorY += leading;
        }
        return cursorY - y;
    }

    /**
     * Fits an ellipsis onto a notation line.
     *
     * @param line source line
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     * @return shortened line ending with {@code ...}
     */
    private static String fitNotationEllipsis(String line, Font font, double fontSize, double width) {
        String base = line == null ? "" : line.stripTrailing();
        while (!base.isEmpty() && notationTextWidth(font, fontSize, base + "...") > width) {
            base = base.substring(0, base.length() - 1).stripTrailing();
        }
        return base.isEmpty() ? "..." : base + "...";
    }

    /**
     * Wraps a figurine-formatted notation paragraph using SVG piece widths.
     *
     * @param text source movetext
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     * @return wrapped notation lines
     */
    private static List<String> wrapNotationLines(String text, Font font, double fontSize, double width) {
        List<String> lines = new ArrayList<>();
        String safe = normalizeNotationWhitespace(text);
        if (safe.isBlank()) {
            return lines;
        }
        if (width <= 0.0) {
            lines.add(safe.trim());
            return lines;
        }

        String[] paragraphs = safe.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                appendWrappedNotationWord(lines, line, word, font, fontSize, width);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    /**
     * Appends one notation word to the current wrapped line.
     *
     * @param lines output lines
     * @param line current line buffer
     * @param word notation word
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     */
    private static void appendWrappedNotationWord(List<String> lines, StringBuilder line, String word, Font font,
            double fontSize, double width) {
        if (line.isEmpty()) {
            if (notationTextWidth(font, fontSize, word) <= width) {
                line.append(word);
            } else {
                appendBrokenNotationWord(lines, line, word, font, fontSize, width);
            }
            return;
        }
        String candidate = line + " " + word;
        if (notationTextWidth(font, fontSize, candidate) <= width) {
            line.setLength(0);
            line.append(candidate);
            return;
        }
        lines.add(line.toString());
        appendBrokenNotationWord(lines, line, word, font, fontSize, width);
    }

    /**
     * Breaks one oversized notation word into line-sized fragments.
     *
     * @param lines output lines
     * @param scratch reusable line buffer
     * @param word notation word
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     */
    private static void appendBrokenNotationWord(List<String> lines, StringBuilder scratch, String word, Font font,
            double fontSize, double width) {
        scratch.setLength(0);
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            String candidate = scratch.toString() + ch;
            if (!scratch.isEmpty() && notationTextWidth(font, fontSize, candidate) > width) {
                lines.add(scratch.toString());
                scratch.setLength(0);
            }
            scratch.append(ch);
        }
    }

    /**
     * Normalizes notation whitespace while preserving paragraph breaks.
     *
     * @param text source text
     * @return normalized text
     */
    private static String normalizeNotationWhitespace(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        return Pattern.compile(" +").matcher(normalized).replaceAll(" ").trim();
    }

    /**
     * Draws one chess notation line with SVG piece placeholders.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param color text fill color
     * @param text figurine-formatted notation line
     */
    private static void drawNotationText(Canvas canvas, double x, double y, Font font, double fontSize, Color color,
            String text) {
        String safe = text == null ? "" : text;
        double cursorX = x;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            String pieceSvg = NotationPieceSvg.svg(ch);
            if (pieceSvg == null) {
                run.append(ch);
                continue;
            }
            cursorX = drawNotationTextRun(canvas, cursorX, y, font, fontSize, color, run);
            drawNotationPiece(canvas, cursorX, y, fontSize, pieceSvg);
            cursorX += notationPieceAdvance(fontSize);
        }
        drawNotationTextRun(canvas, cursorX, y, font, fontSize, color, run);
    }

    /**
     * Draws one ordinary text run inside chess notation.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param font text font
     * @param fontSize text font size
     * @param color text fill color
     * @param run pending text run
     * @return next cursor x-coordinate
     */
    private static double drawNotationTextRun(Canvas canvas, double x, double y, Font font, double fontSize,
            Color color, StringBuilder run) {
        if (run.isEmpty()) {
            return x;
        }
        String text = run.toString();
        canvas.drawText(x, y, font, fontSize, color, text);
        run.setLength(0);
        return x + font.textWidth(text, fontSize);
    }

    /**
     * Draws one inline notation piece.
     *
     * @param canvas drawing surface
     * @param x logical cursor x-coordinate
     * @param y surrounding text top edge
     * @param fontSize surrounding text size
     * @param pieceSvg embedded SVG source
     */
    private static void drawNotationPiece(Canvas canvas, double x, double y, double fontSize, String pieceSvg) {
        double boxSize = notationPieceBoxSize(fontSize);
        double drawX = x + notationPieceLeftPadding(fontSize);
        double drawY = y - fontSize * REPORT_NOTATION_PIECE_TOP_SHIFT_SCALE;
        canvas.drawSvg(pieceSvg, drawX, drawY, boxSize, boxSize);
    }

    /**
     * Measures chess notation with SVG piece advances.
     *
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param text figurine-formatted notation text
     * @return measured width in PDF points
     */
    private static double notationTextWidth(Font font, double fontSize, String text) {
        String safe = text == null ? "" : text;
        double width = 0.0;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            if (!NotationPieceSvg.isPlaceholder(ch)) {
                run.append(ch);
                continue;
            }
            width += font.textWidth(run.toString(), fontSize);
            run.setLength(0);
            width += notationPieceAdvance(fontSize);
        }
        return width + font.textWidth(run.toString(), fontSize);
    }

    /**
     * Computes the inline SVG square size.
     *
     * @param fontSize surrounding text size
     * @return SVG box size
     */
    private static double notationPieceBoxSize(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_SIZE_SCALE;
    }

    /**
     * Computes inline notation piece left padding.
     *
     * @param fontSize surrounding text size
     * @return left padding
     */
    private static double notationPieceLeftPadding(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_LEFT_PADDING_SCALE;
    }

    /**
     * Computes inline notation piece right padding.
     *
     * @param fontSize surrounding text size
     * @return right padding
     */
    private static double notationPieceRightPadding(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_RIGHT_PADDING_SCALE;
    }

    /**
     * Computes inline notation piece cursor advance.
     *
     * @param fontSize surrounding text size
     * @return cursor advance
     */
    private static double notationPieceAdvance(double fontSize) {
        return notationPieceLeftPadding(fontSize) + notationPieceBoxSize(fontSize)
                + notationPieceRightPadding(fontSize);
    }

    /**
     * Builds the display detail for the report puzzle snapshot.
     *
     * @param row source puzzle row
     * @param records optional original record JSON/JSONL files
     * @return display detail using the original source tree when available
     * @throws IOException if a record source cannot be read
     */
    static PuzzleRatingDetail reportPuzzleDetail(PuzzleRatingRow row, List<Path> records) throws IOException {
        if (row == null) {
            return new PuzzleRatingDetail("-", false);
        }
        if (records != null) {
            for (Path source : records) {
                String tree = sourceTreeAlgebraic(row, source);
                if (!tree.isBlank()) {
                    return new PuzzleRatingDetail(tree, true);
                }
            }
        }
        return csvOnlyPuzzleDetail(row);
    }

    /**
     * Builds a display detail from the CSV solution column only.
     *
     * @param row source puzzle row
     * @return CSV-backed display detail
     */
    private static PuzzleRatingDetail csvOnlyPuzzleDetail(PuzzleRatingRow row) {
        return new PuzzleRatingDetail(solutionAlgebraic(row), false);
    }

    /**
     * Finds and formats the explicit solution tree for one puzzle row.
     *
     * @param row source puzzle row
     * @param source original record JSON/JSONL source
     * @return move-numbered SAN variation tree, or blank when not found
     * @throws IOException if the source cannot be read
     */
    private static String sourceTreeAlgebraic(PuzzleRatingRow row, Path source) throws IOException {
        if (row == null || source == null || !Files.isRegularFile(source)) {
            return "";
        }
        PuzzleRatingSourceNode root = sourceRootNode(row, source);
        if (root == null || root.solution() == Move.NO_MOVE) {
            return "";
        }
        populateSourceTree(root, source, Math.max(1, row.nodes()));
        return formatSourceTree(new Position(row.fen()), root);
    }

    /**
     * Reads the source root node for one puzzle row.
     *
     * @param row source puzzle row
     * @param source original record JSON/JSONL source
     * @return source root node, or {@code null} when unavailable
     * @throws IOException if the source cannot be read
     */
    private static PuzzleRatingSourceNode sourceRootNode(PuzzleRatingRow row, Path source) throws IOException {
        try {
            RecordIO.streamRecordJson(source, json -> {
                if (!row.fen().equals(Json.parseStringField(json, "position"))) {
                    return;
                }
                PuzzleRatingSourceNode node = sourceNode(Json.parseStringField(json, "position"),
                        Json.parseStringArrayField(json, "analysis"));
                if (node != null && sourceNodeStartsWithCsvKey(new Position(row.fen()), node, row.solution())) {
                    throw new PuzzleRatingFoundSourceNode(node);
                }
            });
        } catch (PuzzleRatingFoundSourceNode found) {
            return found.node;
        }
        return null;
    }

    /**
     * Populates child variations by following explicit record parent links.
     *
     * @param root source solution root
     * @param source original record JSON/JSONL source
     * @param nodeLimit maximum solver nodes to include
     * @throws IOException if the source cannot be read
     */
    private static void populateSourceTree(PuzzleRatingSourceNode root, Path source, int nodeLimit) throws IOException {
        List<PuzzleRatingSourceNode> frontier = List.of(root);
        int nodes = 1;
        while (!frontier.isEmpty() && nodes < nodeLimit) {
            Map<String, PuzzleRatingSourceNode> parents = sourceParentsAfterSolution(frontier);
            if (parents.isEmpty()) {
                return;
            }
            List<PuzzleRatingSourceNode> next = new ArrayList<>();
            RecordIO.streamRecordJson(source, json -> {
                PuzzleRatingSourceNode parent = parents.get(Json.parseStringField(json, "parent"));
                if (parent == null || sourceTreeSize(root) >= nodeLimit) {
                    return;
                }
                PuzzleRatingSourceNode child = sourceNode(Json.parseStringField(json, "position"),
                        Json.parseStringArrayField(json, "analysis"));
                if (child == null || child.solution() == Move.NO_MOVE) {
                    return;
                }
                short reply = sourceReplyMove(parent.afterSolution(), child.position());
                if (reply == Move.NO_MOVE || parent.hasReply(reply)) {
                    return;
                }
                parent.branches().add(new PuzzleRatingSourceBranch(reply, child));
                next.add(child);
            });
            nodes = sourceTreeSize(root);
            frontier = next;
        }
    }

    /**
     * Builds a lookup of positions after the solver's move for each frontier node.
     *
     * @param frontier current source-tree frontier
     * @return parent FEN to node map
     */
    private static Map<String, PuzzleRatingSourceNode> sourceParentsAfterSolution(List<PuzzleRatingSourceNode> frontier) {
        Map<String, PuzzleRatingSourceNode> parents = new HashMap<>();
        for (PuzzleRatingSourceNode node : frontier) {
            Position after = node.afterSolution();
            if (after != null) {
                parents.put(after.toString(), node);
            }
        }
        return parents;
    }

    /**
     * Creates one source-tree node from raw record fields.
     *
     * @param fen record position
     * @param analysisLines raw UCI analysis lines from the source record
     * @return source node, or {@code null} when unavailable
     */
    private static PuzzleRatingSourceNode sourceNode(String fen, String[] analysisLines) {
        if (fen == null || fen.isBlank() || analysisLines == null || analysisLines.length == 0) {
            return null;
        }
        Output best = new Analysis().addAll(analysisLines).getBestOutput(1);
        short solution = firstMove(best == null ? new short[0] : best.getMoves());
        return new PuzzleRatingSourceNode(new Position(fen), solution, sourceEvalScore(best));
    }

    /**
     * Extracts a sortable evaluation score from a source record output.
     *
     * @param output source UCI output
     * @return normalized score matching record-PGN sorting
     */
    private static int sourceEvalScore(Output output) {
        if (output == null) {
            return Integer.MIN_VALUE / 2;
        }
        Evaluation eval = output.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return Integer.MIN_VALUE / 2;
        }
        int value = eval.getValue();
        if (eval.isMate()) {
            int sign = value >= 0 ? 1 : -1;
            int mate = Math.min(9_999, Math.abs(value));
            return sign * (REPORT_MATE_SORT_SCORE_BASE - mate);
        }
        return value;
    }

    /**
     * Verifies that a source node begins with the same key stored in the CSV row.
     *
     * @param start starting position
     * @param node source tree node
     * @param csvSolution CSV solution text
     * @return true when the source line is compatible with the CSV key
     */
    private static boolean sourceNodeStartsWithCsvKey(Position start, PuzzleRatingSourceNode node, String csvSolution) {
        short csvKey = firstSolutionMove(start, csvSolution);
        return csvKey == Move.NO_MOVE || Move.equals(csvKey, node.solution());
    }

    /**
     * Returns the first non-empty move in a move array.
     *
     * @param moves source moves
     * @return first move, or {@link Move#NO_MOVE}
     */
    private static short firstMove(short[] moves) {
        for (short move : moves) {
            if (move != Move.NO_MOVE) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Finds the legal move that reaches a child source position.
     *
     * @param parentAfterSolution position after the parent solver move
     * @param childPosition child solver-position record
     * @return opponent reply move, or {@link Move#NO_MOVE}
     */
    private static short sourceReplyMove(Position parentAfterSolution, Position childPosition) {
        if (parentAfterSolution == null || childPosition == null) {
            return Move.NO_MOVE;
        }
        long target = childPosition.signatureCore();
        MoveList legal = parentAfterSolution.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            Position candidate = parentAfterSolution.copy();
            try {
                candidate.play(move);
            } catch (RuntimeException ex) {
                continue;
            }
            if (candidate.signatureCore() == target) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Counts nodes in a source solution tree.
     *
     * @param node root node
     * @return node count
     */
    private static int sourceTreeSize(PuzzleRatingSourceNode node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        for (PuzzleRatingSourceBranch branch : sortedSourceBranches(node.branches())) {
            count += sourceTreeSize(branch.child());
        }
        return count;
    }

    /**
     * Formats an explicit solution tree with PGN-style variations.
     *
     * @param start starting position
     * @param root source solution root
     * @return move-numbered SAN variation text
     */
    private static String formatSourceTree(Position start, PuzzleRatingSourceNode root) {
        if (start == null || root == null || root.solution() == Move.NO_MOVE) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.max(128, sourceTreeSize(root) * 18));
        appendSourceNode(out, start.copy(), root, true);
        return out.toString().trim();
    }

    /**
     * Appends one solver move and its continuation tree.
     *
     * @param out destination movetext
     * @param start position before the solver move
     * @param node source-tree solver node
     * @param lineStart whether this move starts a line or variation
     */
    private static void appendSourceNode(StringBuilder out, Position start, PuzzleRatingSourceNode node,
            boolean lineStart) {
        if (start == null || node == null || node.solution() == Move.NO_MOVE) {
            return;
        }
        appendMove(out, start, node.solution(), lineStart);
        Position afterSolution = playOrNull(start, node.solution());
        if (afterSolution == null) {
            return;
        }
        List<PuzzleRatingSourceBranch> branches = sortedSourceBranches(node.branches());
        if (branches.isEmpty()) {
            return;
        }
        PuzzleRatingSourceBranch main = branches.get(0);
        appendMove(out, afterSolution, main.reply(), false);
        for (int i = 1; i < branches.size(); i++) {
            out.append(" (");
            appendSourceBranch(out, afterSolution.copy(), branches.get(i), true);
            out.append(')');
        }
        Position afterReply = playOrNull(afterSolution, main.reply());
        appendSourceNode(out, afterReply, main.child(), false);
    }

    /**
     * Appends one opponent reply and solver continuation as a branch line.
     *
     * @param out destination movetext
     * @param start position before the opponent reply
     * @param branch source solution branch
     * @param lineStart whether the reply starts a variation
     */
    private static void appendSourceBranch(StringBuilder out, Position start, PuzzleRatingSourceBranch branch,
            boolean lineStart) {
        if (start == null || branch == null || branch.reply() == Move.NO_MOVE
                || branch.child().solution() == Move.NO_MOVE) {
            return;
        }
        appendMove(out, start, branch.reply(), lineStart);
        Position afterReply = playOrNull(start, branch.reply());
        appendSourceNode(out, afterReply, branch.child(), false);
    }

    /**
     * Appends a move with explicit PGN-style move numbering.
     *
     * @param out destination movetext
     * @param position position before the move
     * @param move encoded move
     * @param lineStart whether the move starts a line or variation
     */
    private static void appendMove(StringBuilder out, Position position, short move, boolean lineStart) {
        if (position == null || move == Move.NO_MOVE) {
            return;
        }
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '(') {
            out.append(' ');
        }
        if (position.isWhiteToMove()) {
            out.append(Math.max(1, position.fullMoveNumber())).append(". ");
        } else if (lineStart) {
            out.append(Math.max(1, position.fullMoveNumber())).append("... ");
        }
        try {
            out.append(SAN.toAlgebraic(position, move));
        } catch (RuntimeException ex) {
            out.append(Move.toString(move));
        }
    }

    /**
     * Applies one move and suppresses invalid-tree failures.
     *
     * @param position position before the move
     * @param move encoded move
     * @return resulting position, or {@code null} when the move cannot be played
     */
    private static Position playOrNull(Position position, short move) {
        if (position == null || move == Move.NO_MOVE) {
            return null;
        }
        try {
            return position.copy().play(move);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Sorts branches using the same score-first, length-second policy as the
     * record-to-PGN exporter.
     *
     * @param branches source branches
     * @return sorted copy
     */
    private static List<PuzzleRatingSourceBranch> sortedSourceBranches(List<PuzzleRatingSourceBranch> branches) {
        if (branches == null || branches.size() <= 1) {
            return branches == null ? List.of() : branches;
        }
        List<PuzzleRatingSourceBranch> sorted = new ArrayList<>(branches);
        sorted.sort(PuzzleRatingSnapshot::compareSourceBranches);
        return sorted;
    }

    /**
     * Compares source branches by side-to-move quality, then continuation length.
     *
     * @param a first branch
     * @param b second branch
     * @return negative when {@code a} should sort first
     */
    private static int compareSourceBranches(PuzzleRatingSourceBranch a, PuzzleRatingSourceBranch b) {
        int scoreA = branchOptionScore(a);
        int scoreB = branchOptionScore(b);
        if (scoreA != scoreB) {
            return Integer.compare(scoreB, scoreA);
        }
        int lenA = branchOptionLength(a);
        int lenB = branchOptionLength(b);
        return Integer.compare(lenB, lenA);
    }

    /**
     * Returns the record-PGN-style option score for one opponent reply branch.
     *
     * @param branch source branch
     * @return adjusted score
     */
    private static int branchOptionScore(PuzzleRatingSourceBranch branch) {
        if (branch == null || branch.child() == null) {
            return Integer.MIN_VALUE / 2;
        }
        return -branch.child().evalScore();
    }

    /**
     * Returns the record-PGN-style option length for one branch.
     *
     * @param branch source branch
     * @return remaining line length
     */
    private static int branchOptionLength(PuzzleRatingSourceBranch branch) {
        return branch == null ? 0 : 1 + sourceLineLength(branch.child());
    }

    /**
     * Computes the longest remaining source-tree line from one solver node.
     *
     * @param node source node
     * @return line length in plies
     */
    private static int sourceLineLength(PuzzleRatingSourceNode node) {
        if (node == null) {
            return 0;
        }
        int best = node.solution() == Move.NO_MOVE ? 0 : 1;
        for (PuzzleRatingSourceBranch branch : node.branches()) {
            best = Math.max(best, 1 + sourceLineLength(branch.child()));
        }
        return best;
    }

    /**
     * Renders the puzzle board through the same native CRTK SVG renderer used by
     * the book pipeline.
     *
     * @param row source puzzle row
     * @return rendered board SVG
     */
    private static String renderReportBoardSvg(PuzzleRatingRow row) {
        Position position = new Position(row.fen());
        Render render = new Render()
                .setPosition(position)
                .setWhiteSideDown(true)
                .setShowBorder(true)
                .setShowCoordinates(true)
                .setShowSpecialMoveHints(false)
                .addCastlingRights(position)
                .addEnPassant(position);
        return chessWebBoardPalette(render.renderSvg(REPORT_BOARD_PIXELS, REPORT_BOARD_PIXELS));
    }

    /**
     * Recolors the native board SVG to match the chess-web-inspired board palette.
     *
     * @param svg source board SVG
     * @return recolored board SVG
     */
    private static String chessWebBoardPalette(String svg) {
        return removeBoardGridGaps(svg)
                .replace(REPORT_RENDER_FRAME_FILL, REPORT_WEB_BOARD_FRAME_FILL)
                .replace(REPORT_BOARD_GRID_FILL, REPORT_WEB_BOARD_FRAME_FILL)
                .replace(REPORT_BOARD_LIGHT_FILL, REPORT_WEB_BOARD_LIGHT_FILL)
                .replace(REPORT_BOARD_DARK_FILL, REPORT_WEB_BOARD_DARK_FILL)
                .replace(REPORT_BOARD_COORDINATE_FILL, REPORT_WEB_BOARD_COORDINATE_FILL);
    }

    /**
     * Expands generated board-square paths to full tiles so the separator
     * background never appears between fields.
     *
     * @param svg source board SVG
     * @return SVG with contiguous board fields
     */
    private static String removeBoardGridGaps(String svg) {
        Matcher matcher = REPORT_BOARD_SQUARE_PATH.matcher(svg);
        StringBuilder out = new StringBuilder(svg.length());
        while (matcher.find()) {
            String indent = matcher.group(1);
            String fill = matcher.group(2);
            int x0 = Integer.parseInt(matcher.group(3));
            int y0 = Integer.parseInt(matcher.group(4));
            int x1 = Integer.parseInt(matcher.group(5));
            int y1 = Integer.parseInt(matcher.group(8));
            int file = Math.max(0, Math.min(7, ((x0 + x1) / 2) / 200));
            int rank = Math.max(0, Math.min(7, ((y0 + y1) / 2) / 200));
            int fullX0 = file * 200;
            int fullY0 = rank * 200;
            int fullX1 = fullX0 + 200;
            int fullY1 = fullY0 + 200;
            String replacement = indent + "<path fill=\"" + fill + "\" stroke=\"none\" d=\"M"
                    + fullX0 + " " + fullY0
                    + " L" + fullX1 + " " + fullY0
                    + " L" + fullX1 + " " + fullY1
                    + " L" + fullX0 + " " + fullY1
                    + " Z\"/>";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Formats a row solution using the same move-numbered SAN generator used by
     * the puzzle collection builder.
     *
     * @param row source puzzle row
     * @return move-numbered SAN text, or the raw solution when parsing fails
     */
    private static String solutionAlgebraic(PuzzleRatingRow row) {
        String raw = row.solution();
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        Position start = new Position(row.fen());
        String formatted = Builder.formatSanLine(start, solutionMoves(start, raw));
        return formatted.isBlank() ? raw : formatted;
    }

    /**
     * Parses a UCI/SAN move sequence into compact moves for the Chess Puzzle Collection
     * SAN-line formatter.
     *
     * @param start starting position
     * @param moves UCI or SAN move sequence
     * @return parsed compact moves
     */
    private static short[] solutionMoves(Position start, String moves) {
        if (start == null || moves == null || moves.isBlank()) {
            return new short[0];
        }
        List<Short> parsed = new ArrayList<>();
        Position cursor = start.copy();
        for (String token : solutionTokens(moves)) {
            try {
                short move = parseSolutionMove(cursor, token);
                parsed.add(move);
                cursor.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        short[] out = new short[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            out[i] = parsed.get(i);
        }
        return out;
    }

    /**
     * Returns the first parsed move in a solution line.
     *
     * @param start starting position
     * @param moves UCI or SAN move sequence
     * @return first encoded move, or {@link Move#NO_MOVE} when parsing fails
     */
    static short firstSolutionMove(Position start, String moves) {
        if (start == null || moves == null || moves.isBlank()) {
            return Move.NO_MOVE;
        }
        for (String token : solutionTokens(moves)) {
            try {
                return parseSolutionMove(start, token);
            } catch (RuntimeException ex) {
                return Move.NO_MOVE;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Splits UCI/SAN movetext into move tokens and drops PGN move numbers/results.
     *
     * @param moves raw UCI or SAN move text
     * @return move tokens
     */
    private static List<String> solutionTokens(String moves) {
        List<String> out = new ArrayList<>();
        if (moves == null || moves.isBlank()) {
            return out;
        }
        for (String raw : moves.replace(',', ' ').replace('\r', ' ').replace('\n', ' ').trim().split("\\s+")) {
            String token = stripMoveNumberPrefix(raw.trim());
            if (!token.isBlank() && !isResultToken(token)) {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * Removes a leading PGN move-number prefix from a token.
     *
     * @param token source token
     * @return token without a leading {@code 23.} or {@code 23...} prefix
     */
    private static String stripMoveNumberPrefix(String token) {
        int i = 0;
        while (i < token.length() && Character.isDigit(token.charAt(i))) {
            i++;
        }
        int dots = 0;
        while (i + dots < token.length() && token.charAt(i + dots) == '.') {
            dots++;
        }
        if (i > 0 && dots > 0) {
            return token.substring(i + dots);
        }
        return token;
    }

    /**
     * Returns whether a token is a PGN result marker.
     *
     * @param token token to inspect
     * @return true for result markers
     */
    private static boolean isResultToken(String token) {
        return "1-0".equals(token) || "0-1".equals(token) || "1/2-1/2".equals(token) || "*".equals(token);
    }

    /**
     * Parses one solution token as UCI when possible, otherwise as SAN.
     *
     * @param position position before the move
     * @param token move token
     * @return encoded move
     */
    private static short parseSolutionMove(Position position, String token) {
        return Move.isMove(token) ? Move.parse(token) : SAN.fromAlgebraic(position, token);
    }

    /**
     * Draws one normalized metric strip for the difficulty-driver chart.
     * @param canvas SVG canvas builder
     * @param bands rating bands
     * @param colors colors value
     * @param metricName metric name value
     * @param values values to inspect
     * @param labelX label x value
     * @param plotLeft plot left coordinate
     * @param rowTop row top value
     * @param plotWidth plot width in pixels
     * @param rowHeight row height value
     * @param metricIndex metric index
     */
}
