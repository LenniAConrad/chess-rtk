package application.gui.window;
import application.cli.Format;
import application.gui.model.PgnMainline;
import application.gui.history.input.HistoryInputSupport;
import application.gui.history.pgn.PgnStructureSupport;
import application.gui.history.text.HistoryTextSupport;
import application.gui.history.ui.HistoryFileDialogSupport;
import application.gui.util.TransferableImage;
import java.awt.Desktop;
import java.awt.datatransfer.Clipboard;
import java.awt.KeyboardFocusManager;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.text.JTextComponent;
import javax.swing.filechooser.FileNameExtensionFilter;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.uci.Output;

/**
 * GuiWindowHistoryNavigation class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
abstract class GuiWindowHistoryNavigation extends GuiWindowHistoryActions {

			/**
			 * canUserPlayCurrentTurn method.
			 *
			 * @return return value.
			 */
			public boolean canUserPlayCurrentTurn() {
				if (!vsEngineMode || position == null) {
					return true;
				}
				return position.isWhiteToMove() == vsEngineHumanWhite;
			}

			/**
			 * isVsEngineEngineTurn method.
			 * @return return value.
			 */
			protected boolean isVsEngineEngineTurn() {
				return vsEngineMode && position != null && position.isWhiteToMove() != vsEngineHumanWhite;
			}

			/**
			 * updateVsEngineControls method.
			 */
			protected void updateVsEngineControls() {
				if (vsEnginePlayWhiteToggle != null) {
					boolean selected = vsEngineHumanWhite;
					if (vsEnginePlayWhiteToggle.isSelected() != selected) {
						vsEnginePlayWhiteToggle.setSelected(selected);
					}
				}
				if (engineVsStopButton != null) {
					engineVsStopButton.setEnabled(vsEngineMode);
				}
				if (engineVsContinueButton != null) {
					engineVsContinueButton.setText(vsEngineMode ? "Resume Here" : "Continue Vs");
				}
			}

			/**
			 * startVsEngineNewGame method.
			 */
			protected void startVsEngineNewGame() {
				vsEngineMode = false;
				vsEngineAwaitingReply = false;
				resetPosition();
				startVsEngineSession();
			}

			/**
			 * continueVsEngineFromCurrent method.
			 */
			protected void continueVsEngineFromCurrent() {
				startVsEngineSession();
			}

			/**
			 * stopVsEngineGame method.
			 */
			protected void stopVsEngineGame() {
				vsEngineMode = false;
				vsEngineAwaitingReply = false;
				updateVsEngineControls();
				refreshBoard();
				refreshStatusBar();
			}

			/**
			 * startVsEngineSession method.
			 */
			protected void startVsEngineSession() {
				if (vsEnginePlayWhiteToggle != null) {
					vsEngineHumanWhite = vsEnginePlayWhiteToggle.isSelected();
				}
				vsEngineMode = true;
				vsEngineAwaitingReply = false;
				updateVsEngineControls();
				refreshBoard();
				if (engineWorker == null) {
					startEngine();
				}
				requestVsEngineReplyIfNeeded();
				refreshStatusBar();
			}

			/**
			 * requestVsEngineReplyIfNeeded method.
			 */
			protected void requestVsEngineReplyIfNeeded() {
				if (!vsEngineMode) {
					vsEngineAwaitingReply = false;
					updateVsEngineControls();
					return;
				}
				if (!isVsEngineEngineTurn()) {
					vsEngineAwaitingReply = false;
					updateVsEngineControls();
					return;
				}
				vsEngineAwaitingReply = true;
				if (engineWorker == null) {
					startEngine();
				}
				maybeAutoPlayVsEngineMove();
				updateVsEngineControls();
			}

			/**
			 * maybeAutoPlayVsEngineMove method.
			 */
			protected void maybeAutoPlayVsEngineMove() {
				if (!vsEngineMode || !vsEngineAwaitingReply || !isVsEngineEngineTurn() || position == null) {
					return;
				}
				short candidate = Move.NO_MOVE;
				if (engineBestMove != Move.NO_MOVE && position.isLegalMove(engineBestMove)) {
					candidate = engineBestMove;
				} else if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
					Output out = latestAnalysis.getBestOutput(1);
					if (out != null) {
						short[] moves = out.getMoves();
						if (moves != null) {
							for (short move : moves) {
								if (move == Move.NO_MOVE) {
									break;
								}
								if (position.isLegalMove(move)) {
									candidate = move;
									break;
								}
							}
						}
					}
				}
				if (candidate == Move.NO_MOVE) {
					return;
				}
				vsEngineAwaitingReply = false;
				playMoveAsEngine(candidate);
				updateVsEngineControls();
			}

			/**
			 * choosePromotion method.
			 * @param from parameter.
			 * @param to parameter.
			 * @return return value.
			 */
			protected byte choosePromotion(byte from, byte to) {
				PromotionDialog dialog = new PromotionDialog((GuiWindowHistory) this, from, to);
				dialog.setVisible(true);
				if (dialog.wasCancelled()) {
					return PROMOTION_NONE;
				}
				byte promo = dialog.getPromotion();
				short move = Move.of(from, to, promo);
				if (position.isLegalMove(move)) {
					return promo;
				}
				// Fallback to queen if something unexpected happens.
				return PROMOTION_QUEEN;
			}

			/**
			 * playUci method.
			 * @param uci parameter.
			 */
			protected void playUci(String uci) {
				short move = Move.parse(uci);
				if (!position.isLegalMove(move)) {
					JOptionPane.showMessageDialog(frame, "Illegal move: " + uci, "Move error",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				playMove(move);
			}

			/**
			 * runUtilityInput method.
			 * @param rawInput parameter.
			 */
			protected void runUtilityInput(String rawInput) {
				if (rawInput == null) {
					return;
				}
				String input = rawInput.trim();
				if (input.isEmpty()) {
					return;
				}
				String lower = input.toLowerCase(Locale.ROOT);
				switch (lower) {
					case "best":
					case "bm":
						playEngineBest();
						return;
					case "flip":
						flipBoard();
						return;
					case "undo":
						undoMove();
						return;
					case "reset":
						resetPosition();
						return;
					case "editor":
					case "edit":
						openBoardEditor();
						return;
					case "palette":
					case "commands":
					case "cmd":
						openCommandPalette();
						return;
					case "fen":
					case "copy fen":
						copyFen();
						return;
					case "board":
						selectEditorTab("board.pgn");
						return;
					case "pgn":
						selectEditorTab("game.pgn");
						return;
					case "eco":
						selectEditorTab("eco.tsv");
						return;
					case "analysis":
						selectRightTab("Analysis");
						return;
					case "vs":
					case "vs engine":
					case "play engine":
						continueVsEngineFromCurrent();
						return;
					case "vs new":
					case "new vs":
						startVsEngineNewGame();
						return;
					case "vs stop":
					case "stop vs":
						stopVsEngineGame();
						return;
					default:
						break;
				}

				if (lower.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
					playUci(lower);
					return;
				}
				try {
					short sanMove = SAN.fromAlgebraic(position, input);
					if (position.isLegalMove(sanMove)) {
						playMove(sanMove);
						return;
					}
				} catch (IllegalArgumentException ignored) {
					// fall through to command lookup
				}

				for (String command : commandSpecs.keySet()) {
					if (!command.equalsIgnoreCase(input)) {
						continue;
					}
					selectRightTab("Commands");
					if (commandSelect != null) {
						commandSelect.setSelectedItem(command);
						commandSelect.requestFocusInWindow();
					}
					return;
				}

				recordProblem("Utility", "Unknown utility input: " + input);
			}

			/**
			 * playMove method.
			 * @param from parameter.
			 * @param to parameter.
			 * @return return value.
			 */
			protected boolean playMove(byte from, byte to) {
				short move = Move.of(from, to);
				if (!position.isLegalMove(move)) {
					if (!hasPromotionMove(from, to)) {
						return false;
					}
					byte promo = choosePromotion(from, to);
					if (promo == PROMOTION_NONE) {
						return false;
					}
					short promote = Move.of(from, to, promo);
					if (!position.isLegalMove(promote)) {
						return false;
					}
					move = promote;
				}
				playMove(move, false);
				return true;
			}

			/**
			 * hasPromotionMove method.
			 * @param from parameter.
			 * @param to parameter.
			 * @return return value.
			 */
			protected boolean hasPromotionMove(byte from, byte to) {
				MoveList moves = position.legalMoves();
				for (int i = 0; i < moves.size(); i++) {
					short move = moves.get(i);
					if (Move.getFromIndex(move) != from || Move.getToIndex(move) != to) {
						continue;
					}
					if (Move.isPromotion(move)) {
						return true;
					}
				}
				return false;
			}

			/**
			 * playMove method.
			 * @param move parameter.
			 */
			protected void playMove(short move) {
				playMove(move, false);
			}

			/**
			 * playMoveAsEngine method.
			 * @param move parameter.
			 */
			protected void playMoveAsEngine(short move) {
				playMove(move, true);
			}

			/**
			 * playMove method.
			 * @param move parameter.
			 * @param fromEngine parameter.
			 */
			protected void playMove(short move, boolean fromEngine) {
				if (!fromEngine && isVsEngineEngineTurn()) {
					java.awt.Toolkit.getDefaultToolkit().beep();
					return;
				}
				if (!position.isLegalMove(move)) {
					JOptionPane.showMessageDialog(frame, "Illegal move.", "Move error",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				String san = Format.safeSan(position, move);
				ensureGameTree();
				PgnNode base = pgnNavigator.current != null ? pgnNavigator.current : pgnNavigator.root;
				if (base == null) {
					return;
				}
				PgnNode next = findChildByMove(base, move);
				if (next == null) {
					Position after = base.positionAfter.copy().play(move);
					next = new PgnNode(san, base, after.copy(), move, base.ply + 1);
					if (base.mainNext == null) {
						base.mainNext = next;
					} else {
						base.variations.add(next);
					}
				}
				applyPgnNode(next);
				requestVsEngineReplyIfNeeded();
			}

			/**
			 * ensureGameTree method.
			 */
			protected void ensureGameTree() {
				if (pgnNavigator != null) {
					return;
				}
				Position start = history.isEmpty() ? position.copy() : history.get(0).copy();
				PgnNode root = new PgnNode(null, null, start.copy(), Move.NO_MOVE, 0);
				PgnNode cursor = root;
				Position current = start.copy();
				for (String san : moveHistory) {
					if (san == null || san.isBlank()) {
						continue;
					}
					try {
						short move = SAN.fromAlgebraic(current, san);
						Position after = current.copy().play(move);
						PgnNode next = new PgnNode(san, cursor, after.copy(), move, cursor.ply + 1);
						cursor.mainNext = next;
						cursor = next;
						current = after;
					} catch (RuntimeException ex) {
						break;
					}
				}
				pgnNavigator = new PgnNavigator(root);
				pgnNavigator.current = cursor;
				updateHistoryStart(start);
				currentLineNodes.clear();
				List<PgnNode> path = new ArrayList<>();
				PgnNode pathCursor = cursor;
				while (pathCursor != null && pathCursor.parent != null) {
					path.add(pathCursor);
					pathCursor = pathCursor.parent;
				}
				java.util.Collections.reverse(path);
				currentLineNodes.addAll(path);
			}

			/**
			 * findChildByMove method.
			 * @param parent parameter.
			 * @param move parameter.
			 * @return return value.
			 */
			protected PgnNode findChildByMove(PgnNode parent, short move) {
				if (parent == null || move == Move.NO_MOVE) {
					return null;
				}
				if (parent.mainNext != null && parent.mainNext.move == move) {
					return parent.mainNext;
				}
				for (PgnNode variation : parent.variations) {
					if (variation.move == move) {
						return variation;
					}
				}
				return null;
			}

			/**
			 * loadFenList method.
			 */
			protected void loadFenList() {
				if (frame == null) {
					return;
				}
				Path file = HistoryFileDialogSupport.chooseFenListFile(frame);
				if (file != null) {
					loadFenListFromFile(file);
				}
			}

			/**
			 * openFenSourceFile method.
			 */
			protected void openFenSourceFile() {
				Path source = fenListManager.sourcePath();
				if (source == null) {
					JOptionPane.showMessageDialog(frame, "No source file loaded.", "Open file",
							JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				if (!Desktop.isDesktopSupported()) {
					JOptionPane.showMessageDialog(frame, "Desktop integration not supported.", "Open file",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				try {
					Desktop.getDesktop().open(source.toFile());
				} catch (IOException ex) {
					recordProblem("Open file", "Failed to open file: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to open file: " + ex.getMessage(), "Open file",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * loadFenListFromFile method.
			 * @param file parameter.
			 * @return return value.
			 */
			protected boolean loadFenListFromFile(Path file) {
				try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					boolean ok = loadFenListFromReader(reader, file.getFileName().toString());
					if (ok) {
						fenListManager.setSourcePath(file);
						updateFenSourceButton();
					}
					return ok;
				} catch (IOException ex) {
					recordProblem("Load error", "Failed to read file: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to read file: " + ex.getMessage(), "Load error",
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}

			/**
			 * loadFenLines method.
			 * @param lines parameter.
			 * @param sourceLabel parameter.
			 * @return return value.
			 */
			protected boolean loadFenLines(List<String> lines, String sourceLabel) {
				List<String> loaded = new ArrayList<>();
				for (String line : lines) {
					String fen = extractFen(line);
					if (fen == null) {
						continue;
					}
					try {
						new Position(fen);
						loaded.add(fen);
					} catch (IllegalArgumentException ignored) {
						// skip invalid
					}
				}
				if (loaded.isEmpty()) {
					JOptionPane.showMessageDialog(frame, "No valid FENs found.", "Load error",
							JOptionPane.WARNING_MESSAGE);
					return false;
				}
				if (!fenListManager.load(loaded, sourceLabel)) {
					return false;
				}
				fenListManager.setSourcePath(null);
				applyFen(fenListManager.currentFen());
				updateFenStatus();
				return true;
			}

			/**
			 * loadFenListFromReader method.
			 * @param reader parameter.
			 * @param sourceLabel parameter.
			 * @return return value.
			 * @throws java.io.IOException when an error occurs.
			 */
			protected boolean loadFenListFromReader(BufferedReader reader, String sourceLabel) throws IOException {
				List<String> loaded = new ArrayList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					String fen = extractFen(line);
					if (fen == null) {
						continue;
					}
					try {
						new Position(fen);
						loaded.add(fen);
					} catch (IllegalArgumentException ignored) {
						// skip invalid
					}
				}
				if (loaded.isEmpty()) {
					JOptionPane.showMessageDialog(frame, "No valid FENs found.", "Load error",
							JOptionPane.WARNING_MESSAGE);
					return false;
				}
				if (!fenListManager.load(loaded, sourceLabel)) {
					return false;
				}
				fenListManager.setSourcePath(null);
				applyFen(fenListManager.currentFen());
				updateFenStatus();
				return true;
			}

			/**
			 * handleDroppedFiles method.
			 * @param files parameter.
			 * @return return value.
			 */
			protected boolean handleDroppedFiles(List<File> files) {
				if (files == null || files.isEmpty()) {
					return false;
				}
				File file = files.get(0);
				String name = file.getName().toLowerCase();
				Path path = file.toPath();
				if (name.endsWith(".pgn")) {
					try {
						String text = Files.readString(path, StandardCharsets.UTF_8);
						boolean ok = loadPgnText(text);
						if (ok) {
							fenListManager.setSourcePath(path);
							updateFenSourceButton();
						}
						return ok;
					} catch (IOException ex) {
						recordProblem("Import", "Failed to read PGN: " + ex.getMessage());
						JOptionPane.showMessageDialog(frame, "Failed to read PGN: " + ex.getMessage(), "Import",
								JOptionPane.ERROR_MESSAGE);
						return false;
					}
				}
				if (name.endsWith(".fen") || name.endsWith(".txt") || name.endsWith(".csv")) {
					return loadFenListFromFile(path);
				}
				try {
					String text = Files.readString(path, StandardCharsets.UTF_8);
					return handleDroppedText(text);
				} catch (IOException ex) {
					recordProblem("Import", "Failed to read file: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to read file: " + ex.getMessage(), "Import",
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}

			/**
			 * handleDroppedText method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected boolean handleDroppedText(String text) {
				if (text == null || text.isBlank()) {
					return false;
				}
				String trimmed = text.trim();
				if (looksLikePgn(trimmed) && loadPgnText(trimmed)) {
					return true;
				}
				if (trimmed.contains("\n") || trimmed.contains("\r")) {
					String[] lines = trimmed.split("\\R");
					if (loadFenLines(Arrays.asList(lines), "Dropped text")) {
						return true;
					}
				}
				return applyFenIfValid(trimmed);
			}

			/**
			 * looksLikePgn method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected boolean looksLikePgn(String text) {
				return HistoryTextSupport.looksLikePgn(text);
			}

			/**
			 * applyFenIfValid method.
			 * @param fen parameter.
			 * @return return value.
			 */
			protected boolean applyFenIfValid(String fen) {
				try {
					new Position(fen.trim());
				} catch (IllegalArgumentException ex) {
					return false;
				}
				applyFen(fen);
				return true;
			}

			/**
			 * loadPgnText method.
			 * @param text parameter.
			 * @return return value.
			 */
			protected boolean loadPgnText(String text) {
				if (text == null || text.isBlank()) {
					JOptionPane.showMessageDialog(frame, "Paste PGN text first.", "PGN",
							JOptionPane.WARNING_MESSAGE);
					return false;
				}
				List<Game> games = Pgn.parseGames(text);
				if (games.isEmpty()) {
					JOptionPane.showMessageDialog(frame, "No PGN games found in the pasted text.", "PGN",
							JOptionPane.WARNING_MESSAGE);
					return false;
				}
				Game game = choosePgnGame(games);
				if (game == null) {
					return false;
				}
				rememberPgnMetadata(game);
				PgnMainline mainline = buildPgnMainline(game);
				if (mainline.fens().isEmpty()) {
					JOptionPane.showMessageDialog(frame, "No legal moves found in the PGN mainline.", "PGN",
							JOptionPane.WARNING_MESSAGE);
					return false;
				}
				if (mainline.error() != null) {
					JOptionPane.showMessageDialog(frame, mainline.error(), "PGN",
							JOptionPane.WARNING_MESSAGE);
				}
				if (fenListManager.load(mainline.fens(), buildPgnLabel(game, mainline.fens().size() - 1))) {
					fenListManager.setSourcePath(null);
				}
				pgnNavigator = buildPgnNavigator(game);
				if (pgnNavigator != null) {
					applyPgnNode(pgnNavigator.root);
				} else {
					applyFen(fenListManager.currentFen());
				}
				updateFenStatus();
				refreshPgnEditor();
				updatePgnSummaryLabel();
				return true;
			}

			/**
			 * updatePgnSummaryLabel method.
			 */
			protected void updatePgnSummaryLabel() {
				if (pgnSummaryLabel == null) {
					return;
				}
				if (pgnTags.isEmpty()) {
					if (pgnNavigator != null) {
						pgnSummaryLabel.setText("PGN loaded");
					} else {
						pgnSummaryLabel.setText("No PGN loaded");
					}
					return;
				}
				String white = pgnTags.getOrDefault("White", "");
				String black = pgnTags.getOrDefault("Black", "");
				String event = pgnTags.getOrDefault("Event", "");
				String site = pgnTags.getOrDefault("Site", "");
				String date = pgnTags.getOrDefault("Date", "");
				StringBuilder sb = new StringBuilder();
				if (!event.isBlank()) {
					sb.append(event);
				}
				if (!site.isBlank()) {
						if (!sb.isEmpty()) {
						sb.append(" — ");
					}
					sb.append(site);
				}
				if (!date.isBlank()) {
						if (!sb.isEmpty()) {
						sb.append(" — ");
					}
					sb.append(date);
				}
				if (!white.isBlank() || !black.isBlank()) {
						if (!sb.isEmpty()) {
						sb.append(" | ");
					}
					sb.append(white.isBlank() ? "?" : white);
					sb.append(" vs ");
					sb.append(black.isBlank() ? "?" : black);
				}
				if (pgnResult != null && !pgnResult.isBlank() && !"*".equals(pgnResult)) {
						if (!sb.isEmpty()) {
						sb.append(" | ");
					}
					sb.append(pgnResult);
				}
					if (sb.isEmpty()) {
					sb.append("PGN loaded");
				}
				pgnSummaryLabel.setText(truncate(sb.toString(), 72));
			}

			/**
			 * choosePgnGame method.
			 * @param games parameter.
			 * @return return value.
			 */
			protected Game choosePgnGame(List<Game> games) {
				if (games.size() == 1) {
					return games.get(0);
				}
				String[] labels = new String[games.size()];
				for (int i = 0; i < games.size(); i++) {
					labels[i] = pgnGameLabel(games.get(i), i + 1);
				}
				JComboBox<String> combo = new JComboBox<>(labels);
				int res = JOptionPane.showConfirmDialog(frame, combo, "Select PGN game",
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
				if (res != JOptionPane.OK_OPTION) {
					return null;
				}
				int idx = combo.getSelectedIndex();
				if (idx < 0 || idx >= games.size()) {
					return games.get(0);
				}
				return games.get(idx);
			}

			/**
			 * pgnGameLabel method.
			 * @param game parameter.
			 * @param index parameter.
			 * @return return value.
			 */
			protected String pgnGameLabel(Game game, int index) {
				return HistoryTextSupport.pgnGameLabel(game, index);
			}

			/**
			 * buildPgnLabel method.
			 * @param game parameter.
			 * @param plyCount parameter.
			 * @return return value.
			 */
			protected String buildPgnLabel(Game game, int plyCount) {
				return HistoryTextSupport.buildPgnLabel(game, plyCount);
			}

			/**
			 * buildPgnMainline method.
			 * @param game parameter.
			 * @return return value.
			 */
			protected PgnMainline buildPgnMainline(Game game) {
				List<String> fens = new ArrayList<>();
				Position start = game.getStartPosition() != null
						? game.getStartPosition().copy()
						: new Position(Game.STANDARD_START_FEN);
				Position current = start.copy();
				fens.add(current.toString());
				Game.Node node = game.getMainline();
				int ply = 0;
				while (node != null) {
					String san = node.getSan();
					try {
						short move = SAN.fromAlgebraic(current, san);
						current = current.copy().play(move);
						fens.add(current.toString());
						ply++;
					} catch (IllegalArgumentException ex) {
						String error = "Stopped at ply " + (ply + 1) + " (SAN: " + san + "): " + ex.getMessage();
						return new PgnMainline(fens, error);
					}
					node = node.getNext();
				}
				return new PgnMainline(fens, null);
			}

			/**
			 * buildPgnNavigator method.
			 * @param game parameter.
			 * @return return value.
			 */
			protected PgnNavigator buildPgnNavigator(Game game) {
				return PgnStructureSupport.fromGame(game);
			}

			/**
			 * rememberPgnMetadata method.
			 * @param game parameter.
			 */
			protected void rememberPgnMetadata(Game game) {
				pgnTags.clear();
				pgnResult = "*";
				pgnStartPosition = null;
				if (game == null) {
					return;
				}
				pgnResult = game.getResult();
				if (game.getStartPosition() != null) {
					pgnStartPosition = game.getStartPosition().copy();
				}
				for (java.util.Map.Entry<String, String> entry : game.getTags().entrySet()) {
					if (entry.getKey() == null) {
						continue;
					}
					pgnTags.put(entry.getKey(), entry.getValue());
				}
			}

			/**
			 * buildCurrentPgnText method.
			 * @return return value.
			 */
			protected String buildCurrentPgnText() {
				Game game = PgnStructureSupport.toGame(pgnNavigator, moveHistory, history, pgnTags, pgnStartPosition,
						pgnResult);
				return Pgn.toPgn(game);
			}

		/**
		 * applyPgnNode method.
		 *
		 * @param node parameter.
		 */
		protected void applyPgnNode(PgnNode node) {
				if (node == null) {
					return;
				}
				PgnNode previous = pgnNavigator != null ? pgnNavigator.current : null;
				queueAnimationMove(transitionMove(previous, node));
				history.clear();
				moveHistory.clear();
				currentLineNodes.clear();
				if (pgnNavigator != null) {
					pgnNavigator.current = node;
				}
				if (node.parent == null) {
					position = node.positionAfter.copy();
					fenField.setText(position.toString());
					updateHistoryStart(position);
					lastMove = Move.NO_MOVE;
					bumpPosition();
					refreshAll();
					return;
				}
				List<PgnNode> path = new ArrayList<>();
				PgnNode cursor = node;
				while (cursor != null && cursor.parent != null) {
					path.add(cursor);
					cursor = cursor.parent;
				}
				java.util.Collections.reverse(path);
				for (PgnNode step : path) {
					if (step.parent != null && step.parent.positionAfter != null) {
						history.add(step.parent.positionAfter.copy());
					}
					if (step.san != null) {
						moveHistory.add(step.san);
						currentLineNodes.add(step);
					}
				}
				position = node.positionAfter.copy();
				fenField.setText(position.toString());
				if (pgnNavigator != null && pgnNavigator.root != null && pgnNavigator.root.positionAfter != null) {
					updateHistoryStart(pgnNavigator.root.positionAfter);
				} else {
					updateHistoryStart(position);
				}
				updateLastMoveFromHistory();
				bumpPosition();
				refreshAll();
			}

			/**
			 * installNavigationBindings method.
			 */
		protected void installNavigationBindings() {
			if (frame == null) {
				return;
			}
			JComponent target = frame.getRootPane();
			HistoryInputSupport.NavigationActions actions = navigationActions();
			HistoryInputSupport.installNavigationBindings(target, actions);
			navigationDispatcher = HistoryInputSupport.installGlobalNavigationDispatcher(target, actions);
		}

		/**
		 * uninstallGlobalNavigationDispatcher method.
		 */
		protected void uninstallGlobalNavigationDispatcher() {
			HistoryInputSupport.uninstallGlobalNavigationDispatcher(navigationDispatcher);
			navigationDispatcher = null;
		}

		/**
		 * installDragAndDrop method.
		 */
		protected void installDragAndDrop() {
			HistoryInputSupport.installDragAndDrop(root, new HistoryDropHandler());
		}

				/**
		 * Handles navigation actions.
		 * @return computed value
		 */
private HistoryInputSupport.NavigationActions navigationActions() {
			if (navigationActions == null) {
				navigationActions = new NavigationActionsImpl();
			}
			return navigationActions;
		}

				/**
		 * Stores the navigation actions.
		 */
private HistoryInputSupport.NavigationActions navigationActions;

		/**
		 * NavigationActionsImpl class.
		 *
		 * Provides class behavior for the GUI module.
		 *
		 * @since 2026
		 * @author Lennart A. Conrad
		 */
		private final class NavigationActionsImpl implements HistoryInputSupport.NavigationActions {

						/**
			 * Returns whether text focus.
			 * @return computed value
			 */
@Override
			/**
			 * isTextFocus method.
			 *
			 * @return return value.
			 */
			public boolean isTextFocus() {
				return GuiWindowHistoryNavigation.this.isTextFocus();
			}

						/**
			 * Handles navigate prev.
			 */
@Override
			/**
			 * navigatePrev method.
			 */
			public void navigatePrev() {
				GuiWindowHistoryNavigation.this.navigatePrev();
			}

						/**
			 * Handles navigate next.
			 */
@Override
			/**
			 * navigateNext method.
			 */
			public void navigateNext() {
				GuiWindowHistoryNavigation.this.navigateNext();
			}

						/**
			 * Handles navigate start.
			 */
@Override
			/**
			 * navigateStart method.
			 */
			public void navigateStart() {
				GuiWindowHistoryNavigation.this.navigateStart();
			}

						/**
			 * Handles navigate end.
			 */
@Override
			/**
			 * navigateEnd method.
			 */
			public void navigateEnd() {
				GuiWindowHistoryNavigation.this.navigateEnd();
			}

						/**
			 * Handles navigate up.
			 */
@Override
			/**
			 * navigateUp method.
			 */
			public void navigateUp() {
				GuiWindowHistoryNavigation.this.navigateUp();
			}

						/**
			 * Handles navigate down.
			 */
@Override
			/**
			 * navigateDown method.
			 */
			public void navigateDown() {
				GuiWindowHistoryNavigation.this.navigateDown();
			}

						/**
			 * Handles play engine best.
			 */
@Override
			/**
			 * playEngineBest method.
			 */
			public void playEngineBest() {
				GuiWindowHistoryNavigation.this.playEngineBest();
			}

						/**
			 * Handles undo move.
			 */
@Override
			/**
			 * undoMove method.
			 */
			public void undoMove() {
				GuiWindowHistoryNavigation.this.undoMove();
			}

						/**
			 * Handles flip board.
			 */
@Override
			/**
			 * flipBoard method.
			 */
			public void flipBoard() {
				GuiWindowHistoryNavigation.this.flipBoard();
			}

						/**
			 * Handles clear annotations.
			 */
@Override
			/**
			 * clearAnnotations method.
			 */
			public void clearAnnotations() {
				if (boardPanel != null) {
					boardPanel.clearShapes();
				}
			}

						/**
			 * Handles toggle legal hints.
			 */
@Override
			/**
			 * toggleLegalHints method.
			 */
			public void toggleLegalHints() {
				if (legalToggle != null) {
					legalToggle.setSelected(!legalToggle.isSelected());
					guiState.showLegal = legalToggle.isSelected();
					refreshBoard();
				}
			}

						/**
			 * Handles toggle coords.
			 */
@Override
			/**
			 * toggleCoords method.
			 */
			public void toggleCoords() {
				if (coordsToggle != null) {
					coordsToggle.setSelected(!coordsToggle.isSelected());
					showCoords = coordsToggle.isSelected();
					guiState.showCoords = showCoords;
					refreshBoard();
				}
			}

						/**
			 * Handles open board editor.
			 */
@Override
			/**
			 * openBoardEditor method.
			 */
			public void openBoardEditor() {
				GuiWindowHistoryNavigation.this.openBoardEditor();
			}

						/**
			 * Handles toggle engine analysis.
			 */
@Override
			/**
			 * toggleEngineAnalysis method.
			 */
			public void toggleEngineAnalysis() {
				if (engineWorker == null) {
					startEngine();
				} else {
					stopEngine();
				}
			}

						/**
			 * Handles open command palette.
			 */
@Override
			/**
			 * openCommandPalette method.
			 */
			public void openCommandPalette() {
				GuiWindowHistoryNavigation.this.openCommandPalette();
			}

						/**
			 * Handles copy fen.
			 */
@Override
			/**
			 * copyFen method.
			 */
			public void copyFen() {
				GuiWindowHistoryNavigation.this.copyFen();
			}

						/**
			 * Handles copy board image.
			 */
@Override
			/**
			 * copyBoardImage method.
			 */
			public void copyBoardImage() {
				GuiWindowHistoryNavigation.this.copyBoardImage();
			}

						/**
			 * Handles save board image.
			 */
@Override
			/**
			 * saveBoardImage method.
			 */
			public void saveBoardImage() {
				GuiWindowHistoryNavigation.this.saveBoardImage();
			}

						/**
			 * Handles toggle sidebar.
			 */
@Override
			/**
			 * toggleSidebar method.
			 */
			public void toggleSidebar() {
				GuiWindowHistoryNavigation.this.toggleSidebar();
			}

						/**
			 * Handles toggle panel.
			 */
@Override
			/**
			 * togglePanel method.
			 */
			public void togglePanel() {
				GuiWindowHistoryNavigation.this.togglePanel();
			}

						/**
			 * Handles show problems.
			 */
@Override
			/**
			 * showProblems method.
			 */
			public void showProblems() {
				setPanelVisible(true, true);
				openPanelTab("Problems");
			}

						/**
			 * Handles show output.
			 */
@Override
			/**
			 * showOutput method.
			 */
			public void showOutput() {
				setPanelVisible(true, true);
				openPanelTab("Output");
			}

						/**
			 * Handles toggle focus mode.
			 */
@Override
			/**
			 * toggleFocusMode method.
			 */
			public void toggleFocusMode() {
				GuiWindowHistoryNavigation.this.toggleFocusMode();
			}

						/**
			 * Handles focus exit.
			 */
@Override
			/**
			 * focusExit method.
			 */
			public void focusExit() {
				if (focusMode || zenMode) {
					exitFocusModes();
					return;
				}
				if (isEditorTabSelected("board.editor")) {
					selectEditorTab("board.pgn");
					if (boardPanel != null) {
						boardPanel.requestFocusInWindow();
					}
					return;
				}
				if (isEditorTabSelected("board.pgn")) {
					selectEditorTab("board.editor");
				}
			}
		}

		/**
		 * HistoryDropHandler class.
		 *
		 * Provides class behavior for the GUI module.
		 *
		 * @since 2026
		 * @author Lennart A. Conrad
		 */
		private final class HistoryDropHandler implements HistoryInputSupport.DropHandler {

						/**
			 * Handles can handle files.
			 * @param files files value
			 * @return computed value
			 */
@Override
			/**
			 * canHandleFiles method.
			 *
			 * @param files parameter.
			 * @return return value.
			 */
			public boolean canHandleFiles(List<File> files) {
				return files != null && !files.isEmpty();
			}

						/**
			 * Handles handle files.
			 * @param files files value
			 * @return computed value
			 */
@Override
			/**
			 * handleFiles method.
			 *
			 * @param files parameter.
			 * @return return value.
			 */
			public boolean handleFiles(List<File> files) {
				return handleDroppedFiles(files);
			}

						/**
			 * Handles can handle text.
			 * @param text text value
			 * @return computed value
			 */
@Override
			/**
			 * canHandleText method.
			 *
			 * @param text parameter.
			 * @return return value.
			 */
			public boolean canHandleText(String text) {
				return text != null && !text.isBlank();
			}

						/**
			 * Handles handle text.
			 * @param text text value
			 * @return computed value
			 */
@Override
			/**
			 * handleText method.
			 *
			 * @param text parameter.
			 * @return return value.
			 */
			public boolean handleText(String text) {
				return handleDroppedText(text);
			}
		}

		/**
		 * installStateListeners method.
		 */
			protected void installStateListeners() {
				frame.addComponentListener(new java.awt.event.ComponentAdapter() {
										/**
					 * Handles component resized.
					 * @param e e value
					 */
@Override
					public void componentResized(java.awt.event.ComponentEvent e) {
						captureAndScheduleGuiStateSave();
					}

										/**
					 * Handles component moved.
					 * @param e e value
					 */
@Override
					public void componentMoved(java.awt.event.ComponentEvent e) {
						captureAndScheduleGuiStateSave();
					}
				});
			}

			/**
			 * Captures the current GUI state and schedules it for persistence.
			 */
			private void captureAndScheduleGuiStateSave() {
				captureGuiState();
				scheduleGuiStateSave();
			}

			/**
			 * scheduleGuiStateSave method.
			 */
			protected void scheduleGuiStateSave() {
				if (guiStateSaveTimer == null) {
					guiStateSaveTimer = new Timer(1200, e -> {
						captureGuiState();
						saveGuiState();
					});
					guiStateSaveTimer.setRepeats(false);
				}
				if (guiStateSaveTimer.isRunning()) {
					guiStateSaveTimer.restart();
				} else {
					guiStateSaveTimer.start();
				}
			}

			/**
			 * bindNav method.
			 * @param im parameter.
			 * @param am parameter.
			 * @param id parameter.
			 * @param keyCode parameter.
			 * @param modifiers parameter.
			 * @param action parameter.
			 */
			protected void bindNav(InputMap im, ActionMap am, String id, int keyCode, int modifiers, Runnable action) {
				im.put(KeyStroke.getKeyStroke(keyCode, modifiers), id);
				am.put(id, new AbstractAction() {
										/**
					 * Handles action performed.
					 * @param e e value
					 */
@Override
					public void actionPerformed(java.awt.event.ActionEvent e) {
						if (isTextFocus()) {
							return;
						}
						action.run();
					}
				});
			}

			/**
			 * bindNavAlways method.
			 * @param im parameter.
			 * @param am parameter.
			 * @param id parameter.
			 * @param keyCode parameter.
			 * @param modifiers parameter.
			 * @param action parameter.
			 */
			protected void bindNavAlways(InputMap im, ActionMap am, String id, int keyCode, int modifiers, Runnable action) {
				im.put(KeyStroke.getKeyStroke(keyCode, modifiers), id);
				am.put(id, new AbstractAction() {
										/**
					 * Handles action performed.
					 * @param e e value
					 */
@Override
					public void actionPerformed(java.awt.event.ActionEvent e) {
						action.run();
					}
				});
			}

			/**
			 * isTextFocus method.
			 * @return return value.
			 */
			protected boolean isTextFocus() {
				java.awt.Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				return focus instanceof JTextComponent;
			}

			/**
			 * navigatePrev method.
			 */
			protected void navigatePrev() {
				if (pgnNavigator == null && (!moveHistory.isEmpty() || !history.isEmpty())) {
					ensureGameTree();
				}
				if (pgnNavigator != null) {
					if (pgnNavigator.current != null && pgnNavigator.current.parent != null) {
						applyPgnNode(pgnNavigator.current.parent);
					}
					return;
				}
				if (fenListManager.hasEntries()) {
					prevFen();
				}
			}

			/**
			 * navigateNext method.
			 */
			protected void navigateNext() {
				if (pgnNavigator == null && (!moveHistory.isEmpty() || !history.isEmpty())) {
					ensureGameTree();
				}
				if (pgnNavigator != null) {
					PgnNode current = pgnNavigator.current != null ? pgnNavigator.current : pgnNavigator.root;
					PgnNode next = chooseChild(current, 0);
					if (next != null) {
						applyPgnNode(next);
					}
					return;
				}
				if (fenListManager.hasEntries()) {
					nextFen();
				}
			}

			/**
			 * navigateStart method.
			 */
			protected void navigateStart() {
				if (pgnNavigator == null && (!moveHistory.isEmpty() || !history.isEmpty())) {
					ensureGameTree();
				}
				if (pgnNavigator != null) {
					applyPgnNode(pgnNavigator.root);
					return;
				}
				if (fenListManager.hasEntries()) {
					fenListManager.setIndex(0);
					applyFen(fenListManager.currentFen());
					updateFenStatus();
				}
			}

			/**
			 * navigateEnd method.
			 */
			protected void navigateEnd() {
				if (pgnNavigator == null && (!moveHistory.isEmpty() || !history.isEmpty())) {
					ensureGameTree();
				}
				if (pgnNavigator != null) {
					PgnNode current = pgnNavigator.current != null ? pgnNavigator.current : pgnNavigator.root;
					PgnNode cursor = current;
					while (cursor != null && cursor.mainNext != null) {
						cursor = cursor.mainNext;
					}
					if (cursor != null) {
						applyPgnNode(cursor);
					}
					return;
				}
				if (fenListManager.hasEntries()) {
					fenListManager.setIndex(fenListManager.size() - 1);
					applyFen(fenListManager.currentFen());
					updateFenStatus();
				}
			}

			/**
			 * navigateUp method.
			 */
			protected void navigateUp() {
				if (pgnNavigator != null) {
					navigateStart();
					return;
				}
				if (fenListManager.hasEntries()) {
					navigateStart();
				}
			}

			/**
			 * navigateDown method.
			 */
			protected void navigateDown() {
				if (pgnNavigator != null) {
					navigateEnd();
					return;
				}
				if (fenListManager.hasEntries()) {
					navigateEnd();
				}
			}

			/**
			 * cycleVariation method.
			 * @param delta parameter.
			 * @return return value.
			 */
			protected boolean cycleVariation(int delta) {
				if (pgnNavigator == null || pgnNavigator.current == null) {
					return false;
				}
				PgnNode base = pgnNavigator.current.parent != null ? pgnNavigator.current.parent : pgnNavigator.root;
				List<PgnNode> children = collectChildren(base);
				if (children.size() <= 1) {
					return false;
				}
				int idx = children.indexOf(pgnNavigator.current);
				if (idx < 0) {
					idx = 0;
				}
				int nextIdx = (idx + delta + children.size()) % children.size();
				PgnNode next = children.get(nextIdx);
				applyPgnNode(next);
				return true;
			}

			/**
			 * chooseChild method.
			 * @param parent parameter.
			 * @param index parameter.
			 * @return return value.
			 */
			protected PgnNode chooseChild(PgnNode parent, int index) {
				List<PgnNode> children = collectChildren(parent);
				if (children.isEmpty()) {
					return null;
				}
				int idx = Math.max(0, Math.min(index, children.size() - 1));
				return children.get(idx);
			}

			/**
			 * collectChildren method.
			 * @param parent parameter.
			 * @return return value.
			 */
			protected List<PgnNode> collectChildren(PgnNode parent) {
				if (parent == null) {
					return List.of();
				}
				List<PgnNode> children = new ArrayList<>();
				if (parent.mainNext != null) {
					children.add(parent.mainNext);
				}
				if (!parent.variations.isEmpty()) {
					children.addAll(parent.variations);
				}
				return children;
			}

			/**
			 * extractFen method.
			 * @param line parameter.
			 * @return return value.
			 */
			protected String extractFen(String line) {
				return HistoryTextSupport.extractFen(line);
			}

			/**
			 * updateFenStatus method.
			 */
			protected void updateFenStatus() {
				if (!fenListManager.hasEntries() || fenListManager.getIndex() < 0) {
					fenListStatus.setText("—");
					fenSourceLabel.setText("No list loaded");
					updateFenSourceButton();
					return;
				}
				fenListStatus.setText((fenListManager.getIndex() + 1) + " / " + fenListManager.size());
				fenSourceLabel.setText(fenListManager.sourceLabel());
				fenIndexField.setText(String.valueOf(fenListManager.getIndex() + 1));
				updateFenSourceButton();
			}

			/**
			 * updateFenSourceButton method.
			 */
			protected void updateFenSourceButton() {
				if (fenOpenButton == null) {
					return;
				}
				Path source = fenListManager.sourcePath();
				fenOpenButton.setEnabled(source != null && Files.exists(source));
			}

			/**
			 * nextFen method.
			 */
			protected void nextFen() {
				String fen = fenListManager.next();
				if (fen == null) {
					return;
				}
				applyFen(fen);
				updateFenStatus();
			}

			/**
			 * prevFen method.
			 */
			protected void prevFen() {
				String fen = fenListManager.prev();
				if (fen == null) {
					return;
				}
				applyFen(fen);
				updateFenStatus();
			}

			/**
			 * randomFen method.
			 */
			protected void randomFen() {
				String fen = fenListManager.random();
				if (fen == null) {
					return;
				}
				applyFen(fen);
				updateFenStatus();
			}

			/**
			 * jumpFen method.
			 */
			protected void jumpFen() {
				String text = fenIndexField.getText().trim();
				if (text.isEmpty()) {
					return;
				}
				try {
					int idx = Integer.parseInt(text);
					String fen = fenListManager.jump(idx);
					if (fen != null) {
						applyFen(fen);
						updateFenStatus();
					}
				} catch (NumberFormatException ignored) {
					// ignore
				}
			}

			/**
			 * copyBoardImage method.
			 */
			protected void copyBoardImage() {
				try {
					BufferedImage image = boardPanel.snapshot(position, whiteDown, legalToggle.isSelected(), showCoords);
					Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
					cb.setContents(new TransferableImage(image), null);
				} catch (Exception ex) {
					recordProblem("Copy error", "Failed to copy image: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to copy image: " + ex.getMessage(), "Copy error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * saveBoardImage method.
			 */
			protected void saveBoardImage() {
				JFileChooser chooser = new JFileChooser();
				chooser.setSelectedFile(new File("board.png"));
				chooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
				int res = chooser.showSaveDialog(frame);
				if (res != JFileChooser.APPROVE_OPTION) {
					return;
				}
				File file = chooser.getSelectedFile();
				String path = file.getAbsolutePath();
				if (!path.toLowerCase().endsWith(".png")) {
					file = new File(path + ".png");
				}
				BufferedImage image = boardPanel.snapshot(position, whiteDown, legalToggle.isSelected(), showCoords);
				try {
					ImageIO.write(image, "png", file);
				} catch (IOException ex) {
					recordProblem("Save error", "Failed to save image: " + ex.getMessage());
					JOptionPane.showMessageDialog(frame, "Failed to save image: " + ex.getMessage(), "Save error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			/**
			 * nagGlyph method.
			 * @param node parameter.
			 * @return return value.
			 */
			public String nagGlyph(PgnNode node) {
				if (node == null) {
					return "";
				}
				return nagGlyphFromCode(node.nag);
			}

			/**
			 * nagGlyphFromCode method.
			 * @param nag parameter.
			 * @return return value.
			 */
			protected String nagGlyphFromCode(int nag) {
				return HistoryTextSupport.nagGlyphFromCode(nag);
			}


}
