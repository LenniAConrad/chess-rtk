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
 * Decoded HalfKP feature components.
 */
public final class HalfKpFeature {

    /**
     * King square used by the decoded feature.
     */
    public final int kingSquare;

    /**
     * Encoded piece identity used by the decoded feature.
     */
    public final int pieceCode;

    /**
     * Piece square used by the decoded feature.
     */
    public final int pieceSquare;

    /**
     * Whether the feature index decoded into a valid board feature.
     */
    public final boolean valid;

    /**
     * Creates a decoded HalfKP feature descriptor.
     *
     * @param kingSquare king square
     * @param pieceCode piece code
     * @param pieceSquare piece square
     * @param valid true when decoded successfully
     */
    public HalfKpFeature(int kingSquare, int pieceCode, int pieceSquare, boolean valid) {
        this.kingSquare = kingSquare;
        this.pieceCode = pieceCode;
        this.pieceSquare = pieceSquare;
        this.valid = valid;
    }
}
