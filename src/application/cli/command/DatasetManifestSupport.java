package application.cli.command;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import application.Main;
import chess.io.DatasetManifest;
import chess.io.Provenance;
import chess.io.RecordRowHash;

/**
 * Thin call-site helper that writes a {@code crtk.dataset.manifest.v1} sidecar
 * next to every dataset exporter's artifacts.
 *
 * <p>The helper exists to keep the manifest invocation tight at every call
 * site in {@link RecordCommands} and the {@code RecordTrainingJsonlCommand}
 * leaf. It hides the boilerplate of plumbing argv, the CRTK version, and the
 * git commit into {@link DatasetManifest.Builder} so the exporter call sites
 * stay focused on the artifact pipeline itself.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetManifestSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetManifestSupport() {
		// utility
	}

	/**
	 * Writes a manifest for an exporter that consumes one input file.
	 *
	 * @param exporter     logical exporter identifier (e.g. {@code record.dataset.npy})
	 * @param input        single input file
	 * @param outputs      output files produced by the exporter
	 * @param weights      optional weights file ({@code null} when none)
	 * @param manifestPath manifest destination path
	 */
	public static void write(String exporter, Path input, List<Path> outputs, Path weights, Path manifestPath) {
		write(exporter, List.of(input), outputs, weights, manifestPath);
	}

	/**
	 * Writes a manifest for an exporter that consumes one input file and has
	 * exporter-specific metadata.
	 *
	 * @param exporter     logical exporter identifier
	 * @param input        single input file
	 * @param outputs      output files produced by the exporter
	 * @param weights      optional weights file ({@code null} when none)
	 * @param manifestPath manifest destination path
	 * @param metadata     optional metadata writer
	 */
	public static void write(String exporter, Path input, List<Path> outputs, Path weights, Path manifestPath,
			Consumer<DatasetManifest.Builder> metadata) {
		write(exporter, List.of(input), outputs, weights, manifestPath, metadata);
	}

	/**
	 * Writes a manifest for an exporter that consumes one or more input files.
	 *
	 * @param exporter     logical exporter identifier
	 * @param inputs       input files
	 * @param outputs      output files produced by the exporter
	 * @param weights      optional weights file ({@code null} when none)
	 * @param manifestPath manifest destination path
	 */
	public static void write(String exporter, List<Path> inputs, List<Path> outputs, Path weights,
			Path manifestPath) {
		write(exporter, inputs, outputs, weights, manifestPath, null);
	}

	/**
	 * Writes a manifest for an exporter that consumes one or more input files and
	 * has exporter-specific metadata.
	 *
	 * @param exporter     logical exporter identifier
	 * @param inputs       input files
	 * @param outputs      output files produced by the exporter
	 * @param weights      optional weights file ({@code null} when none)
	 * @param manifestPath manifest destination path
	 * @param metadata     optional metadata writer
	 */
	public static void write(String exporter, List<Path> inputs, List<Path> outputs, Path weights,
			Path manifestPath, Consumer<DatasetManifest.Builder> metadata) {
		DatasetManifest.Builder builder = DatasetManifest.builder()
				.exporter(exporter)
				.crtkVersion(VersionCommand.VERSION)
				.gitCommit(Provenance.gitCommit())
				.argv(Main.lastInvocationArgv())
				.inputs(inputs)
				.weights(weights);
		for (Path output : outputs) {
			builder.output(output);
		}
		if (metadata != null) {
			metadata.accept(builder);
		}
		try {
			builder.writeTo(manifestPath);
		} catch (IOException ex) {
			throw new CommandFailure(exporter + ": failed to write provenance manifest "
					+ manifestPath + ": " + ex.getMessage(), ex, 3, false);
		}
	}

	/**
	 * Returns the standard row-hash sidecar path for a dataset or JSONL artifact.
	 *
	 * @param artifact output stem or output file
	 * @return sibling path with {@code .rowhashes.txt} appended to the basename
	 */
	public static Path rowHashPathFor(Path artifact) {
		return artifact.resolveSibling(artifact.getFileName() + ".rowhashes.txt");
	}

	/**
	 * Opens the standard UTF-8 writer for a row-hash sidecar.
	 *
	 * @param sidecar sidecar path
	 * @return buffered writer
	 * @throws IOException when the sidecar cannot be opened
	 */
	public static BufferedWriter openRowHashWriter(Path sidecar) throws IOException {
		Path parent = sidecar.getParent();
		if (parent != null && !Files.isDirectory(parent)) {
			Files.createDirectories(parent);
		}
		return Files.newBufferedWriter(sidecar, StandardCharsets.UTF_8);
	}

	/**
	 * Returns a sink that writes one raw-record-json-v1 hash per accepted output row.
	 *
	 * @param writer row-hash sidecar writer
	 * @return raw JSON sink, or {@code null} when no writer is supplied
	 */
	public static Consumer<String> rowHashSink(BufferedWriter writer) {
		if (writer == null) {
			return null;
		}
		return rawJson -> {
			try {
				writer.write(RecordRowHash.rawRecordJsonV1(rawJson));
				writer.newLine();
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		};
	}

	/**
	 * Adds the row-hash sidecar to a manifest builder.
	 *
	 * @param builder manifest builder
	 * @param sidecar row-hash sidecar path, or {@code null} when disabled
	 */
	public static void addRowHashSidecar(DatasetManifest.Builder builder, Path sidecar) {
		if (sidecar == null) {
			return;
		}
		builder.output(sidecar)
				.metadata("row_hash_algorithm", RecordRowHash.RAW_RECORD_JSON_V1)
				.metadata("row_hash_sidecar", sidecar.getFileName().toString());
	}
}
