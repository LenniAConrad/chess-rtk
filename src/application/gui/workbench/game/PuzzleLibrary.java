package application.gui.workbench.game;

import chess.core.Move;
import chess.core.Position;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * File-backed puzzle library support for the workbench puzzle trainer.
 */
public final class PuzzleLibrary {

    /**
     * Default difficult puzzle collection generated from local puzzle data.
     */
    public static final Path DEFAULT_PATH = Path.of("assets", "puzzles", "difficult-lichess-10k.csv");

    /**
     * Expected Lichess CSV column count.
     */
    private static final int LICHESS_COLUMN_COUNT = 10;

    /**
     * Puzzle identifier column index.
     */
    private static final int ID_COLUMN = 0;

    /**
     * FEN column index.
     */
    private static final int FEN_COLUMN = 1;

    /**
     * UCI move-list column index.
     */
    private static final int MOVES_COLUMN = 2;

    /**
     * Rating column index.
     */
    private static final int RATING_COLUMN = 3;

    /**
     * Themes column index.
     */
    private static final int THEMES_COLUMN = 7;

    /**
     * Game URL column index.
     */
    private static final int URL_COLUMN = 8;

    /**
     * Opening tags column index.
     */
    private static final int OPENING_COLUMN = 9;

    /**
     * Prevents instantiation.
     */
    private PuzzleLibrary() {
        // utility
    }

    /**
     * One Lichess-style puzzle entry.
     *
     * @param id source puzzle identifier
     * @param fen start FEN
     * @param moves solution moves in UCI order
     * @param rating puzzle rating
     * @param themes source theme labels
     * @param gameUrl source game URL
     * @param openingTags source opening labels
     */
    public record Entry(
            String id,
            String fen,
            List<String> moves,
            int rating,
            String themes,
            String gameUrl,
            String openingTags) {

        /**
         * Normalizes nullable entry fields.
         */
        public Entry {
            id = safe(id);
            fen = safe(fen);
            moves = moves == null ? List.of() : List.copyOf(moves);
            themes = safe(themes);
            gameUrl = safe(gameUrl);
            openingTags = safe(openingTags);
        }

        /**
         * Returns a compact title for UI labels.
         *
         * @return entry title
         */
        public String title() {
            return id.isBlank() ? "Difficult Puzzle" : "Difficult " + id;
        }

        /**
         * Returns metadata suitable for a one-line UI label.
         *
         * @return display metadata
         */
        public String detail() {
            StringBuilder builder = new StringBuilder();
            if (rating > 0) {
                builder.append("rating ").append(rating);
            }
            String themeText = compactThemes(themes);
            if (!themeText.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(themeText);
            }
            return builder.toString();
        }
    }

    /**
     * Reads a Lichess-style puzzle CSV.
     *
     * @param path CSV file
     * @return parsed entries
     * @throws IOException when the file cannot be read or no entries exist
     */
    public static List<Entry> read(Path path) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("PuzzleId,")) {
                    continue;
                }
                try {
                    entries.add(parseLine(line, lineNumber));
                } catch (IllegalArgumentException ex) {
                    // Keep user-imported libraries resilient by skipping malformed rows.
                }
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("No puzzle entries found in " + path);
        }
        return List.copyOf(entries);
    }

    /**
     * Returns whether a file path looks like a CSV puzzle library.
     *
     * @param path file path
     * @return true when the extension is csv
     */
    public static boolean isCsv(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    /**
     * Converts one puzzle entry to a PGN-like text preview.
     *
     * @param entry puzzle entry
     * @return PGN text
     */
    public static String toPgn(Entry entry) {
        Position cursor = new Position(entry.fen());
        StringBuilder builder = new StringBuilder(256);
        appendTag(builder, "Event", entry.title());
        appendTag(builder, "SetUp", "1");
        appendTag(builder, "FEN", entry.fen());
        if (entry.rating() > 0) {
            appendTag(builder, "PuzzleRating", Integer.toString(entry.rating()));
        }
        if (!entry.themes().isBlank()) {
            appendTag(builder, "Themes", entry.themes());
        }
        if (!entry.openingTags().isBlank()) {
            appendTag(builder, "Opening", entry.openingTags().replace('_', ' '));
        }
        if (!entry.gameUrl().isBlank()) {
            appendTag(builder, "Site", entry.gameUrl());
        }
        builder.append('\n');
        boolean firstMove = true;
        for (String token : entry.moves()) {
            short move = Move.parse(token);
            if (!cursor.isLegalMove(move)) {
                throw new IllegalArgumentException("Illegal puzzle move " + token);
            }
            if (cursor.isWhiteToMove()) {
                builder.append(cursor.fullMoveNumber()).append(". ");
            } else if (firstMove) {
                builder.append(cursor.fullMoveNumber()).append("... ");
            }
            builder.append(PositionText.safeSan(cursor, move)).append(' ');
            cursor.play(move);
            firstMove = false;
        }
        builder.append('*').append('\n');
        return builder.toString();
    }

    /**
     * Parses one CSV row.
     *
     * @param line source line
     * @param lineNumber source line number
     * @return parsed entry
     */
    private static Entry parseLine(String line, int lineNumber) {
        List<String> columns = splitCsv(line);
        if (columns.size() < LICHESS_COLUMN_COUNT) {
            throw new IllegalArgumentException("Malformed puzzle row " + lineNumber);
        }
        List<String> moves = parseMoves(columns.get(MOVES_COLUMN), lineNumber);
        return new Entry(
                columns.get(ID_COLUMN),
                columns.get(FEN_COLUMN),
                moves,
                parseRating(columns.get(RATING_COLUMN)),
                columns.get(THEMES_COLUMN),
                columns.get(URL_COLUMN),
                columns.get(OPENING_COLUMN));
    }

    /**
     * Parses a whitespace-separated UCI move list.
     *
     * @param text move-list text
     * @param lineNumber source line number
     * @return normalized moves
     */
    private static List<String> parseMoves(String text, int lineNumber) {
        List<String> moves = new ArrayList<>();
        for (String token : safe(text).split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!Move.isMove(normalized)) {
                throw new IllegalArgumentException("Invalid move in puzzle row " + lineNumber + ": " + token);
            }
            moves.add(normalized);
        }
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("Puzzle row has no solution moves: " + lineNumber);
        }
        return List.copyOf(moves);
    }

    /**
     * Parses a puzzle rating.
     *
     * @param text rating text
     * @return rating, or zero when missing
     */
    private static int parseRating(String text) {
        try {
            return Integer.parseInt(safe(text));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Splits one basic CSV line while honoring quoted fields.
     *
     * @param line CSV line
     * @return fields
     */
    private static List<String> splitCsv(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    /**
     * Appends one escaped PGN tag.
     *
     * @param builder target builder
     * @param name tag name
     * @param value tag value
     */
    private static void appendTag(StringBuilder builder, String name, String value) {
        builder.append('[')
                .append(name)
                .append(" \"")
                .append(safe(value).replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\"]\n");
    }

    /**
     * Returns a compact theme label.
     *
     * @param themes source theme string
     * @return compact display value
     */
    private static String compactThemes(String themes) {
        String[] parts = safe(themes).split("\\s+");
        List<String> visible = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                visible.add(part);
            }
            if (visible.size() == 3) {
                break;
            }
        }
        return String.join(", ", visible);
    }

    /**
     * Converts null text to an empty string.
     *
     * @param value text value
     * @return non-null text
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
