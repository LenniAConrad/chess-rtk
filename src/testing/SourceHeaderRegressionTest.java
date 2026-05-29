package testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Regression checks for repository-wide Java source layout.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SourceHeaderRegressionTest {

    /**
     * Java source root checked by this regression.
     */
    private static final Path SOURCE_ROOT = Path.of("src");

    /**
     * Core chess source root.
     */
    private static final Path CHESS_ROOT = SOURCE_ROOT.resolve("chess");

    /**
     * Detached top-of-file attribution banner that should not be used.
     */
    private static final String DETACHED_ATTRIBUTION_HEADER = "/**\n"
            + " * Source file " + "attribution.\n"
            + " *\n"
            + " * @author Lennart A. Conrad\n"
            + " */\n\n";

    /**
     * Java source filename suffix.
     */
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    /**
     * Conventional package documentation filename.
     */
    private static final String PACKAGE_INFO_FILE = "package-info.java";

    /**
     * Java package declaration pattern.
     */
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;");

    /**
     * Java import declaration pattern.
     */
    private static final Pattern IMPORT_DECLARATION = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
                    + "(?:\\.\\*)?)\\s*;");

    /**
     * Public top-level type declaration pattern. The match intentionally starts
     * at column zero so nested public members do not look like top-level types.
     */
    private static final Pattern PUBLIC_TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^public\\s+(?:(?:abstract|final|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /**
     * Type declaration pattern for declarations that need API Javadocs.
     */
    private static final Pattern DOCUMENTED_TYPE_DECLARATION = Pattern.compile(
            "\\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\b.*");

    /**
     * Public or protected member declaration pattern for declarations that need
     * API Javadocs.
     */
    private static final Pattern DOCUMENTED_MEMBER_DECLARATION = Pattern.compile(
            "\\s*(?:public|protected)\\b.*(?:\\([^;]*\\)\\s*(?:throws\\s+[^{}]+)?\\{|[=;]).*");

    /**
     * Prevents instantiation.
     */
    private SourceHeaderRegressionTest() {
        // utility
    }

    /**
     * Runs all source-layout regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testPackageDeclarationsMatchPaths();
        testPublicTypesMatchFileNames();
        testLayerBoundaryImports();
        testJavadocsUseMultilineShape();
        testApiDeclarationsHaveJavadocs();
        List<Path> filesWithDetachedHeaders = new ArrayList<>();
        for (Path sourceFile : javaSources()) {
            if (hasDetachedAttributionHeader(sourceFile)) {
                filesWithDetachedHeaders.add(sourceFile);
            }
        }
        if (!filesWithDetachedHeaders.isEmpty()) {
            throw new AssertionError("detached Java source attribution header: " + filesWithDetachedHeaders);
        }
        System.out.println("SourceHeaderRegressionTest: all checks passed");
    }

    /**
     * Verifies public/protected API declarations and package-private top-level
     * type declarations have multiline Javadocs.
     */
    private static void testApiDeclarationsHaveJavadocs() {
        List<String> missing = new ArrayList<>();
        for (Path sourceFile : javaSources()) {
            if (PACKAGE_INFO_FILE.equals(sourceFile.getFileName().toString())) {
                continue;
            }
            List<String> lines = readSource(sourceFile).lines().toList();
            boolean inTextBlock = false;
            for (int i = 0; i < lines.size(); i++) {
                if (!inTextBlock && requiresJavadoc(lines, i) && !hasMultilineJavadocBefore(lines, i)) {
                    missing.add(sourceFile + ":" + (i + 1) + " is missing multiline Javadoc");
                }
                if (countTextBlockDelimiters(lines.get(i)) % 2 == 1) {
                    inTextBlock = !inTextBlock;
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("missing API Javadocs: " + missing);
        }
    }

    /**
     * Verifies Javadocs use the repository's multiline block shape.
     */
    private static void testJavadocsUseMultilineShape() {
        List<String> malformed = new ArrayList<>();
        for (Path sourceFile : javaSources()) {
            List<String> lines = readSource(sourceFile).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (!trimmed.startsWith("/**")) {
                    continue;
                }
                if (!"/**".equals(trimmed)) {
                    malformed.add(sourceFile + ":" + (i + 1) + " has a non-multiline Javadoc opener");
                }
                boolean closed = false;
                while (i < lines.size()) {
                    String blockLine = lines.get(i).trim();
                    if (blockLine.contains("*/")) {
                        if (!"*/".equals(blockLine)) {
                            malformed.add(sourceFile + ":" + (i + 1)
                                    + " has a non-multiline Javadoc closer");
                        }
                        closed = true;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    malformed.add(sourceFile + " has an unterminated Javadoc block");
                }
            }
        }
        if (!malformed.isEmpty()) {
            throw new AssertionError("malformed Javadocs: " + malformed);
        }
    }

    /**
     * Returns whether a source line is a declaration that should have Javadoc.
     *
     * @param lines source lines
     * @param index line index
     * @return true when the line requires Javadoc
     */
    private static boolean requiresJavadoc(List<String> lines, int index) {
        String trimmed = lines.get(index).trim();
        if (trimmed.isEmpty()
                || trimmed.startsWith("//")
                || trimmed.startsWith("*")
                || trimmed.startsWith("@")
                || trimmed.startsWith("case ")
                || trimmed.startsWith("default")
                || trimmed.startsWith("if ")
                || trimmed.startsWith("for ")
                || trimmed.startsWith("while ")
                || trimmed.startsWith("switch ")
                || trimmed.startsWith("catch ")
                || trimmed.startsWith("try ")
                || trimmed.startsWith("return ")
                || trimmed.startsWith("throw ")
                || trimmed.startsWith("new ")
                || trimmed.startsWith("this(")
                || trimmed.startsWith("super(")
                || trimmed.startsWith("synchronized (")) {
            return false;
        }
        return DOCUMENTED_TYPE_DECLARATION.matcher(trimmed).matches()
                || DOCUMENTED_MEMBER_DECLARATION.matcher(trimmed).matches();
    }

    /**
     * Returns whether a declaration line is preceded by a multiline Javadoc block.
     *
     * @param lines source lines
     * @param index declaration line index
     * @return true when a multiline Javadoc immediately precedes the declaration
     */
    private static boolean hasMultilineJavadocBefore(List<String> lines, int index) {
        int cursor = index - 1;
        while (cursor >= 0) {
            String trimmed = lines.get(cursor).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("@")) {
                break;
            }
            cursor--;
        }
        if (cursor < 0 || !"*/".equals(lines.get(cursor).trim())) {
            return false;
        }
        int end = cursor;
        while (cursor >= 0 && !lines.get(cursor).contains("/**")) {
            cursor--;
        }
        return cursor >= 0 && cursor < end && "/**".equals(lines.get(cursor).trim());
    }

    /**
     * Counts Java text-block delimiters on a source line.
     *
     * @param line source line
     * @return delimiter count
     */
    private static int countTextBlockDelimiters(String line) {
        int count = 0;
        int from = 0;
        while (from < line.length()) {
            int index = line.indexOf("\"\"\"", from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + 3;
        }
        return count;
    }

    /**
     * Verifies reusable source layers do not import higher-level application
     * or test packages.
     */
    private static void testLayerBoundaryImports() {
        assertSourcesDoNotImport(CHESS_ROOT, "application.",
                "imports application-layer code from reusable chess-domain code");
        assertSourcesDoNotImport(CHESS_ROOT, "testing.",
                "imports test code from reusable chess-domain code");
        assertSourcesDoNotImport(SOURCE_ROOT.resolve("application"), "testing.",
                "imports test code from application code");
    }

    /**
     * Verifies every source file declares the Java package implied by its path.
     */
    private static void testPackageDeclarationsMatchPaths() {
        for (Path sourceFile : javaSources()) {
            String source = readSource(sourceFile);
            String expectedPackage = expectedPackageFor(sourceFile);
            Matcher matcher = PACKAGE_DECLARATION.matcher(source);
            if (!matcher.find()) {
                throw new AssertionError(sourceFile + " is missing package declaration");
            }
            String actualPackage = matcher.group(1);
            if (!expectedPackage.equals(actualPackage)) {
                throw new AssertionError(sourceFile + " declares " + actualPackage
                        + " but path expects " + expectedPackage);
            }
        }
    }

    /**
     * Verifies public top-level classes, records, interfaces, annotations, and
     * enums match the source file name.
     */
    private static void testPublicTypesMatchFileNames() {
        for (Path sourceFile : javaSources()) {
            if (PACKAGE_INFO_FILE.equals(sourceFile.getFileName().toString())) {
                continue;
            }
            String expectedTypeName = fileStem(sourceFile);
            Matcher matcher = PUBLIC_TOP_LEVEL_TYPE.matcher(readSource(sourceFile));
            List<String> publicTypes = new ArrayList<>();
            while (matcher.find()) {
                publicTypes.add(matcher.group(1));
            }
            if (publicTypes.size() > 1) {
                throw new AssertionError(sourceFile + " declares multiple public top-level types: " + publicTypes);
            }
            if (publicTypes.size() == 1 && !expectedTypeName.equals(publicTypes.get(0))) {
                throw new AssertionError(sourceFile + " public type " + publicTypes.get(0)
                        + " does not match file name " + expectedTypeName);
            }
        }
    }

    /**
     * Lists Java source files.
     *
     * @return sorted Java source paths
     */
    private static List<Path> javaSources() {
        return javaSourcesUnder(SOURCE_ROOT);
    }

    /**
     * Lists Java source files under one root.
     *
     * @param root source root
     * @return sorted Java source paths
     */
    private static List<Path> javaSourcesUnder(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JAVA_SOURCE_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("could not list Java source files", ex);
        }
    }

    /**
     * Verifies all Java sources below one root avoid imports from one package
     * prefix.
     *
     * @param root source root to scan
     * @param forbiddenPrefix forbidden package prefix
     * @param message assertion message suffix
     */
    private static void assertSourcesDoNotImport(Path root, String forbiddenPrefix, String message) {
        for (Path sourceFile : javaSourcesUnder(root)) {
            Matcher matcher = IMPORT_DECLARATION.matcher(readSource(sourceFile));
            while (matcher.find()) {
                String importedType = matcher.group(1);
                if (importedType.startsWith(forbiddenPrefix)) {
                    throw new AssertionError(sourceFile + " " + message + ": " + importedType);
                }
            }
        }
    }

    /**
     * Verifies whether one source file starts with a detached attribution banner.
     *
     * @param sourceFile Java source file
     * @return true when the file starts with the detached banner
     */
    private static boolean hasDetachedAttributionHeader(Path sourceFile) {
        return readSource(sourceFile).startsWith(DETACHED_ATTRIBUTION_HEADER);
    }

    /**
     * Reads a Java source file.
     *
     * @param sourceFile Java source file
     * @return source text
     */
    private static String readSource(Path sourceFile) {
        try {
            return Files.readString(sourceFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("could not read Java source file " + sourceFile, ex);
        }
    }

    /**
     * Computes the package implied by a source path.
     *
     * @param sourceFile Java source file
     * @return expected package declaration
     */
    private static String expectedPackageFor(Path sourceFile) {
        return SOURCE_ROOT.relativize(sourceFile.getParent())
                .toString()
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
    }

    /**
     * Returns a source file name without the {@code .java} suffix.
     *
     * @param sourceFile Java source file
     * @return file stem
     */
    private static String fileStem(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        return fileName.substring(0, fileName.length() - JAVA_SOURCE_SUFFIX.length());
    }
}
