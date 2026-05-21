package application.gui.workbench.network;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * The mutually-exclusive rendering modes shared by every workbench network
 * view (NNUE / CNN / BT4).
 *
 * <p>Replaces the previous quartet of independent boolean flags
 * ({@code detailed} / {@code rawView} / {@code atlasView} / {@code diagramView})
 * that the panel had to keep consistent by hand. Modelling the choice as one
 * enum makes the "exactly one mode is active" invariant impossible to break and
 * lets the views dispatch with a single {@code switch}.</p>
 *
 * <p>The simplified toolbar exposes only a curated subset of these modes. The
 * enum still keeps the complete renderer set available internally.</p>
 */
public enum ViewMode {

    /**
     * High-level summary view: the architecture's headline outputs plus a few
     * curated readouts.
     */
    ABSTRACT,

    /**
     * Interactive drill-down view: per-layer / per-block / per-slot detail with
     * click-to-select behaviour.
     */
    DETAILED,

    /**
     * Dense "everything at once" view: every channel / feature / head laid out
     * as a single mosaic of small heatmaps.
     */
    RAW,

    /**
     * Learned-weight atlas view. Only NNUE renders a bespoke atlas; CNN and BT4
     * route this onto their raw mosaic.
     */
    ATLAS,

    /**
     * Static architecture schematic — no per-position data, just the layer
     * wiring.
     */
    DIAGRAM;
}
