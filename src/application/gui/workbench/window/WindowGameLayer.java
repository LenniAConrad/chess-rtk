package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.game.FenInput;
import application.gui.workbench.game.PositionText;
import application.gui.workbench.session.HealthSnapshot;
import application.gui.workbench.session.Job;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Generator;
import chess.uci.Engine;
import java.awt.Dimension;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.SwingTasks.runAsync;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.showConfirmDialog;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public abstract class WindowGameLayer extends WindowEngineLayer {
    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Plays a move on the board.
     *
     * @param move move
     */
    protected void playMove(short move) {
        if (currentPosition == null) {
            return;
        }
        if (!isLegalMove(currentPosition, move)) {
            showError("Illegal move", Move.toString(move) + " is not legal in the current position.");
            return;
        }
        Position before = currentPosition.copy();
        Position next = currentPosition.copy();
        next.play(move);
        gameModel.append(before, move, next);
        showGamePly(gameModel.currentPly());
    }

    /**
     * Updates legal moves.
     */
    protected void updateMoves() {
        visibleMoves = movesModel.setPosition(currentPosition);
    }

    /**
     * Updates status label.
     */
    protected void updateStatus() {
        if (currentPosition == null) {
            statusLabel.setText("");
            return;
        }
        statusLabel.setText((currentPosition.isWhiteToMove() ? "White" : "Black")
                + " to move  |  " + PositionText.status(currentPosition)
                + "  |  legal moves " + visibleMoves.length);
    }

    /**
     * Shows a game ply on the board and in dependent panels.
     *
     * @param ply ply to show
     */
    protected void showGamePly(int ply) {
        List<Short> previousPath = gameModel.currentPath();
        short previousLastMove = gameModel.currentLastMove();
        int targetPly = Math.max(0, Math.min(ply, gameModel.lastPly()));
        gameModel.jumpToPly(targetPly);
        showNavigatedGamePosition(previousPath, previousLastMove);
    }

    /**
     * Shows a visible game-table row, including imported PGN variation rows.
     *
     * @param row table row
     */
    protected void showGameRow(int row) {
        List<Short> previousPath = gameModel.currentPath();
        short previousLastMove = gameModel.currentLastMove();
        gameModel.jumpToRow(row);
        showNavigatedGamePosition(previousPath, previousLastMove);
    }

    /**
     * Moves backward or forward in the current game line.
     *
     * @param delta ply delta
     */
    protected void navigateGame(int delta) {
        List<Short> previousPath = gameModel.currentPath();
        short previousLastMove = gameModel.currentLastMove();
        if (gameModel.navigate(delta)) {
            showNavigatedGamePosition(previousPath, previousLastMove);
        } else {
            updateGameState();
        }
    }

    /**
     * Jumps to a game ply.
     *
     * @param ply target ply
     */
    protected void jumpGameTo(int ply) {
        showGamePly(Math.max(0, Math.min(ply, gameModel.lastPly())));
    }

    /**
     * Shows the model's current position after a navigation operation.
     *
     * @param previousPath path before navigation
     * @param previousLastMove move that led to the previous position
     */
    private void showNavigatedGamePosition(List<Short> previousPath, short previousLastMove) {
        List<Short> targetPath = gameModel.currentPath();
        boolean adjacent = pathsAreAdjacent(previousPath, targetPath);
        boolean reverseMoveAnimation = adjacent && targetPath.size() < previousPath.size();
        short visualMove = reverseMoveAnimation ? previousLastMove : gameModel.currentLastMove();
        setPosition(gameModel.currentPosition(), adjacent ? visualMove : Move.NO_MOVE,
                reverseMoveAnimation);
        selectCurrentGameRow();
    }

    /**
     * Returns whether two move paths differ by one move on the same line.
     *
     * @param first first path
     * @param second second path
     * @return true when the paths are adjacent prefixes of one another
     */
    private static boolean pathsAreAdjacent(List<Short> first, List<Short> second) {
        if (Math.abs(first.size() - second.size()) != 1) {
            return false;
        }
        List<Short> shorter = first.size() < second.size() ? first : second;
        List<Short> longer = first.size() < second.size() ? second : first;
        for (int i = 0; i < shorter.size(); i++) {
            if (!shorter.get(i).equals(longer.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects the table row for the current game ply.
     */
    protected void selectCurrentGameRow() {
        int row = gameModel.currentRow();
        syncingGameTableSelection = true;
        try {
            if (row < 0) {
                gameTable.clearSelection();
                return;
            }
            gameTable.getSelectionModel().setSelectionInterval(row, row);
            java.awt.Rectangle target = gameTable.getCellRect(row, 0, true);
            java.awt.Rectangle visible = gameTable.getVisibleRect();
            if (!visible.contains(target.x, target.y)
                    || !visible.contains(target.x, target.y + target.height - 1)) {
                gameTable.scrollRectToVisible(target);
            }
        } finally {
            syncingGameTableSelection = false;
        }
    }

    /**
     * Updates game-line status text.
     */
    protected void updateGameState() {
        int variations = gameModel.variationRowCount();
        gameStateLabel.setText("Ply " + gameModel.currentPly() + " / " + gameModel.lastPly()
                + (gameModel.canBack() ? "  |  back" : "")
                + (gameModel.canForward() ? "  |  forward" : "")
                + (variations > 0 ? "  |  variations " + variations : ""));
        updateBoardNavigationControls();
    }

    /**
     * Tests whether a move is legal in a position.
     *
     * @param position position
     * @param move encoded move
     * @return true when legal
     */
    protected static boolean isLegalMove(Position position, short move) {
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            if (moves.raw(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates tags on a background worker.
     */
    protected void updateTagsAsync() {
        if (tagWorker != null && !tagWorker.isDone()) {
            tagWorker.cancel(true);
        }
        tagModel.clear();
        tagModel.addElement("calculating...");
        tagCloud.setTags(List.of("calculating..."));
        Position snapshot = currentPosition.copy();
        long requestId = ++tagRequestId;
        tagWorker = new SwingWorker<>() {
            /**
             * Computes static tags away from the event-dispatch thread.
             *
             * @return computed tags
             */
            @Override
            protected List<String> doInBackground() {
                return Generator.tags(snapshot);
            }

            /**
             * Updates the tag list after background tagging completes.
             * Stale results from superseded requests are dropped via
             * {@link #tagRequestId}.
             */
            @Override
            protected void done() {
                if (isCancelled() || requestId != tagRequestId) {
                    return;
                }
                try {
                    tagModel.clear();
                    List<String> computedTags = get();
                    for (String tag : computedTags) {
                        tagModel.addElement(tag);
                    }
                    tagCloud.setTags(computedTags);
                    session.updateTags(computedTags);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showTaggingError(ex);
                } catch (ExecutionException ex) {
                    showTaggingError(ex);
                }
            }

            /**
             * Shows a tagging failure in the tag list.
             *
             * @param ex failure
             */
            private void showTaggingError(Exception ex) {
                if (requestId != tagRequestId) {
                    return;
                }
                tagModel.clear();
                String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
                String failure = "tagging failed: " + (message == null ? ex.getClass().getSimpleName() : message);
                tagModel.addElement(failure);
                tagCloud.setTags(List.of(failure));
            }
        };
        tagWorker.execute();
    }

    /**
     * Monotonic identifier for in-flight tag requests; checked in
     * {@code done()} so a slow worker cannot overwrite the latest tags.
     */
    protected long tagRequestId;

    /**
     * Runs built-in search.
     */
    protected void runBuiltInSearch() {
        runCommand(List.of("engine", "builtin", "--fen", currentFen(), "--depth", depthValue(), "--format", "summary"),
                null);
    }

    /**
     * Runs UCI bestmove.
     */
    protected void runBestMove() {
        try {
            runCommand(buildBestMoveArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs UCI analysis.
     */
    protected void runAnalyze() {
        try {
            runCommand(buildAnalyzeArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs a quick external-engine smoke test.
     */
    protected void runEngineSmoke() {
        try {
            runCommand(buildEngineSmokeArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs CLI config validation.
     */
    protected void runConfigValidate() {
        runCommand(List.of("config", "validate"), null);
    }

    /**
     * Runs the {@code doctor} environment self-test.
     */
    protected void runDoctor() {
        runCommand(List.of("doctor"), null);
    }

    /**
     * Runs every environment-health check (config validate, doctor, engine
     * smoke) one after another. {@link #runCommand} only allows one foreground
     * command at a time, so the checks are queued and the completion callback
     * advances the queue — keeping the single-command contract intact while
     * still giving the dashboard a one-click "check everything" entry point.
     */
    protected void runAllHealthChecks() {
        if (runningCommand != null && runningCommand.isRunning()) {
            showWarning("Command running",
                    "Stop the current command before running the health checks.");
            return;
        }
        healthCheckQueue.clear();
        healthCheckQueue.add(List.of("config", "validate"));
        healthCheckQueue.add(List.of("doctor"));
        try {
            healthCheckQueue.add(buildEngineSmokeArgs());
        } catch (IllegalArgumentException ex) {
            appendConsole("Engine smoke skipped: " + ex.getMessage() + System.lineSeparator());
        }
        runNextHealthCheck();
    }

    /**
     * Launches the next queued health check, if any.
     */
    protected void runNextHealthCheck() {
        List<String> next = healthCheckQueue.poll();
        if (next != null) {
            runCommand(next, null);
        }
    }

    /**
     * Restores external-engine fields to config-backed defaults.
     */
    protected void resetEngineSettings() {
        engineProtocolField.setText(Config.getProtocolPath());
        engineNodesField.setText("");
        engineHashField.setText("");
        saveEngineSettings();
        requestCommandPreviews();
        requestEvalUpdate();
    }

    /**
     * Builds a best-move command from current workbench engine settings.
     *
     * @return command args
     */
    protected List<String> buildBestMoveArgs() {
        List<String> args = new ArrayList<>(List.of("engine", "bestmove",
                "--fen", currentFen(),
                "--format", "both",
                "--max-duration", durationValue()));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds an analysis command from current workbench engine settings.
     *
     * @return command args
     */
    protected List<String> buildAnalyzeArgs() {
        return buildAnalyzeArgs(currentFen(), multipvValue(), durationValue());
    }

    /**
     * Builds an analysis command from explicit workspace settings.
     *
     * @param fen position FEN
     * @param multipv requested line count
     * @param duration maximum analysis duration
     * @return command args
     */
    protected List<String> buildAnalyzeArgs(String fen, String multipv, String duration) {
        List<String> args = new ArrayList<>(List.of("engine", "analyze",
                "--fen", fen,
                "--multipv", multipv,
                "--max-duration", duration));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds the lightweight eval-bar command.
     *
     * @param fen position FEN
     * @return command args
     */
    protected List<String> buildEvalBarArgs(String fen) {
        List<String> args = new ArrayList<>(List.of("engine", "analyze",
                "--fen", fen,
                "--multipv", "1",
                "--max-duration", EVAL_BAR_DURATION));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds a quick UCI smoke-test command.
     *
     * @return command args
     */
    protected List<String> buildEngineSmokeArgs() {
        List<String> args = new ArrayList<>(List.of("engine", "uci-smoke",
                "--nodes", "1",
                "--max-duration", durationValue()));
        appendEngineSettingsArgs(args, false, true, true);
        return List.copyOf(args);
    }

    /**
     * Appends shared external-engine settings to one command.
     *
     * @param args target args
     * @param includeNodes whether to include max nodes
     * @param includeThreads whether to include engine threads
     * @param includeHash whether to include hash
     */
    protected void appendEngineSettingsArgs(List<String> args, boolean includeNodes, boolean includeThreads,
            boolean includeHash) {
        addOptionalTextArg(args, "--protocol-path", engineProtocolField);
        if (includeNodes) {
            addOptionalPositiveIntegerArg(args, "--max-nodes", engineNodesField);
        }
        if (includeThreads) {
            args.add("--threads");
            args.add(threadsValue());
        }
        if (includeHash) {
            addOptionalPositiveIntegerArg(args, "--hash", engineHashField);
        }
    }

    /**
     * Runs tag command.
     */
    protected void runTagsCommand() {
        runCommand(List.of("fen", "tags", "--fen", currentFen()), null);
    }

    /**
     * Runs perft.
     */
    protected void runPerft() {
        runCommand(List.of("engine", "perft", "--fen", currentFen(), "--depth", depthValue(), "--threads",
                threadsValue()), null);
    }

    /**
     * Runs the selected curated command template.
     */
    protected void runSelectedTemplate() {
        try {
            updateCommandPreviews();
            runCommand(selectedTemplateArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Template failed", ex.getMessage());
        }
    }

    /**
     * Copies the current command-builder preview after flushing pending edits.
     */
    protected void copyBuiltCommand() {
        updateCommandPreviews();
        copyText(commandField.getText());
    }

    /**
     * Runs one command.
     *
     * @param args args
     * @param stdin stdin
     */
    protected void runCommand(List<String> args, String stdin) {
        if (args == null || args.isEmpty()) {
            showWarning("No command", "Select a workflow first.");
            return;
        }
        if (runningCommand != null && runningCommand.isRunning()) {
            showWarning("Command running", "Stop the current command before starting another one.");
            return;
        }
        appendConsole("\n$ " + CommandRunner.displayCommand(args) + "\n");
        setCommandState("Running");
        // Track the run as a dashboard job so the recent-jobs table reflects
        // status, duration, exit code and a parsed result.
        Job job = session.jobs().create(args);
        session.jobs().markRunning(job);
        runningJob = job;
        runningJobStartMillis = System.currentTimeMillis();
        runningCommand = CommandRunner.run(args, stdin, this::appendConsole, result -> {
            appendConsole("[exit " + result.exitCode() + ", " + result.millis() + " ms]\n");
            setCommandState("Exit " + result.exitCode());
            session.jobs().markFinished(job, result.exitCode(), result.output(), result.millis());
            updateHealthFromCommand(args, result.exitCode());
            List<Path> artifacts = List.of();
            if (result.exitCode() == 0) {
                artifacts = runArtifacts.recordFromCommand(args);
            }
            runArtifacts.persistManifest(job, artifacts, stdin);
            if (!artifacts.isEmpty()) {
                toast(Toast.Kind.SUCCESS, exportToastMessage(artifacts));
            } else if (result.exitCode() != 0) {
                toast(Toast.Kind.ERROR, "Command failed: exit " + result.exitCode());
            }
            runningJob = null;
            maybeHighlightMove(args, result.output());
            if (!healthCheckQueue.isEmpty()) {
                SwingUtilities.invokeLater(this::runNextHealthCheck);
            }
        }, ex -> {
            setCommandState("Stopped");
            appendConsole("[stopped] " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()) + "\n");
            // stopCommand() may already have marked the job cancelled; only
            // record a failure when the job is still in a non-terminal state.
            if (!job.status().isTerminal()) {
                session.jobs().markFailed(job,
                        Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()),
                        System.currentTimeMillis() - runningJobStartMillis);
            }
            runArtifacts.persistManifest(job, List.of(), stdin);
            updateHealthFailedFromCommand(args);
            runningJob = null;
            healthCheckQueue.clear();
        });
    }

    /**
     * Builds the toast message for generated command artifacts.
     *
     * @param artifacts generated artifacts
     * @return export toast message
     */
    private static String exportToastMessage(List<Path> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "Export complete";
        }
        if (artifacts.size() == 1) {
            Path fileName = artifacts.get(0).getFileName();
            return "Exported " + (fileName == null ? artifacts.get(0) : fileName);
        }
        return "Exported " + artifacts.size() + " files";
    }

    /**
     * Updates the session's health snapshot after a health-check command
     * finishes with an exit code. Uses only the structured exit code, never
     * the command's prose output.
     *
     * @param args finished command arguments
     * @param exitCode process exit code
     */
    protected void updateHealthFromCommand(List<String> args, int exitCode) {
        HealthSnapshot.Check check = HealthSnapshot.Check.ofExitCode(exitCode);
        String command = String.join(" ", args);
        HealthSnapshot health = session.health();
        if (command.startsWith("config validate")) {
            session.updateHealth(health.withConfig(check));
        } else if (command.equals("doctor") || command.startsWith("doctor ")) {
            session.updateHealth(health.withDoctor(check));
        } else if (command.contains("uci-smoke")) {
            session.updateHealth(health.withEngineSmoke(check));
        }
    }

    /**
     * Marks the matching health check failed when a health-check command could
     * not even produce an exit code (process failed to launch or was stopped).
     *
     * @param args attempted command arguments
     */
    protected void updateHealthFailedFromCommand(List<String> args) {
        updateHealthFromCommand(args, 1);
    }

    /**
     * Stops a running command.
     */
    protected void stopCommand() {
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
            if (runningJob != null) {
                session.jobs().markCancelled(runningJob,
                        System.currentTimeMillis() - runningJobStartMillis);
                runArtifacts.persistManifest(runningJob, List.of(), null);
                runningJob = null;
            }
            healthCheckQueue.clear();
        }
    }

    /**
     * Loads game text as PGN or as a plain SAN/UCI line.
     *
     * @param text raw game text
     */
    protected void loadGameText(String text) {
        if (text == null || text.isBlank()) {
            showError("Game input", "Paste PGN, SAN, or UCI moves first.");
            return;
        }
        FenInput.Scan fenScan = FenInput.firstFenOrFailure(text);
        if (fenScan.fen() != null) {
            startNewGame(fenScan.fen());
            return;
        }
        List<Game> games = Pgn.parseGames(text);
        if (!games.isEmpty()) {
            Game game = chooseGame(games);
            if (game == null) {
                return;
            }
            try {
                loadGame(game);
                return;
            } catch (IllegalArgumentException ex) {
                if (looksLikePgn(text)) {
                    showError("PGN import failed", ex.getMessage());
                    return;
                }
            }
        }
        try {
            loadMoveLine(text);
        } catch (IllegalArgumentException ex) {
            String detail = ex.getMessage();
            if (fenScan.firstError() != null && !looksLikePgn(text)) {
                detail = (detail == null ? "" : detail + System.lineSeparator())
                        + "FEN parse hint: " + fenScan.firstError();
            }
            showError("Line import failed", detail);
        }
    }

    /**
     * Loads an ECO movetext line from the standard starting position.
     *
     * @param movetext ECO movetext
     */
    @Override
    protected void loadEcoLine(String movetext) {
        startNewGame(Setup.getStandardStartFEN());
        loadGameText(movetext);
    }

    /**
     * Loads a PGN or move-line file.
     */
    protected void loadGameFile() {
        JFileChooser chooser = FileDialogs.createFileChooser(null, null,
    new FileNameExtensionFilter("PGN, text, or FEN files", "pgn", "txt", "fen"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path path = chooser.getSelectedFile().toPath();
        runAsync(
                () -> Files.readString(path, StandardCharsets.UTF_8),
                this::loadGameText,
                ex -> showError("Load file failed", ex.getMessage()));
    }

    /**
     * Saves the current line as PGN.
     */
    protected void savePgnFile() {
        JFileChooser chooser = FileDialogs.createFileChooser(null, new File("workbench-game.pgn"),
    new FileNameExtensionFilter("PGN file", "pgn"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".pgn");
        String contents = gameModel.pgn() + System.lineSeparator();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file;
                },
                saved -> {
                    appendConsole("Saved PGN to " + saved + "\n");
                    toast(Toast.Kind.SUCCESS, "Saved PGN to " + saved.getName());
                },
                ex -> showError("Save PGN failed", ex.getMessage()));
    }

    /**
     * Loads a parsed PGN game, including its visible variation tree.
     *
     * @param game parsed game
     */
    protected void loadGame(Game game) {
        Position start = game.getStartPosition() == null ? new Position(Game.STANDARD_START_FEN)
                : game.getStartPosition().copy();
        validateGameTree(start, game);
        gameModel.loadGame(start, game);
        if (gameModel.getRowCount() == 0) {
    throw new IllegalArgumentException("No legal moves found.");
        }
        session.clearEvalHistory();
        showGamePly(gameModel.lastPly());
        int variations = gameModel.variationRowCount();
        appendConsole("Loaded PGN with " + gameModel.lastPly() + " mainline plies"
                + (variations > 0 ? " and " + variations + " variation plies" : "") + "\n");
    }

    /**
     * Validates all moves in a parsed game before mutating the live model.
     *
     * @param start start position
     * @param game parsed game
     */
    protected static void validateGameTree(Position start, Game game) {
        if (game == null) {
            return;
        }
        for (Game.Node rootVariation : game.getRootVariations()) {
            validateGameSequence(start, rootVariation);
        }
        validateGameSequence(start, game.getMainline());
    }

    /**
     * Validates one game-tree sequence and its nested variations.
     *
     * @param start sequence start position
     * @param node first node
     */
    private static void validateGameSequence(Position start, Game.Node node) {
        Position cursor = start.copy();
        Game.Node current = node;
        while (current != null) {
            Position before = cursor.copy();
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            cursor.play(move);
            for (Game.Node variation : current.getVariations()) {
                validateGameSequence(before, variation);
            }
            current = current.getNext();
        }
    }

    /**
     * Loads a plain SAN or UCI move line from the current position.
     *
     * @param text raw line text
     */
    protected void loadMoveLine(String text) {
        Position start = currentPosition == null ? new Position(Game.STANDARD_START_FEN) : currentPosition.copy();
        List<String> tokens = moveTokens(text);
        if (tokens.isEmpty()) {
    throw new IllegalArgumentException("No moves found.");
        }
        List<Short> line = new ArrayList<>();
        Position cursor = start.copy();
        for (String token : tokens) {
            short move = parseMoveToken(cursor, token);
            line.add(move);
            cursor.play(move);
        }
        gameModel.loadLine(start, line);
        showGamePly(gameModel.lastPly());
        appendConsole("Loaded move line with " + gameModel.lastPly() + " plies\n");
    }

    /**
     * Chooses a PGN game when multiple games are present.
     *
     * @param games parsed games
     * @return selected game, or null
     */
    protected Game chooseGame(List<Game> games) {
        if (games.size() == 1) {
            return games.get(0);
        }
        String[] labels = new String[games.size()];
        for (int i = 0; i < games.size(); i++) {
            labels[i] = gameLabel(games.get(i), i + 1);
        }
        JList<String> list = new JList<>(labels);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(15, labels.length));
        Theme.list(list);
        JScrollPane scroll = Ui.scroll(list);
        scroll.setPreferredSize(new Dimension(420, 320));
        int result = showConfirmDialog(this, scroll, "Select PGN game");
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        int index = list.getSelectedIndex();
        return games.get(Math.max(0, Math.min(index, games.size() - 1)));
    }

    /**
     * Builds a compact PGN game label.
     *
     * @param game game
     * @param index one-based index
     * @return label
     */
    protected static String gameLabel(Game game, int index) {
        String white = game.getTags().getOrDefault("White", "?");
        String black = game.getTags().getOrDefault("Black", "?");
        String event = game.getTags().getOrDefault("Event", "PGN");
        return index + ". " + event + " | " + white + " vs " + black + " | " + game.getResult();
    }

    /**
     * Returns whether text appears to be structured PGN.
     *
     * @param text raw text
     * @return true when PGN tags or comments are present
     */
    protected static boolean looksLikePgn(String text) {
        return text.contains("[") || text.contains("{") || text.contains("(");
    }

    /**
     * Extracts move tokens from a plain line.
     *
     * @param text raw line
     * @return move tokens
     */
    protected static List<String> moveTokens(String text) {
        String cleaned = text.replaceAll("\\{[^}]*}", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replace('\n', ' ')
                .replace('\r', ' ');
        List<String> tokens = new ArrayList<>();
        for (String raw : cleaned.split("\\s+")) {
            String token = normalizeLineToken(raw);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Returns the first valid FEN line from pasted text, or {@code null} when
     * no candidate line parses. Retained for the reflective regression test.
     *
     * @param text raw text
     * @return FEN or null
     */
    protected static String firstFenLine(String text) {
        return FenInput.firstFenLine(text);
    }

    /**
     * Validates every non-empty batch FEN row.
     *
     * @param text raw input
     * @return validation summary
     */
    protected static FenInput.Summary validateBatchFenInput(String text) {
        return FenInput.validateBatchFenInput(text);
    }

    /**
     * Returns a short FEN label for compact previews. Retained for the
     * reflective regression test; implementation lives in {@link FenInput}.
     *
     * @param fen full FEN
     * @return piece placement plus side to move when available
     */
    protected static String compactFenPreview(String fen) {
        return FenInput.compactPreview(fen);
    }

    /**
     * Normalizes one plain-line token.
     *
     * @param raw raw token
     * @return move token or blank
     */
    protected static String normalizeLineToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        if (token.isEmpty() || token.startsWith("$") || token.matches("\\.+") || isResultToken(token)) {
            return "";
        }
        token = token.replaceFirst("^\\d+\\.{1,3}", "");
        if (token.isEmpty() || token.matches("\\d+\\.{1,3}")) {
            return "";
        }
        return token;
    }

    /**
     * Returns whether a token is a game result.
     *
     * @param token token
     * @return true for result tokens
     */
    protected static boolean isResultToken(String token) {
        return "1-0".equals(token) || "0-1".equals(token) || "1/2-1/2".equals(token) || "*".equals(token);
    }

    /**
     * Parses one SAN or UCI move token.
     *
     * @param position position before the move
     * @param token move token
     * @return encoded move
     */
    protected static short parseMoveToken(Position position, String token) {
        String uci = token.toLowerCase(Locale.ROOT);
        if (Move.isMove(uci)) {
            short move = Move.parse(uci);
            if (isLegalMove(position, move)) {
                return move;
            }
        }
        return SAN.fromAlgebraic(position, token);
    }

}
