package chess.uci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import chess.core.Position;
import chess.debug.LogService;
import chess.struct.Record;
import chess.tag.Tagging;

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
	 * Used for destroying the {@code Engine} process, if {@code stop()} does not
	 * exit the search or does not finish resting. The value represents 600000
	 * milliseconds or 10 minutes.
	 */
	private static final long STOP_TIMEOUT = 600_000;

	/**
	 * Used for throttling the busy wait loop when polling the engine output. Tuned
	 * to ~5ms to match typical Stockfish info cadence without adding visible
	 * latency.
	 */
	private static final long OUTPUT_POLL_SLEEP_MS = 5;

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
	private String setPosition = "no position set";

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
			process = new ProcessBuilder(this.protocol.getPath()).redirectErrorStream(true).start();
			output = new PrintStream(process.getOutputStream());
			input = new BufferedReader(new InputStreamReader(process.getInputStream()));

			processId = process.pid();
			engineId = getEngineId();

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
		long deadline = System.currentTimeMillis() + STOP_TIMEOUT;
		while (System.currentTimeMillis() < deadline) {
			if (input.ready()) {
				String line = input.readLine();
				if (line == null) {
					String message = String.format("%s process died or stream closed while getting ready! (%dms timeout)",
							engineId, STOP_TIMEOUT);
					LogService.error(null, message);
					throw new IOException(message);
				}
				if (line.equals(protocol.readyok)) {
					return this;
				}
			} else {
				if (!process.isAlive()) {
					String message = String.format("%s process died while getting ready! (%dms timeout)",
							engineId, STOP_TIMEOUT);
					LogService.error(null, message);
					throw new IOException(message);
				}
				yieldForEngineOutput();
			}
		}
		String message = String.format("%s did not get ready! (%dms timeout)", engineId, STOP_TIMEOUT);
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
			process = new ProcessBuilder(protocol.getPath())
					.redirectErrorStream(true)
					.start();
			output = new PrintStream(process.getOutputStream());
			input = new BufferedReader(new InputStreamReader(process.getInputStream()));

			long oldProcessId = processId;
			processId = process.pid();
			engineId = getEngineId();

			rest();
			setup();

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
		sample.addTags(Tagging.positionalTags(position));
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
			LogService.error(null, String.format("%s output stream closed during analysis", engineId));
			return;
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
		if (System.currentTimeMillis() - stopTimestamp >= STOP_TIMEOUT) {
			LogService.error(null, String.format(
					"%s failed to respond after %dms and will be terminated",
					engineId, STOP_TIMEOUT));
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
	private void checkProcessAlive(Position position) {
		if (!process.isAlive()) {
			LogService.error(null, String.format(
					"%s died whilst analysing position '%s', multipivot '%d', hashsize '%d' "
							+ "and last input '%s'",
					engineId, position, setMultipv, setHashSize, engineInput));
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
		return !string.startsWith("bestmove ") && !string.equals("info depth 0 score mate 0")
				&& !string.equals("info depth 0 score cp 0");
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
		setChess960(position.isChess960());
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
	 *
	 * @throws IOException          if an I/O error occurs while closing streams
	 * @throws InterruptedException if interrupted while waiting for the process to
	 *                              terminate
	 */
	@Override
	public void close() throws Exception {
		LogService.info(String.format("%s is being closed", engineId));
		output.close();
		input.close();
		process.destroy();
		process.waitFor(1, TimeUnit.SECONDS);
	}
}
