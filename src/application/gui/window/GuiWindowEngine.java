package application.gui.window;

import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import application.gui.model.CommandSpec;
import application.gui.model.EngineCacheEntry;
import application.gui.model.EngineUpdate;
import application.gui.model.ProtocolLoad;
import application.gui.model.PvEntry;
import application.gui.model.ReportUpdate;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import application.gui.ui.ThemedScrollBarUI;
import application.gui.ui.ThemedSliderUI;
import application.gui.ui.ThemedTabbedPaneUI;
import application.gui.ui.ThemedToggleIcon;
import application.gui.util.TransferableImage;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.AlphaComposite;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTabbedPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.TransferHandler;
import javax.swing.SwingConstants;
import javax.swing.JWindow;
import javax.swing.text.JTextComponent;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import chess.images.assets.Pictures;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * GuiWindowEngine class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
class GuiWindowEngine extends GuiWindowHistory {
		/**
		 * PV_PREVIEW_BOARD_SIZE constant.
		 */
		private static final int PV_PREVIEW_BOARD_SIZE = 264;
		/**
		 * PV_PREVIEW_OFFSET constant.
		 */
		private static final int PV_PREVIEW_OFFSET = 16;
		/**
		 * pvPreviewWindow field.
		 */
		private JWindow pvPreviewWindow;
		/**
		 * pvPreviewBoardLabel field.
		 */
		private JLabel pvPreviewBoardLabel;
		/**
		 * pvPreviewCachedPv field.
		 */
		private int pvPreviewCachedPv = -1;
		/**
		 * pvPreviewCachedPly field.
		 */
		private int pvPreviewCachedPly = -1;
		/**
		 * pvPreviewCachedVersion field.
		 */
		private long pvPreviewCachedVersion = Long.MIN_VALUE;
		/**
		 * pvPreviewCachedHue field.
		 */
		private int pvPreviewCachedHue = Integer.MIN_VALUE;
		/**
		 * pvPreviewCachedBrightness field.
		 */
		private int pvPreviewCachedBrightness = Integer.MIN_VALUE;
		/**
		 * pvPreviewCachedSaturation field.
		 */
		private int pvPreviewCachedSaturation = Integer.MIN_VALUE;
		/**
		 * pvPreviewCachedWhiteDown field.
		 */
		private boolean pvPreviewCachedWhiteDown = true;
		/**
		 * pvPreviewCachedHistoryFen field.
		 */
		private String pvPreviewCachedHistoryFen;

		/**
		 * resetPvPreviewCache method.
		 */
		private void resetPvPreviewCache() {
		pvPreviewCachedPv = -1;
		pvPreviewCachedPly = -1;
		pvPreviewCachedVersion = Long.MIN_VALUE;
		pvPreviewCachedHue = Integer.MIN_VALUE;
		pvPreviewCachedBrightness = Integer.MIN_VALUE;
		pvPreviewCachedSaturation = Integer.MIN_VALUE;
		pvPreviewCachedWhiteDown = true;
		pvPreviewCachedHistoryFen = null;
		if (pvPreviewBoardLabel != null) {
			pvPreviewBoardLabel.setIcon(null);
		}
	}

		/**
		 * hasRenderablePreviewIcon method.
		 *
		 * @return return value.
		 */
		private boolean hasRenderablePreviewIcon() {
		if (pvPreviewBoardLabel == null) {
			return false;
		}
		javax.swing.Icon icon = pvPreviewBoardLabel.getIcon();
		if (!(icon instanceof ImageIcon imageIcon)) {
			return false;
		}
		Image image = imageIcon.getImage();
		return image != null && imageIcon.getIconWidth() > 0 && imageIcon.getIconHeight() > 0;
	}

				/**
		 * Handles clear mini board preview.
		 */
@Override
	/**
	 * clearMiniBoardPreview method.
	 */
	protected void clearMiniBoardPreview() {
		hideEnginePvMiniBoardPreview();
	}

		/**
		 * stopCommand method.
		 */
		protected void stopCommand() {
					if (currentProcess != null) {
						currentProcess.destroyForcibly();
					}
				}

				/**
				 * runCliCommand method.
				 *
				 * @param command parameter.
				 * @param args parameter.
				 */
				protected void runCliCommand(String command, List<String> args) {
					if (currentProcess != null) {
						appendOutput("Another command is still running.\n");
						return;
					}

					List<String> commandLine = buildJavaCommand(command, args);
					if (commandLine == null) {
						appendOutput("Unable to locate Java runtime or classpath.\n");
						return;
					}

					commandOutput.setText("");
					if (panelOutputArea != null) {
						panelOutputArea.setText("");
					}
					runButton.setEnabled(false);
					stopButton.setEnabled(true);
					helpButton.setEnabled(false);

					SwingWorker<Void, String> worker = new SwingWorker<>() {
												/**
						 * Handles do in background.
						 * @return computed value
						 */
@Override
						protected Void doInBackground() {
							ProcessBuilder builder = new ProcessBuilder(commandLine);
							builder.redirectErrorStream(true);
							try {
								Process process = builder.start();
								currentProcess = process;
								try (BufferedReader reader = new BufferedReader(
										new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
									String line;
									while ((line = reader.readLine()) != null) {
										publish(line + "\n");
									}
								}
								int exit = process.waitFor();
								publish("\n[exit " + exit + "]\n");
							} catch (IOException | InterruptedException ex) {
								publish("Error: " + ex.getMessage() + "\n");
							} finally {
								currentProcess = null;
							}
							return null;
						}

												/**
						 * Handles process.
						 * @param chunks chunks value
						 */
@Override
						protected void process(List<String> chunks) {
							for (String chunk : chunks) {
								appendOutput(chunk);
							}
						}

												/**
						 * Handles done.
						 */
@Override
						protected void done() {
							runButton.setEnabled(true);
							stopButton.setEnabled(false);
							helpButton.setEnabled(true);
						}
					};
					worker.execute();
				}

								/**
								 * appendOutput method.
								 *
								 * @param text parameter.
								 */
								protected void appendOutput(String text) {
					commandOutput.append(text);
					commandOutput.setCaretPosition(commandOutput.getDocument().getLength());
					if (panelOutputArea != null) {
						panelOutputArea.append(text);
						panelOutputArea.setCaretPosition(panelOutputArea.getDocument().getLength());
					}
				}

								/**
				 * Handles build command specs.
				 * @return computed value
				 */
protected java.util.Map<String, CommandSpec> buildCommandSpecs() {
					java.util.Map<String, CommandSpec> specs = new java.util.LinkedHashMap<>();
					specs.put("bestmove-uci", new CommandSpec("bestmove-uci", true, List.of(
							field("--max-nodes", "Nodes", CommandFieldType.NUMBER, "e.g. 1000000"),
							field("--max-duration", "Time ms", CommandFieldType.NUMBER, "e.g. 1000"),
							field("--multipv", "MultiPV", CommandFieldType.NUMBER, "e.g. 1"),
							field("--threads", "Threads", CommandFieldType.NUMBER, "e.g. 4"),
							field("--hash", "Hash MB", CommandFieldType.NUMBER, "e.g. 128"),
							field("--wdl", "WDL", CommandFieldType.FLAG, ""),
							field("--protocol-path", "Protocol", CommandFieldType.PATH, "path/to/protocol.toml")
					)));
					specs.put("bestmove-san", specs.get("bestmove-uci").withName("bestmove-san"));
					specs.put("bestmove-both", specs.get("bestmove-uci").withName("bestmove-both"));
					specs.put("analyze", new CommandSpec("analyze", true, List.of(
							field("--max-nodes", "Nodes", CommandFieldType.NUMBER, "e.g. 1000000"),
							field("--max-duration", "Time ms", CommandFieldType.NUMBER, "e.g. 1000"),
							field("--multipv", "MultiPV", CommandFieldType.NUMBER, "e.g. 3"),
							field("--threads", "Threads", CommandFieldType.NUMBER, "e.g. 4"),
							field("--hash", "Hash MB", CommandFieldType.NUMBER, "e.g. 128"),
							field("--wdl", "WDL", CommandFieldType.FLAG, ""),
							field("--protocol-path", "Protocol", CommandFieldType.PATH, "path/to/protocol.toml")
					)));
					specs.put("eval", new CommandSpec("eval", true, List.of(
							field("--max-nodes", "Nodes", CommandFieldType.NUMBER, "e.g. 1000000"),
							field("--max-duration", "Time ms", CommandFieldType.NUMBER, "e.g. 1000"),
							field("--threads", "Threads", CommandFieldType.NUMBER, "e.g. 4"),
							field("--hash", "Hash MB", CommandFieldType.NUMBER, "e.g. 128"),
							field("--wdl", "WDL", CommandFieldType.FLAG, ""),
							field("--lc0", "LC0", CommandFieldType.FLAG, ""),
							field("--classical", "Classical", CommandFieldType.FLAG, ""),
							field("--protocol-path", "Protocol", CommandFieldType.PATH, "path/to/protocol.toml")
					)));
					specs.put("eval-static", new CommandSpec("eval-static", true, List.of()));
					specs.put("moves-uci", new CommandSpec("moves-uci", true, List.of()));
					specs.put("moves-san", new CommandSpec("moves-san", true, List.of()));
					specs.put("moves-both", new CommandSpec("moves-both", true, List.of()));
					specs.put("perft", new CommandSpec("perft", true, List.of(
							field("--depth", "Depth", CommandFieldType.NUMBER, "e.g. 4"),
							field("--divide", "Divide", CommandFieldType.FLAG, "")
					)));
					specs.put("threats", new CommandSpec("threats", true, List.of(
							field("--max-nodes", "Nodes", CommandFieldType.NUMBER, "e.g. 1000000"),
							field("--max-duration", "Time ms", CommandFieldType.NUMBER, "e.g. 1000"),
							field("--multipv", "MultiPV", CommandFieldType.NUMBER, "e.g. 1"),
							field("--threads", "Threads", CommandFieldType.NUMBER, "e.g. 4"),
							field("--hash", "Hash MB", CommandFieldType.NUMBER, "e.g. 128"),
							field("--protocol-path", "Protocol", CommandFieldType.PATH, "path/to/protocol.toml")
					)));
					specs.put("tags", new CommandSpec("tags", true, List.of(
							field("--input", "Input file", CommandFieldType.PATH, "path/to/fens.txt"),
							field("--include-fen", "Include FEN", CommandFieldType.FLAG, ""),
							field("--delta", "Delta", CommandFieldType.FLAG, "")
					)));
					return specs;
				}

				/**
				 * field method.
				 *
				 * @param flag parameter.
				 * @param label parameter.
				 * @param type parameter.
				 * @param placeholder parameter.
				 * @return return value.
				 */
				protected CommandFieldSpec field(String flag, String label, CommandFieldType type, String placeholder) {
					return new CommandFieldSpec(flag, label, type, placeholder);
				}

								/**
								 * defaultEngineNodes method.
								 *
								 * @return return value.
								 */
								protected long defaultEngineNodes() {
					long cfg = Config.getMaxNodes();
					if (cfg <= 0) {
						return 1_000_000L;
					}
					return Math.min(cfg, 1_000_000L);
				}

								/**
								 * defaultEngineDuration method.
								 *
								 * @return return value.
								 */
								protected long defaultEngineDuration() {
					long cfg = Config.getMaxDuration();
					if (cfg <= 0) {
						return 1000L;
					}
					return Math.min(cfg, 2000L);
				}

								/**
								 * chooseProtocolFile method.
								 */
								protected void chooseProtocolFile() {
					JFileChooser chooser = new JFileChooser();
					chooser.setFileFilter(new FileNameExtensionFilter("Protocol TOML", "toml"));
					int res = chooser.showOpenDialog(frame);
					if (res != JFileChooser.APPROVE_OPTION) {
						return;
					}
					engineProtocolField.setText(chooser.getSelectedFile().getAbsolutePath());
				}

				/**
				 * startEngine method.
				 */
				protected void startEngine() {
					if (engineWorker != null) {
						return;
					}
					engineStopRequested = false;
					engineRestartQueued = false;
					engineOutputArea.setText("");
					if (engineBestArea != null) {
						engineBestArea.setText("");
					}
					updateEngineStats(null);
					if (evalBar != null) {
						evalBar.markPending();
					}
					latestAnalysis = null;
					latestAnalysisVersion = -1;
					setEngineStatus("Starting...");
					engineStartButton.setEnabled(false);
					engineStopButton.setEnabled(true);
					engineBestButton.setEnabled(false);
					if (boardBestButton != null) {
						boardBestButton.setEnabled(false);
					}
					setEvalBarVisible(true);

					engineWorker = new SwingWorker<>() {
												/**
						 * Handles do in background.
						 * @return computed value
						 */
@Override
						protected Void doInBackground() {
							ProtocolLoad protocolLoad = loadProtocol(engineProtocolField.getText());
							if (protocolLoad.protocol() == null) {
								publish(new EngineUpdate(positionVersion, protocolLoad.error(), "", null, null, null,
										Move.NO_MOVE, "Error", true));
								return null;
							}
							if (protocolLoad.error() != null) {
								publish(new EngineUpdate(positionVersion, protocolLoad.error(), "", null, null, null,
										Move.NO_MOVE, "Warning", false));
							}
							long nodes = parseLongField(engineNodesField, defaultEngineNodes());
							long duration = parseLongField(engineTimeField, defaultEngineDuration());
							int multipv = getMultiPv();
							boolean wdl = false;
							boolean endless = engineEndlessToggle != null && engineEndlessToggle.isSelected();
							String configKey = engineConfigKey(protocolLoad.protocol(), nodes, duration, multipv, wdl, endless);
							if (!configKey.equals(engineCacheConfigKey)) {
								engineCache.clear();
								engineCacheConfigKey = configKey;
							}
							try {
								Engine engine = ensureEngine(protocolLoad.protocol());
								engine.newGame();
								engine.setMultiPivot(multipv);
								engine.showWinDrawLoss(wdl);
								publish(new EngineUpdate(positionVersion, null, "", null, null, null,
										Move.NO_MOVE, "Running", false));
								long lastVersion = -1;
								while (!engineStopRequested && !isCancelled()) {
									long version = positionVersion;
									if (!endless && version == lastVersion) {
										sleepQuietly(80);
										continue;
									}
									Position pos = position.copyOf();
									String cacheKey = engineCacheKey(configKey, pos);
									if (!endless) {
										EngineCacheEntry cached = engineCache.get(cacheKey);
										if (cached != null) {
											publish(cachedUpdate(version, cached));
											lastVersion = version;
											while (!engineStopRequested && !isCancelled() && positionVersion == version) {
												sleepQuietly(80);
											}
											continue;
										}
									}

									if (endless) {
										EngineCacheEntry cached = engineCache.get(cacheKey);
										if (cached != null) {
											publish(cachedUpdate(version, cached));
											lastVersion = version;
											while (!engineStopRequested && !isCancelled() && positionVersion == version) {
												sleepQuietly(80);
											}
											continue;
										}
										Analysis analysis = new Analysis();
										final long[] lastPublish = { 0L };
										try {
											engine.analyseInfinite(pos, analysis, null, Math.max(2000L, duration),
													() -> engineStopRequested || isCancelled() || positionVersion != version,
													current -> {
														long now = System.currentTimeMillis();
														if (now - lastPublish[0] < 200L) {
															return;
														}
														EngineUpdate update = buildEngineUpdate(pos, current, multipv, version);
														if (update != null && version == positionVersion) {
															publish(update);
														}
														lastPublish[0] = now;
													});
										} catch (IOException ex) {
											publish(new EngineUpdate(version, "Engine error: " + ex.getMessage(), "", null, null, null,
													Move.NO_MOVE, "Error", true));
											break;
										}
										EngineUpdate update = buildEngineUpdate(pos, analysis, multipv, version);
										if (update != null) {
											engineCache.put(cacheKey, new EngineCacheEntry(update.output(), update.bestMoves(),
													update.eval(), update.chances(), update.analysis(), update.bestMove()));
											if (version == positionVersion) {
												publish(update);
											}
										}
										lastVersion = version;
										if (engineStopRequested || isCancelled()) {
											break;
										}
										continue;
									}

									Analysis analysis = new Analysis();
									try {
										engine.analyse(pos, analysis, null, nodes, duration);
									} catch (IOException ex) {
										publish(new EngineUpdate(version, "Engine error: " + ex.getMessage(), "", null, null, null,
												Move.NO_MOVE, "Error", true));
										break;
									}
									EngineUpdate update = buildEngineUpdate(pos, analysis, multipv, version);
									if (update != null) {
										engineCache.put(cacheKey, new EngineCacheEntry(update.output(), update.bestMoves(),
												update.eval(), update.chances(), update.analysis(), update.bestMove()));
										if (version == positionVersion) {
											publish(update);
										}
									}
									lastVersion = version;
									while (!engineStopRequested && !isCancelled() && positionVersion == version) {
										sleepQuietly(80);
									}
								}
							} catch (Exception ex) {
								publish(new EngineUpdate(positionVersion, "Engine error: " + ex.getMessage(), "", null, null, null,
										Move.NO_MOVE, "Error", true));
							}
							return null;
						}

												/**
						 * Handles process.
						 * @param updates updates value
						 */
@Override
						protected void process(List<EngineUpdate> updates) {
							if (updates.isEmpty()) {
								return;
							}
							EngineUpdate update = updates.get(updates.size() - 1);
							if (update.version() != positionVersion && update.version() >= 0) {
								return;
							}
							boolean repaintBoard = false;
							if (update.status() != null) {
								setEngineStatus(update.status());
							}
							if (update.output() != null) {
								if (!update.output().isBlank() || engineOutputArea.getText().isBlank()) {
									engineOutputArea.setText(update.output());
								}
								if (bestMoveToggle != null && bestMoveToggle.isSelected()) {
									repaintBoard = true;
								}
							}
							if (update.bestMoves() != null && engineBestArea != null) {
								if (!engineLockPv) {
									if (!update.bestMoves().isBlank() || engineBestArea.getText().isBlank()) {
										engineBestArea.setText(update.bestMoves());
									}
								} else {
									if (lockedBestMoves == null) {
										lockedBestMoves = update.bestMoves();
									}
									engineBestArea.setText(lockedBestMoves);
								}
							}
							if (update.analysis() != null) {
								latestAnalysis = update.analysis();
								latestAnalysisVersion = update.version();
								updateEngineStats(update.analysis());
								if (engineLockPv) {
									if (lockedAnalysis == null) {
										lockedAnalysis = update.analysis();
									}
									updateEnginePvList(lockedAnalysis);
								} else {
									lockedAnalysis = null;
									updateEnginePvList(update.analysis());
								}
								if (engineCopyPvButton != null) {
									engineCopyPvButton.setEnabled(true);
								}
							} else if (engineCopyPvButton != null) {
								engineCopyPvButton.setEnabled(false);
							}
							if (evalBar != null) {
								if (update.eval() != null) {
									evalBar.setEvaluation(update.eval(), position.isWhiteTurn());
									repaintBoard = true;
								} else {
									evalBar.markPending();
									repaintBoard = true;
								}
							}
							updateEvalGraph();
							if (engineLockPv && lockedBestMove == Move.NO_MOVE && update.bestMove() != Move.NO_MOVE) {
								lockedBestMove = update.bestMove();
							}
							short bestCandidate = engineLockPv ? lockedBestMove : update.bestMove();
							if (!engineLockPv && bestCandidate == Move.NO_MOVE && update.analysis() != null) {
								Output out = firstAvailableOutput(update.analysis(), getMultiPv());
								if (out != null) {
									bestCandidate = firstMove(out.getMoves());
								}
							}
							boolean candidateLegal = bestCandidate != Move.NO_MOVE && position.isLegalMove(bestCandidate);
							if (candidateLegal && engineBestMove != bestCandidate) {
								engineBestMove = bestCandidate;
								repaintBoard = true;
							}
							boolean hasBestMove = engineBestMove != Move.NO_MOVE && position.isLegalMove(engineBestMove);
							if (hasBestMove) {
								engineBestButton.setEnabled(true);
								if (boardBestButton != null) {
									boardBestButton.setEnabled(true);
								}
								if (boardBestIconButton != null) {
									boardBestIconButton.setEnabled(true);
								}
							} else {
								if (engineBestMove != Move.NO_MOVE) {
									engineBestMove = Move.NO_MOVE;
									repaintBoard = true;
								}
								engineBestButton.setEnabled(false);
								if (boardBestButton != null) {
									boardBestButton.setEnabled(false);
								}
								if (boardBestIconButton != null) {
									boardBestIconButton.setEnabled(false);
								}
							}
							maybeAutoPlayVsEngineMove();
							if (repaintBoard) {
								boardPanel.invalidateRenderCache();
								boardPanel.repaint();
							}
						}

												/**
						 * Handles done.
						 */
@Override
						protected void done() {
							engineWorker = null;
							engineStopRequested = false;
							engineStartButton.setEnabled(true);
							engineStopButton.setEnabled(false);
							if (!engineStatusLabel.getText().equals("Error")) {
								setEngineStatus("Stopped");
							}
							if (evalBar != null) {
								evalBar.clear();
							}
							setEvalBarVisible(true);
							updateEngineStats(null);
							lockedAnalysis = null;
							clearEnginePvList();
							if (engineCopyPvButton != null) {
								engineCopyPvButton.setEnabled(false);
							}
							if (boardBestButton != null) {
								boardBestButton.setEnabled(false);
							}
							if (boardBestIconButton != null) {
								boardBestIconButton.setEnabled(false);
							}
							if (engineRestartQueued) {
								engineRestartQueued = false;
								shutdownEngineInstance();
								startEngine();
							}
						}
					};
					engineWorker.execute();
				}

								/**
								 * startReportAnalysis method.
								 */
								protected void startReportAnalysis() {
					if (reportWorker != null) {
						return;
					}
					ensureGameTree();
					if (pgnNavigator == null || pgnNavigator.root == null) {
						JOptionPane.showMessageDialog(frame, "No game loaded.", "Report",
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					List<PgnNode> mainline = collectMainlineNodes();
					if (mainline.isEmpty()) {
						JOptionPane.showMessageDialog(frame, "No moves to analyze.", "Report",
								JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					ProtocolLoad protocolLoad = loadProtocol(engineProtocolField.getText());
					if (protocolLoad.protocol() == null) {
						String msg = protocolLoad.error() == null ? "Failed to load engine protocol."
								: protocolLoad.error();
						recordProblem("Report", msg);
						JOptionPane.showMessageDialog(frame, msg, "Report", JOptionPane.ERROR_MESSAGE);
						return;
					}
					long nodes = parseLongField(engineNodesField, defaultEngineNodes());
					long duration = parseLongField(engineTimeField, defaultEngineDuration());
					boolean wdl = false;

					List<PgnNode> targets = new ArrayList<>(mainline.size() + 1);
					targets.add(pgnNavigator.root);
					targets.addAll(mainline);
					for (PgnNode node : targets) {
						node.analysisEval = null;
						node.analysisChances = null;
						node.analysisBestMove = Move.NO_MOVE;
						node.analysisDepth = 0;
						node.analysisTimeMs = 0L;
						node.analysisComplete = false;
					}
					rebuildReportList();

					reportStopRequested = false;
					if (reportAnalyzeButton != null) {
						reportAnalyzeButton.setEnabled(false);
					}
					if (reportStopButton != null) {
						reportStopButton.setEnabled(true);
					}
					if (reportApplyNagButton != null) {
						reportApplyNagButton.setEnabled(false);
					}
					if (reportStatusLabel != null) {
						reportStatusLabel.setText("Analyzing 0/" + targets.size());
					}

					reportWorker = new SwingWorker<>() {
												/**
						 * Stores the failure message.
						 */
private String failureMessage;

												/**
						 * Handles do in background.
						 * @return computed value
						 */
@Override
						protected Void doInBackground() {
							Engine engine = null;
							try {
								engine = new Engine(protocolLoad.protocol());
								engine.newGame();
								engine.setMultiPivot(1);
								engine.showWinDrawLoss(wdl);
								for (int i = 0; i < targets.size(); i++) {
									if (isCancelled() || reportStopRequested) {
										break;
									}
									PgnNode node = targets.get(i);
									if (node.positionAfter == null) {
										publish(new ReportUpdate(i + 1, targets.size()));
										continue;
									}
									Position pos = node.positionAfter.copyOf();
									Analysis analysis = new Analysis();
									engine.analyse(pos, analysis, null, nodes, duration);
									Output best = analysis.getBestOutput(1);
									if (best != null) {
										node.analysisEval = best.getEvaluation();
										node.analysisChances = best.getChances();
										node.analysisBestMove = firstMove(best.getMoves());
										node.analysisDepth = best.getDepth();
										node.analysisTimeMs = best.getTime();
										node.analysisComplete = true;
									}
									publish(new ReportUpdate(i + 1, targets.size()));
								}
							} catch (Exception ex) {
								failureMessage = ex.getMessage();
							} finally {
								if (engine != null) {
									try {
										engine.close();
									} catch (Exception ignored) {
										// best effort
									}
								}
							}
							return null;
						}

												/**
						 * Handles process.
						 * @param updates updates value
						 */
@Override
						protected void process(List<ReportUpdate> updates) {
							if (updates == null || updates.isEmpty()) {
								return;
							}
							ReportUpdate update = updates.get(updates.size() - 1);
							if (reportStatusLabel != null) {
								reportStatusLabel.setText("Analyzing " + update.completed() + "/" + update.total());
							}
							rebuildReportList();
						}

												/**
						 * Handles done.
						 */
@Override
						protected void done() {
							boolean stopped = reportStopRequested || isCancelled();
							reportWorker = null;
							reportStopRequested = false;
							if (reportAnalyzeButton != null) {
								reportAnalyzeButton.setEnabled(true);
							}
							if (reportStopButton != null) {
								reportStopButton.setEnabled(false);
							}
							rebuildReportList();
							if (reportStatusLabel != null) {
								if (failureMessage != null && !failureMessage.isBlank()) {
									reportStatusLabel.setText("Analysis error: " + failureMessage);
								} else if (stopped) {
									reportStatusLabel.setText("Analysis stopped");
								}
							}
						}
					};
					reportWorker.execute();
				}

								/**
								 * stopReportAnalysis method.
								 */
								protected void stopReportAnalysis() {
					if (reportWorker == null) {
						return;
					}
					reportStopRequested = true;
					reportWorker.cancel(true);
				}

								/**
								 * ensureEngine method.
								 *
								 * @param protocol parameter.
								 * @return return value.
								 */
								protected Engine ensureEngine(Protocol protocol) throws IOException {
					if (protocol == null) {
						throw new IOException("Engine protocol missing");
					}
					String path = protocol.getPath();
					if (engineInstance == null || engineProtocolPath == null || !engineProtocolPath.equals(path)) {
						shutdownEngineInstance();
						engineInstance = new Engine(protocol);
						engineProtocolPath = path;
					}
					return engineInstance;
				}

								/**
								 * shutdownEngineInstance method.
								 */
								protected void shutdownEngineInstance() {
					if (engineInstance == null) {
						engineProtocolPath = null;
						return;
					}
					try {
						engineInstance.close();
					} catch (Exception ignored) {
						// best-effort shutdown
					}
					engineInstance = null;
					engineProtocolPath = null;
				}

				/**
				 * restartEngine method.
				 */
				protected void restartEngine() {
					if (engineWorker != null) {
						engineRestartQueued = true;
						stopEngine();
						return;
					}
					shutdownEngineInstance();
					startEngine();
				}

				/**
				 * stopEngine method.
				 */
				protected void stopEngine() {
					engineStopRequested = true;
					if (engineWorker != null) {
						engineWorker.cancel(true);
					}
				}

				/**
				 * playEngineBest method.
				 */
				protected void playEngineBest() {
					if (engineBestMove == Move.NO_MOVE) {
						return;
					}
					if (!position.isLegalMove(engineBestMove)) {
						JOptionPane.showMessageDialog(frame, "Engine move is no longer legal.", "Engine",
								JOptionPane.WARNING_MESSAGE);
						return;
					}
					if (isVsEngineEngineTurn()) {
						playMoveAsEngine(engineBestMove);
					} else {
						playMove(engineBestMove);
					}
				}

				/**
				 * setEngineStatus method.
				 *
				 * @param status parameter.
				 */
				protected void setEngineStatus(String status) {
					String label = status == null || status.isBlank() ? "Idle" : status;
					if (engineStatusLabel != null) {
						engineStatusLabel.setText(label);
						Color bg = engineStatusBackground(label);
						Color fg = engineStatusForeground(label);
						engineStatusLabel.setBackground(bg);
						engineStatusLabel.setForeground(fg);
					}
					if (headerEngineStatus != null) {
						headerEngineStatus.setText("Engine: " + label.toLowerCase(Locale.ROOT));
					}
					refreshStatusBar();
				}

								/**
								 * engineStatusBackground method.
								 *
								 * @param status parameter.
								 * @return return value.
								 */
								protected Color engineStatusBackground(String status) {
					String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
					if (s.contains("error")) {
						return lightMode ? new Color(255, 220, 220) : new Color(120, 52, 52);
					}
					if (s.contains("running")) {
						return blend(theme.accent(), theme.surfaceAlt(), 0.15f);
					}
					if (s.contains("cached")) {
						return blend(theme.surfaceAlt(), theme.accent(), 0.12f);
					}
					if (s.contains("stopped") || s.contains("idle")) {
						return theme.surfaceAlt();
					}
					return blend(theme.surfaceAlt(), theme.border(), 0.2f);
				}

								/**
								 * engineStatusForeground method.
								 *
								 * @param status parameter.
								 * @return return value.
								 */
								protected Color engineStatusForeground(String status) {
					String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
					if (s.contains("error")) {
						return lightMode ? new Color(120, 0, 0) : new Color(255, 224, 224);
					}
					if (s.contains("running")) {
						return theme.accentText();
					}
					return theme.text();
				}

								/**
								 * toggleEngineLockPv method.
								 */
								protected void toggleEngineLockPv() {
					hideEnginePvMiniBoardPreview();
					engineLockPv = engineLockPvToggle != null && engineLockPvToggle.isSelected();
					if (engineLockPv) {
						lockedBestMoves = engineBestArea != null ? engineBestArea.getText() : null;
						lockedBestMove = engineBestMove;
						lockedAnalysis = latestAnalysisVersion == positionVersion ? latestAnalysis : null;
						updateEnginePvList(lockedAnalysis);
					} else {
						lockedBestMoves = null;
						lockedBestMove = Move.NO_MOVE;
						lockedAnalysis = null;
						if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
							if (engineBestArea != null) {
								engineBestArea.setText(formatBestMovesFromAnalysis(position, latestAnalysis, getMultiPv()));
							}
							updateEnginePvList(latestAnalysis);
						}
					}
					boardPanel.repaint();
				}

								/**
								 * copyEnginePv method.
								 */
								protected void copyEnginePv() {
					Analysis analysis = activePvAnalysis();
					if (analysis == null) {
						return;
					}
					Output out = firstAvailableOutput(analysis, getMultiPv());
					if (out == null) {
						return;
					}
					String pv = Format.formatPvMovesSan(position, out.getMoves());
					if (pv.isEmpty()) {
						pv = Format.formatPvMoves(out.getMoves());
					}
					if (!pv.isEmpty()) {
						copyText(pv);
					}
				}

				/**
				 * updateEngineStats method.
				 *
				 * @param analysis parameter.
				 */
				protected void updateEngineStats(Analysis analysis) {
					if (engineDepthValue == null || engineNodesValue == null || engineTimeValue == null || engineNpsValue == null) {
						refreshStatusBar();
						return;
					}
					if (analysis == null) {
						engineDepthValue.setText("—");
						engineNodesValue.setText("—");
						engineTimeValue.setText("—");
						engineNpsValue.setText("—");
						if (headerEngineStats != null) {
							headerEngineStats.setText("Depth —  Time —  NPS —");
						}
						refreshStatusBar();
						return;
					}
					Output out = firstAvailableOutput(analysis, getMultiPv());
					if (out == null) {
						engineDepthValue.setText("—");
						engineNodesValue.setText("—");
						engineTimeValue.setText("—");
						engineNpsValue.setText("—");
						if (headerEngineStats != null) {
							headerEngineStats.setText("Depth —  Time —  NPS —");
						}
						refreshStatusBar();
						return;
					}
					engineDepthValue.setText(out.getDepth() + "/" + out.getSelectiveDepth());
					engineNodesValue.setText(CommandSupport.formatCount(out.getNodes()));
					engineTimeValue.setText(formatMillis(out.getTime()));
					engineNpsValue.setText(CommandSupport.formatCount(out.getNodesPerSecond()));
					if (headerEngineStats != null) {
						headerEngineStats.setText("Depth " + out.getDepth() + "  Time " + formatMillis(out.getTime())
								+ "  NPS " + CommandSupport.formatCount(out.getNodesPerSecond()));
					}
					refreshStatusBar();
				}

								/**
								 * updateEnginePvList method.
								 *
								 * @param analysis parameter.
								 */
								protected void updateEnginePvList(Analysis analysis) {
					if (pvListModel == null) {
						return;
					}
					if (analysis == null || analysis.isEmpty()) {
						stopPvHoverAnimation();
						pvHoverAnimationActive = false;
						pvHoverHighlightAmount = 0f;
						if (pvHoverAnimationTimer != null && pvHoverAnimationTimer.isRunning()) {
							pvHoverAnimationTimer.stop();
						}
						clearPvHoverSelection();
						hideEnginePvMiniBoardPreview();
						clearExpandedPvRows();
						pvListModel.clear();
						refreshPvListCellHeight(java.util.Collections.emptyList());
						return;
					}
					java.util.Map<Integer, Output> selected = distinctPvSelection(analysis, getMultiPv());
					java.util.List<PvEntry> nextEntries = new java.util.ArrayList<>(selected.size());
					for (java.util.Map.Entry<Integer, Output> pvEntry : selected.entrySet()) {
						int pv = pvEntry.getKey();
						Output out = pvEntry.getValue();
						short[] pvMoves = out.getMoves();
						if (firstMove(pvMoves) == Move.NO_MOVE) {
							continue;
						}
						String moves = formatPvMovesWithClocks(position, pvMoves);
						if (moves.isEmpty()) {
							moves = Format.formatPvMoves(pvMoves);
						}
						if (moves.isBlank()) {
							continue;
						}
						String evalLabel = formatEvalLabelForWhite(out.getEvaluation(), position.isWhiteTurn());
						String depthLabel = out.getDepth() + "/" + out.getSelectiveDepth();
						nextEntries.add(new PvEntry(pv, moves, evalLabel, depthLabel));
					}
					if (nextEntries.isEmpty()) {
						// Avoid flicker: keep previous PV lines while engine is still searching.
						if (engineWorker != null && !engineStopRequested && pvListModel.getSize() > 0) {
							refreshPvListCellHeight(null);
							return;
						}
						stopPvHoverAnimation();
						pvHoverAnimationActive = false;
						pvHoverHighlightAmount = 0f;
						if (pvHoverAnimationTimer != null && pvHoverAnimationTimer.isRunning()) {
							pvHoverAnimationTimer.stop();
						}
						clearPvHoverSelection();
						hideEnginePvMiniBoardPreview();
						clearExpandedPvRows();
						pvListModel.clear();
						refreshPvListCellHeight(java.util.Collections.emptyList());
						return;
					}
					retainExpandedPvRows(nextEntries);
					stopPvHoverAnimation();
					pvHoverAnimationActive = false;
					pvHoverHighlightAmount = 0f;
					if (pvHoverAnimationTimer != null && pvHoverAnimationTimer.isRunning()) {
						pvHoverAnimationTimer.stop();
					}
					clearPvHoverSelection();
					hideEnginePvMiniBoardPreview();
					pvListModel.clear();
					for (PvEntry entry : nextEntries) {
						pvListModel.addElement(entry);
					}
					refreshPvListCellHeight(nextEntries);
				}

								/**
								 * formatPvMovesWithClocks method.
								 *
								 * @param start parameter.
								 * @param moves parameter.
								 * @return return value.
								 */
								protected String formatPvMovesWithClocks(Position start, short[] moves) {
					if (start == null || moves == null || moves.length == 0) {
						return "";
					}
					Position cursor = start.copyOf();
					StringBuilder sb = new StringBuilder(moves.length * 8);
					boolean firstToken = true;
					for (short move : moves) {
						if (move == Move.NO_MOVE) {
							break;
						}
						if (!cursor.isLegalMove(move)) {
							break;
						}
						String san = Format.safeSan(cursor, move);
						if (sb.length() > 0) {
							sb.append(' ');
						}
						if (cursor.isWhiteTurn()) {
							sb.append(cursor.getFullMove()).append('.').append(displaySan(san));
						} else if (firstToken) {
							sb.append(cursor.getFullMove()).append("...").append(displaySan(san));
						} else {
							sb.append(displaySan(san));
						}
						cursor.play(move);
						firstToken = false;
					}
					return sb.toString();
				}

								/**
								 * clearEnginePvList method.
								 */
								protected void clearEnginePvList() {
					stopPvHoverAnimation();
					pvHoverAnimationActive = false;
					pvHoverHighlightAmount = 0f;
					if (pvHoverAnimationTimer != null && pvHoverAnimationTimer.isRunning()) {
						pvHoverAnimationTimer.stop();
					}
					clearPvHoverSelection();
					hideEnginePvMiniBoardPreview();
					if (pvListModel != null) {
						pvListModel.clear();
					}
				}

								/**
								 * pvIndexAtPoint method.
								 *
								 * @param area parameter.
								 * @param point parameter.
								 * @return return value.
								 */
								protected int pvIndexAtPoint(JTextArea area, Point point) {
					if (area == null) {
						return -1;
					}
					try {
						int pos = area.viewToModel2D(point);
						int line = area.getLineOfOffset(pos);
						int start = area.getLineStartOffset(line);
						int end = area.getLineEndOffset(line);
						String text = area.getText(start, Math.max(0, end - start)).trim();
						int dot = text.indexOf('.');
						if (dot <= 0) {
							return -1;
						}
						String num = text.substring(0, dot).trim();
						return Integer.parseInt(num);
					} catch (Exception ex) {
						return -1;
					}
				}

								/**
								 * previewEnginePv method.
								 *
								 * @param pv parameter.
								 */
								protected void previewEnginePv(int pv) {
					previewEnginePv(pv, Integer.MAX_VALUE);
				}

								/**
								 * previewEnginePv method.
								 *
								 * @param pv parameter.
								 * @param plyCount parameter.
								 */
								protected void previewEnginePv(int pv, int plyCount) {
					Analysis analysis = activePvAnalysis();
					if (analysis == null) {
						return;
					}
					Output out = outputForDisplayedPv(analysis, pv);
					if (out == null) {
						return;
					}
					boardPanel.setPreviewLine(out.getMoves(), Math.max(1, plyCount));
				}

								/**
								 * playEnginePv method.
								 *
								 * @param pv parameter.
								 */
								protected void playEnginePv(int pv) {
					playEnginePv(pv, 1);
				}

								/**
								 * playEnginePv method.
								 *
								 * @param pv parameter.
								 * @param plyCount parameter.
								 */
								protected void playEnginePv(int pv, int plyCount) {
					Analysis analysis = activePvAnalysis();
					if (analysis == null) {
						return;
					}
					Output out = outputForDisplayedPv(analysis, pv);
					if (out == null) {
						return;
					}
					int limit = Math.max(1, plyCount);
					int played = 0;
					for (short move : out.getMoves()) {
						if (move == Move.NO_MOVE || played >= limit) {
							break;
						}
						if (!position.isLegalMove(move)) {
							break;
						}
						playMove(move);
						played++;
					}
				}

								/**
								 * activePvAnalysis method.
								 *
								 * @return return value.
								 */
								protected Analysis activePvAnalysis() {
					if (engineLockPv) {
						return lockedAnalysis;
					}
					if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
						return latestAnalysis;
					}
					return null;
				}

								/**
								 * showEnginePvMiniBoardPreview method.
								 *
								 * @param pv parameter.
								 * @param plyCount parameter.
								 * @param screenPoint parameter.
								 */
								protected void showEnginePvMiniBoardPreview(int pv, int plyCount, Point screenPoint) {
					if (screenPoint == null) {
						hideEnginePvMiniBoardPreview();
						return;
					}
					int limit = Math.max(1, plyCount);
					Position previewPosition = buildEnginePvPreviewPosition(pv, limit);
					if (previewPosition == null) {
						hideEnginePvMiniBoardPreview();
						return;
					}
					ensurePvPreviewWindow();
					if (pvPreviewBoardLabel == null || pvPreviewWindow == null) {
						return;
					}
					boolean needsRefresh = !hasRenderablePreviewIcon()
							|| pvPreviewCachedPv != pv
							|| pvPreviewCachedPly != limit
							|| pvPreviewCachedVersion != positionVersion
							|| pvPreviewCachedHue != boardHueDegrees
							|| pvPreviewCachedBrightness != boardBrightness
							|| pvPreviewCachedSaturation != boardSaturation
							|| pvPreviewCachedWhiteDown != whiteDown;
					if (needsRefresh) {
						BufferedImage image = renderMiniBoard(previewPosition, PV_PREVIEW_BOARD_SIZE);
						if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
							hideEnginePvMiniBoardPreview();
							return;
						}
						pvPreviewBoardLabel.setIcon(new ImageIcon(image));
						pvPreviewBoardLabel.revalidate();
						pvPreviewBoardLabel.repaint();
						pvPreviewCachedPv = pv;
						pvPreviewCachedPly = limit;
						pvPreviewCachedVersion = positionVersion;
						pvPreviewCachedHue = boardHueDegrees;
						pvPreviewCachedBrightness = boardBrightness;
						pvPreviewCachedSaturation = boardSaturation;
						pvPreviewCachedWhiteDown = whiteDown;
						pvPreviewCachedHistoryFen = null;
						pvPreviewWindow.pack();
					}
					positionPvPreviewWindow(screenPoint);
					if (!pvPreviewWindow.isVisible()) {
						pvPreviewWindow.setVisible(true);
					}
				}

								/**
								 * showHistoryMiniBoardPreview method.
								 *
								 * @param previewPosition parameter.
								 * @param screenPoint parameter.
								 */
								protected void showHistoryMiniBoardPreview(Position previewPosition, Point screenPoint) {
					if (screenPoint == null || previewPosition == null) {
						hideEnginePvMiniBoardPreview();
						return;
					}
					ensurePvPreviewWindow();
					if (pvPreviewBoardLabel == null || pvPreviewWindow == null) {
						return;
					}
					String historyFen = previewPosition.toString();
					BufferedImage image = renderMiniBoard(previewPosition, PV_PREVIEW_BOARD_SIZE);
					if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
						hideEnginePvMiniBoardPreview();
						return;
					}
					pvPreviewBoardLabel.setIcon(new ImageIcon(image));
					pvPreviewBoardLabel.revalidate();
					pvPreviewBoardLabel.repaint();
					pvPreviewCachedPv = Integer.MIN_VALUE;
					pvPreviewCachedPly = -1;
					pvPreviewCachedVersion = positionVersion;
					pvPreviewCachedHue = boardHueDegrees;
					pvPreviewCachedBrightness = boardBrightness;
					pvPreviewCachedSaturation = boardSaturation;
					pvPreviewCachedWhiteDown = whiteDown;
					pvPreviewCachedHistoryFen = historyFen;
					pvPreviewWindow.pack();
					positionPvPreviewWindow(screenPoint);
					if (!pvPreviewWindow.isVisible()) {
						pvPreviewWindow.setVisible(true);
					}
				}

								/**
								 * previewHistoryHover method.
								 *
								 * @param idx parameter.
								 * @param screenPoint parameter.
								 */
								protected void previewHistoryHover(int idx, Point screenPoint) {
					if (idx < 0) {
						clearHoverPreviews();
						return;
					}
					Position previewPosition = previewPositionAtHistoryIndex(idx);
					short move = resolveHistoryMove(idx);
					if (boardPanel != null) {
						boardPanel.setPreviewPosition(previewPosition, move);
					}
					showHistoryMiniBoardPreview(previewPosition, screenPoint);
				}

								/**
								 * previewNodeHover method.
								 *
								 * @param node parameter.
								 * @param screenPoint parameter.
								 */
								protected void previewNodeHover(PgnNode node, Point screenPoint) {
					Position previewPosition = node != null && node.positionAfter != null ? node.positionAfter.copyOf() : null;
					short move = node != null ? node.move : Move.NO_MOVE;
					if (boardPanel != null) {
						boardPanel.setPreviewPosition(previewPosition, move);
					}
					showHistoryMiniBoardPreview(previewPosition, screenPoint);
				}

								/**
								 * hideEnginePvMiniBoardPreview method.
								 */
								protected void hideEnginePvMiniBoardPreview() {
					if (pvPreviewWindow != null && pvPreviewWindow.isVisible()) {
						pvPreviewWindow.setVisible(false);
					}
					resetPvPreviewCache();
				}

								/**
								 * maybeHidePvPreviewOnMouseExit method.
								 */
								private void maybeHidePvPreviewOnMouseExit() {
					if (pvPreviewWindow == null || !pvPreviewWindow.isVisible()) {
						return;
					}
					java.awt.PointerInfo pointerInfo = MouseInfo.getPointerInfo();
					if (pointerInfo == null || !pvPreviewWindow.getBounds().contains(pointerInfo.getLocation())) {
						hideEnginePvMiniBoardPreview();
					}
				}

								/**
								 * ensurePvPreviewWindow method.
								 */
								private void ensurePvPreviewWindow() {
					if (pvPreviewWindow == null) {
						pvPreviewWindow = new JWindow(frame);
						pvPreviewWindow.setFocusableWindowState(false);
						MouseAdapter autoHideListener = new MouseAdapter() {
														/**
							 * Handles mouse exited.
							 * @param e e value
							 */
@Override
							public void mouseExited(MouseEvent e) {
								maybeHidePvPreviewOnMouseExit();
							}
						};
						pvPreviewWindow.addMouseListener(autoHideListener);
						JPanel container = new JPanel(new BorderLayout());
						container.addMouseListener(autoHideListener);
						container.setBorder(BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(theme.border()),
								BorderFactory.createEmptyBorder(4, 4, 4, 4)));
						pvPreviewBoardLabel = new JLabel();
						pvPreviewBoardLabel.setOpaque(true);
						pvPreviewBoardLabel.addMouseListener(autoHideListener);
						container.add(pvPreviewBoardLabel, BorderLayout.CENTER);
						pvPreviewWindow.setContentPane(container);
						resetPvPreviewCache();
					}
					if (pvPreviewWindow.getContentPane() instanceof JPanel panel) {
						panel.setBackground(theme.surface());
						panel.setBorder(BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(theme.border()),
								BorderFactory.createEmptyBorder(4, 4, 4, 4)));
					}
					if (pvPreviewBoardLabel != null) {
						pvPreviewBoardLabel.setBackground(theme.editor());
					}
				}

								/**
								 * positionPvPreviewWindow method.
								 *
								 * @param screenPoint parameter.
								 */
								private void positionPvPreviewWindow(Point screenPoint) {
					if (pvPreviewWindow == null) {
						return;
					}
					Dimension size = pvPreviewWindow.getSize();
					Rectangle screen = frame != null && frame.getGraphicsConfiguration() != null
							? frame.getGraphicsConfiguration().getBounds()
							: new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
					int x = screenPoint.x + PV_PREVIEW_OFFSET;
					int y = screenPoint.y + PV_PREVIEW_OFFSET;
					int maxX = screen.x + screen.width - size.width - 8;
					int maxY = screen.y + screen.height - size.height - 8;
					if (x > maxX) {
						x = screenPoint.x - size.width - PV_PREVIEW_OFFSET;
					}
					if (y > maxY) {
						y = maxY;
					}
					x = Math.max(screen.x + 8, Math.min(x, maxX));
					y = Math.max(screen.y + 8, Math.min(y, maxY));
					pvPreviewWindow.setLocation(x, y);
				}

								/**
								 * buildEnginePvPreviewPosition method.
								 *
								 * @param pv parameter.
								 * @param maxPlies parameter.
								 * @return return value.
								 */
								private Position buildEnginePvPreviewPosition(int pv, int maxPlies) {
					Analysis analysis = activePvAnalysis();
					if (analysis == null || position == null) {
						return null;
					}
					Output out = outputForDisplayedPv(analysis, pv);
					if (out == null) {
						return null;
					}
					short[] moves = out.getMoves();
					if (moves == null || moves.length == 0) {
						return null;
					}
					Position preview = position.copyOf();
					int applied = 0;
					for (short move : moves) {
						if (move == Move.NO_MOVE || applied >= maxPlies) {
							break;
						}
						if (!preview.isLegalMove(move)) {
							break;
						}
						preview.play(move);
						applied++;
					}
					return applied > 0 ? preview : null;
				}

								/**
								 * pvPlyAtPoint method.
								 *
								 * @param entry parameter.
								 * @param listPoint parameter.
								 * @param cellBounds parameter.
								 * @return return value.
								 */
								protected int pvPlyAtPoint(PvEntry entry, Point listPoint, Rectangle cellBounds) {
					if (entry == null || listPoint == null || cellBounds == null) {
						return 1;
					}
					String movesText = entry.moves() == null ? "" : entry.moves().trim();
					if (movesText.isEmpty()) {
						return 1;
					}
					Font base = figurineDisplayFont(scaleFont(theme.bodyFont()));
					java.awt.FontMetrics baseFm = pvList != null ? pvList.getFontMetrics(base) : null;
					if (baseFm == null) {
						return 1;
					}
					int wrapWidth = computePvMovesWidth(pvList, entry);
					int indexSize = Math.round(26 * getUiScale());
					int toggleSize = Math.max(12, Math.round(14 * getUiScale()));
					int leftPad = 6;
					int hgap = 8;
					int movesStartX = cellBounds.x + leftPad + toggleSize + 4 + indexSize + hgap;
					String displayText = isPvExpanded(entry)
							? normalizePvText(movesText)
							: collapsePvText(movesText, wrapWidth, baseFm);
					javax.swing.JTextArea probe = new javax.swing.JTextArea(displayText);
					probe.setFont(base);
					probe.setLineWrap(isPvExpanded(entry));
					probe.setWrapStyleWord(true);
					probe.setSize(new Dimension(wrapWidth, Short.MAX_VALUE));
					int relX = Math.max(0, Math.min(wrapWidth - 1, listPoint.x - movesStartX));
					int relY = Math.max(0, listPoint.y - cellBounds.y - 4);
					int offset = probe.viewToModel2D(new Point(relX, relY));
					if (offset < 0) {
						return 1;
					}
					int tokenIndex = 0;
					boolean inToken = false;
					int limit = Math.min(offset, displayText.length());
					for (int i = 0; i < limit; i++) {
						char ch = displayText.charAt(i);
						boolean ws = Character.isWhitespace(ch);
						if (!ws && !inToken) {
							tokenIndex++;
							inToken = true;
						} else if (ws) {
							inToken = false;
						}
					}
					return Math.max(1, tokenIndex + 1);
				}

								/**
								 * renderMiniBoard method.
								 *
								 * @param renderPos parameter.
								 * @param targetSize parameter.
								 * @return return value.
								 */
								private BufferedImage renderMiniBoard(Position renderPos, int targetSize) {
					if (renderPos == null || targetSize <= 0) {
						return null;
					}
					try {
						int tile = Math.max(12, targetSize / 8);
						int size = tile * 8;
						BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
						Graphics2D g2 = image.createGraphics();
						try {
							applyRenderHints(g2);
							Color light = boardColor(GuiWindowBase.LICHESS_LIGHT);
							Color dark = boardColor(GuiWindowBase.LICHESS_DARK);
							light = new Color(light.getRed(), light.getGreen(), light.getBlue());
							dark = new Color(dark.getRed(), dark.getGreen(), dark.getBlue());
							byte[] board = renderPos.getBoard();
							if (board == null || board.length < 64) {
								return null;
							}
							for (int screenRank = 0; screenRank < 8; screenRank++) {
								for (int screenFile = 0; screenFile < 8; screenFile++) {
									int boardFile = whiteDown ? screenFile : 7 - screenFile;
									int boardRank = whiteDown ? 7 - screenRank : screenRank;
									boolean isLight = ((boardFile + boardRank) & 1) == 1;
									g2.setColor(isLight ? light : dark);
									g2.fillRect(screenFile * tile, screenRank * tile, tile, tile);
								}
							}
							int pieceSize = Math.max(8, Math.round(tile * GuiWindowBase.PIECE_SCALE));
							int pad = (tile - pieceSize) / 2;
							for (int square = 0; square < 64; square++) {
								byte piece = board[square];
								if (Piece.isEmpty(piece)) {
									continue;
								}
								BufferedImage pieceImage = pieceImage(piece);
								if (pieceImage == null) {
									continue;
								}
								int file = Field.getX((byte) square);
								int rank = Field.getY((byte) square);
								int screenFile = whiteDown ? file : 7 - file;
								int screenRank = whiteDown ? 7 - rank : rank;
								int x = screenFile * tile + pad;
								int y = screenRank * tile + pad;
								g2.drawImage(pieceImage, x, y, pieceSize, pieceSize, null);
							}
							g2.setColor(theme.border());
							g2.drawRect(0, 0, size - 1, size - 1);
						} finally {
							g2.dispose();
						}
						return image;
					} catch (RuntimeException ex) {
						return null;
					}
				}

								/**
								 * formatBestMovesFromAnalysis method.
								 *
								 * @param pos parameter.
								 * @param analysis parameter.
								 * @param multipv parameter.
								 * @return return value.
								 */
								protected String formatBestMovesFromAnalysis(Position pos, Analysis analysis, int multipv) {
					if (analysis == null || analysis.isEmpty()) {
						return "";
					}
					StringBuilder bestMoves = new StringBuilder();
					java.util.Map<Integer, Output> selected = distinctPvSelection(analysis, multipv);
					boolean firstLine = true;
					for (java.util.Map.Entry<Integer, Output> pvEntry : selected.entrySet()) {
						int pv = pvEntry.getKey();
						Output out = pvEntry.getValue();
						short pvBest = firstMove(out.getMoves());
						if (pvBest == Move.NO_MOVE) {
							continue;
						}
						if (!firstLine) {
							bestMoves.append('\n');
						}
						String bestSan = Format.safeSan(pos, pvBest);
						String evalLabel = formatEvalLabelForWhite(out.getEvaluation(), pos.isWhiteTurn());
						bestMoves.append(pv).append(". ").append(displaySan(bestSan));
						if (!evalLabel.isBlank()) {
							bestMoves.append("  ").append(evalLabel);
						}
						firstLine = false;
					}
					return bestMoves.toString().trim();
				}

								/**
								 * buildEngineUpdate method.
								 *
								 * @param pos parameter.
								 * @param analysis parameter.
								 * @param multipv parameter.
								 * @param version parameter.
								 * @return return value.
								 */
								protected EngineUpdate buildEngineUpdate(Position pos, Analysis analysis, int multipv, long version) {
					if (analysis == null || analysis.isEmpty()) {
						return new EngineUpdate(version, "analysis: (no output)", "", null, null, null,
								Move.NO_MOVE, "Running", false);
					}
					StringBuilder sb = new StringBuilder();
					StringBuilder bestMoves = new StringBuilder();
					short bestMove = Move.NO_MOVE;
					Evaluation bestEval = null;
					Chances bestChances = null;
					java.util.Map<Integer, Output> selected = distinctPvSelection(analysis, multipv);
					Output top = selected.isEmpty() ? null : selected.values().iterator().next();
					if (top != null) {
						bestMove = firstMove(top.getMoves());
						bestEval = top.getEvaluation();
						bestChances = top.getChances();
					}
					boolean firstBlock = true;
					boolean firstBestLine = true;
					for (java.util.Map.Entry<Integer, Output> pvEntry : selected.entrySet()) {
						int pv = pvEntry.getKey();
						Output out = pvEntry.getValue();
						short pvBest = firstMove(out.getMoves());
						if (pvBest == Move.NO_MOVE) {
							continue;
						}
						if (!firstBlock) {
							sb.append('\n');
						}
						String eval = Format.formatEvaluation(out.getEvaluation());
						String wdl = Format.formatChances(out.getChances());
						String bound = Format.formatBound(out.getBound());
						String pvLine = displaySanLine(Format.formatPvMovesSan(pos, out.getMoves()));
						sb.append("PV").append(pv).append("  eval: ").append(eval);
						if (!"-".equals(bound)) {
							sb.append("  ").append(bound);
						}
						if (!"-".equals(wdl)) {
							sb.append("  ").append(wdl);
						}
						sb.append('\n');
						if (pvBest != Move.NO_MOVE) {
							sb.append("  best: ").append(Move.toString(pvBest)).append(" (")
									.append(displaySan(Format.safeSan(pos, pvBest))).append(")").append('\n');
						}
						sb.append("  depth: ").append(out.getDepth()).append(" sel ").append(out.getSelectiveDepth()).append('\n');
						sb.append("  nodes: ").append(CommandSupport.formatCount(out.getNodes()))
								.append("  nps: ").append(CommandSupport.formatCount(out.getNodesPerSecond()))
								.append("  time: ").append(formatMillis(out.getTime())).append('\n');
						if (!pvLine.isEmpty()) {
							sb.append("  line: ").append(pvLine).append('\n');
						}

						if (!firstBestLine) {
							bestMoves.append('\n');
						}
						String bestSan = Format.safeSan(pos, pvBest);
						String evalLabel = formatEvalLabelForWhite(out.getEvaluation(), pos.isWhiteTurn());
						bestMoves.append(pv).append(". ").append(displaySan(bestSan));
						if (!evalLabel.isBlank()) {
							bestMoves.append("  ").append(evalLabel);
						}
						firstBestLine = false;
						firstBlock = false;
					}
					String output = sb.length() == 0 ? "analysis: (no pv moves yet)" : sb.toString().trim();
					return new EngineUpdate(version, output, bestMoves.toString().trim(), bestEval, bestChances, analysis,
							bestMove, "Running", false);
				}

								/**
				 * Handles refresh figurine san engine views.
				 */
@Override
				/**
				 * refreshFigurineSanEngineViews method.
				 */
				protected void refreshFigurineSanEngineViews() {
					Analysis analysis = null;
					if (engineLockPv) {
						analysis = lockedAnalysis;
					} else if (latestAnalysis != null && latestAnalysisVersion == positionVersion) {
						analysis = latestAnalysis;
					}
					if (engineBestArea != null) {
						if (analysis != null) {
							String text = formatBestMovesFromAnalysis(position, analysis, getMultiPv());
							engineBestArea.setText(text);
							if (engineLockPv) {
								lockedBestMoves = text;
							}
						} else if (!engineLockPv) {
							engineBestArea.setText("");
						}
					}
					updateEnginePvList(analysis);
				}

								/**
								 * firstAvailableOutput method.
								 *
								 * @param analysis parameter.
								 * @param multipv parameter.
								 * @return return value.
								 */
								protected Output firstAvailableOutput(Analysis analysis, int multipv) {
					java.util.Map<Integer, Output> selected = distinctPvSelection(analysis, multipv);
					return selected.isEmpty() ? null : selected.values().iterator().next();
				}

								/**
								 * bestOutputWithMoves method.
								 *
								 * @param analysis parameter.
								 * @param pv parameter.
								 * @return return value.
								 */
								protected Output bestOutputWithMoves(Analysis analysis, int pv) {
					return bestOutputWithMoves(analysis, pv, java.util.Collections.emptySet());
				}

								/**
								 * bestOutputWithMoves method.
								 *
								 * @param analysis parameter.
								 * @param pv parameter.
								 * @param excludedFirstMoves parameter.
								 * @return return value.
								 */
								protected Output bestOutputWithMoves(Analysis analysis, int pv, java.util.Set<Short> excludedFirstMoves) {
					if (analysis == null || pv < 1) {
						return null;
					}
					Output direct = analysis.getBestOutput(pv);
					short directFirst = direct == null ? Move.NO_MOVE : firstMove(direct.getMoves());
					if (direct != null
							&& directFirst != Move.NO_MOVE
							&& (excludedFirstMoves == null || !excludedFirstMoves.contains(directFirst))) {
						return direct;
					}
					Output best = null;
					for (Output out : analysis.getOutputs()) {
						if (out == null || out.getPrincipalVariation() != pv) {
							continue;
						}
						short root = firstMove(out.getMoves());
						if (root == Move.NO_MOVE) {
							continue;
						}
						if (excludedFirstMoves != null && excludedFirstMoves.contains(root)) {
							continue;
						}
						if (best == null
								|| out.getDepth() > best.getDepth()
								|| (out.getDepth() == best.getDepth() && out.getSelectiveDepth() > best.getSelectiveDepth())) {
							best = out;
						}
					}
					return best;
				}

																/**
								 * Handles distinct pv selection.
								 * @param analysis analysis value
								 * @param multipv multipv value
								 * @return computed value
								 */
protected java.util.Map<Integer, Output> distinctPvSelection(Analysis analysis, int multipv) {
					java.util.Map<Integer, Output> selected = new java.util.LinkedHashMap<>();
					if (analysis == null || analysis.isEmpty()) {
						return selected;
					}
					int pivots = Math.max(1, Math.min(multipv, analysis.getPivots()));
					java.util.Set<Short> usedRootMoves = new java.util.HashSet<>();
					for (int pv = 1; pv <= pivots; pv++) {
						Output out = bestOutputWithMoves(analysis, pv, usedRootMoves);
						if (out == null) {
							continue;
						}
						short root = firstMove(out.getMoves());
						if (root == Move.NO_MOVE || usedRootMoves.contains(root)) {
							continue;
						}
						usedRootMoves.add(root);
						selected.put(pv, out);
					}
					return selected;
				}

								/**
								 * outputForDisplayedPv method.
								 *
								 * @param analysis parameter.
								 * @param pv parameter.
								 * @return return value.
								 */
								protected Output outputForDisplayedPv(Analysis analysis, int pv) {
					if (pv < 1) {
						return null;
					}
					return distinctPvSelection(analysis, getMultiPv()).get(pv);
				}

								/**
								 * refreshPvListCellHeight method.
								 *
								 * @param entries parameter.
								 */
								protected void refreshPvListCellHeight(java.util.List<PvEntry> entries) {
					if (pvList == null) {
						return;
					}
					java.util.List<PvEntry> sample = entries;
					if (sample == null) {
						sample = new java.util.ArrayList<>(pvListModel.getSize());
						for (int i = 0; i < pvListModel.getSize(); i++) {
							PvEntry e = pvListModel.getElementAt(i);
							if (e != null) {
								sample.add(e);
							}
						}
					}
					int baseRow = Math.max(28, Math.round(30 * getUiScale()));
					if (sample.isEmpty()) {
						pvList.setFixedCellHeight(-1);
						refreshPvListViewportHeight(baseRow, 0, 0);
						return;
					}
					Font base = figurineDisplayFont(scaleFont(theme.bodyFont()));
					java.awt.FontMetrics baseFm = pvList.getFontMetrics(base);
					JTextArea probe = new JTextArea();
					probe.setEditable(false);
					probe.setFont(base);
					int totalRowsHeight = 0;
					int maxRowHeight = baseRow;
					for (PvEntry entry : sample) {
						String raw = entry == null ? "" : entry.moves();
						int movesWidth = computePvMovesWidth(pvList, entry);
						boolean expanded = isPvExpanded(entry);
						String text = expanded ? normalizePvText(raw) : collapsePvText(raw, movesWidth, baseFm);
						probe.setText(text);
						probe.setLineWrap(expanded);
						probe.setWrapStyleWord(true);
						probe.setSize(new Dimension(movesWidth, Short.MAX_VALUE));
						int textHeight = Math.max(baseFm.getHeight(), probe.getPreferredSize().height);
						int verticalPadding = Math.max(6, Math.round(8 * getUiScale()));
						int rowHeight = Math.max(baseRow, textHeight + verticalPadding);
						maxRowHeight = Math.max(maxRowHeight, rowHeight);
						totalRowsHeight += rowHeight;
					}
					pvList.setFixedCellHeight(-1);
					refreshPvListViewportHeight(maxRowHeight, sample.size(), totalRowsHeight);
					pvList.revalidate();
					pvList.repaint();
				}

								/**
								 * refreshPvListViewportHeight method.
								 *
								 * @param rowHeight parameter.
								 * @param entryCount parameter.
								 * @param totalRowsHeight parameter.
								 */
								private void refreshPvListViewportHeight(int rowHeight, int entryCount, int totalRowsHeight) {
					if (pvListScroll == null) {
						return;
					}
					int minHeight = Math.round(140 * getUiScale());
					int maxHeight = Math.round(420 * getUiScale());
					int visibleRows = Math.max(1, Math.min(6, entryCount));
					int contentHeight = totalRowsHeight > 0 ? totalRowsHeight : (rowHeight * visibleRows);
					int targetHeight = Math.min(contentHeight, rowHeight * visibleRows) + Math.round(10 * getUiScale());
					int height = Math.max(minHeight, Math.min(maxHeight, targetHeight));
					Dimension pref = pvListScroll.getPreferredSize();
					int width = pref != null && pref.width > 0 ? pref.width : Math.round(320 * getUiScale());
					if (pref == null || pref.height != height || pref.width != width) {
						pvListScroll.setPreferredSize(new Dimension(width, height));
						pvListScroll.revalidate();
					}
				}

								/**
								 * firstMove method.
								 *
								 * @param moves parameter.
								 * @return return value.
								 */
								protected short firstMove(short[] moves) {
					if (moves == null) {
						return Move.NO_MOVE;
					}
					for (short move : moves) {
						if (move != Move.NO_MOVE) {
							return move;
						}
					}
					return Move.NO_MOVE;
				}

								/**
								 * parseLongField method.
								 *
								 * @param field parameter.
								 * @param def parameter.
								 * @return return value.
								 */
								protected long parseLongField(JTextField field, long def) {
					try {
						long value = Long.parseLong(field.getText().trim());
						return Math.max(1L, value);
					} catch (NumberFormatException ex) {
						return def;
					}
				}

								/**
								 * getMultiPv method.
								 *
								 * @return return value.
								 */
								protected int getMultiPv() {
					Object selected = engineMultiPvBox.getSelectedItem();
					if (selected instanceof Integer) {
						return Math.max(1, (Integer) selected);
					}
					return 1;
				}

								/**
								 * loadProtocol method.
								 *
								 * @param pathText parameter.
								 * @return return value.
								 */
								protected ProtocolLoad loadProtocol(String pathText) {
					String path = (pathText == null || pathText.isBlank()) ? Config.getProtocolPath() : pathText.trim();
					try {
						String toml = Files.readString(Paths.get(path));
						Protocol protocol = new Protocol().fromToml(toml);
						String[] errors = protocol.collectValidationErrors();
						if (!protocol.assertValid()) {
							StringBuilder sb = new StringBuilder("Protocol is missing required values:\\n");
							for (String err : errors) {
								sb.append("  - ").append(err).append("\n");
							}
							return new ProtocolLoad(null, sb.toString().trim());
						}
						if (errors.length > 0) {
							StringBuilder sb = new StringBuilder("Protocol warnings:\\n");
							for (String err : errors) {
								sb.append("  - ").append(err).append("\n");
							}
							return new ProtocolLoad(protocol, sb.toString().trim());
						}
						return new ProtocolLoad(protocol, null);
					} catch (Exception ex) {
						return new ProtocolLoad(null, "Failed to load protocol: " + ex.getMessage());
					}
				}

								/**
								 * engineConfigKey method.
								 *
								 * @param protocol parameter.
								 * @param nodes parameter.
								 * @param duration parameter.
								 * @param multipv parameter.
								 * @param wdl parameter.
								 * @param endless parameter.
								 * @return return value.
								 */
								protected String engineConfigKey(Protocol protocol, long nodes, long duration, int multipv, boolean wdl,
						boolean endless) {
					String path = protocol == null ? "" : String.valueOf(protocol.getPath());
					return path + "|" + nodes + "|" + duration + "|" + multipv + "|" + wdl + "|" + endless;
				}

								/**
								 * engineCacheKey method.
								 *
								 * @param configKey parameter.
								 * @param pos parameter.
								 * @return return value.
								 */
								protected String engineCacheKey(String configKey, Position pos) {
					return configKey + "|" + pos.toString();
				}

								/**
								 * cachedUpdate method.
								 *
								 * @param version parameter.
								 * @param cached parameter.
								 * @return return value.
								 */
								protected EngineUpdate cachedUpdate(long version, EngineCacheEntry cached) {
					return new EngineUpdate(version, cached.output(), cached.bestMoves(), cached.eval(), cached.chances(),
							cached.analysis(), cached.bestMove(), "Cached", false);
				}

								/**
								 * sleepQuietly method.
								 *
								 * @param millis parameter.
								 */
								protected void sleepQuietly(long millis) {
					try {
						Thread.sleep(millis);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}

								/**
								 * formatMillis method.
								 *
								 * @param millis parameter.
								 * @return return value.
								 */
								protected String formatMillis(long millis) {
					if (millis < 1_000L) {
						return millis + "ms";
					}
					if (millis < 60_000L) {
						return String.format(java.util.Locale.ROOT, "%.1fs", millis / 1000.0);
					}
					long seconds = millis / 1000L;
					long minutes = seconds / 60L;
					long remSeconds = seconds % 60L;
					return String.format(java.util.Locale.ROOT, "%dm%02ds", minutes, remSeconds);
				}

								/**
								 * buildJavaCommand method.
								 *
								 * @param command parameter.
								 * @param args parameter.
								 * @return return value.
								 */
								protected List<String> buildJavaCommand(String command, List<String> args) {
					String javaHome = System.getProperty("java.home");
					if (javaHome == null) {
						return null;
					}
					Path javaBin = Paths.get(javaHome, "bin", "java");
					if (!Files.exists(javaBin)) {
						return null;
					}
					Path codeSource;
					try {
						codeSource = Paths.get(GuiWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
					} catch (Exception ex) {
						return null;
					}

					List<String> cmd = new ArrayList<>();
					cmd.add(javaBin.toString());
					if (Files.isRegularFile(codeSource) && codeSource.toString().endsWith(".jar")) {
						cmd.add("-jar");
						cmd.add(codeSource.toString());
					} else {
						cmd.add("-cp");
						cmd.add(codeSource.toString());
						cmd.add("application.Main");
					}
					cmd.add(command);
					cmd.addAll(args);
					return cmd;
				}

				/**
				 * splitArgs method.
				 *
				 * @param input parameter.
				 * @return return value.
				 */
				protected List<String> splitArgs(String input) {
					List<String> out = new ArrayList<>();
					if (input == null || input.isBlank()) {
						return out;
					}
					StringBuilder current = new StringBuilder();
					boolean inQuotes = false;
					char quote = 0;
					for (int i = 0; i < input.length(); i++) {
						char c = input.charAt(i);
						if (inQuotes) {
							if (c == quote) {
								inQuotes = false;
								continue;
							}
							if (c == '\\' && i + 1 < input.length()) {
								current.append(input.charAt(i + 1));
								i++;
								continue;
							}
							current.append(c);
							continue;
						}
						if (c == '"' || c == '\'') {
							inQuotes = true;
							quote = c;
							continue;
						}
						if (Character.isWhitespace(c)) {
							if (current.length() > 0) {
								out.add(current.toString());
								current.setLength(0);
							}
							continue;
						}
						current.append(c);
					}
					if (current.length() > 0) {
						out.add(current.toString());
					}
					return out;
				}
}
