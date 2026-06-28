package testing;

import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.readUtf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Workbench user-facing documentation.
     */
    private static final Path WORKBENCH_DOC = Path.of("wiki", "workbench.md");

    /**
     * Window source that registers the top-level workbench views.
     */
    private static final Path WINDOW_LIFECYCLE_SOURCE =
            WORKBENCH_ROOT.resolve(Path.of("window", "WindowLifecycle.java"));

    /**
     * Root Java package for the Swing workbench.
     */
    private static final String WORKBENCH_PACKAGE = "application.gui.workbench";

    /**
     * Source root for the shared Swing UI layer.
     */
    private static final Path UI_SOURCE_ROOT = WORKBENCH_ROOT.resolve("ui");

    /**
     * Java package for the shared Swing UI layer.
     */
    private static final String UI_PACKAGE = WORKBENCH_PACKAGE + ".ui";

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
     * Heading that introduces the user-facing registered-view table.
     */
    private static final String REGISTERED_VIEWS_HEADING = "## Registered views";

    /**
     * Maximum allowed implementation-line count for a single workbench source file.
     */
    private static final int MAX_WORKBENCH_IMPLEMENTATION_LINES = 2_000;

    /**
     * Pattern that extracts registered-view labels from the shell source.
     */
    private static final Pattern REGISTERED_VIEW_LABEL =
            Pattern.compile("new\\s+RegisteredView\\(\"([^\"]+)\"");

    /**
     * Pattern that extracts public top-level UI types.
     */
    private static final Pattern PUBLIC_TOP_LEVEL_UI_TYPE =
            Pattern.compile("(?m)^public\\s+(?:final\\s+)?(?:class|interface|enum)\\s+([A-Za-z0-9_]+)\\b");

    /**
     * Pattern that extracts imports from the shared UI package.
     */
    private static final Pattern UI_IMPORT =
            Pattern.compile("import\\s+" + Pattern.quote(UI_PACKAGE) + "\\.([A-Za-z0-9_]+)\\s*;");

    /**
     * Feature packages expected after the workbench refactor.
     */
    private static final Set<String> EXPECTED_FEATURE_PACKAGES = Set.of(
            "audio",
            "board",
            "command",
            "dashboard",
            "draw",
            "engine",
            "game",
            "launch",
            "layout",
            "mcts",
            "network",
            "play",
            "publish",
            "relations",
            "session",
            "ui",
            "window");

    /**
     * Public reusable UI primitives that feature packages may import directly.
     * New public classes must be added here intentionally; otherwise helpers
     * should stay package-private behind {@code Ui} or {@code Theme}.
     */
    private static final Set<String> UI_PUBLIC_API_CLASSES = Set.of(
            "AnalysisGraph",
            "AppIcon",
            "BackdropPanel",
            "ChipGroup",
            "EvalBar",
            "FieldValidator",
            "FileDialogs",
            "HitRegions",
            "HoldButton",
            "InspectorDialog",
            "InspectorPanel",
            "LoadingOverlay",
            "ModalOverlay",
            "NotationPainter",
            "RenderAcceleration",
            "SegmentedSwitcher",
            "SettingsChipRow",
            "StatusBadge",
            "SurfacePanel",
            "SwingTasks",
            "SwitchedWorkspace",
            "TagCloud",
            "Theme",
            "Toast",
            "ToggleBox",
            "Ui",
            "WorkspaceHeader",
            "WorkspaceMode",
            "WrappingFlowLayout");

    /**
     * Implementation-only UI helpers that feature packages must not import.
     */
    private static final Set<String> UI_INTERNAL_IMPLEMENTATION_CLASSES = Set.of(
            "AppButton",
            "ArrowButton",
            "CenteredViewportPanel",
            "Card",
            "CheckBoxGlyph",
            "CollapsibleSection",
            "ComponentTreeStyler",
            "ControlStyler",
            "CardGrid",
            "CommandBlock",
            "DataTableStyler",
            "DocumentChangeSupport",
            "EmptyState",
            "FieldRow",
            "FileChooserIcons",
            "FileChooserStyler",
            "IconButton",
            "InputChrome",
            "InspectorText",
            "MiniChart",
            "MenuGlyphs",
            "OptionPaneStyler",
            "PlaceholderPainter",
            "PlaceholderTextAreaUI",
            "PlaceholderTextFieldUI",
            "PopupMenuStyler",
            "ProgressBarChrome",
            "RoundedInputBorder",
            "ScrollPaneStyler",
            "SectionHeader",
            "Spinner",
            "StyledButton",
            "StyledComboBoxUI",
            "StyledComboRenderer",
            "StyledScrollBarUI",
            "StyledSliderUI",
            "StyledSpinnerUI",
            "SvgIcon",
            "Tabs",
            "ThemeBorders",
            "ThemeColors",
            "ThemeComponents",
            "ThemeFonts",
            "ThemeForeground",
            "ThemeInstaller",
            "ThemePalette",
            "ThemeRefresh",
            "ThemeState",
            "Tooltip",
            "TomlHighlighter",
            "TreeStyler",
            "UiFormControls",
            "UiLayout",
            "UiMotion",
            "UiSurfaces",
            "UiText",
            "ViewportPanel");

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
        testWorkbenchUiPublicSurfaceStaysIntentional();
        testWorkbenchUiInternalsStayPrivateToUiPackage();
        testWorkbenchDocsMatchRegisteredViews();
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
            String source = readUtf8(file);
            assertTrue(source.contains(PACKAGE_DECLARATION_PREFIX + expectedPackage
                    + PACKAGE_DECLARATION_SUFFIX),
                    WORKBENCH_ROOT.relativize(file) + " declares " + expectedPackage);
        }
    }

    /**
     * Verifies the reusable UI package exposes only intentional public entry
     * points. Most helper classes should stay package-private behind the
     * {@code Ui} and {@code Theme} facades.
     */
    private static void testWorkbenchUiPublicSurfaceStaysIntentional() {
        for (Path file : uiJavaFiles()) {
            Matcher matcher = PUBLIC_TOP_LEVEL_UI_TYPE.matcher(readUtf8(file));
            while (matcher.find()) {
                String typeName = matcher.group(1);
                assertTrue(UI_PUBLIC_API_CLASSES.contains(typeName),
                        WORKBENCH_ROOT.relativize(file) + " exposes intentional UI API " + typeName);
            }
        }
    }

    /**
     * Verifies feature packages do not couple to implementation-only UI helpers.
     */
    private static void testWorkbenchUiInternalsStayPrivateToUiPackage() {
        for (Path file : workbenchJavaFiles()) {
            if (file.startsWith(UI_SOURCE_ROOT)) {
                continue;
            }
            String source = readUtf8(file);
            Matcher matcher = UI_IMPORT.matcher(source);
            while (matcher.find()) {
                String typeName = matcher.group(1);
                assertFalse(UI_INTERNAL_IMPLEMENTATION_CLASSES.contains(typeName),
                        WORKBENCH_ROOT.relativize(file) + " does not import UI internal " + typeName);
            }
            for (String typeName : UI_INTERNAL_IMPLEMENTATION_CLASSES) {
                assertFalse(source.contains(UI_PACKAGE + "." + typeName),
                        WORKBENCH_ROOT.relativize(file) + " does not fully qualify UI internal " + typeName);
            }
        }
    }

    /**
     * Verifies the user-facing Workbench view table follows the registered shell
     * instead of drifting back to older flat-tab names.
     */
    private static void testWorkbenchDocsMatchRegisteredViews() {
        List<String> registeredViews = registeredViewLabels();
        String docs = readUtf8(WORKBENCH_DOC);
        assertTrue(registeredViews.size() == 8, "workbench shell registers 8 top-level views");
        assertTrue(docs.contains(REGISTERED_VIEWS_HEADING), "workbench docs have registered-view heading");
        for (String view : registeredViews) {
            assertTrue(docs.contains("| " + view + " |"),
                    "workbench docs include registered view " + view);
        }
        assertFalse(docs.contains("| Analyze | Interactive board"),
                "workbench docs no longer list Analyze as a top-level view");
        assertFalse(docs.contains("| Play | Human-vs-engine"),
                "workbench docs no longer list Play as a top-level view");
        assertFalse(docs.contains("| Network | NNUE"),
                "workbench docs no longer list Network as a top-level view");
        assertFalse(docs.contains("| Puzzles | Interactive puzzle"),
                "workbench docs no longer list Puzzles as a top-level view");
    }

    /**
     * Extracts the registered top-level Workbench view labels from the shell.
     *
     * @return labels in registration order
     */
    private static List<String> registeredViewLabels() {
        Matcher matcher = REGISTERED_VIEW_LABEL.matcher(readUtf8(WINDOW_LIFECYCLE_SOURCE));
        List<String> labels = new ArrayList<>();
        while (matcher.find()) {
            labels.add(matcher.group(1));
        }
        return List.copyOf(labels);
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
     * Lists Java files under the shared UI source root.
     *
     * @return sorted shared UI Java files
     */
    private static List<Path> uiJavaFiles() {
        return workbenchJavaFiles().stream()
                .filter(file -> file.startsWith(UI_SOURCE_ROOT))
                .toList();
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
