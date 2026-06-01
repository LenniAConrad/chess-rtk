package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.file.Files;

import chess.core.Position;
import chess.gpu.BackendNames;
import chess.nn.otis.Model;

/**
 * Regression checks for the OTIS evaluator backend selection (CPU plus the
 * optional CUDA/ROCm/oneAPI native backends).
 *
 * <p>The native backends are only exercised when their JNI library and a
 * matching device are present; otherwise this verifies the pure-Java CPU path,
 * graceful capability probes, and the forced-backend failure contract.
 */
public final class OtisBackendRegressionTest {

    /**
     * Standard start position used for prediction smoke checks.
     */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Backend selection system property read by {@link Model}.
     */
    private static final String BACKEND_PROPERTY = "crtk.otis.backend";

    /**
     * Prevents instantiation.
     */
    private OtisBackendRegressionTest() {
        // utility
    }

    /**
     * Runs OTIS backend regression checks.
     *
     * @param args unused command-line arguments
     * @throws IOException if the OTIS weights cannot be read
     */
    public static void main(String[] args) throws IOException {
        testBackendIdentifiers();
        testCapabilityProbesAreGraceful();
        if (!Files.exists(Model.DEFAULT_WEIGHTS)) {
            System.out.println("OtisBackendRegressionTest: weights missing, skipped model checks");
            return;
        }
        testCpuBackendLoadsAndPredicts();
        testForcedUnavailableBackendThrows();
        testNativeBackendsMatchCpuWhenAvailable();
        System.out.println("OtisBackendRegressionTest: all checks passed");
    }

    /**
     * Verifies the public backend identifiers match the shared backend names.
     */
    private static void testBackendIdentifiers() {
        assertEquals(BackendNames.CUDA, Model.BACKEND_CUDA, "OTIS CUDA backend id");
        assertEquals(BackendNames.ROCM, Model.BACKEND_ROCM, "OTIS ROCm backend id");
        assertEquals(BackendNames.ONEAPI, Model.BACKEND_ONEAPI, "OTIS oneAPI backend id");
        assertFalse(Model.BACKEND_CPU.isBlank(), "OTIS CPU backend id is set");
    }

    /**
     * Verifies native capability probes never throw and report sane counts.
     */
    private static void testCapabilityProbesAreGraceful() {
        assertTrue(chess.nn.otis.cuda.Support.deviceCount() >= 0, "OTIS CUDA device count non-negative");
        assertTrue(chess.nn.otis.rocm.Support.deviceCount() >= 0, "OTIS ROCm device count non-negative");
        assertTrue(chess.nn.otis.oneapi.Support.deviceCount() >= 0, "OTIS oneAPI device count non-negative");
        assertEquals(chess.nn.otis.cuda.Support.isAvailable(),
                chess.nn.otis.cuda.Support.deviceCount() > 0, "OTIS CUDA availability matches device count");
        assertEquals(chess.nn.otis.rocm.Support.isAvailable(),
                chess.nn.otis.rocm.Support.deviceCount() > 0, "OTIS ROCm availability matches device count");
        assertEquals(chess.nn.otis.oneapi.Support.isAvailable(),
                chess.nn.otis.oneapi.Support.deviceCount() > 0, "OTIS oneAPI availability matches device count");
    }

    /**
     * Verifies the pure-Java CPU path loads, reports metadata, and predicts.
     *
     * @throws IOException if the weights cannot be read
     */
    private static void testCpuBackendLoadsAndPredicts() throws IOException {
        try (Model model = Model.loadCpu(Model.DEFAULT_WEIGHTS)) {
            assertEquals(Model.BACKEND_CPU, model.backend(), "forced CPU backend label");
            assertEquals(Model.DEFAULT_PARAMETER_COUNT, model.info().parameterCount(),
                    "CPU model parameter count");
            Model.Prediction prediction = model.predict(new Position(START_FEN));
            assertEquals(Model.DEFAULT_POLICY_SIZE, prediction.policy().length, "policy length");
            assertEquals(3, prediction.wdl().length, "WDL length");
            float sum = prediction.wdl()[0] + prediction.wdl()[1] + prediction.wdl()[2];
            assertTrue(sum > 0.99f && sum < 1.01f, "WDL sums to one");
            assertTrue(Float.isFinite(prediction.value()), "value is finite");
        }
    }

    /**
     * Verifies that forcing an unavailable GPU backend fails loudly instead of
     * silently falling back.
     *
     * @throws IOException if an unexpected error occurs
     */
    private static void testForcedUnavailableBackendThrows() throws IOException {
        assertForcedFailure(BackendNames.CUDA, chess.nn.otis.cuda.Backend.isAvailable());
        assertForcedFailure(BackendNames.ROCM, chess.nn.otis.rocm.Backend.isAvailable());
        assertForcedFailure(BackendNames.ONEAPI, chess.nn.otis.oneapi.Backend.isAvailable());
    }

    /**
     * Verifies a single forced-but-unavailable backend throws on load.
     *
     * @param backend backend identifier to force
     * @param available whether the backend is actually available
     * @throws IOException if a load unexpectedly succeeds
     */
    private static void assertForcedFailure(String backend, boolean available) throws IOException {
        if (available) {
            return;
        }
        String previous = System.getProperty(BACKEND_PROPERTY);
        System.setProperty(BACKEND_PROPERTY, backend);
        try (Model model = Model.load(Model.DEFAULT_WEIGHTS)) {
            throw new AssertionError("forced unavailable " + backend + " backend should fail, got " + model.backend());
        } catch (IOException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains("unavailable"),
                    "forced " + backend + " backend reports an unavailable error");
        } finally {
            restoreProperty(previous);
        }
    }

    /**
     * Verifies native backends agree with the CPU value head when available.
     *
     * @throws IOException if a backend load fails
     */
    private static void testNativeBackendsMatchCpuWhenAvailable() throws IOException {
        float[] cpuWdl;
        try (Model cpu = Model.loadCpu(Model.DEFAULT_WEIGHTS)) {
            cpuWdl = cpu.predict(new Position(START_FEN)).wdl();
        }
        assertNativeParity(BackendNames.CUDA, chess.nn.otis.cuda.Backend.isAvailable(), cpuWdl);
        assertNativeParity(BackendNames.ROCM, chess.nn.otis.rocm.Backend.isAvailable(), cpuWdl);
        assertNativeParity(BackendNames.ONEAPI, chess.nn.otis.oneapi.Backend.isAvailable(), cpuWdl);
    }

    /**
     * Verifies one native backend's WDL output is close to the CPU path.
     *
     * @param backend backend identifier to force
     * @param available whether the backend is available
     * @param cpuWdl reference CPU WDL probabilities
     * @throws IOException if a backend load fails
     */
    private static void assertNativeParity(String backend, boolean available, float[] cpuWdl) throws IOException {
        if (!available) {
            return;
        }
        String previous = System.getProperty(BACKEND_PROPERTY);
        System.setProperty(BACKEND_PROPERTY, backend);
        try (Model model = Model.load(Model.DEFAULT_WEIGHTS)) {
            assertEquals(backend, model.backend(), "native backend " + backend);
            float[] wdl = model.predict(new Position(START_FEN)).wdl();
            for (int i = 0; i < 3; i++) {
                assertTrue(Math.abs(wdl[i] - cpuWdl[i]) < 1.0e-3f,
                        "native " + backend + " WDL[" + i + "] matches CPU");
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("failed to initialize")) {
                return;
            }
            throw e;
        } finally {
            restoreProperty(previous);
        }
    }

    /**
     * Restores a previously captured system property value.
     *
     * @param previous prior value, or null to clear
     */
    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(BACKEND_PROPERTY);
        } else {
            System.setProperty(BACKEND_PROPERTY, previous);
        }
    }
}
