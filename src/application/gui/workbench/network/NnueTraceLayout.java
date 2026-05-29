package application.gui.workbench.network;

/**
 * Geometry for the NNUE wired-diagram columns.
 */
public final class NnueTraceLayout {

    /**
     * Center x of the feature column.
     */
    int featureCx;

    /**
     * Center x of the accumulator column.
     */
    int accumCx;

    /**
     * Center x of the clipped-ReLU column.
     */
    int clippedCx;

    /**
     * Center x of the output-contribution column.
     */
    int contribCx;

    /**
     * Center x of the output column.
     */
    int outputCx;

    /**
     * Slot radius.
     */
    int slotRadius;

    /**
     * Top y of the first slot.
     */
    int startY;

    /**
     * Top of the graph content area.
     */
    int graphTop;

    /**
     * Bottom of the graph content area.
     */
    int graphBottom;

    /**
     * Usable graph height.
     */
    int usableHeight;

    /**
     * Top y of the first feature node.
     */
    int featureStartY;

    /**
     * Y pitch between slots.
     */
    int slotPitch;

    /**
     * Y pitch between feature nodes.
     */
    int featurePitch;

    /**
     * Bottom y of the last slot.
     */
    int bottomY;

    /**
     * Bottom y of the last feature node.
     */
    int featureBottomY;

    /**
     * Column-label baseline.
     */
    int labelY;
}
