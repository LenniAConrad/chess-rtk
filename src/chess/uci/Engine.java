package chess.uci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import chess.core.Position;
import chess.debug.LogService;
import chess.struct.Record;
import chess.tag.Generator;

/**
 * Manages a UCI‐compatible chess engine process and drives it via
 * Universal Chess Interface commands.
 * <p>
 * This class handles:
 * <ul>
 * <li>Launching and (re)starting the engine executable.</li>
 * <li>Sending UCI commands (e.g. “uci”, “isready”, “position %s”, “go depth
 * %d”, etc.).</li>
 * <li>Reading and interpreting engine replies (“readyok”, “bestmove”, info
 * lines, W/D/L output).</li>
 * <li>Configuring engine parameters at runtime (threads, hash size, multipv,
 * Chess960, WDL).</li>
 * <li>Lifecycle control: setup, rest (wait for ready), stop, destroy, revive on
 * crash/timeouts.</li>
 * <li>Collecting analysis output into an {@link Analysis} buffer via
 * {@link Output}.</li>
 * </ul>
 * </p>
 * <p>
 * Uses a per‐engine {@link Protocol} instance for all command templates and
 * response tokens, so it can interoperate with engines that differ slightly
 * in their UCI dialect. All public methods are synchronized to serialize
 * I/O; clients may still wish to coordinate access if sharing an Engine
 * across threads.
 * </p>
 *
 * @since 2021
 * @author Lennart A. Conrad
 */
public class Engine implements AutoCloseable {

	/**
	 * Used for limiting how long startup protocol handshakes may wait.
	 */
	private static final long STARTUP_TIMEOUT_MS = 30_000;

	/**
	 * Used for limiting how long readiness checks may wait.
	 */
	private static final long READY_TIMEOUT_MS = 30_000;

	/**
	 * Used for limiting how long a stopped search may remain unresponsive before
	 * the process is killed.
	 */
	private static final long STOP_GRACE_TIMEOUT_MS = 5_000;

	/**
	 * Used for throttling the busy wait loop when polling the engine output. Tuned
	 * to ~5ms to match typical Stockfish info cadence without adding visible
	 * latency.
	 */
	private static final long OUTPUT_POLL_SLEEP_MS = 5;

	/**
	 * Sentinel used before any position has been sent to the engine.
	 */
	private static final String NO_POSITION_SET = "no position set";

	/**
	 * Logs the last output of the chess {@code Engine}.
	 */
	private String engineOutput = "";

	/**
	 * Logs the last input of the chess {@code Engine}.
	 */
	private String engineInput = "";

	/**
	 * Logs the last position that the chess {@code Engine} has been fed into as a
	 * FEN.
	 */
	private String setPosition = NO_POSITION_SET;

	/**
	 * Logs the number of threads that the chess {@code Engine} should use.
	 */
	private int setThreadAmount = 0;

	/**
	 * Logs the number of variations that the chess {@code Engine} should examine.
	 */
	private int setMultipv = 0;

	/**
	 * Logs the size of the transposition table that the chess {@code Engine} can
	 * access.
	 */
	private int setHashSize = 0;

	/**
	 * Used for managing the underlying process running the chess engine.
	 */
	private Process process;

	/**
	 * Used for sending commands to the chess engine.
	 */
	private PrintStream output;

	/**
	 * Used for receiving output from the chess engine.
	 */
	private BufferedReader input;

	/**
	 * Used for storing the protocol details specific to the chess engine.
	 */
	private Protocol protocol;

	/**
	 * Used for tracking if the engine is set to Chess960 variant.
	 */
	private boolean setChess960;

	/**
	 * Tracks whether the last position supplied to the engine was Chess960.
	 */
	private boolean positionChess960;

	/**
	 * The ID of the chess {@code Engine} used for logging and debugging.
	 */
	private String engineId = "";

	/**
	 * The process ID of the chess {@code Engine} process.
	 * This is used for debugging and logging purposes.
	 */
	private long processId = -1;

	/**
	 * Used for constructing a chess {@code Engine}.
	 * The {@code Protocol} is final, meaning its variables cannot be changed upon
	 * initialization of the {@code Engine}.
	 *
	 * @param protocol the protocol to use when launching the engine
	 * @throws IllegalArgumentException if the protocol is null or invalid
	 * @throws IOException              if the engine process cannot be started
	 * @implNote {@code Protocol} is final as a security measure
	 */
	public Engine(Protocol protocol) throws IOException {
		LogService.info("Engine instance is being created");

		if (protocol == null) {
			LogService.error(null, "Engine init failed: protocol is null");
			throw new IllegalArgumentException("Protocol must not be null");
		}

		this.protocol = Protocol.copyOf(protocol);

		String[] protocolerrors = protocol.collectValidationErrors();

		LogService.info(protocolerrors);

		if (!this.protocol.assertExtras()) {
			LogService.warn(String.format("Protocol '%s' has non‑essential null variables", this.protocol.getName()));
		}

		if (!this.protocol.assertValid()) {
			LogService.error(null, String.format("Protocol '%s' missing essential values; engine will not start",
					this.protocol.getName()));
			throw new IllegalArgumentException("Protocol missing essential values");
		}

		engineId = String.format("Engine '%s' (%s)", this.protocol.getName(), this.protocol.getPath());

		try {
			startProcess();
			initializeUci();
			rest();
			setup();
			rest();

			LogService.info(String.format("%s has successfully been started", engineId));
		} catch (IOException e) {
			LogService.error(e, String.format("Failed to launch engine '%s' at '%s'", this.protocol.getName(),
					this.protocol.getPath()));
			throw new IOException("Engine process could not be started", e);
		}
	}

	/**
	 * Used for retrieving the ID of the chess {@code Engine}.
	 * This ID is a formatted string that includes the engine's name, path, and
	 * process ID
	 * 
	 * @return a formatted string representing the engine ID
	 */
	private String getEngineId() {
		return String.format("Engine '%s' (%s) PID %06d", this.protocol.getName(), this.protocol.getPath(), processId);
	}

	/**
	 * Starts the engine process and wires up its streams.
	 *
	 * @throws IOException when the process cannot be started
	 */
	private void startProcess() throws IOException {
		process = new ProcessBuilder(this.protocol.getPath()).redirectErrorStream(true).start();
		output = new PrintStream(process.getOutputStream());
		input = new BufferedReader(new InputStreamReader(process.getInputStream()));
		processId = process.pid();
		engineId = getEngineId();
	}

	/**
	 * Sends the initial UCI command and waits for the engine's {@code uciok}
	 * response.
	 *
	 * @return itself
	 * @throws IOException if the engine does not complete UCI initialization
	 */
	private synchronized Engine initializeUci() throws IOException {
		print(protocol.showUci);
		waitForLine(protocol.uciok, STARTUP_TIMEOUT_MS, "initialize UCI");
		return this;
	}

	/**
	 * Used for waiting until the engine is ready by sending “isready” and blocking
	 * until
	 * “readyok” is received or the timeout elapses.
	 *
	 * @param timeoutMs maximum wait time in milliseconds
	 * @return itself
	 * @throws IOException if the engine process dies, does not respond on time or
	 *                     the stream closes unexpectedly
	 */
	private synchronized Engine rest() throws IOException {
		print(protocol.isready);
		waitForLine(protocol.readyok, READY_TIMEOUT_MS, "get ready");
		return this;
	}

	/**
	 * Waits until a specific output line is received from the engine.
	 *
	 * @param expected  exact line to wait for
	 * @param timeoutMs timeout in milliseconds
	 * @param action    action label used in diagnostics
	 * @throws IOException when the stream closes, process exits, or timeout expires
	 */
	private void waitForLine(String expected, long timeoutMs, String action) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (input.ready()) {
				String line = input.readLine();
				if (line == null) {
					String message = String.format("%s process died or stream closed while trying to %s (%dms timeout)",
							engineId, action, timeoutMs);
					LogService.error(null, message);
					throw new IOException(message);
				}
				engineOutput = line;
				if (line.equals(expected)) {
					return;
				}
			} else {
				if (!process.isAlive()) {
					String message = String.format("%s process died while trying to %s (%dms timeout)",
							engineId, action, timeoutMs);
					LogService.error(null, message);
					throw new IOException(message);
				}
				yieldForEngineOutput();
			}
		}
		String message = String.format("%s failed to %s (%dms timeout; expected '%s', last output '%s')",
				engineId, action, timeoutMs, expected, engineOutput);
		LogService.error(null, message);
		throw new IOException(message);
	}

	/**
	 * Used for reviving the {@code Engine} in case of failure. If the
	 * {@code Engine}
	 * has crashed, it will close the old process and its streams, restart it,
	 * reapply setup and the last position, multipv and hash size settings, then
	 * wait
	 * until ready.
	 *
	 * @return itself
	 * @throws IOException if restarting the engine fails at any step
	 */
	public synchronized Engine revive() throws IOException {
		if (process.isAlive()) {
			return this;
		}

		LogService.info(String.format("%s is being attempted to revived", engineId));

		try {
			input.close();
		} catch (IOException e) {
			LogService.info(String.format("%s input stream close failed: %s", engineId, e.getMessage()));
		}

		output.close();
		process.destroyForcibly();

		try {
			process.waitFor();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			LogService.error(ie, String.format("%s termination interrupted", engineId));
			throw new IOException(engineId + " termination interrupted", ie);
		}

		try {
			long oldProcessId = processId;
			String previousPosition = setPosition;
			boolean previousPositionChess960 = positionChess960;

			startProcess();
			setChess960 = !previousPositionChess960;
			initializeUci();
			rest();
			setup();
			setChess960(previousPositionChess960);

			if (!NO_POSITION_SET.equals(previousPosition)) {
				print(String.format(protocol.setPosition, previousPosition));
			}

			if (setMultipv > 0) {
				setMultiPivot(setMultipv);
			}

			if (setHashSize > 0) {
				setHashSize(setHashSize);
			}

			if (setThreadAmount > 0) {
				setThreadAmount(setThreadAmount);
			}

			rest();

			LogService
					.info(String.format("%s process id changed from %06d to %06d", engineId, oldProcessId, processId));
			LogService.info(String.format("%s revived successfully", engineId));

		} catch (Exception e) {
			LogService.error(e, String.format("%s revive failed", engineId));
			throw new IOException(engineId + " revive failed", e);
		}

		return this;
	}

	/**
	 * Used for sending setup commands to the {@code Engine} and flushing any
	 * pending output so it doesn’t interfere with future communications.
	 *
	 * @return itself
	 */
	private synchronized Engine setup() {
		if (protocol.setup == null) {
			return this;
		}

		LogService.info(String.format("%s is being setup", engineId));

		for (int i = 0; i < protocol.setup.length; i++) {
			print(protocol.setup[i]);
		}

		return this;
	}

	/**
	 * 
	 * Used for printing a command to the {@code Engine}.
	 * 
	 * @param string the exact UCI command to send; ignored if {@code null}
	 * @return this {@code Engine} instance for chaining
	 */
	private synchronized Engine print(String string) {
		if (string == null) {
			return this;
		}
		engineInput = string;
		output.println(string);
		output.flush();
		return this;
	}

	/**
	 * 
	 * Used for analysing a {@code Position} with the chess {@code Engine} and
	 * writing results into
	 * 
	 * the {@code Analysis} buffer. The analysis stops when {@code arguments} apply,
	 * the {@code nodes}
	 * 
	 * limit is reached, or {@code duration} (ms) elapses.
	 * 
	 * @param position  the position to analyse
	 * @param analysis  the analysis buffer to append engine output to
	 * @param arguments optional stopping criteria (may be {@code null})
	 * @param nodes     the maximum number of nodes to search
	 * @param duration  the maximum analysis time in milliseconds
	 * @return this {@code Engine} instance for chaining
	 * @throws IOException if I/O fails while communicating with the engine
	 * @see Analysis
	 * @see Output
	 */
	public synchronized Engine analyse(Position position, Analysis analysis, Filter arguments, long nodes,
			long duration) throws IOException {
		if (invalidArguments(position, analysis, nodes, duration)) {
			return this;
		}

		revive();

		rest();
		goNodes(position, nodes);

		boolean engineUnresponsive = false;
		long startTime = System.currentTimeMillis();
		long stopTimestamp = 0;

		while (searchOngoing(engineOutput) && process.isAlive()) {
			if (input.ready()) {
				engineUnresponsive = false;
				processEngineOutput(analysis, arguments);
			} else if (timeoutExceeded(startTime, duration)) {
				if (engineUnresponsive) {
					stopTimestamp = terminateIfStalled(stopTimestamp);
				} else {
					stopTimestamp = markUnresponsive(duration);
					engineUnresponsive = true;
				}
			} else {
				yieldForEngineOutput();
			}
		}

		checkProcessAlive(position);

		engineOutput = "";
		return this;
	}

	/**
	 * Used for running an infinite analysis that only stops when the caller signals
	 * a stop or the engine emits a {@code bestmove} line.
	 *
	 * @param position     the position to analyse
	 * @param analysis     the analysis buffer to append engine output to
	 * @param arguments    optional stopping criteria (may be {@code null})
	 * @param stallTimeout max idle time (ms) before stopping due to unresponsive engine
	 * @param stopSignal   external stop signal (may be {@code null})
	 * @param onUpdate     callback invoked after each parsed output (may be {@code null})
	 * @return this {@code Engine} instance for chaining
	 * @throws IOException if I/O fails while communicating with the engine
	 */
	public synchronized Engine analyseInfinite(
			Position position,
			Analysis analysis,
			Filter arguments,
			long stallTimeout,
			BooleanSupplier stopSignal,
			Consumer<Analysis> onUpdate) throws IOException {
		if (position == null || analysis == null) {
			return this;
		}

		revive();
		rest();
		setPosition(position);
		print("go infinite");

		InfiniteAnalysisState state = new InfiniteAnalysisState();

		while (searchOngoing(engineOutput) && process.isAlive()) {
			maybeIssueInfiniteStop(stopSignal, state);
			if (consumeInfiniteOutput(analysis, arguments, onUpdate, state)) {
				// output consumed
			} else if (handleInfiniteStall(stallTimeout, state)) {
				// timeout handling applied
			} else {
				yieldForEngineOutput();
			}
		}

		checkProcessAlive(position);
		engineOutput = "";
		return this;
	}

	/**
	 * Issues a single stop command when the external stop signal becomes true.
	 *
	 * @param stopSignal external stop signal
	 * @param state infinite-analysis loop state
	 */
	private void maybeIssueInfiniteStop(BooleanSupplier stopSignal, InfiniteAnalysisState state) {
		if (!state.stopIssued && stopSignal != null && stopSignal.getAsBoolean()) {
			stop();
			state.stopIssued = true;
		}
	}

	/**
	 * Consumes ready engine output during infinite analysis.
	 *
	 * @param analysis target analysis buffer
	 * @param arguments optional output filter
	 * @param onUpdate optional update callback
	 * @param state infinite-analysis loop state
	 * @return true when output was consumed
	 * @throws IOException if reading or parsing fails
	 */
	private boolean consumeInfiniteOutput(
			Analysis analysis,
			Filter arguments,
			Consumer<Analysis> onUpdate,
			InfiniteAnalysisState state) throws IOException {
		if (!input.ready()) {
			return false;
		}
		state.engineUnresponsive = false;
		processEngineOutput(analysis, arguments);
		state.lastOutput = System.currentTimeMillis();
		if (onUpdate != null) {
			onUpdate.accept(analysis);
		}
		return true;
	}

	/**
	 * Handles the unresponsive-engine timeout logic for infinite analysis.
	 *
	 * @param stallTimeout max idle time before the engine is treated as stalled
	 * @param state infinite-analysis loop state
	 * @return true when timeout handling was applied
	 */
	private boolean handleInfiniteStall(long stallTimeout, InfiniteAnalysisState state) {
		if (stallTimeout <= 0 || System.currentTimeMillis() - state.lastOutput < stallTimeout) {
			return false;
		}
		if (state.engineUnresponsive) {
			state.stopTimestamp = terminateIfStalled(state.stopTimestamp);
		} else {
			state.stopTimestamp = markUnresponsive(stallTimeout);
			state.engineUnresponsive = true;
		}
		return true;
	}

	/**
	 * Mutable state for one infinite-analysis loop.
	 */
	private static final class InfiniteAnalysisState {

		/**
		 * Whether the engine was already flagged as unresponsive.
		 */
		private boolean engineUnresponsive;

		/**
		 * Wall-clock timestamp of the last parsed output line.
		 */
		private long lastOutput = System.currentTimeMillis();

		/**
		 * Timestamp of the stop command issued for stall handling.
		 */
		private long stopTimestamp;

		/**
		 * Whether an external stop command was already sent.
		 */
		private boolean stopIssued;
	}

	/**
	 * Used for analysing a {@link Record} by extracting its {@link Position} and
	 * {@link Analysis},
	 * applying the provided limits, and delegating to
	 * {@link #analyse(Position, Analysis, Filter, long, long)}. Also stamps the
	 * engine name
	 * and creation time into the record.
	 *
	 * @param sample    the record containing the position and analysis buffer
	 * @param arguments optional stopping criteria (may be {@code null})
	 * @param nodes     the maximum number of nodes to search
	 * @param duration  the maximum analysis time in milliseconds
	 * @return this {@code Engine} instance for chaining
	 * @throws IOException if I/O fails while communicating with the engine
	 * @see #analyse(Position, Analysis, Filter, long, long)
	 */
	public synchronized Engine analyse(Record sample, Filter arguments, long nodes,
			long duration) throws IOException {
		Position position = sample.getPosition();
		Analysis analysis = sample.getAnalysis();
		sample.withEngine(protocol.getName()).withCreated(System.currentTimeMillis());
		sample.addTags(Generator.positionalTags(position));
		return analyse(position, analysis, arguments, nodes, duration);
	}

	/**
	 * Used for checking if the given arguments to the analyse method are valid.
	 *
	 * @param position the position to analyse
	 * @param analysis the analysis buffer
	 * @param nodes    the maximum nodes to search
	 * @param duration the maximum duration to search
	 * @return true if the arguments are invalid; false otherwise
	 */
	private boolean invalidArguments(Position position, Analysis analysis, long nodes, long duration) {
		return position == null || analysis == null || nodes <= 0 || duration <= 0;
	}

	/**
	 * Used for starting the engine search with the given position and node count.
	 *
	 * @param position the position to search
	 * @param nodes    the maximum nodes to search
	 */
	private void goNodes(Position position, long nodes) {
		setPosition(position);
		print(String.format(protocol.searchNodes, nodes));
	}

	/**
	 * Used for processing output from the engine and updating analysis.
	 *
	 * @param analysis  the analysis buffer to update
	 * @param arguments the arguments that may trigger stopping the search
	 * @throws IOException if reading from the engine input fails
	 */
	private void processEngineOutput(Analysis analysis, Filter arguments) throws IOException {
		engineOutput = input.readLine();
		if (engineOutput == null) {
			String message = String.format("%s output stream closed during analysis", engineId);
			LogService.error(null, message);
			throw new IOException(message);
		}
		analysis.add(new Output(engineOutput));
		if (arguments != null && arguments.apply(analysis)) {
			stop();
		}
	}

	/**
	 * Used for checking if the allowed search duration has been exceeded.
	 *
	 * @param startTime the start time of the search
	 * @param duration  the maximum allowed duration
	 * @return true if the duration has been exceeded; false otherwise
	 */
	private boolean timeoutExceeded(long startTime, long duration) {
		return System.currentTimeMillis() - startTime >= duration;
	}

	/**
	 * Used for yielding the thread briefly when we have nothing to read.
	 * 
	 * @implNote this helps to avoid busy waiting and reduces CPU usage by ~50%
	 */
	private void yieldForEngineOutput() {
		try {
			TimeUnit.MILLISECONDS.sleep(OUTPUT_POLL_SLEEP_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Used for logging and handling cases when the engine becomes unresponsive.
	 *
	 * @param duration the maximum allowed duration for the search
	 * @return the timestamp when unresponsiveness was detected
	 */
	private long markUnresponsive(long duration) {
		LogService.warn(String.format(
				"%s %dms analysis timeout with position '%s', multipivot '%d', hashsize '%d' "
						+ "and last input '%s'",
				engineId, duration, setPosition, setMultipv, setHashSize, engineInput));
		stop();
		return System.currentTimeMillis();
	}

	/**
	 * Used for terminating the engine if it has stalled for longer than
	 * STOP_TIMEOUT.
	 *
	 * @param stopTimestamp the timestamp when unresponsiveness started
	 * @return the new timestamp if terminated, otherwise the previous value
	 */
	private long terminateIfStalled(long stopTimestamp) {
		if (System.currentTimeMillis() - stopTimestamp >= STOP_GRACE_TIMEOUT_MS) {
			LogService.error(null, String.format(
					"%s failed to respond after %dms and will be terminated",
					engineId, STOP_GRACE_TIMEOUT_MS));
			process.destroyForcibly();
			return System.currentTimeMillis();
		}
		return stopTimestamp;
	}

	/**
	 * Used for checking if the process is still alive during analysis and logging
	 * errors if not.
	 *
	 * @param position the chess position being analysed
	 */
	private void checkProcessAlive(Position position) throws IOException {
		if (!process.isAlive()) {
			String message = String.format(
					"%s died whilst analysing position '%s', multipivot '%d', hashsize '%d' "
							+ "and last input '%s'",
					engineId, position, setMultipv, setHashSize, engineInput);
			LogService.error(null, message);
			throw new IOException(message);
		}
	}

	/**
	 * 
	 * Used for checking whether the current {@code Engine} search is still ongoing.
	 * 
	 * @param string a single UCI output line to evaluates
	 * @return {@code true} if the engine is still searching; {@code false} if a
	 *         terminal line was seen
	 */
	private static boolean searchOngoing(String string) {
		if (string == null) {
			return false;
		}
		return !string.startsWith("bestmove ");
	}

	/**
	 * 
	 * Used for setting the current {@code Position} of the {@code Engine}.
	 * 
	 * @param position the chess position to set (FEN derived from
	 *                 {@code position.toString()})
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine setPosition(Position position) {
		setPosition = position.toString();
		positionChess960 = position.isChess960();
		setChess960(positionChess960);
		print(String.format(protocol.setPosition, setPosition));
		return this;
	}

	/**
	 * 
	 * Used for setting the amount of threads that the chess {@code Engine} can use.
	 * 
	 * @param amount the number of worker threads to enable
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine setThreadAmount(int amount) {
		setThreadAmount = amount;
		print(String.format(protocol.setThreadAmount, amount));
		return this;
	}

	/**
	 * 
	 * Used for setting the size of the transposition table that the chess
	 * {@code Engine} can access.
	 * 
	 * @param size the hash table size in megabytes
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine setHashSize(int size) {
		setHashSize = size;
		print(String.format(protocol.setHashSize, size));
		return this;
	}

	/**
	 * 
	 * Used for setting the amount of pivots that the chess {@code Engine} should
	 * look for.
	 * 
	 * @param amount the number of principal variations to request (MultiPV)
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine setMultiPivot(int amount) {
		setMultipv = amount;
		print(String.format(protocol.setMultiPivotAmount, amount));
		return this;
	}

	/**
	 * 
	 * Used for setting the chess variant to standard or Chess960 of the
	 * {@code Engine}.
	 * 
	 * @param value {@code true} to enable Chess960 (Fischer Random); {@code false}
	 *              for standard chess
	 * @return this {@code Engine} instance for chaining
	 */
	private Engine setChess960(boolean value) {
		if (value != setChess960) {
			setChess960 = value;
			if (protocol.setChess960 == null) {
				LogService.warn(String.format("%s cannot set Chess960=%s because the protocol command is missing",
						engineId, value));
				return this;
			}
			print(String.format(protocol.setChess960, value));
		}
		return this;
	}

	/**
	 * 
	 * Used for making the {@code Engine} show the win-draw-loss chances.
	 * 
	 * @param value {@code true} to enable WDL output; {@code false} to disable
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine showWinDrawLoss(boolean value) {
		print(String.format(protocol.showWinDrawLoss, value));
		return this;
	}

	/**
	 * 
	 * Used for letting the {@code Engine} know that a new game is being analysed.
	 * 
	 * @return this {@code Engine} instance for chaining
	 */
	public Engine newGame() {
		print(protocol.newGame);
		return this;
	}

	/**
	 * Used for stopping the {@code Engine}. All computations will come to a halt.
	 * 
	 * @return Itself
	 */
	private Engine stop() {
		print(protocol.stop);
		return this;
	}

	/**
	 * Used for closing engine I/O streams and terminating the engine process.
	 */
	@Override
	public void close() {
		LogService.info(String.format("%s is being closed", engineId));
		if (output != null) {
			output.close();
		}
		try {
			if (input != null) {
				input.close();
			}
		} catch (IOException ex) {
			LogService.info(String.format("%s input stream close failed: %s", engineId, ex.getMessage()));
		}
		if (process != null) {
			process.destroy();
			try {
				if (!process.waitFor(1, TimeUnit.SECONDS)) {
					process.destroyForcibly();
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				LogService.error(ex, String.format("%s close interrupted", engineId));
			}
		}
	}
}
