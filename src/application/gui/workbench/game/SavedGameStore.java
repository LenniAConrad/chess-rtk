package application.gui.workbench.game;

import application.cli.PathOps;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small JSONL-backed store for Workbench games.
 */
public final class SavedGameStore {

    /**
     * Default local filename.
     */
    private static final String DEFAULT_FILENAME = "workbench-games.jsonl";

    /**
     * Per-process suffix used for generated ids.
     */
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /**
     * Store path.
     */
    private final Path path;

    /**
     * Creates a store using the default Workbench dump file.
     */
    public SavedGameStore() {
        this(PathOps.dumpPath(DEFAULT_FILENAME));
    }

    /**
     * Creates a store at a supplied path.
     *
     * @param path JSONL path
     */
    public SavedGameStore(Path path) {
        this.path = path;
    }

    /**
     * Creates a local id for a new saved game.
     *
     * @return id
     */
    public String nextId() {
        return "wg-" + Long.toUnsignedString(System.currentTimeMillis(), 36)
                + "-" + Integer.toUnsignedString(NEXT_ID.incrementAndGet(), 36);
    }

    /**
     * Loads saved games, most recently updated first.
     *
     * @return saved games
     * @throws IOException when reading fails
     */
    public synchronized List<SavedGame> load() throws IOException {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        Map<String, SavedGame> byId = readAll();
        return byId.values().stream()
                .sorted(Comparator.comparingLong(SavedGame::updatedAtMillis).reversed())
                .toList();
    }

    /**
     * Finds one saved game by id.
     *
     * @param id source id
     * @return saved game, or null
     * @throws IOException when reading fails
     */
    public synchronized SavedGame find(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return null;
        }
        return readAll().get(id);
    }

    /**
     * Upserts one saved game.
     *
     * @param game saved game
     * @throws IOException when writing fails
     */
    public synchronized void save(SavedGame game) throws IOException {
        if (game == null || game.id() == null || game.id().isBlank()) {
            return;
        }
        Map<String, SavedGame> byId = readAll();
        byId.put(game.id(), game);
        writeAll(byId.values());
    }

    /**
     * Parses a saved UCI line into legal CRTK moves.
     *
     * @param start start position
     * @param uciLine UCI move line
     * @return validated moves
     */
    public static List<Short> parseUciLine(Position start, String uciLine) {
        if (start == null || uciLine == null || uciLine.isBlank()) {
            return List.of();
        }
        List<Short> moves = new ArrayList<>();
        Position cursor = start.copy();
        for (String token : uciLine.trim().split("\\s+")) {
            short move = legalMoveByUci(cursor, token);
            if (move == Move.NO_MOVE) {
                throw new IllegalArgumentException("Illegal saved move: " + token);
            }
            moves.add(Short.valueOf(move));
            cursor.play(move);
        }
        return List.copyOf(moves);
    }

    /**
     * Reads the read all.
     *
     * @return read all
     * @throws java.io.IOException if external I/O or engine communication fails
     */
    private Map<String, SavedGame> readAll() throws IOException {
        Map<String, SavedGame> byId = new LinkedHashMap<>();
        if (path == null || !Files.exists(path)) {
            return byId;
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            SavedGame game = SavedGame.fromJson(line);
            if (game != null) {
                byId.put(game.id(), game);
            }
        }
        return byId;
    }

    /**
     * Writes the write all.
     *
     * @param games game list
     * @throws java.io.IOException if external I/O or engine communication fails
     */
    private void writeAll(Iterable<SavedGame> games) throws IOException {
        if (path == null) {
            return;
        }
        PathOps.ensureParentDir(path);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        StringBuilder sb = new StringBuilder();
        for (SavedGame game : games) {
            if (game == null) {
                continue;
            }
            sb.append(game.toJson()).append(System.lineSeparator());
        }
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Returns the legal move by UCI.
     *
     * @param position chess position
     * @param uci UCI move string
     * @return legal move by UCI
     */
    private static short legalMoveByUci(Position position, String uci) {
        MoveList legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            if (Move.toString(move).equals(uci)) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }
}
