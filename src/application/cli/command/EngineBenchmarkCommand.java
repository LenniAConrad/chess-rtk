package application.cli.command;

import static application.cli.Constants.CMD_BENCHMARK;
import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_ITERATIONS;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requirePositive;

import java.util.Locale;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Position;
import chess.debug.LogService;
import chess.debug.Perft;
import chess.debug.Perft.Result;
import utility.Argv;

/**
 * Implements core move-generator benchmark commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineBenchmarkCommand {

	/**
	 * Default benchmark depth.
	 */
	private static final int DEFAULT_DEPTH = 4;

	/**
	 * Default benchmark iteration count.
	 */
	private static final int DEFAULT_ITERATIONS = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private EngineBenchmarkCommand() {
		// utility
	}

	/**
	 * Handles {@code engine benchmark}.
	 *
	 * @param a argument parser
	 */
	public static void runBenchmark(Argv a) {
		String cmd = "engine " + CMD_BENCHMARK;
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Integer depthValue = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		Integer iterationsValue = a.integer(OPT_ITERATIONS);
		Integer threadsValue = a.integer(OPT_THREADS);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, cmd);
		Position position = CommandSupport.resolvePositionArgument(a, cmd, true, verbose);

		int depth = depthValue == null ? DEFAULT_DEPTH : depthValue;
		int iterations = iterationsValue == null ? DEFAULT_ITERATIONS : iterationsValue;
		int threads = threadsValue == null ? 1 : threadsValue;
		requirePositive(cmd, OPT_DEPTH, depth);
		requirePositive(cmd, OPT_ITERATIONS, iterations);
		requirePositive(cmd, OPT_THREADS, threads);

		try {
			BenchmarkResult result = runBenchmark(position, depth, iterations, threads);
			if (outputMode == OutputMode.JSON || outputMode == OutputMode.JSONL) {
				System.out.println(result.toJson());
			} else {
				printText(result);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LogService.error(ex, cmd + " interrupted");
			throw new CommandFailure(cmd + ": interrupted", ex, 130, verbose);
		}
	}

	/**
	 * Runs the perft benchmark loop.
	 *
	 * @param position root position
	 * @param depth perft depth
	 * @param iterations iteration count
	 * @param threads worker threads
	 * @return benchmark result
	 * @throws InterruptedException when interrupted
	 */
	private static BenchmarkResult runBenchmark(
			Position position,
			int depth,
			int iterations,
			int threads) throws InterruptedException {
		long nodes = 0L;
		long totalNodes = 0L;
		long totalNanos = 0L;
		for (int i = 0; i < iterations; i++) {
			Result result = Perft.run(position.copy(), depth, threads);
			nodes = result.stats().nodes();
			totalNodes += nodes;
			totalNanos += result.nanos();
		}
		return new BenchmarkResult(position.toString(), depth, iterations, threads, nodes, totalNodes, totalNanos);
	}

	/**
	 * Prints human-readable benchmark output.
	 *
	 * @param result benchmark result
	 */
	private static void printText(BenchmarkResult result) {
		System.out.println("Benchmark: core perft");
		System.out.println("FEN: " + result.fen());
		System.out.println("depth: " + result.depth());
		System.out.println("iterations: " + result.iterations());
		System.out.println("threads: " + result.threads());
		System.out.println("nodes: " + result.nodes());
		System.out.println("total-nodes: " + result.totalNodes());
		System.out.printf(Locale.ROOT, "time-ms: %.3f%n", result.totalNanos() / 1_000_000.0);
		System.out.printf(Locale.ROOT, "nps: %.0f%n", result.nodesPerSecond());
	}

	/**
	 * Benchmark result.
	 *
	 * @param fen root FEN
	 * @param depth perft depth
	 * @param iterations iteration count
	 * @param threads worker threads
	 * @param nodes nodes per iteration
	 * @param totalNodes total nodes across all iterations
	 * @param totalNanos total elapsed nanoseconds
	 */
	private record BenchmarkResult(
			String fen,
			int depth,
			int iterations,
			int threads,
			long nodes,
			long totalNodes,
			long totalNanos) {

		/**
		 * Returns measured nodes per second.
		 *
		 * @return nodes per second
		 */
		double nodesPerSecond() {
			return totalNanos <= 0L ? 0.0 : totalNodes * 1_000_000_000.0 / totalNanos;
		}

		/**
		 * Converts the benchmark result to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			return "{\"fen\":" + CommandSupport.jsonString(fen)
					+ ",\"depth\":" + depth
					+ ",\"iterations\":" + iterations
					+ ",\"threads\":" + threads
					+ ",\"nodes\":" + nodes
					+ ",\"total_nodes\":" + totalNodes
					+ ",\"time_ms\":" + String.format(Locale.ROOT, "%.3f", totalNanos / 1_000_000.0)
					+ ",\"nps\":" + String.format(Locale.ROOT, "%.0f", nodesPerSecond())
					+ "}";
		}
	}
}
