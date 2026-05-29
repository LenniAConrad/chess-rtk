package application.gui.workbench.session;

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
public final class ArtifactIndex {

    /**
     * Maximum number of artifact paths retained.
     */
    public static final int LIMIT = 12;

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
    public void addListener(Runnable listener) {
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
    public void add(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        recent.remove(normalized);
        recent.add(0, normalized);
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
    public List<Path> recent() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    /**
     * Returns whether the index is empty.
     *
     * @return true when no artifacts have been recorded
     */
    public boolean isEmpty() {
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
