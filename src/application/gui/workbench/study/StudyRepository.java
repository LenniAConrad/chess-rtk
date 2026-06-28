package application.gui.workbench.study;

import application.gui.workbench.game.GameModel;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.review.StudyUnit;
import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.schema.JsonValue.Kind;
import chess.study.StudyChapter;
import chess.study.StudyChapterMode;
import chess.study.StudyProject;
import chess.struct.Game;
import chess.struct.Pgn;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import utility.Json;

/**
 * Local Workbench study-book repository.
 *
 * <p>A study book is stored as one PGN file; each parsed game in that PGN is a
 * chapter. That keeps the local study format portable without adding a
 * separate GUI-only file type.</p>
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
     * Sidecar schema marker.
     */
    private static final String SIDECAR_SCHEMA = "crtk.study.project.v1";

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
        return createStudy("Workbench Study").pgnPath();
    }

    /**
     * Creates and saves a local PGN-backed study.
     *
     * @param title study title
     * @return created project
     * @throws IOException when writing fails
     */
    public StudyProject createStudy(String title) throws IOException {
        Files.createDirectories(root);
        Path path = uniqueStudyPath(title == null || title.isBlank() ? STARTER_FILE : slug(title) + ".pgn");
        Game game = emptyChapter(title == null || title.isBlank() ? "Workbench Study" : title);
        StudyProject project = new StudyProject(path, game.getTags().getOrDefault("Event", "Workbench Study"),
                List.of(new StudyChapter("chapter-001", chapterName(game, 0), 0, game,
                        StudyChapterMode.NORMAL, "auto", "")),
                Map.of());
        saveStudy(project);
        return project;
    }

    /**
     * Opens a study from a PGN file and optional sidecar.
     *
     * @param path PGN path
     * @return study project
     * @throws IOException when reading fails
     */
    public StudyProject openStudy(Path path) throws IOException {
        Path normalized = path == null ? root.resolve(STARTER_FILE) : path.toAbsolutePath().normalize();
        List<Game> games = Pgn.read(normalized);
        Sidecar sidecar = readSidecar(sidecarPath(normalized));
        String title = sidecar.title().isBlank() ? inferredTitle(normalized, games) : sidecar.title();
        List<StudyChapter> chapters = new ArrayList<>();
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            ChapterMeta meta = sidecar.chapterByOrder(i);
            String id = meta == null || meta.id().isBlank() ? chapterId(i) : meta.id();
            String name = meta == null || meta.name().isBlank() ? chapterName(game, i) : meta.name();
            StudyChapterMode mode = meta == null ? StudyChapterMode.NORMAL : meta.mode();
            String orientation = meta == null ? "auto" : meta.orientation();
            String description = meta == null ? "" : meta.description();
            chapters.add(new StudyChapter(id, name, i, game, mode, orientation, description));
        }
        if (chapters.isEmpty()) {
            chapters.add(new StudyChapter("chapter-001", "Chapter 1", 0, emptyChapter(title),
                    StudyChapterMode.NORMAL, "auto", ""));
        }
        return new StudyProject(normalized, title, chapters, sidecar.metadata());
    }

    /**
     * Saves a study to its current PGN path and sidecar.
     *
     * @param project project
     * @throws IOException when writing fails
     */
    public void saveStudy(StudyProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        Path path = project.pgnPath();
        if (path == null) {
            path = uniqueStudyPath(slug(project.title()) + ".pgn");
            project.setPgnPath(path);
        }
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        project.normalizeOrder();
        List<Game> games = new ArrayList<>();
        for (StudyChapter chapter : project.chapters()) {
            chapter.game().putTag("Event", project.title());
            chapter.game().putTag("ChapterName", chapter.name());
            chapter.game().putTag("Result", chapter.game().getResult());
            games.add(chapter.game());
        }
        Pgn.write(path, games);
        Files.writeString(sidecarPath(path), sidecarJson(project), StandardCharsets.UTF_8);
    }

    /**
     * Saves a study to a new PGN path.
     *
     * @param project project
     * @param path new PGN path
     * @throws IOException when writing fails
     */
    public void saveStudyAs(StudyProject project, Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        project.setPgnPath(path.toAbsolutePath().normalize());
        saveStudy(project);
    }

    /**
     * Adds a chapter from a game.
     *
     * @param project project
     * @param name chapter name
     * @param game source game
     * @return added chapter
     */
    public StudyChapter addChapterFromGame(StudyProject project, String name, Game game) {
        Objects.requireNonNull(project, "project");
        int order = project.mutableChapters().size();
        String chapterName = name == null || name.isBlank() ? chapterName(game, order) : name;
        Game chapterGame = game == null ? emptyChapter(chapterName) : game;
        chapterGame.putTag("ChapterName", chapterName);
        StudyChapter chapter = new StudyChapter(nextChapterId(project), chapterName, order, chapterGame,
                StudyChapterMode.NORMAL, "auto", "");
        project.addChapter(chapter);
        return chapter;
    }

    /**
     * Adds a chapter from a FEN root.
     *
     * @param project project
     * @param name chapter name
     * @param fen FEN root
     * @return added chapter
     */
    public StudyChapter addChapterFromFen(StudyProject project, String name, String fen) {
        Game game = emptyChapter(name == null || name.isBlank() ? "FEN Chapter" : name);
        game.setStartPosition(new Position(fen));
        game.putTag("SetUp", "1");
        game.putTag("FEN", game.getStartPosition().toString());
        return addChapterFromGame(project, name, game);
    }

    /**
     * Adds a chapter from the current Workbench game line.
     *
     * @param project project
     * @param name chapter name
     * @param model game model
     * @return added chapter
     */
    public StudyChapter addChapterFromCurrentLine(StudyProject project, String name, GameModel model) {
        Game parsed = model == null ? null : Pgn.parseGame(model.pgn());
        return addChapterFromGame(project, name, parsed);
    }

    /**
     * Imports chapters from a PGN path.
     *
     * @param project project
     * @param path PGN path
     * @return imported count
     * @throws IOException when reading fails
     */
    public int importChaptersFromPgn(StudyProject project, Path path) throws IOException {
        return importChaptersFromPgn(project, Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * Imports chapters from PGN text.
     *
     * @param project project
     * @param pgn PGN text
     * @return imported count
     */
    public int importChaptersFromPgn(StudyProject project, String pgn) {
        Objects.requireNonNull(project, "project");
        int count = 0;
        for (Game game : Pgn.parseGames(pgn)) {
            addChapterFromGame(project, chapterName(game, project.mutableChapters().size()), game);
            count++;
        }
        return count;
    }

    /**
     * Imports review study-unit JSONL rows as chapters.
     *
     * @param project project
     * @param path study-unit JSONL path
     * @return imported count
     * @throws IOException when reading fails
     */
    public int importStudyUnitsFromJsonl(StudyProject project, Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        int count = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonValue row = JsonParser.parse(line);
            if (!StudyUnit.SCHEMA_VERSION.equals(string(row, "schemaVersion"))) {
                continue;
            }
            addChapterFromGame(project, fallback(string(row, "id"), "Review Unit"), studyUnitGame(row));
            count++;
        }
        return count;
    }

    /**
     * Deletes a chapter.
     *
     * @param project project
     * @param id chapter id
     * @return true when deleted
     */
    public boolean deleteChapter(StudyProject project, String id) {
        Objects.requireNonNull(project, "project");
        return project.removeChapter(id);
    }

    /**
     * Renames a chapter.
     *
     * @param project project
     * @param id chapter id
     * @param name new name
     * @return true when renamed
     */
    public boolean renameChapter(StudyProject project, String id, String name) {
        Objects.requireNonNull(project, "project");
        return project.chapter(id).map(chapter -> {
            chapter.setName(name);
            return Boolean.TRUE;
        }).orElse(Boolean.FALSE).booleanValue();
    }

    /**
     * Reorders a chapter.
     *
     * @param project project
     * @param id chapter id
     * @param newIndex new index
     * @return true when reordered
     */
    public boolean reorderChapter(StudyProject project, String id, int newIndex) {
        Objects.requireNonNull(project, "project");
        List<StudyChapter> chapters = project.mutableChapters();
        StudyChapter target = null;
        for (StudyChapter chapter : chapters) {
            if (chapter.id().equals(id)) {
                target = chapter;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        chapters.remove(target);
        chapters.add(Math.max(0, Math.min(newIndex, chapters.size())), target);
        for (int i = 0; i < chapters.size(); i++) {
            chapters.get(i).setOrder(i);
        }
        return true;
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
        Sidecar sidecar = readSidecar(sidecarPath(path));
        String title = sidecar.title().isBlank() ? inferredTitle(path, games) : sidecar.title();
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
     * Infers a study title from PGN or file name.
     *
     * @param path PGN path
     * @param games parsed games
     * @return title
     */
    private static String inferredTitle(Path path, List<Game> games) {
        return games == null || games.isEmpty()
                ? titleFromFile(path)
                : games.get(0).getTags().getOrDefault("Event", titleFromFile(path));
    }

    /**
     * Builds a chapter name.
     *
     * @param game game
     * @param index chapter index
     * @return chapter name
     */
    private static String chapterName(Game game, int index) {
        if (game != null) {
            String chapter = game.getTags().get("ChapterName");
            if (chapter != null && !chapter.isBlank()) {
                return chapter;
            }
            String event = game.getTags().get("Event");
            if (event != null && !event.isBlank()) {
                return event;
            }
        }
        return "Chapter " + (index + 1);
    }

    /**
     * Creates an empty chapter game.
     *
     * @param title title
     * @return game
     */
    private static Game emptyChapter(String title) {
        Game game = new Game();
        String safeTitle = title == null || title.isBlank() ? "Workbench Study" : title;
        game.putTag("Event", safeTitle);
        game.putTag("Site", "ChessRTK");
        game.putTag("Round", "-");
        game.putTag("White", "White");
        game.putTag("Black", "Black");
        game.putTag("Result", "*");
        game.putTag("Annotator", "ChessRTK");
        game.putTag("ChapterName", safeTitle);
        game.setResult("*");
        return game;
    }

    /**
     * Builds one study-unit chapter game.
     *
     * @param row parsed JSON row
     * @return game
     */
    private static Game studyUnitGame(JsonValue row) {
        Game game = emptyChapter(fallback(string(row, "id"), "Review Unit"));
        game.setStartPosition(new Position(string(row, "parent_fen")));
        game.putTag("SetUp", "1");
        game.putTag("FEN", game.getStartPosition().toString());
        game.putTag("White", fallback(string(row, "white"), "White"));
        game.putTag("Black", fallback(string(row, "black"), "Black"));
        String bestUci = string(row, "best_uci");
        String playedUci = string(row, "played_uci");
        Position cursor = game.getStartPosition().copy();
        Game.Node best = appendUciLine(cursor, bestUci, strings(row, "refutation_line"));
        if (best != null) {
            best.addNag(1);
            best.addCommentAfter(studyUnitComment(row));
            game.setMainline(best);
        }
        if (playedUci != null && !playedUci.equals(bestUci)) {
            Game.Node played = appendUciLine(game.getStartPosition().copy(), playedUci, List.of());
            if (played != null) {
                String category = fallback(string(row, "mistake_category"), "");
                played.addNag(category.toLowerCase(Locale.ROOT).contains("blunder") ? 4 : 2);
                played.addCommentAfter("Played in review: " + fallback(string(row, "played_san"), playedUci));
                game.addRootVariation(played);
            }
        }
        return game;
    }

    /**
     * Appends a UCI line into newly created nodes.
     *
     * @param start start position
     * @param first first move
     * @param continuation continuation moves
     * @return head node
     */
    private static Game.Node appendUciLine(Position start, String first, List<String> continuation) {
        if (first == null || first.isBlank()) {
            return null;
        }
        List<String> moves = new ArrayList<>();
        moves.add(first);
        if (continuation != null) {
            for (String move : continuation) {
                if (move != null && !move.isBlank() && !move.equals(first)) {
                    moves.add(move);
                }
            }
        }
        Position cursor = start.copy();
        Game.Node head = null;
        Game.Node previous = null;
        for (String uci : moves) {
            short move = Move.parse(uci);
            if (!cursor.isLegalMove(move)) {
                break;
            }
            Game.Node node = new Game.Node(SAN.toAlgebraic(cursor, move));
            if (head == null) {
                head = node;
            } else {
                previous.setNext(node);
            }
            previous = node;
            cursor.play(move);
        }
        return head;
    }

    /**
     * Builds a study-unit comment.
     *
     * @param row row
     * @return comment
     */
    private static String studyUnitComment(JsonValue row) {
        return "Review unit: played " + fallback(string(row, "played_san"), string(row, "played_uci"))
                + "; best " + fallback(string(row, "best_san"), string(row, "best_uci"))
                + "; category " + fallback(string(row, "mistake_category"), "unknown")
                + "; difficulty " + fallback(string(row, "difficulty"), "unknown")
                + "; tags " + String.join(", ", strings(row, "tags"));
    }

    /**
     * Returns a stable next chapter id.
     *
     * @param project project
     * @return chapter id
     */
    private static String nextChapterId(StudyProject project) {
        int index = project.mutableChapters().size() + 1;
        String id;
        do {
            id = chapterId(index - 1);
            index++;
        } while (project.chapter(id).isPresent());
        return id;
    }

    /**
     * Returns a deterministic chapter id.
     *
     * @param index zero-based index
     * @return chapter id
     */
    private static String chapterId(int index) {
        return String.format(Locale.ROOT, "chapter-%03d", index + 1);
    }

    /**
     * Returns a sidecar path for a PGN file.
     *
     * @param pgnPath PGN path
     * @return sidecar path
     */
    private static Path sidecarPath(Path pgnPath) {
        Path absolute = pgnPath.toAbsolutePath().normalize();
        String file = absolute.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String stem = dot > 0 ? file.substring(0, dot) : file;
        return absolute.resolveSibling(stem + ".crtk-study.json");
    }

    /**
     * Builds sidecar JSON.
     *
     * @param project project
     * @return sidecar JSON
     */
    private static String sidecarJson(StudyProject project) {
        StringBuilder out = new StringBuilder(512);
        out.append("{\n");
        out.append("  \"schemaVersion\":\"").append(SIDECAR_SCHEMA).append("\",\n");
        out.append("  \"title\":\"").append(Json.esc(project.title())).append("\",\n");
        out.append("  \"chapters\":[\n");
        List<StudyChapter> chapters = project.chapters();
        for (int i = 0; i < chapters.size(); i++) {
            StudyChapter chapter = chapters.get(i);
            out.append("    {\"id\":\"").append(Json.esc(chapter.id()))
                    .append("\",\"name\":\"").append(Json.esc(chapter.name()))
                    .append("\",\"order\":").append(chapter.order())
                    .append(",\"mode\":\"").append(chapter.mode().name())
                    .append("\",\"orientation\":\"").append(Json.esc(chapter.orientation()))
                    .append("\",\"description\":\"").append(Json.esc(chapter.description()))
                    .append("\"}");
            if (i + 1 < chapters.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    /**
     * Reads sidecar metadata.
     *
     * @param path sidecar path
     * @return sidecar metadata
     * @throws IOException when reading fails
     */
    private static Sidecar readSidecar(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return Sidecar.EMPTY;
        }
        JsonValue value = JsonParser.parse(Files.readString(path, StandardCharsets.UTF_8));
        if (value.kind() != Kind.OBJECT || !SIDECAR_SCHEMA.equals(string(value, "schemaVersion"))) {
            return Sidecar.EMPTY;
        }
        List<ChapterMeta> chapters = new ArrayList<>();
        JsonValue chapterArray = child(value, "chapters");
        if (chapterArray != null && chapterArray.kind() == Kind.ARRAY) {
            for (JsonValue item : chapterArray.asArray()) {
                if (item.kind() == Kind.OBJECT) {
                    chapters.add(new ChapterMeta(
                            string(item, "id"),
                            fallback(string(item, "name"), ""),
                            integer(item, "order", chapters.size()),
                            mode(string(item, "mode")),
                            fallback(string(item, "orientation"), "auto"),
                            fallback(string(item, "description"), "")));
                }
            }
        }
        return new Sidecar(fallback(string(value, "title"), ""), chapters, Map.of());
    }

    /**
     * Returns one child value.
     *
     * @param value object value
     * @param key key
     * @return child value
     */
    private static JsonValue child(JsonValue value, String key) {
        if (value == null || value.kind() != Kind.OBJECT) {
            return null;
        }
        return value.asObject().get(key);
    }

    /**
     * Returns a string field.
     *
     * @param value object value
     * @param key key
     * @return string, or null
     */
    private static String string(JsonValue value, String key) {
        JsonValue child = child(value, key);
        return child != null && child.kind() == Kind.STRING ? child.asString() : null;
    }

    /**
     * Returns string array field.
     *
     * @param value object value
     * @param key key
     * @return strings
     */
    private static List<String> strings(JsonValue value, String key) {
        JsonValue child = child(value, key);
        if (child == null || child.kind() != Kind.ARRAY) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonValue item : child.asArray()) {
            if (item.kind() == Kind.STRING) {
                out.add(item.asString());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Returns an integer field.
     *
     * @param value object value
     * @param key key
     * @param fallback fallback
     * @return integer
     */
    private static int integer(JsonValue value, String key, int fallback) {
        JsonValue child = child(value, key);
        return child != null && child.kind() == Kind.NUMBER ? (int) child.asNumber() : fallback;
    }

    /**
     * Returns a chapter mode.
     *
     * @param value mode text
     * @return mode
     */
    private static StudyChapterMode mode(String value) {
        if (value != null) {
            for (StudyChapterMode mode : StudyChapterMode.values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return StudyChapterMode.NORMAL;
    }

    /**
     * Returns fallback when value is blank.
     *
     * @param value source value
     * @param fallback fallback
     * @return chosen value
     */
    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Returns a safe file slug.
     *
     * @param title title
     * @return slug
     */
    private static String slug(String title) {
        String slug = title == null ? "study" : title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "study" : slug;
    }

    /**
     * Returns a unique study path.
     *
     * @param fileName desired file name
     * @return unique path
     * @throws IOException when directories cannot be created
     */
    private Path uniqueStudyPath(String fileName) throws IOException {
        Files.createDirectories(root);
        Path path = root.resolve(fileName);
        if (!Files.exists(path)) {
            return path;
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : ".pgn";
        for (int i = 2; ; i++) {
            Path candidate = root.resolve(stem + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Optional sidecar metadata.
     *
     * @param title project title
     * @param chapters chapter metadata
     * @param metadata additional metadata
     */
    private record Sidecar(String title, List<ChapterMeta> chapters, Map<String, String> metadata) {

        /**
         * Empty sidecar.
         */
        private static final Sidecar EMPTY = new Sidecar("", List.of(), Map.of());

        /**
         * Creates sidecar metadata.
         *
         * @param title project title
         * @param chapters chapter metadata
         * @param metadata additional metadata
         */
        private Sidecar {
            title = title == null ? "" : title;
            chapters = chapters == null ? List.of() : List.copyOf(chapters);
            metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
        }

        /**
         * Returns metadata for a chapter order.
         *
         * @param order chapter order
         * @return metadata, or null
         */
        private ChapterMeta chapterByOrder(int order) {
            return chapters.stream()
                    .filter(chapter -> chapter.order() == order)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Chapter sidecar metadata.
     *
     * @param id chapter id
     * @param name chapter name
     * @param order chapter order
     * @param mode chapter mode
     * @param orientation orientation
     * @param description description
     */
    private record ChapterMeta(String id, String name, int order, StudyChapterMode mode,
            String orientation, String description) {

        /**
         * Creates chapter metadata.
         *
         * @param id chapter id
         * @param name chapter name
         * @param order chapter order
         * @param mode chapter mode
         * @param orientation orientation
         * @param description description
         */
        private ChapterMeta {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            mode = mode == null ? StudyChapterMode.NORMAL : mode;
            orientation = orientation == null || orientation.isBlank() ? "auto" : orientation;
            description = description == null ? "" : description;
        }
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
