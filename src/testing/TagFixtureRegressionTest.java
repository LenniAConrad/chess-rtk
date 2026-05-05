package testing;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import chess.core.Position;
import chess.tag.Generator;

/**
 * TSV-backed no-framework regression checks for canonical static tags.
 */
public final class TagFixtureRegressionTest {

    /**
     * Fixture directory.
     */
    private static final Path FIXTURE_DIR = Paths.get("testdata", "tags");

    /**
     * Prevents instantiation.
     */
    private TagFixtureRegressionTest() {
        // utility
    }

    /**
     * Runs all tag fixtures.
     *
     * @param args ignored
     * @throws IOException if fixture files cannot be read
     */
    public static void main(String[] args) throws IOException {
        List<Path> files = fixtureFiles();
        int rows = 0;
        int failures = 0;
        for (Path file : files) {
            for (Fixture fixture : load(file)) {
                rows++;
                failures += check(file, fixture);
            }
        }
        if (failures > 0) {
            throw new AssertionError("TagFixtureRegressionTest: " + failures + " failures across " + rows + " rows");
        }
        System.out.println("TagFixtureRegressionTest: " + rows + " fixture rows passed");
    }

    /**
     * Returns all TSV fixture files.
     *
     * @return sorted fixture paths
     * @throws IOException if the fixture directory cannot be read
     */
    private static List<Path> fixtureFiles() throws IOException {
        if (!Files.isDirectory(FIXTURE_DIR)) {
            throw new AssertionError("missing fixture directory: " + FIXTURE_DIR);
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(FIXTURE_DIR, "*.tsv")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        if (files.isEmpty()) {
            throw new AssertionError("no tag fixture files under " + FIXTURE_DIR);
        }
        return files;
    }

    /**
     * Loads fixtures from a TSV file.
     *
     * @param file fixture path
     * @return loaded fixtures
     * @throws IOException if the file cannot be read
     */
    private static List<Fixture> load(Path file) throws IOException {
        List<Fixture> fixtures = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length != 4) {
                throw new AssertionError(file + ":" + lineNumber + " expected 4 TSV columns but found "
                        + cols.length);
            }
            fixtures.add(new Fixture(cols[0], cols[1], parseTags(cols[2]), parseTags(cols[3])));
        }
        return fixtures;
    }

    /**
     * Checks one fixture row.
     *
     * @param file fixture file
     * @param fixture fixture row
     * @return failure count for the row
     */
    private static int check(Path file, Fixture fixture) {
        List<String> tags = Generator.tags(new Position(fixture.fen));
        int failures = 0;
        for (String required : fixture.required) {
            if (!tags.contains(required)) {
                failures++;
                System.err.println(file + " " + fixture.id + " missing tag: " + required);
                System.err.println("actual: " + tags);
            }
        }
        for (String forbidden : fixture.forbidden) {
            if (tags.contains(forbidden)) {
                failures++;
                System.err.println(file + " " + fixture.id + " unexpected tag: " + forbidden);
                System.err.println("actual: " + tags);
            }
        }
        return failures;
    }

    /**
     * Parses a semicolon-separated tag column.
     *
     * @param value raw column value
     * @return parsed tags
     */
    private static List<String> parseTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String tag : value.split(";")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    /**
     * One TSV fixture row.
     */
    private static final class Fixture {

        /**
         * Fixture id.
         */
        private final String id;

        /**
         * Position FEN.
         */
        private final String fen;

        /**
         * Tags that must be present.
         */
        private final List<String> required;

        /**
         * Tags that must be absent.
         */
        private final List<String> forbidden;

        /**
         * Creates a fixture row.
         *
         * @param id fixture id
         * @param fen position FEN
         * @param required required tags
         * @param forbidden forbidden tags
         */
        private Fixture(String id, String fen, List<String> required, List<String> forbidden) {
            this.id = id;
            this.fen = fen;
            this.required = required;
            this.forbidden = forbidden;
        }
    }
}
