package application.gui.workbench.game;

import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed puzzle library support for the workbench puzzle trainer.
 */
public final class PuzzleLibrary {

    /**
     * Default puzzle collection exported from the chess-web puzzle PGN. This
     * lives beside the ECO book under config so the launcher can auto-load it
     * from the application home.
     */
    public static final Path DEFAULT_PATH = Path.of("config", "puzzles.pgn");

    /**
     * Previous config location retained as a fallback for existing checkouts.
     */
    private static final Path PREVIOUS_DEFAULT_PATH =
            Path.of("config", "puzzles", "chess-web-stack-100k.pgn");

    /**
     * Original asset location retained as a fallback for older checkouts.
     */
    private static final Path LEGACY_ASSET_PATH =
            Path.of("assets", "puzzles", "chess-web-stack-100k.pgn");

    /**
     * Expected Lichess CSV column count.
     */
    private static final int LICHESS_COLUMN_COUNT = 10;

    /**
     * Pattern for PGN tag pair lines.
     */
    private static final Pattern PGN_TAG_LINE = Pattern.compile("^\\[(\\w+)\\s+\"(.*)\"\\]$");

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
     * One CSV or PGN-backed puzzle entry.
     *
     * @param id source puzzle identifier
     * @param fen start FEN
     * @param moves solution moves in UCI order
     * @param rating puzzle rating
     * @param themes source theme labels
     * @param gameUrl source game URL
     * @param openingTags source opening labels
     * @param pgnText original PGN block for variation-aware puzzles
     */
    public record Entry(
            String id,
            String fen,
            List<String> moves,
            int rating,
            String themes,
            String gameUrl,
            String openingTags,
            String pgnText) {

        /**
         * Normalizes nullable entry fields.
         *
         * @param id source puzzle identifier
         * @param fen start FEN
         * @param moves solution moves in UCI order
         * @param rating puzzle rating
         * @param themes source theme labels
         * @param gameUrl source game URL
         * @param openingTags source opening labels
         * @param pgnText original PGN block
         */
        public Entry {
            id = safe(id);
            fen = safe(fen);
            moves = moves == null ? List.of() : List.copyOf(moves);
            themes = safe(themes);
            gameUrl = safe(gameUrl);
            openingTags = safe(openingTags);
            pgnText = pgnText == null ? "" : pgnText.trim();
        }

        /**
         * Returns whether the entry carries its source PGN block.
         *
         * @return true when PGN text is available
         */
        public boolean hasPgnText() {
            return !pgnText.isBlank();
        }

        /**
         * Returns a compact title for UI labels.
         *
         * @return entry title
         */
        public String title() {
            if (hasPgnText()) {
                return id.isBlank() ? "Chess-web puzzle" : id;
            }
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
     * Reads a puzzle library file. CSV rows are interpreted as Lichess-style
     * UCI lines; PGN files are indexed as variation-aware puzzle blocks.
     *
     * @param path puzzle library file
     * @return parsed entries
     * @throws IOException when the file cannot be read or no entries exist
     */
    public static List<Entry> read(Path path) throws IOException {
        if (isCsv(path)) {
            return readCsv(path);
        }
        if (isPgn(path)) {
            return readPgn(path);
        }
        throw new IOException("Unsupported puzzle library format: " + path);
    }

    /**
     * Resolves the bundled chess-web puzzle collection from either the current
     * working directory or the application home that contains the running jar.
     *
     * @return first existing default puzzle path, or {@link #DEFAULT_PATH}
     */
    public static Path defaultPath() {
        for (Path candidate : defaultPathCandidates()) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_PATH;
    }

    /**
     * Builds ordered fallback locations for the default puzzle pack.
     *
     * @return candidate paths
     */
    private static List<Path> defaultPathCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        addDefaultPathCandidate(candidates, DEFAULT_PATH);
        addDefaultPathCandidate(candidates, PREVIOUS_DEFAULT_PATH);
        addDefaultPathCandidate(candidates, LEGACY_ASSET_PATH);

        String appHome = System.getProperty("crtk.home");
        if (appHome != null && !appHome.isBlank()) {
            addDefaultPathCandidates(candidates, Path.of(appHome));
        }

        Path codeSourceDirectory = codeSourceDirectory();
        if (codeSourceDirectory != null) {
            addDefaultPathCandidates(candidates, codeSourceDirectory);
            Path parent = codeSourceDirectory.getParent();
            if (parent != null) {
                addDefaultPathCandidates(candidates, parent);
            }
        }
        return List.copyOf(candidates);
    }

    /**
     * Adds default and legacy default paths relative to a base directory.
     *
     * @param candidates destination set
     * @param base base directory
     */
    private static void addDefaultPathCandidates(Set<Path> candidates, Path base) {
        if (base != null) {
            addDefaultPathCandidate(candidates, base.resolve(DEFAULT_PATH));
            addDefaultPathCandidate(candidates, base.resolve(PREVIOUS_DEFAULT_PATH));
            addDefaultPathCandidate(candidates, base.resolve(LEGACY_ASSET_PATH));
        }
    }

    /**
     * Adds one normalized candidate path.
     *
     * @param candidates destination set
     * @param path candidate path
     */
    private static void addDefaultPathCandidate(Set<Path> candidates, Path path) {
        if (path != null) {
            candidates.add(path.normalize());
        }
    }

    /**
     * Returns the directory that contains the active class path entry.
     *
     * @return code-source directory, or null when unavailable
     */
    private static Path codeSourceDirectory() {
        try {
            CodeSource source = PuzzleLibrary.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path) ? path.getParent() : path;
        } catch (IllegalArgumentException | SecurityException | URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Reads a Lichess-style puzzle CSV.
     *
     * @param path CSV file
     * @return parsed entries
     * @throws IOException when the file cannot be read or no entries exist
     */
    private static List<Entry> readCsv(Path path) throws IOException {
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
     * Reads a PGN puzzle library while keeping each source block intact.
     *
     * @param path PGN file
     * @return indexed entries
     * @throws IOException when the file cannot be read or no entries exist
     */
    private static List<Entry> readPgn(Path path) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder current = new StringBuilder();
            boolean sawMoveText = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    if (sawMoveText && !current.isEmpty()) {
                        addPgnEntry(entries, current, path);
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
                if (!trimmed.startsWith("[")) {
                    sawMoveText = true;
                }
            }
            if (!current.isEmpty()) {
                addPgnEntry(entries, current, path);
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
     * Returns whether a file path looks like a PGN puzzle library.
     *
     * @param path file path
     * @return true when the extension is pgn
     */
    public static boolean isPgn(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pgn") || name.endsWith(".txt");
    }

    /**
     * Returns whether a file path is a supported puzzle library.
     *
     * @param path file path
     * @return true when the extension is supported
     */
    public static boolean isLibrary(Path path) {
        return isCsv(path) || isPgn(path);
    }

    /**
     * Converts one puzzle entry to a PGN-like text preview.
     *
     * @param entry puzzle entry
     * @return PGN text
     */
    public static String toPgn(Entry entry) {
        if (entry.hasPgnText()) {
            return entry.pgnText().endsWith("\n") ? entry.pgnText() : entry.pgnText() + '\n';
        }
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
                columns.get(OPENING_COLUMN),
                "");
    }

    /**
     * Adds one indexed PGN puzzle entry and clears the source block.
     *
     * @param entries destination entries
     * @param current source PGN block
     * @param path source file path
     */
    private static void addPgnEntry(List<Entry> entries, StringBuilder current, Path path) {
        String pgnText = current.toString().trim();
        current.setLength(0);
        if (pgnText.isBlank()) {
            return;
        }
        int index = entries.size() + 1;
        String title = firstNonBlank(tagValue(pgnText, "Event"), fileStem(path) + " #" + index);
        String fen = firstNonBlank(tagValue(pgnText, "FEN"), Game.STANDARD_START_FEN);
        String themes = firstNonBlank(tagValue(pgnText, "Themes"), "chess-web pgn");
        String normalizedPgn = tagValue(pgnText, "Event").isBlank()
                ? tagLine("Event", title) + System.lineSeparator() + pgnText
                : pgnText;
        entries.add(new Entry(
                title,
                fen,
                List.of(),
                parseRating(tagValue(pgnText, "PuzzleRating")),
                themes,
                tagValue(pgnText, "Site"),
                tagValue(pgnText, "Opening"),
                normalizedPgn));
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
        builder.append(tagLine(name, value)).append('\n');
    }

    /**
     * Formats one escaped PGN tag line.
     *
     * @param name tag name
     * @param value tag value
     * @return formatted tag line
     */
    private static String tagLine(String name, String value) {
        return '[' + name + " \"" + safe(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
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
     * Reads one PGN tag value from a source block.
     *
     * @param pgnText source PGN block
     * @param tagName source tag name
     * @return unescaped tag value, or an empty string
     */
    private static String tagValue(String pgnText, String tagName) {
        for (String line : pgnText.split("\\R")) {
            Matcher matcher = PGN_TAG_LINE.matcher(line.trim());
            if (matcher.matches() && matcher.group(1).equals(tagName)) {
                return unescapeTag(matcher.group(2));
            }
        }
        return "";
    }

    /**
     * Returns the first non-blank value.
     *
     * @param preferred source preferred
     * @param fallback default used when input is absent or invalid
     * @return selected value
     */
    private static String firstNonBlank(String preferred, String fallback) {
        return safe(preferred).isBlank() ? safe(fallback) : safe(preferred);
    }

    /**
     * Derives a readable file stem from a source path.
     *
     * @param path source path
     * @return file stem
     */
    private static String fileStem(Path path) {
        if (path == null || path.getFileName() == null) {
            return "Puzzle";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Unescapes a PGN tag value.
     *
     * @param value escaped tag value
     * @return unescaped value
     */
    private static String unescapeTag(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                builder.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                builder.append(ch);
            }
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
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
