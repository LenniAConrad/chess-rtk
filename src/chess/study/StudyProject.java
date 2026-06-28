package chess.study;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PGN-backed local study project.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyProject {

    /**
     * Backing PGN path.
     */
    private Path pgnPath;

    /**
     * Project title.
     */
    private String title;

    /**
     * Ordered chapters.
     */
    private final List<StudyChapter> chapters;

    /**
     * Optional sidecar metadata not represented directly in PGN.
     */
    private final Map<String, String> metadata;

    /**
     * Creates a project.
     *
     * @param pgnPath backing PGN path
     * @param title project title
     * @param chapters chapters
     * @param metadata optional metadata
     */
    public StudyProject(Path pgnPath, String title, List<StudyChapter> chapters, Map<String, String> metadata) {
        this.pgnPath = pgnPath;
        this.title = title == null || title.isBlank() ? "Untitled Study" : title;
        this.chapters = new ArrayList<>(chapters == null ? List.of() : chapters);
        this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        normalizeOrder();
    }

    /**
     * Returns the backing PGN path.
     *
     * @return PGN path
     */
    public Path pgnPath() {
        return pgnPath;
    }

    /**
     * Updates the backing PGN path.
     *
     * @param pgnPath PGN path
     */
    public void setPgnPath(Path pgnPath) {
        this.pgnPath = pgnPath;
    }

    /**
     * Returns the project title.
     *
     * @return title
     */
    public String title() {
        return title;
    }

    /**
     * Returns chapters in display order.
     *
     * @return ordered chapters
     */
    public List<StudyChapter> chapters() {
        normalizeOrder();
        return List.copyOf(chapters);
    }

    /**
     * Returns a mutable chapter list for repository operations.
     *
     * @return mutable chapters
     */
    public List<StudyChapter> mutableChapters() {
        return chapters;
    }

    /**
     * Looks up a chapter by id.
     *
     * @param id chapter id
     * @return chapter
     */
    public Optional<StudyChapter> chapter(String id) {
        return chapters.stream().filter(chapter -> Objects.equals(chapter.id(), id)).findFirst();
    }

    /**
     * Adds a chapter and normalizes order.
     *
     * @param chapter chapter
     */
    public void addChapter(StudyChapter chapter) {
        if (chapter != null) {
            chapters.add(chapter);
            normalizeOrder();
        }
    }

    /**
     * Removes a chapter by id.
     *
     * @param id chapter id
     * @return true when removed
     */
    public boolean removeChapter(String id) {
        boolean removed = chapters.removeIf(chapter -> Objects.equals(chapter.id(), id));
        normalizeOrder();
        return removed;
    }

    /**
     * Normalizes chapter order fields.
     */
    public void normalizeOrder() {
        chapters.sort(Comparator.comparingInt(StudyChapter::order).thenComparing(StudyChapter::id));
        for (int i = 0; i < chapters.size(); i++) {
            chapters.get(i).setOrder(i);
        }
    }
}
