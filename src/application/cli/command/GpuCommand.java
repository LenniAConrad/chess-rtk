package application.cli.command;

import utility.Argv;

/**
 * Implements the {@code gpu-info} CLI command.
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
	 * Handles {@code gpu-info}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runGpuInfo(Argv a) {
		a.ensureConsumed();

		boolean loaded = chess.lc0.cuda.Support.isLoaded();
		int count = chess.lc0.cuda.Support.deviceCount();
		boolean available = chess.lc0.cuda.Support.isAvailable();
		System.out.printf(
				"CUDA JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				loaded ? "yes" : "no",
				available ? "yes" : "no",
				count);

		boolean rocmLoaded = chess.lc0.rocm.Support.isLoaded();
		int rocmCount = chess.lc0.rocm.Support.deviceCount();
		boolean rocmAvailable = chess.lc0.rocm.Support.isAvailable();
		System.out.printf(
				"ROCm JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				rocmLoaded ? "yes" : "no",
				rocmAvailable ? "yes" : "no",
				rocmCount);

		boolean oneapiLoaded = chess.lc0.oneapi.Support.isLoaded();
		int oneapiCount = chess.lc0.oneapi.Support.deviceCount();
		boolean oneapiAvailable = chess.lc0.oneapi.Support.isAvailable();
		System.out.printf(
				"oneAPI JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				oneapiLoaded ? "yes" : "no",
				oneapiAvailable ? "yes" : "no",
				oneapiCount);
	}
}
