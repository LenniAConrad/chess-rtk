package application.gui.workbench.board;

import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * Context handed to a drag-start filter.
 *
 * @param square origin square
 * @param piece piece on the origin square
 * @param fen current FEN
 */
public record DragContext(byte square, byte piece, String fen) { }
