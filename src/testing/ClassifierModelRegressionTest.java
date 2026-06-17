package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression coverage for classifier model defaults.
 *
 * <p>The classifier default path is part of the dataset pipeline contract: docs,
 * examples, and {@code Model.loadDefault()} must agree on the same filename.
 * Weight binaries are gitignored, so this test always checks documentation
 * parity and only loads the model when the local file exists.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ClassifierModelRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ClassifierModelRegressionTest() {
		// utility
	}

	/**
	 * Runs classifier model checks.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		testDefaultPathMatchesDocumentedModel();
		testLoadDefaultWhenWeightsArePresent();
		System.out.println("ClassifierModelRegressionTest: all checks passed");
	}

	/**
	 * Verifies the Java default points at the documented model filename.
	 */
	private static void testDefaultPathMatchesDocumentedModel() {
		Path defaultWeights = chess.nn.classifier.Model.DEFAULT_WEIGHTS;
		String filename = defaultWeights.getFileName().toString();
		assertEquals("puzzle-classifier_21planes-6blocksx64-head32-logit1.bin", filename,
				"classifier default filename");
		Path readme = Path.of("models", "README.md");
		try {
			String text = Files.readString(readme, StandardCharsets.UTF_8);
			assertTrue(text.contains(filename),
					"models README documents the classifier default filename");
		} catch (IOException ex) {
			throw new AssertionError("failed to read " + readme + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies {@code loadDefault()} succeeds when the local weights file exists.
	 */
	private static void testLoadDefaultWhenWeightsArePresent() {
		Path defaultWeights = chess.nn.classifier.Model.DEFAULT_WEIGHTS;
		if (!Files.isRegularFile(defaultWeights)) {
			return;
		}
		try (chess.nn.classifier.Model model = chess.nn.classifier.Model.loadDefault()) {
			assertEquals(21, model.info().inputChannels(),
					"classifier input channels");
			assertEquals(1, model.info().outputSize(),
					"classifier output size");
		} catch (IOException ex) {
			throw new AssertionError("Model.loadDefault() failed for "
					+ defaultWeights + ": " + ex.getMessage(), ex);
		}
	}
}
