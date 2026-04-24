package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import chess.book.cover.Binding;
import chess.book.cover.Interior;
import chess.book.cover.Options;

/**
 * Shared rendering helpers for book commands that can emit interiors and covers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class BookRenderSupport {

	/**
	 * Writes output to a path.
	 */
	@FunctionalInterface
	interface PathWriter {
		void write(Path path) throws IOException;
	}

	/**
	 * Consumes a path and may throw while doing I/O.
	 */
	@FunctionalInterface
	interface PathConsumer {
		void accept(Path path) throws IOException;
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private BookRenderSupport() {
		// utility
	}

	/**
	 * Writes an optional interior PDF and/or cover PDF, rendering a temporary
	 * interior only when cover metrics are needed and no interior output was
	 * requested.
	 *
	 * @param interiorOutput optional interior output path
	 * @param coverOutput optional cover output path
	 * @param tempPrefix prefix for a temporary metrics PDF
	 * @param writeInterior interior PDF writer
	 * @param writeCover cover writer that receives the interior metrics PDF
	 * @param afterInterior notification hook after writing a requested interior
	 * @param afterCover notification hook after writing a requested cover
	 * @throws IOException if rendering or cleanup fails
	 */
	static void writeInteriorAndCover(
			Path interiorOutput,
			Path coverOutput,
			String tempPrefix,
			PathWriter writeInterior,
			PathConsumer writeCover,
			Consumer<Path> afterInterior,
			Runnable afterCover) throws IOException {
		Path metricsPdf = null;
		Path tempPdf = null;
		try {
			if (interiorOutput != null) {
				writeInterior.write(interiorOutput);
				metricsPdf = interiorOutput;
				if (afterInterior != null) {
					afterInterior.accept(interiorOutput);
				}
			}
			if (coverOutput != null) {
				if (metricsPdf == null) {
					tempPdf = Files.createTempFile(tempPrefix, ".pdf");
					writeInterior.write(tempPdf);
					metricsPdf = tempPdf;
				}
				writeCover.accept(metricsPdf);
				if (afterCover != null) {
					afterCover.run();
				}
			}
		} finally {
			if (tempPdf != null) {
				Files.deleteIfExists(tempPdf);
			}
		}
	}

	/**
	 * Builds cover options from an already-rendered interior PDF.
	 *
	 * @param interiorPdf rendered interior PDF
	 * @param pages page-count override, or zero for automatic
	 * @param binding selected binding
	 * @param interior selected paper stock
	 * @return cover options
	 * @throws IOException if the interior PDF cannot be inspected
	 */
	static Options coverOptions(Path interiorPdf, int pages, Binding binding, Interior interior) throws IOException {
		return new Options()
				.setBinding(binding)
				.setInterior(interior)
				.setPages(pages)
				.setInteriorPdfMetrics(chess.pdf.Inspector.inspect(interiorPdf));
	}
}
