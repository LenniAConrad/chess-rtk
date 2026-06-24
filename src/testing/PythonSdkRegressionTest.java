package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.writeUtf8;

import application.cli.command.ServeCommand;
import application.cli.command.ServeCommand.RunningServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Regression checks for the generated Python daemon client.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PythonSdkRegressionTest {

    /**
     * SDK generator script path.
     */
    private static final Path GENERATOR = Path.of("scripts", "generate_python_sdk.py");

    /**
     * Python launchers accepted by this optional regression.
     */
    private static final List<String> PYTHON_CANDIDATES = List.of("python3", "python");

    /**
     * Valid standard chess starting position.
     */
    private static final String VALID_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Utility class; prevent instantiation.
     */
    private PythonSdkRegressionTest() {
        // utility
    }

    /**
     * Runs the Python SDK regression checks.
     *
     * @param args ignored command-line arguments
     * @throws Exception if generation or smoke execution fails
     */
    public static void main(String[] args) throws Exception {
        String python = findPython();
        if (python == null) {
            System.out.println("PythonSdkRegressionTest: skipped (python not found)");
            return;
        }

        Path workspace = Files.createTempDirectory("crtk-python-sdk-");
        try {
            String catalog = TestSupport.runMain("help", "--json");
            Path catalogPath = workspace.resolve("catalog.json");
            writeUtf8(catalogPath, catalog);

            Path client = workspace.resolve("crtk_client.py");
            runProcess(List.of(
                    python,
                    GENERATOR.toString(),
                    "--catalog",
                    catalogPath.toString(),
                    "--output",
                    client.toString()), "generate python sdk");
            String generated = readUtf8(client);
            assertGeneratedClientShape(generated);

            Path secondClient = workspace.resolve("crtk_client.second.py");
            runProcess(List.of(
                    python,
                    GENERATOR.toString(),
                    "--catalog",
                    catalogPath.toString(),
                    "--output",
                    secondClient.toString()), "regenerate python sdk");
            assertEquals(generated, readUtf8(secondClient),
                    "generated python sdk determinism");

            testChangedCatalogProducesBinding(python, workspace, catalog);
            testSdkSmoke(python, workspace);
        } finally {
            deleteRecursively(workspace);
        }
        System.out.println("PythonSdkRegressionTest: all checks passed");
    }

    /**
     * Verifies the generated client exposes the expected public surface.
     *
     * @param generated generated Python source
     */
    private static void assertGeneratedClientShape(String generated) {
        assertTrue(generated.contains("class CrtkClient:"), "sdk client class exists");
        assertTrue(generated.contains("class CrtkCommandError(RuntimeError):"),
                "sdk command error exists");
        assertTrue(generated.contains("def fen_validate("), "sdk has fen_validate binding");
        assertTrue(generated.contains("def serve("), "sdk has serve binding");
        assertTrue(generated.contains("GENERATED_COMMANDS = "), "sdk command count is stamped");
    }

    /**
     * Verifies a changed catalog creates a new generated binding.
     *
     * @param python python launcher
     * @param workspace temporary workspace
     * @param catalog original command catalog
     * @throws IOException if file I/O fails
     * @throws InterruptedException if the generator is interrupted
     */
    private static void testChangedCatalogProducesBinding(
            String python,
            Path workspace,
            String catalog) throws IOException, InterruptedException {
        String changed = catalog.replace("\"commands\": [", "\"commands\": [\n" + fakeCommand() + ",");
        Path changedCatalog = workspace.resolve("catalog.changed.json");
        Path changedClient = workspace.resolve("crtk_client.changed.py");
        writeUtf8(changedCatalog, changed);
        runProcess(List.of(
                python,
                GENERATOR.toString(),
                "--catalog",
                changedCatalog.toString(),
                "--output",
                changedClient.toString()), "generate python sdk from changed catalog");
        String generated = readUtf8(changedClient);
        assertTrue(generated.contains("def toy_run("),
                "changed catalog produces generated toy_run binding");
    }

    /**
     * Verifies the generated client can run a real command through the daemon.
     *
     * @param python python launcher
     * @param workspace temporary workspace containing the generated client
     * @throws Exception if the daemon or Python smoke script fails
     */
    private static void testSdkSmoke(String python, Path workspace) throws Exception {
        String expected = TestSupport.runMain("fen", "validate", "--fen", VALID_START_FEN, "--json");
        try (RunningServer server = ServeCommand.start("127.0.0.1", 0)) {
            Path smoke = workspace.resolve("smoke.py");
            writeUtf8(smoke, smokeScript(server.baseUri().toString()));
            ProcessResult result = runProcess(List.of(python, smoke.getFileName().toString()),
                    workspace, "run python sdk smoke");
            assertEquals(expected, result.stdout(), "python sdk fen validate stdout");
            assertEquals("", result.stderr(), "python sdk smoke stderr");
        }
    }

    /**
     * Returns a synthetic runnable command node for catalog-change coverage.
     *
     * @return JSON object text
     */
    private static String fakeCommand() {
        return "    {\n"
                + "      \"path\": \"toy run\",\n"
                + "      \"name\": \"run\",\n"
                + "      \"summary\": \"Fake generated command\",\n"
                + "      \"group\": false,\n"
                + "      \"runnable\": true,\n"
                + "      \"usage\": \"toy run [options]\"\n"
                + "    }";
    }

    /**
     * Builds the Python smoke script text.
     *
     * @param baseUri daemon base URI
     * @return Python script source
     */
    private static String smokeScript(String baseUri) {
        return "from crtk_client import CrtkClient\n"
                + "fen = " + pythonString(VALID_START_FEN) + "\n"
                + "client = CrtkClient(" + pythonString(baseUri) + ")\n"
                + "result = client.fen_validate('--fen', fen, '--json', check=True)\n"
                + "print(result.stdout, end='')\n";
    }

    /**
     * Finds an available Python executable.
     *
     * @return launcher name, or {@code null} when Python is unavailable
     * @throws InterruptedException if version probing is interrupted
     */
    private static String findPython() throws InterruptedException {
        for (String candidate : PYTHON_CANDIDATES) {
            try {
                ProcessResult result = runProcessAllowFailure(List.of(candidate, "--version"),
                        null, "probe " + candidate);
                if (result.exitCode() == 0) {
                    return candidate;
                }
            } catch (IOException ignored) {
                // Try the next common launcher.
            }
        }
        return null;
    }

    /**
     * Runs a process and requires exit code zero.
     *
     * @param command command and arguments
     * @param label assertion label
     * @return completed process
     * @throws IOException if the process cannot be started or read
     * @throws InterruptedException if waiting is interrupted
     */
    private static ProcessResult runProcess(List<String> command, String label)
            throws IOException, InterruptedException {
        return runProcess(command, null, label);
    }

    /**
     * Runs a process in an optional working directory and requires exit code zero.
     *
     * @param command command and arguments
     * @param directory working directory, or {@code null} for the current process directory
     * @param label assertion label
     * @return completed process
     * @throws IOException if the process cannot be started or read
     * @throws InterruptedException if waiting is interrupted
     */
    private static ProcessResult runProcess(List<String> command, Path directory, String label)
            throws IOException, InterruptedException {
        ProcessResult result = runProcessAllowFailure(command, directory, label);
        if (result.exitCode() != 0) {
            throw new AssertionError(label + " failed with exit code " + result.exitCode()
                    + "\ncommand: " + String.join(" ", command)
                    + "\nstdout: " + result.stdout()
                    + "\nstderr: " + result.stderr());
        }
        return result;
    }

    /**
     * Runs a process without asserting its exit code.
     *
     * @param command command and arguments
     * @param directory working directory, or {@code null} for the current process directory
     * @param label assertion label
     * @return completed process
     * @throws IOException if the process cannot be started or read
     * @throws InterruptedException if waiting is interrupted
     */
    private static ProcessResult runProcessAllowFailure(List<String> command, Path directory, String label)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory.toFile());
        }
        Process process = builder.start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError(label + " timed out: " + String.join(" ", command));
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    /**
     * Returns a single-quoted Python string literal.
     *
     * @param value source text
     * @return Python string literal
     */
    private static String pythonString(String value) {
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "'";
    }

    /**
     * Process result tuple.
     *
     * @param exitCode process exit code
     * @param stdout captured standard output
     * @param stderr captured standard error
     */
    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
