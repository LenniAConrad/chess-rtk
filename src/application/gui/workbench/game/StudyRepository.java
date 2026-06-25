package application.gui.workbench.game;

import chess.struct.Game;
import chess.struct.Pgn;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local Workbench study-book repository.
 *
 * <p>A study book is stored as one PGN file; each parsed game in that PGN is a
 * chapter. That mirrors the important Lichess study shape without adding a
 * separate GUI-only study format.</p>
 */
public final class StudyRepository {

    /**
     * Default local study directory.
     */
    private static final Path DEFAULT_ROOT = Path.of("dump", "studies");

    /**
     * Starter file name.
     */
    private static final String STARTER_FILE = "workbench-study.pgn";

    /**
     * Repository root.
     */
    private final Path root;

    /**
     * Creates a repository rooted in {@code root}.
     *
     * @param root repository root
     */
    public StudyRepository(Path root) {
        this.root = root == null ? DEFAULT_ROOT : root.toAbsolutePath().normalize();
    }

    /**
     * Creates the default Workbench study repository.
     *
     * @return default repository
     */
    public static StudyRepository defaultRepository() {
        return new StudyRepository(DEFAULT_ROOT);
    }

    /**
     * Returns the repository root.
     *
     * @return repository root
     */
    public Path root() {
        return root;
    }

    /**
     * Lists local PGN-backed study books.
     *
     * @return sorted study books
     * @throws IOException when scanning or parsing fails
     */
    public List<StudyBook> studies() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> files = studyFiles();
        List<StudyBook> books = new ArrayList<>(files.size());
        for (Path file : files) {
            books.add(readBook(file));
        }
        return List.copyOf(books);
    }

    /**
     * Returns the number of local study books. Scan failures read as zero so a
     * broken local file never breaks the Workbench navigator paint path.
     *
     * @return study count
     */
    public int count() {
        try {
            return studyFiles().size();
        } catch (IOException ex) {
            return 0;
        }
    }

    /**
     * Creates the starter study if the repository is empty.
     *
     * @return starter or first existing study path
     * @throws IOException when writing fails
     */
    public Path ensureStarterStudy() throws IOException {
        List<StudyBook> existing = studies();
        if (!existing.isEmpty()) {
            return existing.get(0).path();
        }
        Files.createDirectories(root);
        Path starter = root.resolve(STARTER_FILE);
        if (!Files.exists(starter)) {
            Files.writeString(starter, starterPgn(), StandardCharsets.UTF_8);
        }
        return starter;
    }

    /**
     * Reads summary metadata from one PGN-backed study book.
     *
     * @param path PGN file
     * @return study book summary
     * @throws IOException when reading fails
     */
    private static StudyBook readBook(Path path) throws IOException {
        List<Game> games = Pgn.read(path);
        String title = games.isEmpty()
                ? titleFromFile(path)
                : games.get(0).getTags().getOrDefault("Event", titleFromFile(path));
        boolean empty = games.isEmpty() || games.stream()
                .allMatch(game -> game.getMainline() == null && game.getRootVariations().isEmpty());
        return new StudyBook(path, title, games.size(), empty);
    }

    /**
     * Lists PGN study files without parsing them.
     *
     * @return sorted PGN files
     * @throws IOException when scanning fails
     */
    private List<Path> studyFiles() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(StudyRepository::isPgn)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    /**
     * Returns whether a path is a PGN file.
     *
     * @param path candidate path
     * @return true when PGN
     */
    private static boolean isPgn(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgn");
    }

    /**
     * Builds a readable title from a file name.
     *
     * @param path source path
     * @return title
     */
    private static String titleFromFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return "Untitled Study";
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Starter PGN. One PGN file is the study book; the one empty game is its
     * first chapter.
     *
     * @return starter PGN
     */
    private static String starterPgn() {
        return """
                [Event "Workbench Study"]
                [Site "ChessRTK"]
                [Round "-"]
                [White "White"]
                [Black "Black"]
                [Result "*"]
                [Annotator "ChessRTK"]

                *
                """;
    }

    /**
     * Local study-book summary.
     *
     * @param path PGN path
     * @param title display title
     * @param chapters parsed PGN game/chapter count
     * @param empty true when all chapters contain no moves
     */
    public record StudyBook(Path path, String title, int chapters, boolean empty) {
    }
}
