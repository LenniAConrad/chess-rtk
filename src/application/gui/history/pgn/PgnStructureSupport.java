package application.gui.history.pgn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;

import application.gui.window.PgnNode;
import application.gui.window.PgnNavigator;

/**
 * Support for converting between chesslib {@link Game}s and the in-memory {@link PgnNode} tree used by the GUI.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PgnStructureSupport {

	/**
	 * PgnStructureSupport method.
	 */
	private PgnStructureSupport() {
	}

	/**
	 * fromGame method.
	 *
	 * @param game parameter.
	 * @return return value.
	 */
	public static PgnNavigator fromGame(Game game) {
		if (game == null) {
			return null;
		}
		Position start = game.getStartPosition() != null ? game.getStartPosition().copyOf()
				: new Position(Game.STANDARD_START_FEN);
		PgnNode root = new PgnNode(null, null, start.copyOf(), Move.NO_MOVE, 0);
		Game.Node mainline = game.getMainline();
		PgnNode mainNode = buildPgnNode(root, mainline, start.copyOf());
		root.setMainNext(mainNode);
		for (Game.Node variation : game.getRootVariations()) {
			PgnNode varNode = buildPgnNode(root, variation, start.copyOf());
			if (varNode != null) {
				root.getVariations().add(varNode);
			}
		}
		return new PgnNavigator(root);
	}

	/**
	 * buildPgnNode method.
	 *
	 * @param parent parameter.
	 * @param node parameter.
	 * @param pos parameter.
	 * @return return value.
	 */
	private static PgnNode buildPgnNode(PgnNode parent, Game.Node node, Position pos) {
		if (node == null) {
			return null;
		}
		short move;
		try {
			move = SAN.fromAlgebraic(pos, node.getSan());
		} catch (IllegalArgumentException ex) {
			return null;
		}
		Position after = pos.copyOf().play(move);
		PgnNode current = new PgnNode(node.getSan(), parent, after.copyOf(), move, parent.getPly() + 1);
		current.setComment(joinComments(node.getCommentsAfter()));
		current.setNag(pickNag(node.getNags()));
		PgnNode mainNext = buildPgnNode(current, node.getNext(), after.copyOf());
		current.setMainNext(mainNext);
		for (Game.Node variation : node.getVariations()) {
			PgnNode varNode = buildPgnNode(current, variation, after.copyOf());
			if (varNode != null) {
				current.getVariations().add(varNode);
			}
		}
		return current;
	}

	/**
	 * joinComments method.
	 *
	 * @param comments parameter.
	 * @return return value.
	 */
	private static String joinComments(List<String> comments) {
		if (comments == null || comments.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (String comment : comments) {
			if (comment == null || comment.isBlank()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(comment.trim());
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**
	 * pickNag method.
	 *
	 * @param nags parameter.
	 * @return return value.
	 */
	private static int pickNag(List<Integer> nags) {
		if (nags == null) {
			return 0;
		}
		for (Integer nag : nags) {
			if (nag == null) {
				continue;
			}
			int code = nag.intValue();
			if (code >= 1 && code <= 6) {
				return code;
			}
		}
		return 0;
	}

	/**
	 * toGame method.
	 *
	 * @param navigator parameter.
	 * @param moveHistory parameter.
	 * @param history parameter.
	 * @param tags parameter.
	 * @param startPosition parameter.
	 * @param result parameter.
	 * @return return value.
	 */
	public static Game toGame(PgnNavigator navigator, List<String> moveHistory, List<Position> history,
			Map<String, String> tags, Position startPosition, String result) {
		Game game = new Game();
		Position start = chooseStartPosition(navigator, startPosition, history);
		if (start != null) {
			game.setStartPosition(start);
		}
		if (result != null) {
			game.setResult(result);
		}
		if (tags != null) {
			for (Map.Entry<String, String> entry : tags.entrySet()) {
				String key = entry.getKey();
				if (key == null || "FEN".equalsIgnoreCase(key) || "SetUp".equalsIgnoreCase(key)) {
					continue;
				}
				game.putTag(key, entry.getValue());
			}
		}
		if (navigator != null && navigator.getRoot() != null) {
			PgnNode main = navigator.getRoot().getMainNext();
			if (main != null) {
				game.setMainline(buildGameNode(main));
			}
			for (PgnNode variation : navigator.getRoot().getVariations()) {
				game.addRootVariation(buildGameNode(variation));
			}
			return game;
		}
		if (moveHistory != null && !moveHistory.isEmpty()) {
			Game.Node head = null;
			Game.Node prev = null;
			for (String san : moveHistory) {
				if (san == null || san.isBlank()) {
					continue;
				}
				Game.Node node = new Game.Node(san);
				if (head == null) {
					head = node;
				} else {
					prev.setNext(node);
				}
				prev = node;
			}
			game.setMainline(head);
		}
		return game;
	}

	/**
	 * chooseStartPosition method.
	 *
	 * @param navigator parameter.
	 * @param startPosition parameter.
	 * @param history parameter.
	 * @return return value.
	 */
	private static Position chooseStartPosition(PgnNavigator navigator, Position startPosition, List<Position> history) {
		if (navigator != null && navigator.getRoot() != null && navigator.getRoot().getPositionAfter() != null) {
			return navigator.getRoot().getPositionAfter().copyOf();
		}
		if (startPosition != null) {
			return startPosition.copyOf();
		}
		if (history != null && !history.isEmpty()) {
			return history.get(0).copyOf();
		}
		return null;
	}

		/**
	 * Handles build game node.
	 * @param node node value
	 * @return computed value
	 */
private static Game.Node buildGameNode(PgnNode node) {
		if (node == null) {
			return null;
		}
		Game.Node result = new Game.Node(node.getSan());
		if (node.getMainNext() != null) {
			result.setNext(buildGameNode(node.getMainNext()));
		}
		for (PgnNode variation : node.getVariations()) {
			result.addVariation(buildGameNode(variation));
		}
		return result;
	}
}
