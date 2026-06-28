package application.gui.feature.dataset;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Narrow services needed by the Dataset feature.
 *
 * @param openFenInBoard callback that loads a FEN into the shared Board tab
 * @param openFenInNewBoard callback that loads a FEN into a detached Board tab
 */
public record DatasetDependencies(
        Consumer<String> openFenInBoard,
        Consumer<String> openFenInNewBoard) {

    /**
     * Creates a dependency bundle.
     */
    public DatasetDependencies {
        Objects.requireNonNull(openFenInBoard, "openFenInBoard");
        Objects.requireNonNull(openFenInNewBoard, "openFenInNewBoard");
    }
}
