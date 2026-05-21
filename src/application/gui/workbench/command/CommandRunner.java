package application.gui.workbench.command;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Runs CRTK commands in a child JVM using the current classpath.
 */
public final class CommandRunner {

    /**
     * Prevents instantiation.
     */
    private CommandRunner() {
        // utility
    }

    /**
     * Starts one command.
     *
     * @param args CRTK arguments without the launcher token
     * @param stdin optional standard input
     * @param onChunk output callback on the EDT
     * @param onDone completion callback on the EDT
     * @param onError error callback on the EDT
     * @return running command handle
     */
    public static RunningCommand run(List<String> args, String stdin, Consumer<String> onChunk,
            Consumer<CommandResult> onDone, Consumer<Exception> onError) {
        Objects.requireNonNull(args, "args");
        RunningCommand running = new RunningCommand();
        SwingWorker<CommandResult, String> worker = new SwingWorker<>() {
            /**
             * Executes the child JVM process and captures its merged output.
             *
             * @return command result
             * @throws IOException on process or stream failure
             * @throws InterruptedException when waiting for the process is interrupted
             */
            @Override
            protected CommandResult doInBackground() throws IOException, InterruptedException {
                long started = System.nanoTime();
                List<String> processArgs = javaInvocation(args);
                ProcessBuilder builder = new ProcessBuilder(processArgs);
                builder.redirectErrorStream(true);
                if (isCancelled()) {
                    running.markCancelled();
    throw new CancellationException("command cancelled");
                }
                Process process = builder.start();
                running.process.set(process);
                running.markRunning();
                if (isCancelled()) {
                    running.destroyProcess();
                    running.markCancelled();
    throw new CancellationException("command cancelled");
                }
                Thread stdinThread = startStdinThread(process, stdin);
                BoundedOutput output = new BoundedOutput(MAX_OUTPUT_BYTES);
                try {
                    try (InputStreamReader reader = new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8)) {
                        char[] buffer = new char[4096];
                        int read;
                        while ((read = reader.read(buffer)) >= 0) {
                            if (isCancelled()) {
                                running.destroyProcess();
                                running.markCancelled();
    throw new CancellationException("command cancelled");
                            }
                            String chunk = new String(buffer, 0, read);
                            output.append(chunk);
                            publish(chunk);
                        }
                    }
                } finally {
                    if (stdinThread != null) {
                        try {
                            stdinThread.join(2000);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        if (stdinThread.isAlive()) {
                            stdinThread.interrupt();
                            closeProcessOutput(process);
                        }
                    }
                }
                int exitCode = process.waitFor();
                long millis = (System.nanoTime() - started) / 1_000_000L;
                running.markCompleted();
    return new CommandResult(List.copyOf(args), exitCode, output.toString(), millis);
            }

            /**
             * Publishes process output chunks on the event-dispatch thread.
             *
             * @param chunks output chunks
             */
            @Override
            protected void process(List<String> chunks) {
                if (onChunk == null || chunks.isEmpty() || isCancelled()
                        || running.state() == RunningCommand.State.CANCELLED) {
                    return;
                }
                if (chunks.size() == 1) {
                    onChunk.accept(chunks.get(0));
                    return;
                }
                StringBuilder coalesced = new StringBuilder(chunks.size() * 96);
                for (String chunk : chunks) {
                    coalesced.append(chunk);
                }
                onChunk.accept(coalesced.toString());
            }

            /**
             * Reports command completion, cancellation, or failure on the event-dispatch thread.
             */
            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        running.markCancelled();
    throw new CancellationException("command cancelled");
                    }
                    if (onDone != null) {
                        onDone.accept(get());
                    }
                } catch (CancellationException ex) {
                    notifyError(onError, ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    notifyError(onError, ex);
                } catch (ExecutionException ex) {
                    running.markFailed();
                    notifyError(onError, unwrapExecutionException(ex));
                }
            }
        };
        running.worker = worker;
        worker.execute();
        return running;
    }

    /**
     * Maximum captured output size before truncation.
     */
    private static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    /**
     * Starts a background thread that drains stdin to the child process so a
     * payload larger than the OS pipe buffer cannot deadlock the read loop.
     *
     * @param process child process
     * @param stdin stdin payload, or null
     * @return the started thread, or null when there is nothing to write
     */
    private static Thread startStdinThread(Process process, String stdin) {
        if (stdin == null || stdin.isEmpty()) {
            closeProcessOutput(process);
            return null;
        }
        Thread thread = new Thread(() -> {
            try (OutputStream out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // child closed or terminated; nothing to do
            }
        }, "CommandRunner-stdin");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Closes process stdin if it is still open.
     *
     * @param process child process
     */
    private static void closeProcessOutput(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // best-effort close
        }
    }

    /**
     * Bounded output buffer that retains a head and tail window with a
     * "(... truncated)" marker between them rather than growing unboundedly.
     */
    private static final class BoundedOutput {
        /**
         * Maximum retained character count.
         */
        private final int limit;

        /**
         * Leading output segment.
         */
        private final StringBuilder head = new StringBuilder();

        /**
         * Trailing output segment.
         */
        private final StringBuilder tail = new StringBuilder();

        /**
         * Characters discarded from the middle of the output to keep the
         * buffer bounded. Only incremented when {@link #appendTail} actually
         * evicts characters, never when chunks land within the head/tail caps.
         */
        private long dropped;

        /**
         * Creates a bounded output buffer.
         *
         * @param limit maximum retained character count
         */
        BoundedOutput(int limit) {
            this.limit = limit;
        }

        /**
         * Appends a command-output chunk.
         *
         * @param chunk output chunk
         */
    void append(String chunk) {
            if (head.length() < limit / 2) {
                int room = (limit / 2) - head.length();
                head.append(chunk, 0, Math.min(room, chunk.length()));
                if (chunk.length() > room) {
                    appendTail(chunk.substring(room));
                }
                return;
            }
            appendTail(chunk);
        }

        /**
         * Appends to the tail segment, evicting old tail text when needed.
         *
         * @param chunk output chunk
         */
        private void appendTail(String chunk) {
            tail.append(chunk);
            if (tail.length() > limit / 2) {
                int over = tail.length() - limit / 2;
                tail.delete(0, over);
                dropped += over;
            }
        }

        @Override
        public String toString() {
            if (dropped == 0) {
                return head.toString() + tail;
            }
            return head + System.lineSeparator()
                    + "... (truncated " + dropped + " characters) ..."
                    + System.lineSeparator() + tail;
        }
    }

    /**
     * Notifies the error callback when one is configured.
     *
     * @param onError error callback
     * @param exception exception to report
     */
    private static void notifyError(Consumer<Exception> onError, Exception exception) {
        if (onError != null) {
            onError.accept(exception);
        }
    }

    /**
     * Returns the real failure behind a SwingWorker wrapper.
     *
     * @param exception wrapper exception
     * @return wrapped exception when available
     */
    private static Exception unwrapExecutionException(ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof Exception ex) {
            return ex;
        }
        return exception;
    }

    /**
     * Renders a CRTK argument list as a launcher command.
     *
     * @param args CRTK arguments
     * @return display command
     */
    public static String displayCommand(List<String> args) {
        List<String> withLauncher = new ArrayList<>();
        withLauncher.add("crtk");
        withLauncher.addAll(args);
    return join(withLauncher);
    }

    /**
     * Joins tokens with shell-style quoting.
     *
     * @param tokens tokens
     * @return command line
     */
    public static String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(quote(token));
        }
        return sb.toString();
    }

    /**
     * Returns the Java invocation for a CRTK argument list.
     *
     * @param args CRTK args
     * @return process args
     */
    private static List<String> javaInvocation(List<String> args) {
        List<String> processArgs = new ArrayList<>();
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        processArgs.add(java.toString());
        processArgs.add("-cp");
        processArgs.add(System.getProperty("java.class.path"));
        processArgs.add("application.Main");
        processArgs.addAll(args);
        return processArgs;
    }

    /**
     * Quotes one token when necessary.
     *
     * @param token raw token
     * @return quoted token
     */
    private static String quote(String token) {
        if (token == null || token.isEmpty()) {
            return "\"\"";
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '\\') {
                return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return token;
    }

    /**
     * Returns whether the current OS is Windows.
     *
     * @return true on Windows
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * One process result.
     *
     * @param args CRTK arguments
     * @param exitCode process exit code
     * @param output combined stdout/stderr
     * @param millis elapsed milliseconds
     */
    public record CommandResult(List<String> args, int exitCode, String output, long millis) {
    }

    /**
     * Handle for a running command.
     */
    public static final class RunningCommand {

        /**
         * Lifecycle states for a running command.
         */
        enum State {
            /** Created but not yet running. */
            PENDING,
            /** Process has started and is running. */
            RUNNING,
            /** Cancelled by the user. */
            CANCELLED,
            /** Finished with an exit code. */
            COMPLETED,
            /** Failed with an exception. */
            FAILED
        }

        /**
         * Grace period (ms) before SIGTERM is escalated to SIGKILL.
         */
        private static final long GRACEFUL_TERMINATE_MS = 750L;

        /**
         * Worker running the process.
         */
        private SwingWorker<CommandResult, String> worker;

        /**
         * Child process.
         */
        private final AtomicReference<Process> process = new AtomicReference<>();

        /**
         * Current lifecycle state.
         */
        private volatile State state = State.PENDING;

        /**
         * Cancels the command, attempting a graceful SIGTERM first.
         *
         * The destroy + waitFor is moved off the calling (typically EDT)
         * thread so the UI does not freeze for {@link #GRACEFUL_TERMINATE_MS}
         * waiting for a wedged child to exit.
         */
        public void cancel() {
            markCancelled();
            if (worker != null) {
                worker.cancel(true);
            }
            Process runningProcess = process.get();
            if (runningProcess == null) {
                return;
            }
            // Close stdin pipe immediately so a blocked stdin writer wakes up
            // with IOException instead of waiting for the OS to tear it down.
            closeProcessOutput(runningProcess);
            Thread destroyer = new Thread(this::destroyProcess,
                    "CommandRunner-destroy");
            destroyer.setDaemon(true);
            destroyer.start();
        }

        /**
         * Idempotent guard so concurrent {@link #cancel()} and worker-thread
         * cancellation paths do not both run a graceful-then-forcible destroy.
         */
        private final java.util.concurrent.atomic.AtomicBoolean destroyRequested =
                new java.util.concurrent.atomic.AtomicBoolean();

        /**
         * Tries a graceful destroy first, escalating to forcible kill if the
         * child does not exit within {@link #GRACEFUL_TERMINATE_MS}. Safe to
         * call from multiple threads; only the first call performs work.
         */
        private void destroyProcess() {
            Process runningProcess = process.get();
            if (runningProcess == null || !destroyRequested.compareAndSet(false, true)) {
                return;
            }
            runningProcess.destroy();
            try {
                if (!runningProcess.waitFor(GRACEFUL_TERMINATE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    runningProcess.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                runningProcess.destroyForcibly();
            }
        }

        /**
         * Returns whether this command is still active.
         *
         * @return true while running
         */
        public boolean isRunning() {
            return worker != null && !worker.isDone();
        }

        /**
         * Returns the current lifecycle state.
         *
         * @return state
         */
    State state() {
            return state;
        }

        /**
         * Marks the command task as running.
         */
    void markRunning() {
            state = State.RUNNING;
        }

        /**
         * Marks the command task as cancelled.
         */
    void markCancelled() {
            state = State.CANCELLED;
        }

        /**
         * Marks the command task as completed successfully.
         */
    void markCompleted() {
            state = State.COMPLETED;
        }

        /**
         * Marks the command task as failed.
         */
    void markFailed() {
            state = State.FAILED;
        }
    }
}
