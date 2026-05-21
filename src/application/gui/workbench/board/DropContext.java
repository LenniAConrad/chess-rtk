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

import chess.core.Field;

/**
 * Context handed to a drop resolver.
 *
 * @param fromSquare origin square
 * @param toSquare drop square ({@link Field#NO_SQUARE} when off-board)
 * @param piece piece being dropped
 * @param fen FEN before the drop
 * @param defaultMove first matching legal move, or no move
 */
public record DropContext(byte fromSquare, byte toSquare, byte piece, String fen, short defaultMove) { }
