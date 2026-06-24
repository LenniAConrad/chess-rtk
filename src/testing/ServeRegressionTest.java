package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import application.cli.command.ServeCommand;
import application.cli.command.ServeCommand.RunningServer;
import utility.Json;

/**
 * Regression checks for the localhost JSON-RPC daemon.
 */
public final class ServeRegressionTest {

    /**
     * Valid standard chess starting position.
     */
    private static final String VALID_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Utility class; prevent instantiation.
     */
    private ServeRegressionTest() {
        // utility
    }

    /**
     * Runs every serve regression check.
     *
     * @param args ignored command-line arguments
     * @throws Exception if a round-trip fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        testLoopbackServerRoundTrip();
        testNonLoopbackBindRejected();
        System.out.println("ServeRegressionTest: all checks passed");
    }

    /**
     * Verifies health, catalog, and JSON-RPC command dispatch over loopback.
     *
     * @throws Exception if the HTTP round-trip fails
     */
    private static void testLoopbackServerRoundTrip() throws Exception {
        try (RunningServer server = ServeCommand.start("127.0.0.1", 0)) {
            String base = server.baseUri().toString();
            HttpResult health = get(URI.create(base + "/health"));
            assertEquals(200, health.status(), "serve health status");
            assertTrue(health.body().contains("\"schema\":\"crtk.serve.v1\""), "serve health schema");
            assertTrue(health.body().contains("\"loopback\":true"), "serve health loopback");

            HttpResult catalog = get(URI.create(base + "/catalog"));
            assertEquals(200, catalog.status(), "serve catalog status");
            assertTrue(catalog.body().contains("\"schemaVersion\": \"crtk.cli.catalog.v1\""),
                    "serve catalog exposes help json");
            assertTrue(catalog.body().contains("\"path\": \"serve\""), "serve catalog includes serve command");

            String expected = TestSupport.runMain("fen", "validate", "--fen", VALID_START_FEN, "--json");
            String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"run\","
                    + "\"params\":{\"argv\":[\"fen\",\"validate\",\"--fen\"," + quote(VALID_START_FEN)
                    + ",\"--json\"]}}";
            HttpResult response = post(URI.create(base + "/rpc"), request);
            assertEquals(200, response.status(), "serve rpc status");
            assertTrue(response.body().contains("\"jsonrpc\":\"2.0\""), "serve rpc envelope");
            assertEquals(expected, Json.parseStringField(response.body(), "stdout"),
                    "serve rpc stdout matches CLI");
            assertEquals("", Json.parseStringField(response.body(), "stderr"),
                    "serve rpc stderr empty");
            assertTrue(response.body().contains("\"exitCode\":0"), "serve rpc exit code");

            HttpResult missing = get(URI.create(base + "/play"));
            assertEquals(404, missing.status(), "serve has no play endpoint");
        }
    }

    /**
     * Verifies non-loopback binding is refused.
     *
     * @throws Exception if address resolution fails unexpectedly
     */
    private static void testNonLoopbackBindRejected() throws Exception {
        try {
            ServeCommand.start(InetAddress.getByName("0.0.0.0"), 0);
            throw new AssertionError("non-loopback bind unexpectedly succeeded");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("refusing to bind non-loopback"),
                    "serve non-loopback diagnostic");
        }
    }

    /**
     * Performs a GET request.
     *
     * @param uri target URI
     * @return HTTP
     * @throws IOException if the request fails
     */
    private static HttpResult get(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        return read(connection);
    }

    /**
     * Performs a JSON POST request.
     *
     * @param uri target URI
     * @param body request body
     * @return HTTP
     * @throws IOException if the request fails
     */
    private static HttpResult post(URI uri, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        return read(connection);
    }

    /**
     * Reads an HTTP response.
     *
     * @param connection opened connection
     * @return parsed an HTTP response
     * @throws IOException if reading fails
     */
    private static HttpResult read(HttpURLConnection connection) throws IOException {
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body;
        try (InputStream in = stream) {
            body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
        return new HttpResult(status, body);
    }

    /**
     * Returns a JSON string literal.
     *
     * @param value source text
     * @return JSON string
     */
    private static String quote(String value) {
        return "\"" + Json.esc(value) + "\"";
    }

    /**
     * HTTP response tuple.
     *
     * @param status HTTP status
     * @param body response body
     */
    private record HttpResult(int status, String body) {
    }
}
