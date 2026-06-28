package chess.study;

import chess.struct.Game;
import java.util.Locale;
import java.util.Objects;

/**
 * One PGN game chapter inside a local study project.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyChapter {

    /**
     * Stable chapter id.
     */
    private final String id;

    /**
     * Display name.
     */
    private String name;

    /**
     * Chapter order.
     */
    private int order;

    /**
     * PGN game backing this chapter.
     */
    private final Game game;

    /**
     * Chapter training mode.
     */
    private StudyChapterMode mode;

    /**
     * Orientation preference.
     */
    private String orientation;

    /**
     * Optional chapter description.
     */
    private String description;

    /**
     * Creates a chapter.
     *
     * @param id stable id
     * @param name display name
     * @param order chapter order
     * @param game backing PGN game
     * @param mode chapter mode
     * @param orientation orientation preference
     * @param description description
     */
    public StudyChapter(String id, String name, int order, Game game, StudyChapterMode mode,
            String orientation, String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null || name.isBlank() ? "Chapter " + (order + 1) : name;
        this.order = Math.max(0, order);
        this.game = game == null ? new Game() : game;
        this.mode = mode == null ? StudyChapterMode.NORMAL : mode;
        this.orientation = normalizeOrientation(orientation);
        this.description = description == null ? "" : description;
    }

    /**
     * Returns the stable chapter id.
     *
     * @return chapter id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the display name.
     *
     * @return chapter name
     */
    public String name() {
        return name;
    }

    /**
     * Renames the chapter.
     *
     * @param name display name
     */
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? this.name : name;
        game.putTag("Event", this.name);
        game.putTag("ChapterName", this.name);
    }

    /**
     * Returns the chapter order.
     *
     * @return order
     */
    public int order() {
        return order;
    }

    /**
     * Updates the chapter order.
     *
     * @param order order
     */
    public void setOrder(int order) {
        this.order = Math.max(0, order);
    }

    /**
     * Returns the backing game.
     *
     * @return game
     */
    public Game game() {
        return game;
    }

    /**
     * Returns the chapter mode.
     *
     * @return mode
     */
    public StudyChapterMode mode() {
        return mode;
    }

    /**
     * Updates the chapter mode.
     *
     * @param mode mode
     */
    public void setMode(StudyChapterMode mode) {
        this.mode = mode == null ? StudyChapterMode.NORMAL : mode;
    }

    /**
     * Returns the orientation preference.
     *
     * @return orientation id
     */
    public String orientation() {
        return orientation;
    }

    /**
     * Updates the orientation preference.
     *
     * @param orientation orientation id
     */
    public void setOrientation(String orientation) {
        this.orientation = normalizeOrientation(orientation);
    }

    /**
     * Returns the optional description.
     *
     * @return description
     */
    public String description() {
        return description;
    }

    /**
     * Updates the description.
     *
     * @param description description
     */
    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    /**
     * Returns the display text.
     *
     * @return display label
     */
    @Override
    public String toString() {
        return (order + 1) + ". " + name;
    }

    /**
     * Normalizes orientation to white, black, or auto.
     *
     * @param value source value
     * @return normalized orientation
     */
    private static String normalizeOrientation(String value) {
        String normalized = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "white", "black" -> normalized;
            default -> "auto";
        };
    }
}
