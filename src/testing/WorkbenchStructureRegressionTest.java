package testing;

import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Regression checks for the split workbench source layout.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class WorkbenchStructureRegressionTest {

    /**
     * Workbench source root.
     */
    private static final Path WORKBENCH_ROOT = Path.of("src/application/gui/workbench");

    /**
     * Root Java package for the Swing workbench.
     */
    private static final String WORKBENCH_PACKAGE = "application.gui.workbench";

    /**
     * Workbench name token that should not be repeated in file names.
     */
    private static final String WORKBENCH_NAME_TOKEN = "Workbench";

    /**
     * Java source filename suffix.
     */
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    /**
     * Conventional package documentation filename.
     */
    private static final String PACKAGE_INFO_FILE = "package-info.java";

    /**
     * Prefix used by Java package declarations.
     */
    private static final String PACKAGE_DECLARATION_PREFIX = "package ";

    /**
     * Suffix used by Java package declarations.
     */
    private static final String PACKAGE_DECLARATION_SUFFIX = ";";

    /**
     * Maximum allowed implementation-line count for a single workbench source file.
     */
    private static final int MAX_WORKBENCH_IMPLEMENTATION_LINES = 2_000;

    /**
     * Feature packages expected after the workbench refactor.
     */
    private static final Set<String> EXPECTED_FEATURE_PACKAGES = Set.of(
            "audio",
            "board",
            "command",
            "dashboard",
            "game",
            "launch",
            "layout",
            "mcts",
            "network",
            "publish",
            "session",
            "ui",
            "window");

    /**
     * Prevents instantiation.
     */
    private WorkbenchStructureRegressionTest() {
        // utility
    }

    /**
     * Runs all workbench structure regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testWorkbenchSourceRootExists();
        testWorkbenchFilesStayBelowLineLimit();
        testWorkbenchFileNamesStayPackageScoped();
        testWorkbenchPackageLayoutStaysSplit();
        testWorkbenchPackageDeclarationsMatchPaths();
        System.out.println("WorkbenchStructureRegressionTest: all checks passed");
    }

    /**
     * Verifies the workbench source root exists before layout assertions run.
     */
    private static void testWorkbenchSourceRootExists() {
        assertTrue(Files.isDirectory(WORKBENCH_ROOT), "workbench source root exists");
    }

    /**
     * Verifies no workbench implementation file regresses into a large monolith.
     */
    private static void testWorkbenchFilesStayBelowLineLimit() {
        for (Path file : workbenchJavaFiles()) {
            int lines = implementationLineCount(file);
            assertTrue(lines <= MAX_WORKBENCH_IMPLEMENTATION_LINES,
                    WORKBENCH_ROOT.relativize(file) + " has at most "
                            + MAX_WORKBENCH_IMPLEMENTATION_LINES + " implementation lines");
        }
    }

    /**
     * Verifies workbench file names do not redundantly repeat the package name.
     */
    private static void testWorkbenchFileNamesStayPackageScoped() {
        for (Path file : workbenchJavaFiles()) {
            assertFalse(file.getFileName().toString().contains(WORKBENCH_NAME_TOKEN),
                    WORKBENCH_ROOT.relativize(file) + " avoids redundant Workbench prefix");
        }
    }

    /**
     * Verifies the workbench stays organized into feature packages.
     */
    private static void testWorkbenchPackageLayoutStaysSplit() {
        for (String packageName : EXPECTED_FEATURE_PACKAGES) {
            Path packageDir = WORKBENCH_ROOT.resolve(packageName);
            assertTrue(Files.isDirectory(packageDir), packageName + " package exists");
            assertTrue(Files.isRegularFile(packageDir.resolve(PACKAGE_INFO_FILE)),
                    packageName + " package has package-info.java");
        }

        long rootImplementationFiles = workbenchJavaFiles().stream()
                .filter(file -> WORKBENCH_ROOT.equals(file.getParent()))
                .filter(file -> !file.getFileName().toString().equals(PACKAGE_INFO_FILE))
                .count();
        assertTrue(rootImplementationFiles <= 1,
                "workbench root package contains only shared root-level implementation");
    }

    /**
     * Verifies Java package declarations mirror the directory layout.
     */
    private static void testWorkbenchPackageDeclarationsMatchPaths() {
        for (Path file : workbenchJavaFiles()) {
            String expectedPackage = expectedPackageFor(file);
            String source = readString(file);
            assertTrue(source.contains(PACKAGE_DECLARATION_PREFIX + expectedPackage
                    + PACKAGE_DECLARATION_SUFFIX),
                    WORKBENCH_ROOT.relativize(file) + " declares " + expectedPackage);
        }
    }

    /**
     * Lists Java files under the workbench source root.
     *
     * @return sorted workbench Java files
     */
    private static List<Path> workbenchJavaFiles() {
        try (Stream<Path> files = Files.walk(WORKBENCH_ROOT)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(JAVA_SOURCE_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("could not list workbench Java files", ex);
        }
    }

    /**
     * Counts non-comment, non-blank implementation lines in one source file.
     *
     * @param file source file
     * @return number of implementation lines
     */
    private static int implementationLineCount(Path file) {
        try {
            int count = 0;
            boolean inBlockComment = false;
            for (String line : Files.readAllLines(file)) {
                CommentStrip strip = stripComments(line, inBlockComment);
                inBlockComment = strip.inBlockComment();
                if (!strip.code().isBlank()) {
                    count++;
                }
            }
            return count;
        } catch (IOException ex) {
            throw new AssertionError("could not count lines for " + file, ex);
        }
    }

    /**
     * Removes Java line and block comments from one physical line.
     *
     * @param line source line
     * @param inBlockComment true when a previous line opened a block comment
     * @return remaining code and updated block-comment state
     */
    private static CommentStrip stripComments(String line, boolean inBlockComment) {
        StringBuilder code = new StringBuilder(line.length());
        int index = 0;
        boolean inBlock = inBlockComment;
        while (index < line.length()) {
            if (inBlock) {
                int end = line.indexOf("*/", index);
                if (end < 0) {
                    return new CommentStrip(code.toString(), true);
                }
                index = end + 2;
                inBlock = false;
                continue;
            }
            int lineComment = line.indexOf("//", index);
            int blockComment = line.indexOf("/*", index);
            if (lineComment >= 0 && (blockComment < 0 || lineComment < blockComment)) {
                code.append(line, index, lineComment);
                break;
            }
            if (blockComment >= 0) {
                code.append(line, index, blockComment);
                index = blockComment + 2;
                inBlock = true;
                continue;
            }
            code.append(line, index, line.length());
            break;
        }
        return new CommentStrip(code.toString(), inBlock);
    }

    /**
     * Comment-stripping result for one physical source line.
     *
     * @param code source text outside comments
     * @param inBlockComment true when a block comment remains open
     */
    private record CommentStrip(String code, boolean inBlockComment) {
    }

    /**
     * Reads a UTF-8 source file into memory.
     *
     * @param file source file
     * @return source text
     */
    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new AssertionError("could not read " + file, ex);
        }
    }

    /**
     * Computes the package expected for a workbench source file path.
     *
     * @param file source file
     * @return expected Java package
     */
    private static String expectedPackageFor(Path file) {
        Path parent = WORKBENCH_ROOT.relativize(file.getParent());
        if (parent.toString().isEmpty()) {
            return WORKBENCH_PACKAGE;
        }
        return WORKBENCH_PACKAGE + "." + parent.toString().replace(file.getFileSystem().getSeparator(), ".");
    }

}
