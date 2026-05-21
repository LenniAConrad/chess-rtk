package application.gui.workbench;

import static application.gui.workbench.WorkbenchNnueAtlas.*;
import static application.gui.workbench.WorkbenchNnueDrawing.*;
import static application.gui.workbench.WorkbenchNnueFeatureDecoder.*;
import static application.gui.workbench.WorkbenchNnueTraceGeometry.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import chess.nn.nnue.FeatureEncoder;

/**
 * Workbench panel that visualises an NNUE half-KP forward pass.
 *
 * <p>Abstract mode shows the centipawn output, the top-N active features that
 * fired and their signed impact on the eval, and a short readout of which
 * accumulator slots moved the most. Detailed mode draws the full wired diagram
 * (features -&gt; accumulator -&gt; clipped -&gt; output) with weighted edges
 * coloured by sign and opacity scaled by magnitude.</p>
 *
 * <p>Shared scaffolding lives in {@link WorkbenchNetworkView}; this class adds
 * the NNUE-specific drawing plus the {@link Scrollable} plumbing the atlas mode
 * needs to expose every accumulator slot in a tall scrolling mosaic.</p>
 */

/**
 * Workbench panel that visualises an NNUE half-KP forward pass.
 */
final class WorkbenchNnueView extends WorkbenchNnueTraceView {
    /** Serialization identifier for Swing component compatibility. */
    private static final long serialVersionUID = 1L;


    /**
     * Decodes a HalfKP feature index for regression tests and tooltip text.
     *
     * @param featureIndex encoded feature index
     * @param whitePerspective true when labels should use White's perspective
     * @return readable HalfKP label
     */
    protected static String decodeHalfKP(int featureIndex, boolean whitePerspective) {
        return WorkbenchNnueViewBase.decodeHalfKP(featureIndex, whitePerspective);
    }

    /**
     * Exposes the trace layout calculator on this concrete type for regression tests.
     *
     * @param r graph bounds
     * @return trace layout
     */
    @Override
    protected WorkbenchNnueTraceLayout layout(Rectangle r) {
        return super.layout(r);
    }
}
