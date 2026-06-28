package application.gui.app;

import application.gui.feature.dataset.DatasetController;
import application.gui.feature.dataset.DatasetDependencies;
import application.gui.feature.dataset.DatasetView;
import application.gui.feature.publishing.PublishingController;
import application.gui.feature.publishing.PublishingDependencies;
import application.gui.feature.publishing.PublishingView;
import application.gui.feature.publishing.ReportController;
import application.gui.feature.publishing.ReportDependencies;
import application.gui.feature.publishing.ReportView;
import java.util.Objects;

/**
 * Composition helpers for the current Swing Workbench while feature packages
 * are being extracted from the legacy window shell.
 */
public final class LegacyWorkbenchComposition {

    /**
     * Prevents instantiation.
     */
    private LegacyWorkbenchComposition() {
        // utility
    }

    /**
     * Creates a Publishing view from feature dependencies.
     *
     * @param dependencies publishing dependencies
     * @return publishing view
     */
    public static PublishingView publishingView(PublishingDependencies dependencies) {
        return new PublishingController(Objects.requireNonNull(dependencies, "dependencies")).createView();
    }

    /**
     * Creates a Dataset view from feature dependencies.
     *
     * @param dependencies dataset dependencies
     * @return dataset view
     */
    public static DatasetView datasetView(DatasetDependencies dependencies) {
        return new DatasetController(Objects.requireNonNull(dependencies, "dependencies")).createView();
    }

    /**
     * Creates a report view from feature dependencies.
     *
     * @param dependencies report dependencies
     * @return report view
     */
    public static ReportView reportView(ReportDependencies dependencies) {
        return new ReportController(Objects.requireNonNull(dependencies, "dependencies")).createView();
    }
}
