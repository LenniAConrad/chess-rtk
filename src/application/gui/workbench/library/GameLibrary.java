package application.gui.workbench.library;

import application.cli.PathOps;
import application.gui.workbench.game.GameModel;
import chess.pgn.PgnStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Workbench-facing facade over the local PGN store.
 */
public final class GameLibrary {

    /**
     * Default library root shared with {@code crtk pgn}.
     */
    private static final Path DEFAULT_ROOT = PathOps.DEFAULT_DUMP_DIR.resolve("pgn-store");

    /**
     * Backing store root.
     */
    private final Path root;

    /**
     * Creates the default Workbench game library.
     */
    public GameLibrary() {
        this(DEFAULT_ROOT);
    }

    /**
     * Creates a library at the supplied root.
     *
     * @param root store root
     */
    public GameLibrary(Path root) {
        this.root = root == null ? DEFAULT_ROOT : root;
    }

    /**
     * Imports all games in a PGN file.
     *
     * @param pgnFile source PGN file
     * @return import report
     * @throws IOException when reading or writing fails
     */
    public PgnStore.ImportReport importPgn(Path pgnFile) throws IOException {
        return PgnStore.open(root).importPgn(pgnFile);
    }

    /**
     * Stores the current Workbench line as a PGN game.
     *
     * @param model source game model
     * @param sourceLabel label recorded in the PGN store
     * @return import report
     * @throws IOException when writing fails
     */
    public PgnStore.ImportReport saveCurrent(GameModel model, String sourceLabel) throws IOException {
        if (model == null || model.lastPly() <= 0) {
            return new PgnStore.ImportReport(clean(sourceLabel), 0, 0, 0, 0);
        }
        return PgnStore.open(root).importPgnText(model.pgn(), clean(sourceLabel));
    }

    /**
     * Loads recent stored games for display.
     *
     * @param limit maximum number of entries
     * @return library entries
     * @throws IOException when reading fails
     */
    public List<Entry> recent(int limit) throws IOException {
        return PgnStore.open(root).listGames(limit).stream()
                .map(Entry::fromStored)
                .toList();
    }

    /**
     * Summarized library game row.
     *
     * @param gameId canonical PGN-store id
     * @param event event tag
     * @param white white player tag
     * @param black black player tag
     * @param result result tag
     * @param source import source
     * @param pgn stored PGN
     */
    public record Entry(String gameId, String event, String white, String black,
            String result, String source, String pgn) {

        /**
         * Creates a UI entry from a stored game.
         *
         * @param stored stored game
         * @return summarized entry
         */
        static Entry fromStored(PgnStore.StoredGame stored) {
            Map<String, String> headers = stored.headers();
            return new Entry(stored.gameId(),
                    header(headers, "Event", "PGN"),
                    header(headers, "White", "?"),
                    header(headers, "Black", "?"),
                    header(headers, "Result", "*"),
                    clean(stored.importedFrom()),
                    clean(stored.pgn()));
        }

        /**
         * Compact single-line label.
         *
         * @return display label
         */
        public String label() {
            return event + " | " + white + " vs " + black + " | " + result;
        }

        /**
         * Searchable text for UI filtering.
         *
         * @return lower-level search text
         */
        public String searchableText() {
            return (gameId + " " + event + " " + white + " " + black + " "
                    + result + " " + source + " " + pgn).toLowerCase(java.util.Locale.ROOT);
        }

        private static String header(Map<String, String> headers, String name, String fallback) {
            if (headers == null) {
                return fallback;
            }
            String value = headers.get(name);
            return clean(value).isBlank() ? fallback : clean(value);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
