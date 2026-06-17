package application.cli.command;

import java.util.List;

import utility.Argv;

/**
 * Implements the {@code engine gpu} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GpuCommand {

	/**
	 * One native backend availability row.
	 *
	 * @param label       backend label
	 * @param loaded      whether the JNI library loaded
	 * @param available   whether a device is available
	 * @param deviceCount visible device count
	 */
	public record Backend(String label, boolean loaded, boolean available, int deviceCount) {
	}

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

		for (Backend backend : backends()) {
			printBackend(backend);
		}
	}

	/**
	 * Returns native backend availability rows.
	 *
	 * @return deterministic backend matrix
	 */
	public static List<Backend> backends() {
		return List.of(
				backend("LC0 CNN CUDA", chess.nn.lc0.cnn.cuda.Support.isLoaded(),
						chess.nn.lc0.cnn.cuda.Support.isAvailable(), chess.nn.lc0.cnn.cuda.Support.deviceCount()),
				backend("LC0 BT4 CUDA", chess.nn.lc0.bt4.cuda.Support.isLoaded(),
						chess.nn.lc0.bt4.cuda.Support.isAvailable(), chess.nn.lc0.bt4.cuda.Support.deviceCount()),
				backend("LC0 CNN ROCm", chess.nn.lc0.cnn.rocm.Support.isLoaded(),
						chess.nn.lc0.cnn.rocm.Support.isAvailable(), chess.nn.lc0.cnn.rocm.Support.deviceCount()),
				backend("LC0 BT4 ROCm", chess.nn.lc0.bt4.rocm.Support.isLoaded(),
						chess.nn.lc0.bt4.rocm.Support.isAvailable(), chess.nn.lc0.bt4.rocm.Support.deviceCount()),
				backend("LC0 CNN oneAPI", chess.nn.lc0.cnn.oneapi.Support.isLoaded(),
						chess.nn.lc0.cnn.oneapi.Support.isAvailable(), chess.nn.lc0.cnn.oneapi.Support.deviceCount()),
				backend("LC0 BT4 oneAPI", chess.nn.lc0.bt4.oneapi.Support.isLoaded(),
						chess.nn.lc0.bt4.oneapi.Support.isAvailable(), chess.nn.lc0.bt4.oneapi.Support.deviceCount()),
				backend("OTIS CUDA", chess.nn.otis.cuda.Support.isLoaded(),
						chess.nn.otis.cuda.Support.isAvailable(), chess.nn.otis.cuda.Support.deviceCount()),
				backend("OTIS ROCm", chess.nn.otis.rocm.Support.isLoaded(),
						chess.nn.otis.rocm.Support.isAvailable(), chess.nn.otis.rocm.Support.deviceCount()),
				backend("OTIS oneAPI", chess.nn.otis.oneapi.Support.isLoaded(),
						chess.nn.otis.oneapi.Support.isAvailable(), chess.nn.otis.oneapi.Support.deviceCount()),
				backend("PERFT CUDA", chess.nn.perft.cuda.Support.isLoaded(),
						chess.nn.perft.cuda.Support.isAvailable(), chess.nn.perft.cuda.Support.deviceCount()),
				backend("PERFT ROCm", chess.nn.perft.rocm.Support.isLoaded(),
						chess.nn.perft.rocm.Support.isAvailable(), chess.nn.perft.rocm.Support.deviceCount()),
				backend("PERFT oneAPI", chess.nn.perft.oneapi.Support.isLoaded(),
						chess.nn.perft.oneapi.Support.isAvailable(), chess.nn.perft.oneapi.Support.deviceCount()));
	}

	/**
	 * Creates one backend row.
	 *
	 * @param label       backend label
	 * @param loaded      whether the JNI library loaded
	 * @param available   whether a device is available
	 * @param deviceCount visible device count
	 * @return backend row
	 */
	private static Backend backend(String label, boolean loaded, boolean available, int deviceCount) {
		return new Backend(label, loaded, available, deviceCount);
	}

	/**
	 * Prints one backend availability row.
	 *
	 * @param backend backend row
	 */
	private static void printBackend(Backend backend) {
		System.out.printf(
				"%s JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				backend.label(),
				backend.loaded() ? "yes" : "no",
				backend.available() ? "yes" : "no",
				backend.deviceCount());
	}
}
