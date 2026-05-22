package application.cli.command;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import utility.Argv;
import utility.CommandLine;

/**
 * Runs a plain-text script containing one ChessRTK CLI command per line.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BatchRunCommand {

    /**
     * Command label used in diagnostics.
     */
    private static final String CMD = "batch run";

    /**
     * File input option.
     */
    private static final String OPT_INPUT = "--input";

    /**
     * Short file input option.
     */
    private static final String OPT_INPUT_SHORT = "-i";

    /**
     * Standard-input option.
     */
    private static final String OPT_STDIN = "--stdin";

    /**
     * Keep-going option.
     */
    private static final String OPT_KEEP_GOING = "--keep-going";

    /**
     * Quiet option.
     */
    private static final String OPT_QUIET = "--quiet";

    /**
     * Verbose option.
     */
    private static final String OPT_VERBOSE = "--verbose";

    /**
     * Comment prefix for script rows.
     */
    private static final String COMMENT_PREFIX = "#";

    /**
     * Prevents instantiation.
     */
    private BatchRunCommand() {
        // utility
    }

    /**
     * Runs commands from a script file or standard input.
     *
     * @param argv parsed CLI arguments
     */
    public static void runBatch(Argv argv) {
        boolean stdin = argv.flag(OPT_STDIN);
        boolean keepGoing = argv.flag(OPT_KEEP_GOING);
        boolean quiet = argv.flag(OPT_QUIET);
        boolean verbose = argv.flag(OPT_VERBOSE);
        Path input = argv.path(OPT_INPUT, OPT_INPUT_SHORT);
        argv.ensureConsumed();
        List<String> lines = loadLines(input, stdin, verbose);
        int exitCode = runLines(lines, keepGoing, quiet, verbose);
        if (exitCode != 0) {
            throw new CommandFailure(CMD + ": one or more commands failed with exit " + exitCode,
                    exitCode);
        }
    }

    /**
     * Loads script rows from a file or standard input.
     *
     * @param input input file, or {@code null}
     * @param stdin whether standard input should be read
     * @param verbose whether failures include stack traces
     * @return raw script rows
     */
    private static List<String> loadLines(Path input, boolean stdin, boolean verbose) {
        if (stdin == (input != null)) {
            throw new CommandFailure(CMD + ": provide exactly one of " + OPT_INPUT + " or "
                    + OPT_STDIN, 2);
        }
        if (stdin) {
            return CommandSupport.readStdinLines(CMD, verbose);
        }
        try {
            return Files.readAllLines(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CommandFailure(CMD + ": failed to read " + input + ": " + ex.getMessage(),
                    ex, 2, verbose);
        }
    }

    /**
     * Runs every non-empty script row.
     *
     * @param lines script rows
     * @param keepGoing whether to continue after a non-zero exit
     * @param quiet whether command echo should be suppressed
     * @param verbose whether failures include stack traces
     * @return zero when all commands succeeded, otherwise the first failing exit
     */
    private static int runLines(List<String> lines, boolean keepGoing, boolean quiet, boolean verbose) {
        int firstFailure = 0;
        int commandCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            String row = lines.get(i).trim();
            if (row.isEmpty() || row.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            List<String> args = parseRow(row, i + 1);
            if (args.isEmpty()) {
                continue;
            }
            commandCount++;
            int exitCode = runOne(args, quiet, verbose);
            if (exitCode != 0 && firstFailure == 0) {
                firstFailure = exitCode;
            }
            if (exitCode != 0 && !keepGoing) {
                return exitCode;
            }
        }
        if (commandCount == 0) {
            throw new CommandFailure(CMD + ": script has no commands", 2);
        }
        return firstFailure;
    }

    /**
     * Parses one script row, accepting an optional leading {@code crtk}.
     *
     * @param row script row
     * @param lineNumber one-based source line number
     * @return command arguments without launcher token
     */
    private static List<String> parseRow(String row, int lineNumber) {
        try {
            List<String> parsed = new ArrayList<>(CommandLine.split(row));
            if (!parsed.isEmpty() && "crtk".equals(parsed.get(0))) {
                parsed.remove(0);
            }
            return List.copyOf(parsed);
        } catch (IllegalArgumentException ex) {
            throw new CommandFailure(CMD + ": line " + lineNumber + ": " + ex.getMessage(), 2);
        }
    }

    /**
     * Runs one command in a child JVM.
     *
     * @param args command arguments without launcher token
     * @param quiet whether command echo should be suppressed
     * @param verbose whether failures include stack traces
     * @return process exit code
     */
    private static int runOne(List<String> args, boolean quiet, boolean verbose) {
        if (!quiet) {
            System.out.println("$ crtk " + CommandLine.join(args));
        }
        Process process = null;
        try {
            process = new ProcessBuilder(javaInvocation(args))
                    .redirectErrorStream(true)
                    .start();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(System.out);
            }
            return process.waitFor();
        } catch (IOException ex) {
            throw new CommandFailure(CMD + ": failed to run " + CommandLine.join(args)
                    + ": " + ex.getMessage(), ex, 2, verbose);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new CommandFailure(CMD + ": interrupted while running " + CommandLine.join(args),
                    ex, 130, verbose);
        }
    }

    /**
     * Builds the Java invocation used for one child command.
     *
     * @param args command arguments without launcher token
     * @return process arguments
     */
    private static List<String> javaInvocation(List<String> args) {
        List<String> processArgs = new ArrayList<>();
        Path java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        processArgs.add(java.toString());
        processArgs.add("-cp");
        processArgs.add(System.getProperty("java.class.path"));
        processArgs.add("application.Main");
        processArgs.addAll(args);
        return List.copyOf(processArgs);
    }

    /**
     * Returns whether the current OS is Windows.
     *
     * @return true on Windows
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
