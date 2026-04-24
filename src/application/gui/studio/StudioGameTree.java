package application.gui.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;

/**
 * Game tree used by GUI v3.
 */
public final class StudioGameTree {

	/**
	 * Root node representing the start position.
	 */
	private final StudioGameNode root;

	/**
	 * Editable PGN tag map.
	 */
	private final Map<String, String> tags = new LinkedHashMap<>();

	/**
	 * Currently selected node in the tree.
	 */
	private StudioGameNode current;

	/**
	 * Constructor.
	 *
	 * @param start starting position
	 */
	private StudioGameTree(Position start) {
		root = new StudioGameNode(null, Move.NO_MOVE, "", start.toString(), start.toString());
		current = root;
		defaultTags();
	}

	/**
	 * Creates a game tree from a starting position.
	 *
	 * @param start start position
	 * @return game tree
	 */
	public static StudioGameTree fromPosition(Position start) {
		return new StudioGameTree(start == null ? new Position(Game.STANDARD_START_FEN) : start.copy());
	}

	/**
	 * Creates a game tree from the first parsed PGN game.
	 *
	 * @param text PGN text
	 * @return parsed tree
	 */
	public static StudioGameTree fromPgn(String text) {
		List<Game> games = Pgn.parseGames(text);
		if (games.isEmpty()) {
			throw new IllegalArgumentException("No PGN game found.");
		}
		return fromGame(games.get(0));
	}

	/**
	 * Creates a tree from a parsed game.
	 *
	 * @param game parsed game
	 * @return game tree
	 */
	public static StudioGameTree fromGame(Game game) {
		StudioGameTree tree = fromPosition(game.getStartPosition());
		tree.tags.clear();
		tree.tags.putAll(game.getTags());
		if (!tree.tags.containsKey("Result")) {
			tree.tags.put("Result", game.getResult());
		}
		appendSequence(tree.root, new Position(tree.root.afterFen()), game.getMainline());
		for (Game.Node variation : game.getRootVariations()) {
			appendSequence(tree.root, new Position(tree.root.afterFen()), variation);
		}
		return tree;
	}

	/**
	 * Plays a legal move from the current node.
	 *
	 * @param move encoded move
	 * @return selected or created child node
	 */
	public StudioGameNode play(short move) {
		Position before = currentPosition();
		if (!before.isLegalMove(move)) {
			throw new IllegalArgumentException("Illegal move: " + Move.toString(move));
		}
		String uci = Move.toString(move);
		for (StudioGameNode child : current.children()) {
			if (uci.equals(child.uci())) {
				current = child;
				return child;
			}
		}
		String san = SAN.toAlgebraic(before, move);
		Position after = before.copy().play(move);
		current = current.addChild(move, san, before.toString(), after.toString());
		return current;
	}

	/**
	 * Navigates to a node.
	 *
	 * @param node target node
	 */
	public void navigateTo(StudioGameNode node) {
		if (node != null) {
			current = node;
		}
	}

	/**
	 * Navigates backward.
	 */
	public void previous() {
		if (current.parent() != null) {
			current = current.parent();
		}
	}

	/**
	 * Navigates forward on the mainline child.
	 */
	public void next() {
		if (!current.children().isEmpty()) {
			current = current.children().get(0);
		}
	}

	/**
	 * Deletes a child variation.
	 *
	 * @param node node to delete
	 * @return true if deleted
	 */
	public boolean deleteNode(StudioGameNode node) {
		if (node == null || node.parent() == null) {
			return false;
		}
		if (isAncestor(node, current)) {
			current = node.parent();
		}
		return node.parent().mutableChildren().remove(node);
	}

	/**
	 * Promotes a child to mainline at its parent.
	 *
	 * @param node node to promote
	 * @return true if promoted
	 */
	public boolean promoteNode(StudioGameNode node) {
		if (node == null || node.parent() == null) {
			return false;
		}
		List<StudioGameNode> siblings = node.parent().mutableChildren();
		int index = siblings.indexOf(node);
		if (index <= 0) {
			return false;
		}
		siblings.remove(index);
		siblings.add(0, node);
		return true;
	}

	/**
	 * Exports the tree to PGN.
	 *
	 * @return PGN text
	 */
	public String toPgn() {
		return Pgn.toPgn(toGame());
	}

	/**
	 * Exports to the shared PGN game model.
	 *
	 * @return game model
	 */
	public Game toGame() {
		Game game = new Game();
		game.setStartPosition(new Position(root.afterFen()));
		for (Map.Entry<String, String> entry : tags.entrySet()) {
			if ("Result".equals(entry.getKey())) {
				game.setResult(entry.getValue());
			}
			game.putTag(entry.getKey(), entry.getValue());
		}
		if (!root.children().isEmpty()) {
			game.setMainline(toGameNode(root.children().get(0)));
			for (int i = 1; i < root.children().size(); i++) {
				game.addRootVariation(toGameNode(root.children().get(i)));
			}
		}
		return game;
	}

	/**
	 * Returns current position.
	 *
	 * @return current position
	 */
	public Position currentPosition() {
		return new Position(current.afterFen());
	}

	/**
	 * Returns current node.
	 *
	 * @return current node
	 */
	public StudioGameNode current() {
		return current;
	}

	/**
	 * Returns root node.
	 *
	 * @return root node
	 */
	public StudioGameNode root() {
		return root;
	}

	/**
	 * Returns editable PGN tags.
	 *
	 * @return tag map
	 */
	public Map<String, String> tags() {
		return tags;
	}

	/**
	 * Returns all nodes in preorder, excluding the root.
	 *
	 * @return node list
	 */
	public List<StudioGameNode> nodes() {
		List<StudioGameNode> result = new ArrayList<>();
		addNodes(root, result);
		return result;
	}

	/**
	 * Returns SAN line to current.
	 *
	 * @return current SAN line
	 */
	public String currentLineSan() {
		List<String> tokens = new ArrayList<>();
		StudioGameNode node = current;
		while (node != null && node.parent() != null) {
			tokens.add(0, node.san());
			node = node.parent();
		}
		return String.join(" ", tokens);
	}

	/**
	 * Installs the default PGN tags for a new tree.
	 */
	private void defaultTags() {
		tags.put("Event", "ChessRTK Studio");
		tags.put("Site", "?");
		tags.put("Date", "????.??.??");
		tags.put("Round", "?");
		tags.put("White", "?");
		tags.put("Black", "?");
		tags.put("Result", "*");
	}

	/**
	 * Appends a parsed PGN node sequence under a Studio node.
	 *
	 * @param parent parent Studio node
	 * @param start start position for the sequence
	 * @param source first parsed PGN node
	 */
	private static void appendSequence(StudioGameNode parent, Position start, Game.Node source) {
		Game.Node cursor = source;
		StudioGameNode targetParent = parent;
		Position position = start.copy();
		while (cursor != null) {
			String san = cleanSan(cursor.getSan());
			short move = SAN.fromAlgebraic(position, san);
			Position after = position.copy().play(move);
			StudioGameNode child = targetParent.addChild(move, san, position.toString(), after.toString());
			if (!cursor.getCommentsAfter().isEmpty()) {
				child.setComment(String.join(" ", cursor.getCommentsAfter()));
			}
			child.setNags(cursor.getNags());
			for (Game.Node variation : cursor.getVariations()) {
				appendSequence(child, after, variation);
			}
			targetParent = child;
			position = after;
			cursor = cursor.getNext();
		}
	}

	/**
	 * Converts a Studio node and its children to a PGN game node.
	 *
	 * @param node Studio node
	 * @return PGN node
	 */
	private static Game.Node toGameNode(StudioGameNode node) {
		Game.Node result = new Game.Node(node.san());
		if (!node.comment().isBlank()) {
			result.addCommentAfter(node.comment());
		}
		for (Integer nag : node.nags()) {
			result.addNag(nag);
		}
		if (!node.children().isEmpty()) {
			result.setNext(toGameNode(node.children().get(0)));
			for (int i = 1; i < node.children().size(); i++) {
				result.addVariation(toGameNode(node.children().get(i)));
			}
		}
		return result;
	}

	/**
	 * Adds nodes to a preorder result list.
	 *
	 * @param node node to traverse
	 * @param result destination list
	 */
	private static void addNodes(StudioGameNode node, List<StudioGameNode> result) {
		for (StudioGameNode child : node.children()) {
			result.add(child);
			addNodes(child, result);
		}
	}

	/**
	 * Returns whether one node is an ancestor of another.
	 *
	 * @param ancestor possible ancestor
	 * @param node candidate descendant
	 * @return true when ancestor is on node's parent chain
	 */
	private static boolean isAncestor(StudioGameNode ancestor, StudioGameNode node) {
		StudioGameNode cursor = node;
		while (cursor != null) {
			if (cursor == ancestor) {
				return true;
			}
			cursor = cursor.parent();
		}
		return false;
	}

	/**
	 * Removes inline NAG tokens from SAN text.
	 *
	 * @param san SAN text
	 * @return cleaned SAN text
	 */
	private static String cleanSan(String san) {
		if (san == null) {
			return "";
		}
		return san.replaceAll("\\$\\d+", "").trim();
	}
}
