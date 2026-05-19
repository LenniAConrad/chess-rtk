package application.gui.workbench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded most-recent-first index of artifact files the workbench has produced
 * this session — generated PDFs, exported PNGs, JSONL/CSV datasets, reports.
 *
 * <p>Kept deliberately small: it is a convenience list for the dashboard's
 * "Outputs" card, not a persistent catalogue. Re-adding an existing path moves
 * it back to the front. All access is expected on the Swing event-dispatch
 * thread.</p>
 */
final class WorkbenchArtifactIndex {

    /**
     * Maximum number of artifact paths retained.
     */
    static final int LIMIT = 12;

    /**
     * Recent artifact paths, newest first.
     */
    private final List<Path> recent = new ArrayList<>();

    /**
     * Change listeners notified after every mutation.
     */
    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * Registers a change listener.
     *
     * @param listener listener invoked after the index changes
     */
    void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Records an artifact path, moving it to the front when already present
     * and trimming the oldest entry past {@link #LIMIT}.
     *
     * @param path artifact path (ignored when null)
     */
    void add(Path path) {
        if (path == null) {
            return;
        }
        recent.remove(path);
        recent.add(0, path);
        while (recent.size() > LIMIT) {
            recent.remove(recent.size() - 1);
        }
        fireChanged();
    }

    /**
     * Returns the recorded artifact paths, newest first.
     *
     * @return immutable snapshot, newest first
     */
    List<Path> recent() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    /**
     * Returns whether the index is empty.
     *
     * @return true when no artifacts have been recorded
     */
    boolean isEmpty() {
        return recent.isEmpty();
    }

    /**
     * Notifies every registered listener.
     */
    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
