package application.gui.workbench.window;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.showConfirmDialog;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.tabbedPane;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.trimmed;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.withTooltip;
import static application.gui.workbench.ui.SwingTasks.runAsync;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ButtonGroup;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import application.Config;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Generator;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public abstract class WindowEngineLayer extends WindowBoardLayer {
    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Background worker that owns the live external-engine process.
     */
    protected LiveEngineWorker liveEngineWorker;

    /**
     * Lock guarding live-analysis request handoff to the worker.
     */
    protected final Object liveAnalysisLock = new Object();

    /**
     * Latest live-analysis request consumed by the worker.
     */
    protected LiveAnalysisRequest liveAnalysisRequest;

    /**
     * Monotonic live-analysis request id.
     */
    protected long liveAnalysisRequestId;

    /**
     * Whether a live-analysis failure has already been surfaced for the current
     * enabled session.
     */
    protected boolean liveAnalysisFailureLogged;

    /**
     * Max live-analysis idle time before the engine is considered stalled.
     */
    protected static final long LIVE_ANALYSIS_STALL_TIMEOUT_MS = 10_000L;

    /**
     * Minimum interval between live-analysis UI updates.
     */
    protected static final long LIVE_ANALYSIS_UPDATE_INTERVAL_MS = 80L;

    @Override
    protected boolean hasLiveEngineWorker() {
        return liveEngineWorker != null;
    }

    /**
     * Updates the current position from the FEN field.
     */
    protected void setPositionFromField() {
        startNewGame(fenField.getText());
    }

    /**
     * Starts a fresh game line from a FEN.
     *
     * @param fen FEN
     */
    protected void startNewGame(String fen) {
        try {
            Position start = new Position(fen.trim());
            gameModel.reset(start);
            session.clearEvalHistory();
            showGamePly(0);
            appendConsole("New game from " + start + "\n");
        } catch (IllegalArgumentException ex) {
            showError("Invalid FEN", ex.getMessage());
        }
    }

    /**
     * Sets the visible current position.
     *
     * @param position position
     * @param lastMove last move
     */
    protected void setPosition(Position position, short lastMove) {
        currentPosition = position.copy();
        fenField.setText(currentPosition.toString());
        board.setPosition(currentPosition, lastMove);
        analysisGraph.resetForPosition(currentPosition.toString());
        networkPanel.setFen(currentPosition.toString());
        mctsPanel.setFen(currentPosition.toString());
        updateMoves();
        updateStatus();
        updateTagsAsync();
        requestCommandPreviews();
        updateGameState();
        requestEvalUpdate();
        refreshStatusBar();
        updateSessionPosition();
    }

    /**
     * Pushes the current position into the shared {@link #session} so the
     * Dashboard tab updates without scraping Swing components. Tags arrive
     * later, asynchronously, via {@link #updateTagsAsync()}.
     */
    protected void updateSessionPosition() {
        if (currentPosition == null) {
            return;
        }
        session.updatePosition(currentPosition.toString(), currentPosition.isWhiteToMove(),
                gameModel.currentPly(), gameModel.lastPly(), visibleMoves.length);
        updateSessionEngine();
    }

    /**
     * Pushes the current external-engine configuration into the session so the
     * Dashboard's Engine card stays current.
     */
    protected void updateSessionEngine() {
        session.updateEngine(engineProtocolValue(), liveExternalEngineEnabled,
                session.engineSummary());
    }

    /**
     * Magnitude, in centipawns, used to represent a forced mate in the
     * dashboard eval sparkline so the line stays on a sensible scale.
     */
    protected static final int MATE_EVAL_CENTIPAWNS = 3000;

    /**
     * Records a white-relative engine evaluation for the current ply into the
     * session so the Dashboard's eval-over-plies sparkline can plot it.
     *
     * @param whiteCentipawns evaluation in centipawns, from White's view
     */
    protected void recordSessionEval(int whiteCentipawns) {
        session.recordEval(gameModel.currentPly(), whiteCentipawns);
    }

    /**
     * Schedules an eval-bar refresh; coalesces rapid navigation into a single
     * subprocess fork after a short debounce.
     */
    protected void requestEvalUpdate() {
        if (liveExternalEngineEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            if (isAnalyzePaneVisible()) {
                requestLiveAnalysisUpdate();
            } else {
                pauseHiddenLiveAnalysis();
            }
            return;
        }
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            stopLiveAnalysis();
            evalBar.setUnavailable("off");
            return;
        }
        if (currentPosition == null) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            evalBar.setUnavailable("n/a");
            return;
        }
        ++evalRequestId;
        cancelEvalCommand();
        evalBar.setThinking();
        evalDebounceTimer.restart();
    }

    /**
     * Forks the engine subprocess for the most recent eval request.
     */
    protected void startEvalCommand() {
        if (currentPosition == null) {
            return;
        }
        evalFailureLogged = false;
        long requestId = evalRequestId;
        String fen = currentFen();
        boolean whiteToMove = currentPosition.isWhiteToMove();
        List<String> args;
        try {
            args = buildEvalBarArgs(fen);
        } catch (IllegalArgumentException ex) {
            evalBar.setUnavailable("cfg");
            appendConsole("Eval bar paused: " + ex.getMessage() + System.lineSeparator());
            return;
        }
        evalCommand = CommandRunner.run(args, null, null,
                result -> applyEvalResult(requestId, whiteToMove, result.output()),
                ex -> handleEvalFailure(requestId, ex));
    }

    /**
     * Applies an engine result to the eval bar when it is still current.
     *
     * @param requestId request id
     * @param whiteToMove true when White was to move in the analyzed position
     * @param output engine command output
     */
    protected void applyEvalResult(long requestId, boolean whiteToMove, String output) {
        if (requestId != evalRequestId) {
            return;
        }
        EngineEval eval = parseEngineEval(output);
        if (eval == null) {
            evalBar.setUnavailable("n/a");
            return;
        }
        int value = whiteToMove ? eval.value() : -eval.value();
        if (eval.mate()) {
            if (eval.value() == 0) {
                evalBar.setMateDelivered(whiteToMove);
                recordSessionEval(whiteToMove ? -MATE_EVAL_CENTIPAWNS : MATE_EVAL_CENTIPAWNS);
            } else {
                evalBar.setMate(value);
                recordSessionEval(value > 0 ? MATE_EVAL_CENTIPAWNS : -MATE_EVAL_CENTIPAWNS);
            }
        } else {
            evalBar.setCentipawns(value);
            recordSessionEval(value);
        }
    }

    /**
     * Handles automatic eval-bar command failures.
     *
     * @param requestId request id
     * @param exception failure
     */
    protected void handleEvalFailure(long requestId, Exception exception) {
        if (requestId != evalRequestId || exception instanceof CancellationException) {
            return;
        }
        evalBar.setUnavailable("n/a");
        if (!evalFailureLogged) {
            evalFailureLogged = true;
            String message = exception == null ? "unknown failure"
                    : exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            appendConsole("Eval bar disabled: " + message + System.lineSeparator());
            toast(Toast.Kind.WARNING, "Eval bar disabled: " + message);
        }
    }

    /**
     * Whether the eval-bar diagnostic line has already been emitted.
     * Reset by {@link #startEvalCommand} so a recovery message after the user
     * fixes their configuration is also surfaced once.
     */
    protected boolean evalFailureLogged;

    /**
     * Cancels the currently running eval-bar process.
     */
    protected void cancelEvalCommand() {
        if (evalCommand != null && evalCommand.isRunning()) {
            evalCommand.cancel();
        }
        evalCommand = null;
    }

    /**
     * Enables or disables live external-engine analysis.
     *
     * @param enabled true to keep an external engine analyzing the board
     */
    protected void setLiveExternalEngineEnabled(boolean enabled) {
        if (liveExternalEngineEnabled == enabled) {
            liveEngineToggle.setSelected(enabled);
            return;
        }
        liveExternalEngineEnabled = enabled;
        liveEngineToggle.setSelected(enabled);
        saveDisplaySettings();
        if (enabled) {
            liveAnalysisFailureLogged = false;
            evalDebounceTimer.stop();
            cancelEvalCommand();
            appendConsole("Live external engine enabled\n");
            if (isAnalyzePaneVisible()) {
                requestLiveAnalysisUpdate();
            } else {
                pauseHiddenLiveAnalysis();
            }
        } else {
            appendConsole("Live external engine disabled\n");
            stopLiveAnalysis();
            setStatusBarEngine("idle");
            session.updateEngine(engineProtocolValue(), false, "");
            if (autoEvalBarEnabled) {
                requestEvalUpdate();
            } else {
                evalBar.setUnavailable("off");
            }
        }
    }

    /**
     * Restarts the live engine after external-engine settings change.
     */
    protected void restartLiveAnalysis() {
        stopLiveAnalysisWorker();
        liveAnalysisFailureLogged = false;
        if (isAnalyzePaneVisible()) {
            requestLiveAnalysisUpdate();
        } else {
            pauseHiddenLiveAnalysis();
        }
    }

    /**
     * Stops live analysis while preserving the enabled setting because the
     * Analyze pane is hidden.
     */
    protected void pauseHiddenLiveAnalysis() {
        stopLiveAnalysis();
        setStatusBarEngine("live paused");
        session.updateEngine(engineProtocolValue(), true, "paused");
    }

    /**
     * Requests live analysis for the current board position.
     */
    protected void requestLiveAnalysisUpdate() {
        if (!liveExternalEngineEnabled) {
            return;
        }
        if (currentPosition == null) {
            evalBar.setUnavailable("n/a");
            return;
        }
        LiveAnalysisRequest request;
        try {
            request = createLiveAnalysisRequest();
        } catch (IllegalArgumentException ex) {
            evalBar.setUnavailable("cfg");
            setStatusBarEngine("live config");
            toast(Toast.Kind.WARNING, ex.getMessage());
            return;
        }
        board.setSuggestedMove(Move.NO_MOVE);
        evalBar.setThinking();
        setStatusBarEngine("live starting");
        synchronized (liveAnalysisLock) {
            liveAnalysisRequest = request;
            liveAnalysisLock.notifyAll();
        }
        ensureLiveAnalysisWorker(request.protocolPath());
    }

    /**
     * Creates a snapshot request for live analysis.
     *
     * @return live-analysis request
     */
    protected LiveAnalysisRequest createLiveAnalysisRequest() {
    return new LiveAnalysisRequest(
                ++liveAnalysisRequestId,
                currentPosition.copy(),
                currentPosition.isWhiteToMove(),
                liveProtocolPath(),
                ((Number) multipvModel.getValue()).intValue(),
                ((Number) threadsModel.getValue()).intValue(),
                optionalPositiveInteger(engineHashField, "--hash"));
    }

    /**
     * Returns the protocol path used by live analysis.
     *
     * @return configured protocol path, or the config default
     */
    protected String liveProtocolPath() {
        String path = engineProtocolValue();
        return path.isEmpty() ? Config.getProtocolPath() : path;
    }

    /**
     * Ensures a live-analysis worker is running for the requested protocol.
     *
     * @param protocolPath protocol TOML path
     */
    protected void ensureLiveAnalysisWorker(String protocolPath) {
        if (liveEngineWorker != null && !liveEngineWorker.isDone()
                && liveEngineWorker.usesProtocol(protocolPath)) {
            return;
        }
        stopLiveAnalysisWorker();
        liveEngineWorker = new LiveEngineWorker(protocolPath);
        liveEngineWorker.execute();
    }

    /**
     * Stops live analysis and clears the latest request.
     */
    protected void stopLiveAnalysis() {
        liveAnalysisRequestId++;
        synchronized (liveAnalysisLock) {
            liveAnalysisRequest = null;
            liveAnalysisLock.notifyAll();
        }
        stopLiveAnalysisWorker();
        board.setSuggestedMove(Move.NO_MOVE);
    }

    /**
     * Stops the live-analysis worker without changing the enabled flag.
     */
    protected void stopLiveAnalysisWorker() {
        LiveEngineWorker worker = liveEngineWorker;
        if (worker != null) {
            worker.requestStop();
            worker.cancel(true);
            liveEngineWorker = null;
        }
        synchronized (liveAnalysisLock) {
            liveAnalysisLock.notifyAll();
        }
    }

    /**
     * Waits for the next live-analysis request after the supplied id.
     *
     * @param previousId request id already consumed by the worker
     * @param worker worker waiting for work
     * @return next request, or null when the worker should exit
     * @throws InterruptedException when interrupted while waiting
     */
    protected LiveAnalysisRequest awaitLiveAnalysisRequest(long previousId, LiveEngineWorker worker)
            throws InterruptedException {
        synchronized (liveAnalysisLock) {
            while (!worker.shouldStop()
                    && (liveAnalysisRequest == null || liveAnalysisRequest.id() == previousId)) {
                liveAnalysisLock.wait();
            }
            return worker.shouldStop() ? null : liveAnalysisRequest;
        }
    }

    /**
     * Returns whether a live request has been superseded.
     *
     * @param request request currently being analyzed
     * @return true when analysis should stop
     */
    protected boolean liveRequestSuperseded(LiveAnalysisRequest request) {
        synchronized (liveAnalysisLock) {
            return !liveExternalEngineEnabled
                    || liveAnalysisRequest == null
                    || liveAnalysisRequest.id() != request.id();
        }
    }

    /**
     * Applies one live-analysis update to the board.
     *
     * @param update streamed update
     */
    protected void applyLiveAnalysisUpdate(LiveAnalysisUpdate update) {
        if (update.status() != null) {
            setStatusBarEngine(update.status());
            return;
        }
        if (!liveExternalEngineEnabled || update.requestId() != liveAnalysisRequestId) {
            return;
        }
        Output output = update.output();
        if (output == null) {
            return;
        }
        applyLiveEvaluation(update.whiteToMove(), output.getEvaluation());
        if (update.bestMove() != Move.NO_MOVE) {
            board.setSuggestedMove(update.bestMove());
        }
        analysisGraph.addSample(update.whiteToMove(), output, update.bestMove());
        String summary = formatLiveEngineStatus(output, update.bestMove());
        setStatusBarEngine(summary);
        session.updateEngine(engineProtocolValue(), true, summary);
    }

    /**
     * Applies a live engine evaluation to the eval bar.
     *
     * @param whiteToMove true when White was to move in the analyzed position
     * @param evaluation engine evaluation from side-to-move perspective
     */
    protected void applyLiveEvaluation(boolean whiteToMove, Evaluation evaluation) {
        if (evaluation == null || !evaluation.isValid()) {
            return;
        }
        int value = whiteToMove ? evaluation.getValue() : -evaluation.getValue();
        if (evaluation.isMate()) {
            if (evaluation.getValue() == 0) {
                evalBar.setMateDelivered(whiteToMove);
                recordSessionEval(whiteToMove ? -MATE_EVAL_CENTIPAWNS : MATE_EVAL_CENTIPAWNS);
            } else {
                evalBar.setMate(value);
                recordSessionEval(value > 0 ? MATE_EVAL_CENTIPAWNS : -MATE_EVAL_CENTIPAWNS);
            }
        } else {
            evalBar.setCentipawns(value);
            recordSessionEval(value);
        }
    }

    /**
     * Formats compact live-engine status text for the bottom status bar.
     *
     * @param output latest engine output
     * @param bestMove latest best move
     * @return status text
     */
    protected static String formatLiveEngineStatus(Output output, short bestMove) {
        StringBuilder status = new StringBuilder("live d").append(output.getDepth());
        Evaluation evaluation = output.getEvaluation();
        if (evaluation != null && evaluation.isValid()) {
            status.append(' ').append(formatLiveEvaluation(evaluation));
        }
        if (bestMove != Move.NO_MOVE) {
            status.append(' ').append(Move.toString(bestMove));
        }
        return status.toString();
    }

    /**
     * Formats an engine evaluation for compact live status text.
     *
     * @param evaluation engine evaluation
     * @return formatted text
     */
    protected static String formatLiveEvaluation(Evaluation evaluation) {
        if (evaluation.isMate()) {
            return "#" + evaluation.getValue();
        }
        int value = evaluation.getValue();
        return (value > 0 ? "+" : "") + value;
    }

    /**
     * Handles an unrecoverable live-analysis failure.
     *
     * @param exception failure
     */
    protected void handleLiveAnalysisFailure(Exception exception) {
        if (liveAnalysisFailureLogged) {
            return;
        }
        liveAnalysisFailureLogged = true;
        String message = exception == null ? "unknown failure"
                : exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        liveExternalEngineEnabled = false;
        liveEngineToggle.setSelected(false);
        saveDisplaySettings();
        evalBar.setUnavailable("n/a");
        board.setSuggestedMove(Move.NO_MOVE);
        setStatusBarEngine("live failed");
        appendConsole("Live external engine disabled: " + message + System.lineSeparator());
        toast(Toast.Kind.WARNING, "Live engine disabled: " + message);
    }

    /**
     * Loads a UCI protocol file for live analysis.
     *
     * @param protocolPath protocol TOML path
     * @return parsed protocol
     * @throws IOException when the protocol cannot be read or is invalid
     */
    protected static Protocol loadLiveProtocol(String protocolPath) throws IOException {
        Protocol protocol = new Protocol().fromToml(Files.readString(Path.of(protocolPath)));
        if (!protocol.assertValid()) {
            StringBuilder message = new StringBuilder("Protocol is missing required values:");
            for (String error : protocol.collectValidationErrors()) {
                message.append(System.lineSeparator()).append("  - ").append(error);
            }
    throw new IOException(message.toString());
        }
        return protocol;
    }

    /**
     * Applies live-engine options before a search starts.
     *
     * @param engine engine process
     * @param request live-analysis request
     */
    protected static void configureLiveEngine(Engine engine, LiveAnalysisRequest request) {
        engine.setMultiPivot(request.multipv());
        engine.setThreadAmount(request.threads());
        if (request.hash() != null) {
            engine.setHashSize(request.hash());
        }
    }

    /**
     * Parses an optional positive integer field.
     *
     * @param field source field
     * @param label human-readable setting label
     * @return parsed value, or null when blank
     */
    protected static Integer optionalPositiveInteger(JTextField field, String label) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("[1-9]\\d*")) {
    throw new IllegalArgumentException(label + " expects a positive integer.");
        }
        return Integer.valueOf(value);
    }

    /**
     * Live external-engine worker. Owns the UCI process and streams updates until
     * the current request is replaced or live mode is disabled.
     */
    protected final class LiveEngineWorker extends SwingWorker<Void, LiveAnalysisUpdate> {

        /**
         * Protocol path this worker was started with.
         */
        protected final String protocolPath;

        /**
         * Whether this worker should stop.
         */
        protected volatile boolean stopRequested;

        /**
         * Last time a UI update was published.
         */
        protected long lastPublishedAt;

        /**
         * Creates a worker.
         *
         * @param protocolPath protocol TOML path
         */
        protected LiveEngineWorker(String protocolPath) {
            this.protocolPath = protocolPath;
        }

        /**
         * Returns whether this worker uses the given protocol path.
         *
         * @param value protocol path
         * @return true when equal
         */
        protected boolean usesProtocol(String value) {
            return Objects.equals(protocolPath, value);
        }

        /**
         * Requests worker shutdown.
         */
        protected void requestStop() {
            stopRequested = true;
        }

        /**
         * Returns whether this worker should stop.
         *
         * @return true when stopping
         */
        protected boolean shouldStop() {
            return stopRequested || isCancelled();
        }

        /**
         * Runs live analysis.
         *
         * @return unused
         * @throws Exception on engine startup or I/O failure
         */
        @Override
        protected Void doInBackground() throws Exception {
            publish(LiveAnalysisUpdate.status("live starting"));
            try (Engine engine = new Engine(loadLiveProtocol(protocolPath))) {
                long lastRequestId = -1;
                while (!shouldStop()) {
                    LiveAnalysisRequest request = awaitLiveAnalysisRequest(lastRequestId, this);
                    if (request == null) {
                        break;
                    }
                    lastRequestId = request.id();
                    configureLiveEngine(engine, request);
                    publish(LiveAnalysisUpdate.status("live thinking"));
                    Analysis analysis = new Analysis();
                    engine.analyseInfinite(request.position(), analysis, null, LIVE_ANALYSIS_STALL_TIMEOUT_MS,
                            () -> shouldStop() || liveRequestSuperseded(request),
                            current -> publishLiveUpdate(request, current));
                }
            }
            return null;
        }

        /**
         * Publishes throttled live-analysis output.
         *
         * @param request active request
         * @param analysis active analysis buffer
         */
        protected void publishLiveUpdate(LiveAnalysisRequest request, Analysis analysis) {
            long now = System.currentTimeMillis();
            if (now - lastPublishedAt < LIVE_ANALYSIS_UPDATE_INTERVAL_MS) {
                return;
            }
            lastPublishedAt = now;
            publish(LiveAnalysisUpdate.analysis(request, analysis));
        }

        /**
         * Applies streamed updates on the event thread.
         *
         * @param chunks updates
         */
        @Override
        protected void process(List<LiveAnalysisUpdate> chunks) {
            for (LiveAnalysisUpdate update : chunks) {
                applyLiveAnalysisUpdate(update);
            }
        }

        /**
         * Handles worker completion on the event thread.
         */
        @Override
        protected void done() {
            if (liveEngineWorker != this) {
                return;
            }
            liveEngineWorker = null;
            if (shouldStop() || !liveExternalEngineEnabled) {
                return;
            }
            try {
                get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handleLiveAnalysisFailure(ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                handleLiveAnalysisFailure(cause instanceof Exception failure ? failure : ex);
            }
        }
    }

    /**
     * Snapshot of one live-analysis request.
     *
     * @param id request id
     * @param position position snapshot
     * @param whiteToMove true when White is to move
     * @param protocolPath protocol TOML path
     * @param multipv requested MultiPV count
     * @param threads requested engine thread count
     * @param hash optional hash size
     */
    protected record LiveAnalysisRequest(
            long id,
            Position position,
            boolean whiteToMove,
            String protocolPath,
            int multipv,
            int threads,
            Integer hash) {
    }

    /**
     * Streamed live-analysis UI update.
     *
     * @param requestId request id
     * @param whiteToMove true when White was to move
     * @param output latest best output
     * @param bestMove latest best move
     * @param status status-only update
     */
    protected record LiveAnalysisUpdate(
            long requestId,
            boolean whiteToMove,
            Output output,
            short bestMove,
            String status) {

        /**
         * Creates a status-only update.
         *
         * @param status status text
         * @return update
         */
        protected static LiveAnalysisUpdate status(String status) {
    return new LiveAnalysisUpdate(0, false, null, Move.NO_MOVE, status);
        }

        /**
         * Creates an analysis update from the current analysis buffer.
         *
         * @param request source request
         * @param analysis analysis buffer
         * @return update
         */
        protected static LiveAnalysisUpdate analysis(LiveAnalysisRequest request, Analysis analysis) {
            Output output = analysis.getBestOutput();
    return new LiveAnalysisUpdate(
                    request.id(),
                    request.whiteToMove(),
                    output == null ? null : new Output(output),
                    analysis.getBestMove(),
                    null);
        }
    }

}
