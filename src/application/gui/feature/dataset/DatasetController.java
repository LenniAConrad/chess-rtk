package application.gui.feature.dataset;

import application.gui.workbench.dataset.DatasetPanel;
import java.util.Objects;

/**
 * Compatibility controller that creates the existing Dataset Swing panel
 * through a feature view contract.
 */
public final class DatasetController {

    /**
     * Feature dependencies.
     */
    private final DatasetDependencies dependencies;

    /**
     * Creates a controller.
     *
     * @param dependencies feature dependencies
     */
    public DatasetController(DatasetDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Creates the current legacy Swing view through the Dataset seam.
     *
     * @return dataset view
     */
    public DatasetView createView() {
        return new DatasetPanel(dependencies.openFenInBoard(), dependencies.openFenInNewBoard());
    }
}
