package testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.readUtf8;

/**
 * Regression checks for the extracted GUI architecture seams.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GuiArchitectureRegressionTest {

    /**
     * GUI source root.
     */
    private static final Path GUI_ROOT = Path.of("src/application/gui");

    /**
     * Feature source root.
     */
    private static final Path FEATURE_ROOT = GUI_ROOT.resolve("feature");

    /**
     * Foundation source root.
     */
    private static final Path FOUNDATION_ROOT = GUI_ROOT.resolve("foundation");

    /**
     * Architecture baseline file.
     */
    private static final Path BASELINE = Path.of("testdata/gui/architecture-baseline.tsv");

    /**
     * Java source filename suffix.
     */
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    /**
     * Package documentation filename.
     */
    private static final String PACKAGE_INFO = "package-info.java";

    /**
     * Baseline rule for non-static fields on WindowBase.
     */
    private static final String WINDOW_BASE_FIELD_RULE = "window-base-field";

    /**
     * WindowBase source file.
     */
    private static final Path WINDOW_BASE_SOURCE =
            GUI_ROOT.resolve(Path.of("workbench", "window", "WindowBase.java"));

    /**
     * Classes that may extend the legacy window/layer chain.
     */
    private static final Set<String> ALLOWED_WINDOW_EXTENSION_CLASSES = Set.of(
            "Window",
            "WindowLifecycle",
            "WindowBoardLayer",
            "WindowEngineLayer",
            "WindowGameLayer",
            "WindowCommandLayer");

    /**
     * Classes that may extend WindowHost while compatibility adapters remain.
     */
    private static final Set<String> ALLOWED_WINDOW_HOST_SUBCLASSES = Set.of(
            "WindowDashboardActions",
            "WindowPlayHost",
            "WindowRunArtifactHost");

    /**
     * Direct Swing/decorating debt counted against the checked-in baseline.
     */
    private static final List<ScanRule> SCAN_RULES = List.of(
            new ScanRule("raw-swing-construction", Pattern.compile(
                    "\\bnew\\s+(?:JButton|JToggleButton|JCheckBox|JRadioButton|JTextField|JTextArea"
                            + "|JTextPane|JComboBox|JSpinner|JSlider|JTable|JTree|JList|JScrollPane"
                            + "|JTabbedPane|JSplitPane|JProgressBar)\\b")),
            new ScanRule("direct-color-construction", Pattern.compile("\\bnew\\s+Color\\s*\\(")),
            new ScanRule("direct-set-font", Pattern.compile("\\.setFont\\s*\\(")),
            new ScanRule("direct-set-border", Pattern.compile("\\.setBorder\\s*\\(")),
            new ScanRule("ui-manager-access", Pattern.compile("\\bUIManager\\.")));

    /**
     * Class extension pattern.
     */
    private static final Pattern CLASS_EXTENDS = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?class\\s+([A-Za-z0-9_]+)\\s+extends\\s+"
                    + "([A-Za-z0-9_]+)\\b");

    /**
     * Import pattern for sibling feature packages.
     */
    private static final Pattern FEATURE_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+application\\.gui\\.feature\\.([A-Za-z0-9_]+)\\.");

    /**
     * Prevents instantiation.
     */
    private GuiArchitectureRegressionTest() {
        // utility
    }

    /**
     * Runs GUI architecture checks or writes the current debt baseline.
     *
     * @param args optional {@code --write-baseline}
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--write-baseline".equals(args[0])) {
            writeBaseline();
            return;
        }
        testGuiPackagesHavePackageInfo();
        testPackageDeclarationsMatchPaths();
        testFoundationHasNoHigherLayerImports();
        testFeaturePackagesStaySiblingIndependent();
        testFeaturePackagesDoNotReceiveWindowBase();
        testWindowInheritanceDoesNotGrow();
        testDatasetUsesFeatureComposition();
        testPublishingUsesFeatureComposition();
        testReportUsesFeatureComposition();
        testArchitectureDebtDoesNotIncrease();
        System.out.println("GuiArchitectureRegressionTest: all checks passed");
    }

    /**
     * Verifies every GUI package with Java sources has package documentation.
     */
    private static void testGuiPackagesHavePackageInfo() {
        for (Path directory : guiSourceDirectories()) {
            assertTrue(Files.isRegularFile(directory.resolve(PACKAGE_INFO)),
                    GUI_ROOT.relativize(directory) + " has package-info.java");
        }
    }

    /**
     * Verifies package declarations match source paths.
     */
    private static void testPackageDeclarationsMatchPaths() {
        for (Path file : guiJavaFiles()) {
            String expected = expectedPackage(file);
            assertTrue(readUtf8(file).contains("package " + expected + ";"),
                    GUI_ROOT.relativize(file) + " declares " + expected);
        }
    }

    /**
     * Verifies foundation code stays below app, feature, Workbench, CLI, and
     * chess-domain layers.
     */
    private static void testFoundationHasNoHigherLayerImports() {
        for (Path file : guiJavaFiles()) {
            if (!file.startsWith(FOUNDATION_ROOT)) {
                continue;
            }
            String source = readUtf8(file);
            assertFalse(source.contains("import application.gui.app."),
                    GUI_ROOT.relativize(file) + " does not import app");
            assertFalse(source.contains("import application.gui.feature."),
                    GUI_ROOT.relativize(file) + " does not import features");
            assertFalse(source.contains("import application.gui.workbench."),
                    GUI_ROOT.relativize(file) + " does not import workbench");
            assertFalse(source.contains("import application.cli."),
                    GUI_ROOT.relativize(file) + " does not import CLI");
            assertFalse(source.contains("import chess."),
                    GUI_ROOT.relativize(file) + " does not import chess domain");
        }
    }

    /**
     * Verifies feature implementations do not import sibling feature
     * implementations.
     */
    private static void testFeaturePackagesStaySiblingIndependent() {
        for (Path file : guiJavaFiles()) {
            if (!file.startsWith(FEATURE_ROOT)) {
                continue;
            }
            Path relative = FEATURE_ROOT.relativize(file);
            if (relative.getNameCount() < 2) {
                continue;
            }
            String feature = relative.getName(0).toString();
            Matcher matcher = FEATURE_IMPORT.matcher(readUtf8(file));
            while (matcher.find()) {
                String importedFeature = matcher.group(1);
                assertTrue(feature.equals(importedFeature),
                        GUI_ROOT.relativize(file) + " does not import sibling feature " + importedFeature);
            }
        }
    }

    /**
     * Verifies extracted feature code does not accept the legacy window root.
     */
    private static void testFeaturePackagesDoNotReceiveWindowBase() {
        for (Path file : guiJavaFiles()) {
            if (!file.startsWith(FEATURE_ROOT)) {
                continue;
            }
            String code = stripComments(readUtf8(file));
            assertFalse(code.contains("WindowBase"), GUI_ROOT.relativize(file) + " does not depend on WindowBase");
            assertFalse(code.contains("WindowHost"), GUI_ROOT.relativize(file) + " does not depend on WindowHost");
        }
    }

    /**
     * Verifies the legacy window inheritance chain and host-adapter count do not
     * grow.
     */
    private static void testWindowInheritanceDoesNotGrow() {
        for (Path file : guiJavaFiles()) {
            String code = stripComments(readUtf8(file));
            Matcher matcher = CLASS_EXTENDS.matcher(code);
            while (matcher.find()) {
                String className = matcher.group(1);
                String superName = matcher.group(2);
                if ("WindowHost".equals(superName)) {
                    assertTrue(ALLOWED_WINDOW_HOST_SUBCLASSES.contains(className),
                            className + " is an intentional WindowHost adapter");
                } else if ("WindowBase".equals(superName) || superName.startsWith("Window")) {
                    assertTrue(ALLOWED_WINDOW_EXTENSION_CLASSES.contains(className),
                            className + " is an intentional window inheritance class");
                }
            }
        }
    }

    /**
     * Verifies the Dataset tab is created through the new feature seam.
     */
    private static void testDatasetUsesFeatureComposition() {
        String source = readUtf8(WINDOW_BASE_SOURCE);
        assertTrue(source.contains("LegacyWorkbenchComposition.datasetView"),
                "WindowBase creates Dataset through the composition root");
        assertFalse(source.contains("new DatasetPanel"),
                "WindowBase does not create DatasetPanel directly");
    }

    /**
     * Verifies the Publishing tab is created through the new feature seam.
     */
    private static void testPublishingUsesFeatureComposition() {
        String source = readUtf8(WINDOW_BASE_SOURCE);
        assertTrue(source.contains("LegacyWorkbenchComposition.publishingView"),
                "WindowBase creates Publishing through the composition root");
        assertFalse(source.contains("new WindowPublishingHost"),
                "WindowBase does not create Publishing through WindowPublishingHost");
        assertFalse(Files.exists(GUI_ROOT.resolve(Path.of("workbench", "window", "WindowPublishingHost.java"))),
                "WindowPublishingHost compatibility adapter has been removed");
    }

    /**
     * Verifies report views are created through the new feature seam.
     */
    private static void testReportUsesFeatureComposition() {
        String source = readUtf8(WINDOW_BASE_SOURCE);
        assertTrue(source.contains("LegacyWorkbenchComposition.reportView"),
                "WindowBase creates reports through the composition root");
        assertFalse(source.contains("new WindowReportHost"),
                "WindowBase does not create reports through WindowReportHost");
        assertFalse(Files.exists(GUI_ROOT.resolve(Path.of("workbench", "window", "WindowReportHost.java"))),
                "WindowReportHost compatibility adapter has been removed");
    }

    /**
     * Verifies direct Swing construction and decoration counts do not exceed
     * the checked-in baseline.
     */
    private static void testArchitectureDebtDoesNotIncrease() {
        Map<BaselineKey, Integer> baseline = readBaseline();
        Map<BaselineKey, Integer> current = scanCurrentDebt();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<BaselineKey, Integer> entry : current.entrySet()) {
            BaselineKey key = entry.getKey();
            int actual = entry.getValue();
            int allowed = baseline.getOrDefault(key, 0);
            if (actual > allowed) {
                failures.add(key.rule() + "\t" + key.path() + " baseline=" + allowed + " actual=" + actual);
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("GUI architecture debt increased: " + failures);
        }
    }

    /**
     * Writes the current architecture debt baseline.
     */
    private static void writeBaseline() {
        try {
            Files.createDirectories(BASELINE.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# rule\tpath\tcount");
            for (Map.Entry<BaselineKey, Integer> entry : scanCurrentDebt().entrySet()) {
                lines.add(entry.getKey().rule() + "\t" + entry.getKey().path() + "\t" + entry.getValue());
            }
            Files.write(BASELINE, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("could not write GUI architecture baseline", ex);
        }
    }

    /**
     * Reads the checked-in architecture baseline.
     *
     * @return baseline counts
     */
    private static Map<BaselineKey, Integer> readBaseline() {
        Map<BaselineKey, Integer> baseline = new HashMap<>();
        for (String line : readUtf8(BASELINE).lines().toList()) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\\t");
            assertTrue(columns.length == 3, "baseline row has three columns: " + line);
            baseline.put(new BaselineKey(columns[0], columns[1]), Integer.parseInt(columns[2]));
        }
        return baseline;
    }

    /**
     * Scans current GUI debt counts.
     *
     * @return current counts keyed by rule and path
     */
    private static Map<BaselineKey, Integer> scanCurrentDebt() {
        Map<BaselineKey, Integer> counts = new LinkedHashMap<>();
        List<Path> files = guiJavaFiles();
        for (ScanRule rule : SCAN_RULES) {
            for (Path file : files) {
                int count = countMatches(rule.pattern(), stripComments(readUtf8(file)));
                if (count > 0) {
                    counts.put(new BaselineKey(rule.id(), normalize(file)), count);
                }
            }
        }
        int windowBaseFields = countWindowBaseFields();
        if (windowBaseFields > 0) {
            counts.put(new BaselineKey(WINDOW_BASE_FIELD_RULE, normalize(WINDOW_BASE_SOURCE)), windowBaseFields);
        }
        return sortCounts(counts);
    }

    /**
     * Sorts baseline counts for deterministic output.
     *
     * @param counts unsorted counts
     * @return sorted counts
     */
    private static Map<BaselineKey, Integer> sortCounts(Map<BaselineKey, Integer> counts) {
        Map<BaselineKey, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<BaselineKey, Integer> entry) -> entry.getKey().rule())
                        .thenComparing(entry -> entry.getKey().path()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    /**
     * Counts regex matches.
     *
     * @param pattern pattern to count
     * @param text text to scan
     * @return match count
     */
    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts non-static fields currently held by WindowBase.
     *
     * @return field count
     */
    private static int countWindowBaseFields() {
        int count = 0;
        for (String line : stripComments(readUtf8(WINDOW_BASE_SOURCE)).lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.matches("(protected|private|public)\\s+.*")) {
                continue;
            }
            if (trimmed.contains(" static ") || trimmed.contains("(") || trimmed.contains(" class ")
                    || trimmed.contains(" interface ") || trimmed.contains(" enum ")
                    || trimmed.contains(" record ")) {
                continue;
            }
            if (trimmed.endsWith(";") || trimmed.contains("=")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Lists GUI Java source files.
     *
     * @return sorted source files
     */
    private static List<Path> guiJavaFiles() {
        try (Stream<Path> files = Files.walk(GUI_ROOT)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(JAVA_SOURCE_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("could not list GUI Java files", ex);
        }
    }

    /**
     * Lists GUI source directories that contain Java files.
     *
     * @return sorted source directories
     */
    private static List<Path> guiSourceDirectories() {
        try (Stream<Path> directories = Files.walk(GUI_ROOT)) {
            return directories
                    .filter(Files::isDirectory)
                    .filter(GuiArchitectureRegressionTest::containsJavaSource)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new AssertionError("could not list GUI source directories", ex);
        }
    }

    /**
     * Returns whether a directory has direct Java source children.
     *
     * @param directory directory to inspect
     * @return true when the directory contains Java source
     */
    private static boolean containsJavaSource(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .anyMatch(file -> file.getFileName().toString().endsWith(JAVA_SOURCE_SUFFIX));
        } catch (IOException ex) {
            throw new AssertionError("could not list " + directory, ex);
        }
    }

    /**
     * Computes the expected package for a source file.
     *
     * @param file source file
     * @return expected package
     */
    private static String expectedPackage(Path file) {
        Path relative = GUI_ROOT.relativize(file.getParent());
        if (relative.toString().isEmpty()) {
            return "application.gui";
        }
        return "application.gui." + relative.toString().replace(file.getFileSystem().getSeparator(), ".");
    }

    /**
     * Normalizes a path for TSV output.
     *
     * @param file source file
     * @return normalized path
     */
    private static String normalize(Path file) {
        return file.toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    /**
     * Removes Java comments before architecture pattern scans.
     *
     * @param source source text
     * @return source without line and block comments
     */
    private static String stripComments(String source) {
        StringBuilder code = new StringBuilder(source.length());
        boolean inBlock = false;
        for (String line : source.lines().toList()) {
            int index = 0;
            while (index < line.length()) {
                if (inBlock) {
                    int end = line.indexOf("*/", index);
                    if (end < 0) {
                        index = line.length();
                        continue;
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
                    inBlock = true;
                    index = blockComment + 2;
                    continue;
                }
                code.append(line, index, line.length());
                break;
            }
            code.append('\n');
        }
        return code.toString();
    }

    /**
     * One architecture scan rule.
     *
     * @param id baseline rule id
     * @param pattern regex pattern
     */
    private record ScanRule(String id, Pattern pattern) {
    }

    /**
     * Baseline map key.
     *
     * @param rule rule id
     * @param path normalized source path
     */
    private record BaselineKey(String rule, String path) {
    }
}
