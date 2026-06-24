package application.cli.command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import application.Main;
import utility.Argv;
import utility.Json;

/**
 * Implements the localhost-only {@code serve} JSON-RPC command.
 */
public final class ServeCommand {

    /**
     * Command label used in diagnostics.
     */
    private static final String COMMAND = "serve";

    /**
     * Server schema identifier.
     */
    private static final String SCHEMA = "crtk.serve.v1";

    /**
     * Default loopback host.
     */
    private static final String DEFAULT_HOST = "127.0.0.1";

    /**
     * Default TCP port.
     */
    private static final int DEFAULT_PORT = 8787;

    /**
     * HTTP OK status.
     */
    private static final int HTTP_OK = 200;

    /**
     * HTTP bad request status.
     */
    private static final int HTTP_BAD_REQUEST = 400;

    /**
     * HTTP method-not-allowed status.
     */
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;

    /**
     * Shared lock for process-wide stdout/stderr capture around {@link Main#run(String[])}.
     */
    private static final Object DISPATCH_LOCK = new Object();

    /**
     * Prevents instantiation.
     */
    private ServeCommand() {
        // utility
    }

    /**
     * Starts the daemon and blocks until the JVM is interrupted or stopped.
     *
     * @param a parsed command arguments
     */
    public static void runServe(Argv a) {
        Options options = parseOptions(a);
        RunningServer server;
        try {
            server = start(options.host(), options.port());
        } catch (IOException ex) {
            throw new CommandFailure(COMMAND + ": failed to start server: " + ex.getMessage(), ex, 2,
                    options.verbose());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "crtk-serve-shutdown"));
        System.out.println(options.json() ? server.startupJson() : server.startupText());
        try {
            server.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }

    /**
     * Starts a loopback-only server for tests and embedded callers.
     *
     * @param host loopback host name or address
     * @param port TCP port, or {@code 0} for an ephemeral port
     * @return running server handle
     * @throws IOException if the server cannot bind
     */
    public static RunningServer start(String host, int port) throws IOException {
        InetAddress address = resolveLoopback(host);
        return start(address, port);
    }

    /**
     * Starts a loopback-only server for tests and embedded callers.
     *
     * @param address loopback bind address
     * @param port TCP port, or {@code 0} for an ephemeral port
     * @return running server handle
     * @throws IOException if the server cannot bind
     */
    public static RunningServer start(InetAddress address, int port) throws IOException {
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalArgumentException(COMMAND + ": refusing to bind non-loopback address "
                    + (address == null ? "<null>" : address.getHostAddress()));
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException(COMMAND + ": --port must be between 0 and 65535");
        }
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(address, port), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "crtk-serve-dispatch");
            thread.setDaemon(false);
            return thread;
        });
        CountDownLatch stopped = new CountDownLatch(1);
        RunningServer running = new RunningServer(server, executor, address, stopped);
        server.createContext("/health", new HealthHandler(running));
        server.createContext("/catalog", new CatalogHandler());
        server.createContext("/rpc", new RpcHandler());
        server.setExecutor(executor);
        server.start();
        return running;
    }

    /**
     * Parses command options.
     *
     * @param a argument parser
     * @return parsed options
     */
    private static Options parseOptions(Argv a) {
        String host = a.string("--host");
        Integer portBox = a.integer("--port");
        boolean json = a.flag("--json");
        boolean verbose = a.flag("--verbose", "-v");
        a.ensureConsumed();
        int port = portBox == null ? DEFAULT_PORT : portBox;
        return new Options(host == null || host.isBlank() ? DEFAULT_HOST : host.trim(), port, json, verbose);
    }

    /**
     * Resolves and validates a loopback host.
     *
     * @param host host text
     * @return loopback address
     * @throws IOException if resolution fails
     */
    private static InetAddress resolveLoopback(String host) throws IOException {
        InetAddress address = InetAddress.getByName(host == null || host.isBlank() ? DEFAULT_HOST : host);
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException(COMMAND + ": refusing to bind non-loopback address "
                    + address.getHostAddress());
        }
        return address;
    }

    /**
     * Captures one CLI invocation through {@link Main#run(String[])}.
     *
     * @param argv command argv tokens
     * @return captured
     */
    private static CliResult dispatch(String[] argv) {
        if (argv.length > 0 && "serve".equals(argv[0])) {
            return new CliResult(2, "", COMMAND + ": nested serve is not available through the daemon\n");
        }
        synchronized (DISPATCH_LOCK) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            try (PrintStream outReplacement = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                    PrintStream errReplacement = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                System.setOut(outReplacement);
                System.setErr(errReplacement);
                int exitCode = Main.run(argv);
                return new CliResult(exitCode,
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8));
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }

    /**
     * Reads a UTF-8 request body.
     *
     * @param exchange HTTP exchange
     * @return request body text
     * @throws IOException if reading fails
     */
    private static String readBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes one JSON response.
     *
     * @param exchange HTTP exchange
     * @param status HTTP status
     * @param body JSON body
     * @throws IOException if writing fails
     */
    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Returns a JSON string literal.
     *
     * @param value source value
     * @return JSON string
     */
    private static String jsonString(String value) {
        return "\"" + Json.esc(value == null ? "" : value) + "\"";
    }

    /**
     * Builds a JSON-RPC success envelope.
     *
     * @param id raw request id JSON
     * @param result raw result JSON
     * @return response JSON
     */
    private static String success(String id, String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}";
    }

    /**
     * Builds a JSON-RPC error envelope.
     *
     * @param id raw request id JSON
     * @param code JSON-RPC error code
     * @param message error message
     * @return response JSON
     */
    private static String error(String id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":" + code
                + ",\"message\":" + jsonString(message) + "}}";
    }

    /**
     * Extracts the raw JSON-RPC id value.
     *
     * @param json request body
     * @return id JSON, or {@code null}
     */
    private static String requestId(String json) {
        int idx = fieldValueStart(json, "id");
        if (idx < 0) {
            return "null";
        }
        char c = json.charAt(idx);
        if (c == '"') {
            String value = Json.parseStringField(json, "id");
            return jsonString(value);
        }
        int end = idx;
        while (end < json.length() && "0123456789-".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        if (end > idx) {
            return json.substring(idx, end);
        }
        if (json.startsWith("null", idx)) {
            return "null";
        }
        return "null";
    }

    /**
     * Finds the start index of a JSON field value.
     *
     * @param json source JSON
     * @param name field name
     * @return value start index, or {@code -1}
     */
    private static int fieldValueStart(String json, String name) {
        String key = '"' + name + '"' + ':';
        int idx = json == null ? -1 : json.indexOf(key);
        if (idx < 0) {
            return -1;
        }
        int start = idx + key.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        return start < json.length() ? start : -1;
    }

    /**
     * Tests whether the JSON contains a field name.
     *
     * @param json source JSON
     * @param name field name
     * @return true when present
     */
    private static boolean hasField(String json, String name) {
        return json != null && json.contains('"' + name + '"' + ':');
    }

    /**
     * Handles liveness probes.
     */
    private static final class HealthHandler implements com.sun.net.httpserver.HttpHandler {

        /**
         * Running server handle.
         */
        private final RunningServer server;

        /**
         * Creates a health handler.
         *
         * @param server running server
         */
        private HealthHandler(RunningServer server) {
            this.server = server;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                writeJson(exchange, HTTP_METHOD_NOT_ALLOWED,
                        "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            writeJson(exchange, HTTP_OK, "{\"ok\":true,\"schema\":" + jsonString(SCHEMA)
                    + ",\"host\":" + jsonString(server.host())
                    + ",\"port\":" + server.port()
                    + ",\"loopback\":true}");
        }
    }

    /**
     * Handles command-catalog requests.
     */
    private static final class CatalogHandler implements com.sun.net.httpserver.HttpHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                writeJson(exchange, HTTP_METHOD_NOT_ALLOWED,
                        "{\"ok\":false,\"error\":\"method not allowed\"}");
                return;
            }
            CliResult result = dispatch(new String[] {"help", "--json"});
            writeJson(exchange, result.exitCode() == 0 ? HTTP_OK : HTTP_BAD_REQUEST, result.stdout());
        }
    }

    /**
     * Handles JSON-RPC command requests.
     */
    private static final class RpcHandler implements com.sun.net.httpserver.HttpHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                writeJson(exchange, HTTP_METHOD_NOT_ALLOWED,
                        error("null", -32600, "POST required"));
                return;
            }
            String body = readBody(exchange);
            String id = requestId(body);
            String method = Json.parseStringField(body, "method");
            if (!"run".equals(method)) {
                writeJson(exchange, HTTP_BAD_REQUEST, error(id, -32601, "unsupported method: " + method));
                return;
            }
            if (!hasField(body, "argv")) {
                writeJson(exchange, HTTP_BAD_REQUEST, error(id, -32602, "params.argv string array required"));
                return;
            }
            String[] argv = Json.parseStringArrayField(body, "argv");
            CliResult result = dispatch(argv);
            writeJson(exchange, HTTP_OK, success(id, result.toJson()));
        }
    }

    /**
     * Parsed serve options.
     *
     * @param host loopback host
     * @param port TCP port
     * @param json true to print startup JSON
     * @param verbose true for verbose startup failures
     */
    private record Options(String host, int port, boolean json, boolean verbose) {
    }

    /**
     * Captured CLI dispatch result.
     *
     * @param exitCode CLI exit code
     * @param stdout captured standard output
     * @param stderr captured standard error
     */
    private record CliResult(int exitCode, String stdout, String stderr) {

        /**
         * Serializes the result as JSON.
         *
         * @return JSON object
         */
        private String toJson() {
            return "{\"exitCode\":" + exitCode
                    + ",\"stdout\":" + jsonString(stdout)
                    + ",\"stderr\":" + jsonString(stderr)
                    + ",\"argv\":" + Json.stringArray(Main.lastInvocationArgv().toArray(String[]::new))
                    + "}";
        }
    }

    /**
     * Running server handle.
     */
    public static final class RunningServer implements AutoCloseable {

        /**
         * HTTP server.
         */
        private final com.sun.net.httpserver.HttpServer server;

        /**
         * Request executor.
         */
        private final ExecutorService executor;

        /**
         * Bound address.
         */
        private final InetAddress address;

        /**
         * Stop latch.
         */
        private final CountDownLatch stopped;

        /**
         * Creates a running server handle.
         *
         * @param server HTTP server
         * @param executor request executor
         * @param address bound address
         * @param stopped stop latch
         */
        private RunningServer(com.sun.net.httpserver.HttpServer server, ExecutorService executor,
                InetAddress address, CountDownLatch stopped) {
            this.server = server;
            this.executor = executor;
            this.address = address;
            this.stopped = stopped;
        }

        /**
         * Returns the bound host address.
         *
         * @return host address
         */
        public String host() {
            return address.getHostAddress();
        }

        /**
         * Returns the bound TCP port.
         *
         * @return port
         */
        public int port() {
            return server.getAddress().getPort();
        }

        /**
         * Returns the base HTTP URI.
         *
         * @return base URI
         */
        public URI baseUri() {
            return URI.create("http://" + host() + ":" + port());
        }

        /**
         * Returns a human-readable startup message.
         *
         * @return startup text
         */
        public String startupText() {
            return "crtk serve listening on " + baseUri() + " (endpoints: /rpc, /catalog, /health)";
        }

        /**
         * Returns a JSON startup message.
         *
         * @return startup JSON
         */
        public String startupJson() {
            String base = baseUri().toString();
            return "{\"ok\":true,\"schema\":" + jsonString(SCHEMA)
                    + ",\"host\":" + jsonString(host())
                    + ",\"port\":" + port()
                    + ",\"rpc\":" + jsonString(base + "/rpc")
                    + ",\"catalog\":" + jsonString(base + "/catalog")
                    + ",\"health\":" + jsonString(base + "/health")
                    + "}";
        }

        /**
         * Blocks until the server is stopped.
         *
         * @throws InterruptedException if interrupted
         */
        public void await() throws InterruptedException {
            stopped.await();
        }

        /**
         * Stops the server and releases its executor.
         */
        public void stop() {
            server.stop(0);
            executor.shutdownNow();
            stopped.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            stop();
        }

        /**
         * Returns a concise debug string.
         *
         * @return debug string
         */
        @Override
        public String toString() {
            return startupText();
        }
    }

}
