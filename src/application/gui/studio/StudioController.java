package application.gui.studio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingWorker;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.io.Reader;
import chess.tag.Tagging;

/**
 * Coordinates GUI v3 state and services.
 */
public final class StudioController {

	private final StudioSettings settings;
	private final StudioEngineService engineService = new StudioEngineService();
	private StudioGameTree gameTree;
	private StudioProject project;
	private StudioListener listener;
	private final List<BoardMark> marks = new ArrayList<>();
	private final List<String> fenList = new ArrayList<>();
	private final List<String> tags = new ArrayList<>();
	private byte selectedSquare = Field.NO_SQUARE;
	private short lastMove = Move.NO_MOVE;
	private String positionNote = "";
	private long positionVersion;
	private StudioEngineSnapshot engineSnapshot;
	private String engineConfigKey = "";
	private int fenIndex = -1;

	/**
	 * Listener for state changes.
	 */
	public interface StudioListener {
		void stateChanged();

		void status(String message);

		char choosePromotion(byte from, byte to);
	}

	/**
	 * Constructor.
	 *
	 * @param initialFen initial FEN
	 * @param settings settings
	 */
	public StudioController(String initialFen, StudioSettings settings) {
		this.settings = settings;
		this.gameTree = StudioGameTree.fromPosition(new Position(
				initialFen == null || initialFen.isBlank() ? chess.struct.Game.STANDARD_START_FEN : initialFen));
		refreshTags();
	}

	/**
	 * Sets listener.
	 *
	 * @param listener listener
	 */
	public void setListener(StudioListener listener) {
		this.listener = listener;
	}

	/**
	 * Creates a board view model.
	 *
	 * @return board model
	 */
	public BoardViewModel boardModel() {
		boolean[] legal = new boolean[64];
		boolean[] captures = new boolean[64];
		fillLegalTargets(legal, captures);
		return new BoardViewModel(position(), settings.isWhiteDown(), selectedSquare, legal, captures,
				lastMove, List.copyOf(marks), theme());
	}

	/**
	 * Selects or plays a square.
	 *
	 * @param square square index
	 */
	public void selectSquare(byte square) {
		Position position = position();
		if (selectedSquare != Field.NO_SQUARE && isLegalTarget(selectedSquare, square)) {
			playFromTo(selectedSquare, square);
			return;
		}
		if (square != Field.NO_SQUARE && position.hasPiece(square)
				&& ((position.isWhiteToMove() && position.isWhitePieceAt(square))
						|| (!position.isWhiteToMove() && position.isBlackPieceAt(square)))) {
			selectedSquare = square;
		} else {
			selectedSquare = Field.NO_SQUARE;
		}
		notifyState();
	}

	/**
	 * Plays a move by source and target.
	 *
	 * @param from source square
	 * @param to target square
	 */
	public void playFromTo(byte from, byte to) {
		MoveList matches = matchingMoves(from, to);
		if (matches.isEmpty()) {
			status("Illegal move: " + Field.toString(from) + Field.toString(to));
			return;
		}
		short move = matches.size() == 1 ? matches.raw(0) : choosePromotionMove(from, to, matches);
		playMove(move);
	}

	/**
	 * Plays a SAN or UCI token.
	 *
	 * @param text move text
	 */
	public void playTextMove(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		Position position = position();
		try {
			short move = Move.isMove(text.trim()) ? Move.parse(text.trim()) : SAN.fromAlgebraic(position, text.trim());
			playMove(move);
		} catch (RuntimeException ex) {
			status("Could not play move: " + ex.getMessage());
		}
	}

	/**
	 * Plays an encoded legal move from the current node.
	 *
	 * @param move encoded move
	 */
	public void playEncodedMove(short move) {
		playMove(move);
	}

	/**
	 * Navigates to a game node.
	 *
	 * @param node target node
	 */
	public void navigateToNode(StudioGameNode node) {
		gameTree.navigateTo(node);
		navigationChanged();
	}

	/**
	 * Navigates backward in the current line.
	 */
	public void previousNode() {
		gameTree.previous();
		navigationChanged();
	}

	/**
	 * Navigates forward in the current line.
	 */
	public void nextNode() {
		gameTree.next();
		navigationChanged();
	}

	/**
	 * Promotes a node to mainline.
	 *
	 * @param node node to promote
	 */
	public void promoteNode(StudioGameNode node) {
		gameTree.promoteNode(node);
		saveProject();
		notifyState();
	}

	/**
	 * Deletes a node.
	 *
	 * @param node node to delete
	 */
	public void deleteNode(StudioGameNode node) {
		if (gameTree.deleteNode(node)) {
			navigationChanged();
		}
	}

	/**
	 * Loads a FEN.
	 *
	 * @param fen FEN text
	 */
	public void loadFen(String fen) {
		try {
			Position position = new Position(fen);
			gameTree = StudioGameTree.fromPosition(position);
			selectedSquare = Field.NO_SQUARE;
			lastMove = Move.NO_MOVE;
			positionNote = "";
			positionChanged();
		} catch (IllegalArgumentException ex) {
			status("Invalid FEN: " + ex.getMessage());
		}
	}

	/**
	 * Loads a FEN list.
	 *
	 * @param path file path
	 */
	public void loadFenList(Path path) {
		try {
			fenList.clear();
			fenList.addAll(Reader.readFenList(path));
			if (!fenList.isEmpty()) {
				fenIndex = 0;
				loadFen(fenList.get(0));
				status("Loaded " + fenList.size() + " FENs.");
			}
		} catch (IOException ex) {
			status("Failed to read FEN list: " + ex.getMessage());
		}
	}

	/**
	 * Navigates a loaded FEN list.
	 *
	 * @param delta index delta
	 */
	public void navigateFen(int delta) {
		if (fenList.isEmpty()) {
			return;
		}
		fenIndex = Math.floorMod(fenIndex + delta, fenList.size());
		loadFen(fenList.get(fenIndex));
	}

	/**
	 * Loads PGN text.
	 *
	 * @param text PGN text
	 */
	public void loadPgn(String text) {
		try {
			gameTree = StudioGameTree.fromPgn(text);
			selectedSquare = Field.NO_SQUARE;
			lastMove = gameTree.current().move();
			positionChanged();
			status("PGN loaded.");
		} catch (RuntimeException ex) {
			status("Failed to load PGN: " + ex.getMessage());
		}
	}

	/**
	 * Adds or removes an annotation.
	 *
	 * @param mark requested mark
	 */
	public void toggleMark(BoardMark mark) {
		for (int i = 0; i < marks.size(); i++) {
			BoardMark existing = marks.get(i);
			if (existing.type() == mark.type() && existing.from() == mark.from() && existing.to() == mark.to()) {
				marks.remove(i);
				notifyState();
				return;
			}
		}
		marks.add(mark);
		notifyState();
	}

	/**
	 * Starts live engine analysis.
	 *
	 * @param profile engine profile
	 * @param nodes node cap
	 * @param multipv multipv
	 * @param wdl wdl flag
	 */
	public void startEngine(StudioEngineProfile profile, long nodes, int multipv, boolean wdl) {
		engineConfigKey = StudioEngineService.configKey(profile, nodes, multipv, wdl);
		long version = positionVersion;
		engineSnapshot = new StudioEngineSnapshot(version, engineConfigKey, "Starting", "-", "-", "-", "-",
				"", Move.NO_MOVE, false);
		notifyState();
		engineService.start(profile, position(), version, nodes, multipv, wdl, update -> {
			if (update.isCurrent(positionVersion, engineConfigKey)) {
				engineSnapshot = update;
				notifyState();
			}
		});
	}

	/**
	 * Stops engine analysis.
	 */
	public void stopEngine() {
		engineService.stop();
		status("Engine stopped.");
	}

	/**
	 * Closes services.
	 */
	public void close() {
		engineService.close();
		settings.save();
	}

	/**
	 * Opens a project folder.
	 *
	 * @param path folder path
	 */
	public void openProject(Path path) {
		try {
			project = StudioProject.open(path);
			saveProject();
			status("Project opened: " + path);
		} catch (IOException ex) {
			status("Failed to open project: " + ex.getMessage());
		}
	}

	/**
	 * Saves project state if a project is open.
	 */
	public void saveProject() {
		if (project == null) {
			return;
		}
		try {
			project.save(position(), gameTree, fenList);
		} catch (IOException ex) {
			status("Project save failed: " + ex.getMessage());
		}
	}

	/**
	 * Appends the current position note.
	 */
	public void savePositionNote() {
		if (project != null && !positionNote.isBlank()) {
			try {
				project.appendNote("position", position().toString(), positionNote);
			} catch (IOException ex) {
				status("Note save failed: " + ex.getMessage());
			}
		}
	}

	/**
	 * Returns current position.
	 *
	 * @return position
	 */
	public Position position() {
		return gameTree.currentPosition();
	}

	/**
	 * Returns active theme.
	 *
	 * @return theme
	 */
	public StudioTheme theme() {
		return settings.isLightMode() ? StudioTheme.light() : StudioTheme.dark();
	}

	public StudioSettings settings() {
		return settings;
	}

	public StudioGameTree gameTree() {
		return gameTree;
	}

	public List<String> tags() {
		return List.copyOf(tags);
	}

	public StudioEngineSnapshot engineSnapshot() {
		return engineSnapshot;
	}

	public String positionNote() {
		return positionNote;
	}

	public void setPositionNote(String note) {
		positionNote = note == null ? "" : note;
	}

	public int fenIndex() {
		return fenIndex;
	}

	public int fenCount() {
		return fenList.size();
	}

	public long positionVersion() {
		return positionVersion;
	}

	private void playMove(short move) {
		try {
			gameTree.play(move);
			lastMove = move;
			selectedSquare = Field.NO_SQUARE;
			positionChanged();
		} catch (RuntimeException ex) {
			status("Illegal move: " + ex.getMessage());
		}
	}

	private void positionChanged() {
		positionVersion++;
		engineSnapshot = null;
		refreshTags();
		saveProject();
		notifyState();
	}

	private void navigationChanged() {
		selectedSquare = Field.NO_SQUARE;
		lastMove = gameTree.current().move();
		positionChanged();
	}

	private void fillLegalTargets(boolean[] legal, boolean[] captures) {
		Arrays.fill(legal, false);
		Arrays.fill(captures, false);
		if (selectedSquare == Field.NO_SQUARE) {
			return;
		}
		MoveList moves = position().legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.get(i);
			if (Move.getFromIndex(move) == selectedSquare) {
				byte to = Move.getToIndex(move);
				legal[to] = true;
				captures[to] = position().isCapture(move);
			}
		}
	}

	private boolean isLegalTarget(byte from, byte to) {
		return !matchingMoves(from, to).isEmpty();
	}

	private MoveList matchingMoves(byte from, byte to) {
		return position().legalMovesBetween(from, to);
	}

	private short choosePromotionMove(byte from, byte to, MoveList matches) {
		char promotion = listener == null ? 'q' : listener.choosePromotion(from, to);
		for (int i = 0; i < matches.size(); i++) {
			short move = matches.raw(i);
			String uci = Move.toString(move);
			if (uci.length() == 5 && uci.charAt(4) == promotion) {
				return move;
			}
		}
		return matches.raw(0);
	}

	private void refreshTags() {
		long version = positionVersion;
		Position snapshot = position().copy();
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() {
				return Tagging.tags(snapshot);
			}

			@Override
			protected void done() {
				if (version != positionVersion) {
					return;
				}
					try {
						tags.clear();
						tags.addAll(get());
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						tags.clear();
						tags.add("tagging interrupted");
					} catch (Exception ex) {
						tags.clear();
						tags.add("tagging unavailable: " + ex.getMessage());
					}
				notifyState();
			}
		}.execute();
	}

	private void notifyState() {
		if (listener != null) {
			listener.stateChanged();
		}
	}

	private void status(String message) {
		if (listener != null) {
			listener.status(message);
		}
	}
}
