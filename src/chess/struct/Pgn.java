package chess.struct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chess.core.Position;

/**
 * Lightweight PGN utility that parses tags, comments, NAGs, and variations into
 * a {@link Game} tree and can render games back to PGN text.
 * <p>
 * The parser is deliberately permissive: it ignores malformed move numbers and
 * tolerates missing tag pairs.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Pgn {

    /**
     * Pattern for PGN tag pair lines: {@code [Key "Value"]}.
     */
    private static final Pattern TAG_LINE = Pattern.compile("^\\[(\\w+)\\s+\"(.*)\"\\]$");

    /**
     * Legal PGN result tokens.
     */
    private static final Pattern RESULT_TOKEN = Pattern.compile("^(1-0|0-1|1/2-1/2|\\*)$");

    private Pgn() {
        // utility class
    }

    /**
     * Reads all PGN games from {@code path}. Missing files yield an empty list.
     *
     * @param path PGN file
     * @return list of parsed games (possibly empty)
     * @throws IOException if reading fails
     */
    public static List<Game> read(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        String content = Files.readString(path);
        return parseGames(content);
    }

    /**
     * Writes games to a PGN file, overwriting existing content.
     *
     * @param path  destination path (created or truncated)
     * @param games games to serialize; null/empty writes an empty file
     * @throws IOException if writing fails
     */
    public static void write(Path path, List<Game> games) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        String pgn = toPgnString(games);
        Files.writeString(path, pgn, StandardCharsets.UTF_8);
    }

    /**
     * Parses one or more PGN games from a string.
     *
     * @param content raw PGN text
     * @return parsed games (possibly empty)
     */
    public static List<Game> parseGames(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<Game> games = new ArrayList<>();
        for (String block : splitGames(content)) {
            Game g = parseGame(block);
            if (g != null) {
                games.add(g);
            }
        }
        return games;
    }

    /**
     * Parses a single PGN game block into a {@link Game}.
     *
     * @param block PGN game text
     * @return parsed game or {@code null} when no moves/tags are present
     */
    public static Game parseGame(String block) {

        if (block == null || block.trim().isEmpty()) {
            return null;
        }

        String[] lines = block.split("\\R");
        Game game = new Game();

        int lineIdx = parseTagSection(lines, game);

        setStartPositionFromTags(game);
        applyResultTag(game);

        String movetext = collectMovetext(lines, lineIdx);
        List<Token> tokens = tokenize(movetext);
        if (tokens.isEmpty() && game.getTags().isEmpty()) {
            return null;
        }

        Index idx = new Index();
        List<String> pending = new ArrayList<>();
        Game.Node mainline = parseSequence(game, tokens, idx, pending);

        // Comments before the first move belong to the game preamble.
        for (String c : pending) {
            game.addPreambleComment(c);
        }
        game.setMainline(mainline);
        return game;
    }

    /**
     * Parses the tag section at the top of a PGN block and returns the line index
     * where movetext begins.
     */
    private static int parseTagSection(String[] lines, Game game) {
        int lineIdx = 0;
        boolean stop = false;
        while (!stop && lineIdx < lines.length) {
            String line = lines[lineIdx].trim();
            if (line.isEmpty()) {
                stop = true;
            } else {

                boolean consumed = false;
                Matcher m = TAG_LINE.matcher(line);
                if (m.matches()) {
                    game.putTag(m.group(1), m.group(2));
                    consumed = true;
                } else if (line.startsWith("[")) {
                    // Skip malformed tag-like lines such as placeholders like "[insert pgn tags]"
                    consumed = true;
                }

                if (consumed) {
                    lineIdx++;
                } else {
                    stop = true;
                }
            }
        }
        if (lineIdx < lines.length && lines[lineIdx].trim().isEmpty()) {
            lineIdx++; // skip blank separator
        }
        return lineIdx;
    }

    /**
     * Applies the Result tag to the game if present.
     */
    private static void applyResultTag(Game game) {
        String resultTag = game.getTags().get("Result");
        if (resultTag != null && !resultTag.isEmpty()) {
            game.setResult(resultTag);
        }
    }

    /**
     * Collects movetext lines into a single string from a starting index.
     */
    private static String collectMovetext(String[] lines, int startIdx) {
        StringBuilder movetext = new StringBuilder();
        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                movetext.append(line).append(' ');
            }
        }
        return movetext.toString().trim();
    }

    /**
     * Derives and sets the game's starting position from its tag pairs.
     * Defaults to the standard start when tags are absent or invalid.
     *
     * @param game target game (non-null)
     */
    private static void setStartPositionFromTags(Game game) {
        String fen = game.getTags().get("FEN");
        String setup = game.getTags().get("SetUp");
        if (fen == null || fen.isEmpty()) {
            game.setStartPosition(new Position(Game.STANDARD_START_FEN));
            return;
        }
        // Per PGN, SetUp="1" indicates a FEN start; SetUp missing defaults to 1.
        boolean useFen = (setup == null || !"0".equals(setup.trim()));
        if (!useFen) {
            game.setStartPosition(new Position(Game.STANDARD_START_FEN));
            return;
        }
        try {
            game.setStartPosition(new Position(fen.trim()));
        } catch (IllegalArgumentException ex) {
            game.setStartPosition(new Position(Game.STANDARD_START_FEN));
        }
    }

    /**
     * Splits a PGN file content into discrete game blocks separated by blank
     * lines, preserving intra-game newlines.
     *
     * @param content full PGN text
     * @return list of trimmed game blocks (possibly empty)
     */
    private static List<String> splitGames(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean sawMoveText = false;

        for (String line : content.split("\\R", -1)) {
            String trimmed = line.trim();
            boolean blank = trimmed.isEmpty();

            if (blank) {
                if (sawMoveText && !current.isEmpty()) {
                    blocks.add(current.toString().trim());
                    current.setLength(0);
                    sawMoveText = false;
                } else if (!current.isEmpty()) {
                    current.append(System.lineSeparator());
                }
                continue;
            }

            if (!current.isEmpty()) {
                current.append(System.lineSeparator());
            }
            current.append(line);

            // Heuristic: lines that don't start with '[' are movetext
            if (!trimmed.startsWith("[")) {
                sawMoveText = true;
            }
        }

        if (!current.isEmpty()) {
            blocks.add(current.toString().trim());
        }
        return blocks;
    }

    /**
     * Serializes multiple games to PGN text (two newlines between games).
     */
    public static String toPgnString(List<Game> games) {
        if (games == null || games.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < games.size(); i++) {
            sb.append(toPgn(games.get(i)));
            if (i < games.size() - 1) {
                sb.append(System.lineSeparator()).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * Serializes a single game to PGN text, including SetUp/FEN tags when the
     * start position is non-standard.
     */
    public static String toPgn(Game game) {
        StringBuilder sb = new StringBuilder(256);
        Position start = game.getStartPosition();
        boolean nonStandardStart = start != null && !Game.STANDARD_START_FEN.equals(start.toString());

        if (nonStandardStart) {
            sb.append("[SetUp \"1\"]").append(System.lineSeparator());
            sb.append("[FEN \"").append(start.toString()).append("\"]").append(System.lineSeparator());
        }

        for (var entry : game.getTags().entrySet()) {
            String key = entry.getKey();
            if (nonStandardStart && ("FEN".equalsIgnoreCase(key) || "SetUp".equalsIgnoreCase(key))) {
                continue;
            }
            sb.append('[')
                    .append(key)
                    .append(" \"")
                    .append(entry.getValue() != null ? entry.getValue() : "")
                    .append("\"]")
                    .append(System.lineSeparator());
        }

        sb.append(System.lineSeparator());

        for (String c : game.getPreambleComments()) {
            sb.append('{').append(c).append("} ");
        }

        boolean blackToMove = start != null && start.isBlackTurn();
        PlyTracker tracker = new PlyTracker(
                blackToMove ? 1 : 0,
                start != null ? start.getFullMove() : 1,
                blackToMove);

        if (game.getMainline() != null) {
            appendSequence(game.getMainline(), tracker, sb);
            sb.append(' ');
        }

        sb.append(game.getResult() != null ? game.getResult() : "*");
        return sb.toString().trim();
    }

    private static void appendSequence(Game.Node node, PlyTracker tracker, StringBuilder sb) {
        Game.Node cur = node;
        while (cur != null) {
            appendMoveNumber(tracker, sb);
            appendComments(cur.getCommentsBefore(), sb);
            sb.append(cur.getSan());
            appendNags(cur.getNags(), sb);
            appendComments(cur.getCommentsAfter(), sb);

            PlyTracker afterMove = tracker.next();
            for (Game.Node variation : cur.getVariations()) {
                sb.append(" (");
                appendSequence(variation, tracker.copy(), sb);
                sb.append(')');
            }

            tracker = afterMove;
            cur = cur.getNext();
            if (cur != null) {
                sb.append(' ');
            }
        }
    }

    private static void appendMoveNumber(PlyTracker tracker, StringBuilder sb) {
        if (tracker.ply % 2 == 0) {
            sb.append(tracker.moveNumber).append(". ");
        } else if (tracker.printBlackNumbers && tracker.ply == 1) {
            // Only emit the initial "...": 71... when starting from a black-to-move FEN
            sb.append(tracker.moveNumber).append("... ");
        }
    }

    private static void appendComments(List<String> comments, StringBuilder sb) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        for (String c : comments) {
            sb.append('{').append(c).append("} ");
        }
    }

    private static void appendNags(List<Integer> nags, StringBuilder sb) {
        if (nags == null || nags.isEmpty()) {
            return;
        }
        for (Integer n : nags) {
            if (n != null) {
                sb.append('$').append(n).append(' ');
            }
        }
    }

    /**
     * Parses a sequence of tokens into a mainline with variations, accumulating
     * comments and NAGs along the way.
     *
     * @param game             target game to mutate
     * @param tokens           pre-tokenized movetext
     * @param index            mutable cursor into {@code tokens}
     * @param pendingComments  comments collected before encountering a move
     * @return head of the parsed mainline, or {@code null} if none
     */
    private static final int MAX_NESTING = 256;

    private static Game.Node parseSequence(Game game, List<Token> tokens, Index index, List<String> pendingComments) {
        return parseSequence(game, tokens, index, pendingComments, 1);
    }

    /**
     * Parses a sequence of tokens into a mainline with variations, accumulating
     * comments and NAGs along the way.
     *
     * @param game             target game to mutate
     * @param tokens           pre-tokenized movetext
     * @param index            mutable cursor into {@code tokens}
     * @param pendingComments  comments collected before encountering a move
     * @param depth            current recursion depth (for variation nesting)
     * @return head of the parsed mainline, or {@code null} if none
     */
    private static Game.Node parseSequence(
            Game game,
            List<Token> tokens,
            Index index,
            List<String> pendingComments,
            int depth) {
        Game.Node head = null;
        Game.Node current = null;
        boolean lastWasMove = false;

        while (index.value < tokens.size()) {
            Token token = tokens.get(index.value++);
            switch (token.kind) {
                case MOVE -> {
                    Game.Node node = createMoveNode(pendingComments, token.value);
                    if (head == null) {
                        head = node;
                    } else {
                        current.setNext(node);
                    }
                    current = node;
                    lastWasMove = true;
                }
                case COMMENT -> {
                    handleCommentToken(current, pendingComments, token, lastWasMove);
                    lastWasMove = false;
                }
                case NAG -> {
                    handleNagToken(current, token);
                    lastWasMove = false;
                }
                case VAR_OPEN -> {
                    handleVariation(game, tokens, index, current, depth);
                    lastWasMove = false;
                }
                case VAR_CLOSE -> {
                    return head;
                }
                case RESULT -> {
                    String currentResult = game.getResult();
                    if (currentResult == null
                            || "*".equals(currentResult)
                            || !"*".equals(token.value)) {
                        game.setResult(token.value);
                    }
                    lastWasMove = false;
                }
                default -> {
                    // no-op for unsupported token kinds
                }
            }
        }

        return head;
    }

    /**
     * Tokenizes raw movetext into a flat list of move, comment, variation, NAG,
     * and result tokens.
     *
     * @param movetext raw PGN movetext section
     * @return ordered token list (possibly empty)
     */
    private static List<Token> tokenize(String movetext) {
        List<Token> tokens = new ArrayList<>();
        if (movetext == null || movetext.isEmpty()) {
            return tokens;
        }

        int i = 0;
        final int len = movetext.length();
        while (i < len) {
            char c = movetext.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            switch (c) {
                case '{' -> i = readBlockComment(movetext, i, tokens);
                case ';' -> i = readLineComment(movetext, i, tokens);
                case '(' -> i = readParenToken(i, tokens, TokenKind.VAR_OPEN);
                case ')' -> i = readParenToken(i, tokens, TokenKind.VAR_CLOSE);
                case '$' -> i = readNag(movetext, i, tokens, len);
                default -> i = readMoveOrResult(movetext, i, tokens, len);
            }
        }

        return tokens;
    }

    /**
     * Detects whether a result token begins at {@code start} in {@code text}.
     */
    private static String resultToken(String text, int start) {
        String[] candidates = { "1-0", "0-1", "1/2-1/2", "*" };
        for (String candidate : candidates) {
            if (start + candidate.length() > text.length()) {
                continue;
            }
            if (text.startsWith(candidate, start) && isTokenBoundary(text, start, candidate.length())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Checks if a candidate token is delimited by whitespace or punctuation.
     */
    private static boolean isTokenBoundary(String text, int start, int length) {
        int end = start + length;
        boolean left = start == 0 || Character.isWhitespace(text.charAt(start - 1)) || "(){}".indexOf(text.charAt(start - 1)) >= 0;
        boolean right = end >= text.length()
                || Character.isWhitespace(text.charAt(end))
                || "(){};".indexOf(text.charAt(end)) >= 0;
        return left && right;
    }

    /**
     * Strips move numbers and result tokens, returning cleaned SAN tokens or empty
     * string when none remain.
     */
    private static String sanitizeMoveToken(String token) {
        if (token == null) {
            return "";
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (t.startsWith("[")) {
            return "";
        }
        // Strip leading move numbers such as "12." or "12..."
        t = t.replaceFirst("^\\d+\\.(?:\\.{1,2})?", "");

        if (t.isEmpty() || t.equals("...")) {
            return "";
        }
        if (RESULT_TOKEN.matcher(t).matches()) {
            return "";
        }
        return t;
    }

    /**
     * Simple value object representing a token kind and its payload.
     */
    private static final class Token {
        final TokenKind kind;
        final String value;

        Token(TokenKind kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    /**
     * Enumeration of supported PGN token kinds.
     */
    private enum TokenKind {
        MOVE,
        VAR_OPEN,
        VAR_CLOSE,
        COMMENT,
        NAG,
        RESULT
    }

    /**
     * Mutable cursor used during recursive descent parsing.
     */
    private static final class Index {
        int value = 0;
    }

    /**
     * Creates a move node and attaches any pending comments that appeared before
     * it.
     */
    private static Game.Node createMoveNode(List<String> pendingComments, String san) {
        Game.Node node = new Game.Node(san);
        for (String c : pendingComments) {
            node.addCommentBefore(c);
        }
        pendingComments.clear();
        return node;
    }

    /**
     * Applies a comment token either before the next move or after the current one,
     * depending on parsing context.
     */
    private static void handleCommentToken(Game.Node current, List<String> pendingComments, Token token, boolean lastWasMove) {
        if (current != null && lastWasMove) {
            current.addCommentAfter(token.value);
        } else {
            pendingComments.add(token.value);
        }
    }

    /**
     * Parses a NAG token and attaches it to the current node when possible.
     */
    private static void handleNagToken(Game.Node current, Token token) {
        if (current == null) {
            return;
        }
        try {
            current.addNag(Integer.parseInt(token.value));
        } catch (NumberFormatException ignored) {
            // ignore malformed NAG
        }
    }

    /**
     * Parses a nested variation starting at the current cursor and attaches it
     * either to the current move or to the game's root variations.
     */
    private static void handleVariation(Game game, List<Token> tokens, Index index, Game.Node current, int depth) {
        if (depth >= MAX_NESTING) {
            return; // avoid stack blow-up on pathological nesting
        }
        List<String> nestedPending = new ArrayList<>();
        Game.Node variation = parseSequence(game, tokens, index, nestedPending, depth + 1);
        if (variation == null) {
            return;
        }
        for (String c : nestedPending) {
            variation.addCommentBefore(c);
        }
        if (current != null) {
            current.addVariation(variation);
        } else {
            game.addRootVariation(variation);
        }
    }

    /**
     * Reads a block comment starting at {@code idx} and returns the next index to
     * scan.
     */
    private static int readBlockComment(String text, int idx, List<Token> tokens) {
        int end = text.indexOf('}', idx + 1);
        String comment = end >= 0 ? text.substring(idx + 1, end) : text.substring(idx + 1);
        tokens.add(new Token(TokenKind.COMMENT, comment.trim()));
        return end >= 0 ? end + 1 : text.length();
    }

    /**
     * Reads a line comment starting at {@code idx} and returns the next index.
     */
    private static int readLineComment(String text, int idx, List<Token> tokens) {
        int end = text.indexOf('\n', idx + 1);
        String comment = end >= 0 ? text.substring(idx + 1, end) : text.substring(idx + 1);
        tokens.add(new Token(TokenKind.COMMENT, comment.trim()));
        return end >= 0 ? end + 1 : text.length();
    }

    /**
     * Emits a parenthesis token and returns the advanced index.
     */
    private static int readParenToken(int idx, List<Token> tokens, TokenKind kind) {
        tokens.add(new Token(kind, String.valueOf((kind == TokenKind.VAR_OPEN ? '(' : ')'))));
        return idx + 1;
    }

    /**
     * Reads a NAG token (e.g., {@code $3}) and returns the advanced index.
     */
    private static int readNag(String text, int idx, List<Token> tokens, int len) {
        int i = idx + 1;
        while (i < len && Character.isDigit(text.charAt(i))) {
            i++;
        }
        if (i > idx + 1) {
            tokens.add(new Token(TokenKind.NAG, text.substring(idx + 1, i)));
        }
        return i;
    }

    /**
     * Reads either a result token or a SAN move starting at {@code idx}.
     */
    private static int readMoveOrResult(String text, int idx, List<Token> tokens, int len) {
        String result = resultToken(text, idx);
        if (result != null) {
            tokens.add(new Token(TokenKind.RESULT, result));
            return idx + result.length();
        }

        int i = idx;
        while (i < len) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == ';') {
                break;
            }
            i++;
        }

        String raw = text.substring(idx, i);
        String san = sanitizeMoveToken(raw);
        if (!san.isEmpty()) {
            tokens.add(new Token(TokenKind.MOVE, san));
        }
        return Math.max(i, idx + 1);
    }

    /**
     * Tracks ply and move number while emitting movetext.
     */
    private static final class PlyTracker {
        final int ply;
        final int moveNumber;
        final boolean printBlackNumbers;

        PlyTracker(int ply, int moveNumber, boolean printBlackNumbers) {
            this.ply = ply;
            this.moveNumber = moveNumber;
            this.printBlackNumbers = printBlackNumbers;
        }

        PlyTracker copy() {
            return new PlyTracker(ply, moveNumber, printBlackNumbers);
        }

        PlyTracker next() {
            int nextPly = ply + 1;
            int nextMoveNumber = moveNumber;
            if (nextPly % 2 == 0) {
                nextMoveNumber++;
            }
            return new PlyTracker(nextPly, nextMoveNumber, printBlackNumbers);
        }
    }
}
