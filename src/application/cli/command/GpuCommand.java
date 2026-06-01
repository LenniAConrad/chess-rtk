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

		printBackend("LC0 CNN CUDA", chess.nn.lc0.cnn.cuda.Support.isLoaded(),
				chess.nn.lc0.cnn.cuda.Support.isAvailable(), chess.nn.lc0.cnn.cuda.Support.deviceCount());
		printBackend("LC0 BT4 CUDA", chess.nn.lc0.bt4.cuda.Support.isLoaded(),
				chess.nn.lc0.bt4.cuda.Support.isAvailable(), chess.nn.lc0.bt4.cuda.Support.deviceCount());

		printBackend("LC0 CNN ROCm", chess.nn.lc0.cnn.rocm.Support.isLoaded(),
				chess.nn.lc0.cnn.rocm.Support.isAvailable(), chess.nn.lc0.cnn.rocm.Support.deviceCount());
		printBackend("LC0 BT4 ROCm", chess.nn.lc0.bt4.rocm.Support.isLoaded(),
				chess.nn.lc0.bt4.rocm.Support.isAvailable(), chess.nn.lc0.bt4.rocm.Support.deviceCount());

		printBackend("LC0 CNN oneAPI", chess.nn.lc0.cnn.oneapi.Support.isLoaded(),
				chess.nn.lc0.cnn.oneapi.Support.isAvailable(), chess.nn.lc0.cnn.oneapi.Support.deviceCount());
		printBackend("LC0 BT4 oneAPI", chess.nn.lc0.bt4.oneapi.Support.isLoaded(),
				chess.nn.lc0.bt4.oneapi.Support.isAvailable(), chess.nn.lc0.bt4.oneapi.Support.deviceCount());

		printBackend("OTIS CUDA", chess.nn.otis.cuda.Support.isLoaded(),
				chess.nn.otis.cuda.Support.isAvailable(), chess.nn.otis.cuda.Support.deviceCount());
		printBackend("OTIS ROCm", chess.nn.otis.rocm.Support.isLoaded(),
				chess.nn.otis.rocm.Support.isAvailable(), chess.nn.otis.rocm.Support.deviceCount());
		printBackend("OTIS oneAPI", chess.nn.otis.oneapi.Support.isLoaded(),
				chess.nn.otis.oneapi.Support.isAvailable(), chess.nn.otis.oneapi.Support.deviceCount());

		printBackend("PERFT CUDA", chess.nn.perft.cuda.Support.isLoaded(),
				chess.nn.perft.cuda.Support.isAvailable(), chess.nn.perft.cuda.Support.deviceCount());
		printBackend("PERFT ROCm", chess.nn.perft.rocm.Support.isLoaded(),
				chess.nn.perft.rocm.Support.isAvailable(), chess.nn.perft.rocm.Support.deviceCount());
		printBackend("PERFT oneAPI", chess.nn.perft.oneapi.Support.isLoaded(),
				chess.nn.perft.oneapi.Support.isAvailable(), chess.nn.perft.oneapi.Support.deviceCount());
	}

	/**
	 * Prints one backend availability row.
	 *
	 * @param label backend label
	 * @param loaded whether the JNI library loaded
	 * @param available whether a device is available
	 * @param deviceCount visible device count
	 */
	private static void printBackend(String label, boolean loaded, boolean available, int deviceCount) {
		System.out.printf(
				"%s JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				label,
				loaded ? "yes" : "no",
				available ? "yes" : "no",
				deviceCount);
	}
}
