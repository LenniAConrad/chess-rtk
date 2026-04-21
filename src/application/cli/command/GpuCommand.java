package application.cli.command;

import utility.Argv;

/**
 * Implements the {@code engine gpu} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GpuCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private GpuCommand() {
		// utility
	}

	/**
	 * Handles {@code engine gpu}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runGpuInfo(Argv a) {
		a.ensureConsumed();

		boolean loaded = chess.nn.lc0.cuda.Support.isLoaded();
		int count = chess.nn.lc0.cuda.Support.deviceCount();
		boolean available = chess.nn.lc0.cuda.Support.isAvailable();
		System.out.printf(
				"CUDA JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				loaded ? "yes" : "no",
				available ? "yes" : "no",
				count);

		boolean rocmLoaded = chess.nn.lc0.rocm.Support.isLoaded();
		int rocmCount = chess.nn.lc0.rocm.Support.deviceCount();
		boolean rocmAvailable = chess.nn.lc0.rocm.Support.isAvailable();
		System.out.printf(
				"ROCm JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				rocmLoaded ? "yes" : "no",
				rocmAvailable ? "yes" : "no",
				rocmCount);

		boolean oneapiLoaded = chess.nn.lc0.oneapi.Support.isLoaded();
		int oneapiCount = chess.nn.lc0.oneapi.Support.deviceCount();
		boolean oneapiAvailable = chess.nn.lc0.oneapi.Support.isAvailable();
		System.out.printf(
				"oneAPI JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				oneapiLoaded ? "yes" : "no",
				oneapiAvailable ? "yes" : "no",
				oneapiCount);
	}
}
