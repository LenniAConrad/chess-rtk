package application.gui.workbench.network;

import java.awt.Rectangle;
import javax.swing.Scrollable;

import static application.gui.workbench.network.NnueAtlas.*;
import static application.gui.workbench.network.NnueDrawing.*;
import static application.gui.workbench.network.NnueFeatureDecoder.*;
import static application.gui.workbench.network.NnueTraceGeometry.*;

/**
 * Workbench panel that visualises an NNUE half-KP forward pass.
 *
 * <p>Abstract mode shows the centipawn output, the top-N active features that
 * fired and their signed impact on the eval, and a short readout of which
 * accumulator slots moved the most. Detailed mode draws the full wired diagram
 * (features -&gt; accumulator -&gt; clipped -&gt; output) with weighted edges
 * coloured by sign and opacity scaled by magnitude.</p>
 *
 * <p>Shared scaffolding lives in {@link NetworkView}; this class adds
 * the NNUE-specific drawing plus the {@link Scrollable} plumbing used to keep
 * dense atlas and trace views inside the active viewport.</p>
 */
public final class NnueView extends NnueTraceView {
    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Decodes a HalfKP feature index for regression tests and tooltip text.
     *
     * @param featureIndex encoded feature index
     * @param whitePerspective true when labels should use White's perspective
     * @return readable HalfKP label
     */
    protected static String decodeHalfKP(int featureIndex, boolean whitePerspective) {
        return NnueViewBase.decodeHalfKP(featureIndex, whitePerspective);
    }

    /**
     * Exposes the trace layout calculator on this concrete type for regression tests.
     *
     * @param r graph bounds
     * @return trace layout
     */
    @Override
    protected NnueTraceLayout layout(Rectangle r) {
        return super.layout(r);
    }
}
