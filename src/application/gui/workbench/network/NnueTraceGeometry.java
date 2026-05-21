package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import java.awt.Color;

/**
 * Trace geometry helpers for {@link NnueView}.
 */
public final class NnueTraceGeometry {

    /**
     * Vertical pitch for accumulator neurons in the Trace graph.
     */
    private static final int TRACE_SLOT_PITCH = 22;

    /**
     * Utility class.
     */
    private NnueTraceGeometry() {
    }

    /**
     * Returns Trace column centers in display order.
     *
     * @param layout layout
     * @return center x coordinates
     */
    public static int[] traceColumnCenters(NnueTraceLayout layout) {
        return new int[] { layout.featureCx, layout.accumCx, layout.clippedCx,
                layout.contribCx, layout.outputCx };
    }

    /**
     * Computes a responsive stage-band width from the current column spacing.
     *
     * @param layout layout
     * @return band width in pixels
     */
    public static int traceBandWidth(NnueTraceLayout layout) {
        int gap = Math.min(layout.accumCx - layout.featureCx,
                Math.min(layout.clippedCx - layout.accumCx,
                        Math.min(layout.contribCx - layout.clippedCx,
                                layout.outputCx - layout.contribCx)));
        return Math.max(38, Math.min(132, gap - 18));
    }

    /**
     * Stage tint for the Trace backdrop.
     *
     * @param index stage index
     * @return translucent tint
     */
    public static Color traceStageTint(int index) {
        switch (index) {
            case 0:
                return Theme.withAlpha(TensorViz.POSITIVE, 18);
            case 1:
                return Theme.withAlpha(Theme.ACCENT, 20);
            case 2:
                return Theme.withAlpha(Theme.NN_TRUNK, 20);
            case 3:
                return Theme.withAlpha(Theme.NN_POLICY, 16);
            default:
                return Theme.withAlpha(Theme.NN_VALUE, 16);
        }
    }

    /**
     * Returns a centered y coordinate for a column with its own row count.
     *
     * @param layout layout
     * @param row row index
     * @param count row count
     * @return center y
     */
    public static int columnY(NnueTraceLayout layout, int row, int count) {
        if (count <= 1) {
            return (layout.graphTop + layout.graphBottom) / 2;
        }
        int pitch = NnueDrawing.adaptivePitch(layout.usableHeight, count, TRACE_SLOT_PITCH);
        int span = (count - 1) * pitch;
        int start = layout.graphTop + Math.max(0, (layout.usableHeight - span) / 2);
        return start + row * pitch;
    }
}
