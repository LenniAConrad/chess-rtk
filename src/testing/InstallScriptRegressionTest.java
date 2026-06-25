package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.writeUtf8;

import application.gui.workbench.ui.AppIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Regression checks for installer-generated launcher safety.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class InstallScriptRegressionTest {

    /**
     * Installer script path.
     */
    private static final Path INSTALL_SCRIPT = Path.of("install.sh");

    /**
     * Tracked-weight guard script path.
     */
    private static final Path CHECK_NO_WEIGHTS_SCRIPT =
            Path.of("scripts", "check_no_weights_tracked.sh").toAbsolutePath();

    /**
     * Workbench launch-command source path.
     */
    private static final Path LAUNCH_COMMAND_SOURCE =
            Path.of("src", "application", "gui", "workbench", "launch", "LaunchCommand.java");

    /**
     * Workbench app-icon source path.
     */
    private static final Path APP_ICON_SOURCE =
            Path.of("src", "application", "gui", "workbench", "ui", "AppIcon.java");

    /**
     * Workbench window source path.
     */
    private static final Path WORKBENCH_WINDOW_SOURCE =
            Path.of("src", "application", "gui", "workbench", "window", "Window.java");

    /**
     * Active app logo directory.
     */
    private static final Path ACTIVE_APP_LOGO_DIR =
            Path.of("assets", "logo", "app");

    /**
     * Active app logo generator path.
     */
    private static final Path ACTIVE_APP_ICON_GENERATOR =
            ACTIVE_APP_LOGO_DIR.resolve("generate_crtk_chemical_board_logo.py");

    /**
     * OTIS lattice logo directory.
     */
    private static final Path OTIS_LOGO_DIR =
            Path.of("assets", "logo", "otis");

    /**
     * OTIS lattice logo generator path.
     */
    private static final Path OTIS_ICON_GENERATOR =
            OTIS_LOGO_DIR.resolve("generate_otis_lattice_app_icon.py");

    /**
     * Prevents instantiation.
     */
    private InstallScriptRegressionTest() {
        // utility
    }

    /**
     * Runs installer regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testGeneratedLauncherUsesShellQuotedAppHome();
        testDesktopEntryUsesActiveChemicalBoardLogo();
        testActiveChemicalBoardAssetsRetained();
        testActiveChemicalBoardGeneratorIsDistinctive();
        testOtisLatticeAssetsRetainedOutsideActiveAppLogoDirectory();
        testOtisLatticeGeneratorUsesSharedChessAssets();
        testAppIconAssetLoadsWindowVariants();
        testWorkbenchInstallsDesktopIdentity();
        testInstallerAutoLaunchesWorkbenchWhenGuiAvailable();
        testInstallerIncludesStarPrompt();
        testInstallerVerifiesModelWeightChecksums();
        testTrackedWeightGuardRejectsTrackedModelFiles();
        System.out.println("InstallScriptRegressionTest: all checks passed");
    }

    /**
     * Verifies the generated launcher embeds the repository path as a shell
     * literal instead of interpolating it inside double quotes.
     */
    private static void testGeneratedLauncherUsesShellQuotedAppHome() {
        String script = readInstallScript();
        assertTrue(script.contains("shell_quote()"), "installer defines shell_quote helper");
        assertTrue(script.contains("APP_HOME_LITERAL=\"$(shell_quote \"$APP_HOME\")\""),
                "installer pre-quotes APP_HOME");
        assertTrue(script.contains("APP_HOME=$APP_HOME_LITERAL"),
                "launcher uses quoted APP_HOME literal");
        assertFalse(script.contains("APP_HOME=\"$APP_HOME\"\nJAVA_BIN="),
                "launcher does not interpolate APP_HOME in double quotes");
    }

    /**
     * Verifies the generated desktop entry uses the active chemical-board logo,
     * while deprecated and old piece logos are no longer referenced.
     */
    private static void testDesktopEntryUsesActiveChemicalBoardLogo() {
        String script = readInstallScript();
        String iconPath = AppIcon.assetPath().toString().replace('\\', '/');
        assertTrue(script.contains("DESKTOP_ICON=\"$APP_HOME/" + iconPath + "\""),
                "desktop entry uses active app icon");
        assertTrue(iconPath.equals("assets/logo/app/crtk-chemical-board.png"),
                "active app icon is the chemical-board logo");
        assertFalse(script.contains("assets/logo/app/crtk-otis-lattice.png"),
                "desktop entry does not use deprecated OTIS lattice logo");
        assertFalse(script.contains("assets/logo/app/crtk-route-knight.png"),
                "desktop entry does not use deprecated route-knight logo");
        assertFalse(script.contains("assets/logo/app/crtk-lab-board.png"),
                "desktop entry does not use deprecated lab-board logo");
        assertFalse(script.contains("assets/logo/pieces/crtk-white-knight.png"),
                "desktop entry does not use the old white knight piece logo");
        assertTrue(script.contains("StartupWMClass=crtk-workbench"),
                "desktop entry matches the Java workbench WM class");
        assertTrue(script.contains("-Dsun.awt.X11.XWMClass=crtk-workbench"),
                "launcher sets the Java workbench WM class");
        assertTrue(Files.isRegularFile(AppIcon.assetPath()), "app icon PNG asset exists");
    }

    /**
     * Verifies the active chemical-board logo assets remain available.
     */
    private static void testActiveChemicalBoardAssetsRetained() {
        assertTrue(Files.isRegularFile(ACTIVE_APP_LOGO_DIR.resolve("crtk-chemical-board.png")),
                "active chemical-board PNG asset exists");
        assertTrue(Files.isRegularFile(ACTIVE_APP_LOGO_DIR.resolve("crtk-chemical-board.ico")),
                "active chemical-board ICO asset exists");
        assertTrue(Files.isRegularFile(ACTIVE_APP_LOGO_DIR.resolve("crtk-chemical-board.svg")),
                "active chemical-board SVG asset exists");
        assertTrue(Files.isRegularFile(ACTIVE_APP_ICON_GENERATOR),
                "active chemical-board generator exists");
        assertFalse(Files.exists(ACTIVE_APP_LOGO_DIR.resolve("crtk-route-knight.png")),
                "route-knight PNG is no longer in the active app-logo directory");
        assertFalse(Files.exists(ACTIVE_APP_LOGO_DIR.resolve("crtk-lab-board.png")),
                "lab-board PNG is no longer in the active app-logo directory");
    }

    /**
     * Verifies the active logo generator keeps the mark distinctive.
     */
    private static void testActiveChemicalBoardGeneratorIsDistinctive() {
        String generator = readUtf8(ACTIVE_APP_ICON_GENERATOR);
        assertTrue(generator.contains("crtk-chemical-board"), "active logo generator writes chemical-board assets");
        assertTrue(generator.contains("SOURCE_VIEWBOX_WIDTH"), "active logo generator embeds the source SVG geometry");
        assertTrue(generator.contains("source_svg_mark()"), "active logo generator draws the downloaded flask glyph");
        assertTrue(generator.contains("LIGHT_TILE_TOP"), "active logo generator keeps light board palette");
        assertTrue(generator.contains("DARK_TILE_TOP"), "active logo generator keeps dark board palette");
        assertTrue(generator.contains("BUBBLE_LIGHT"), "active logo generator keeps blue research bubbles");
    }

    /**
     * Verifies the OTIS lattice logo assets remain available outside the active
     * application-logo directory.
     */
    private static void testOtisLatticeAssetsRetainedOutsideActiveAppLogoDirectory() {
        assertTrue(Files.isRegularFile(OTIS_LOGO_DIR.resolve("crtk-otis-lattice.png")),
                "OTIS lattice PNG asset exists");
        assertTrue(Files.isRegularFile(OTIS_LOGO_DIR.resolve("crtk-otis-lattice.ico")),
                "OTIS lattice ICO asset exists");
        assertTrue(Files.isRegularFile(OTIS_LOGO_DIR.resolve("crtk-otis-lattice.svg")),
                "OTIS lattice SVG asset exists");
        assertFalse(Files.exists(Path.of("assets", "logo", "app", "crtk-otis-lattice.png")),
                "OTIS lattice PNG is no longer in the active app-logo directory");
    }

    /**
     * Verifies the OTIS icon generator still composes the archived icon from
     * the shared board and piece resources.
     */
    private static void testOtisLatticeGeneratorUsesSharedChessAssets() {
        String generator = readUtf8(OTIS_ICON_GENERATOR);
        assertTrue(generator.contains("OUTPUT_DIR = Path(__file__).resolve().parent"),
                "OTIS app icon generator writes into its asset directory");
        assertTrue(generator.contains("assets\" / \"embedded\" / \"board\" / \"png\" / \"board.png"),
                "OTIS app icon generator uses shared board asset");
        assertTrue(generator.contains("assets\" / \"embedded\" / \"pieces\" / \"png\" / \"white-king.png"),
                "OTIS app icon generator uses shared white king asset");
        assertTrue(generator.contains("BOARD_CROP_SQUARES = 2"),
                "OTIS app icon generator uses a 2x2 board crop");
        assertTrue(generator.contains("def coat_board("),
                "OTIS app icon generator color-coats the board crop");
        assertTrue(Files.isRegularFile(Path.of("assets", "embedded", "board", "png", "board.png")),
                "shared board asset exists");
        assertTrue(Files.isRegularFile(Path.of("assets", "embedded", "pieces", "png", "white-king.png")),
                "shared white king asset exists");
    }

    /**
     * Verifies the app icon PNG and Java window-icon variants decode cleanly.
     */
    private static void testAppIconAssetLoadsWindowVariants() {
        BufferedImage image = readImage(AppIcon.assetPath());
        assertTrue(image.getWidth() >= 512, "app icon PNG width is high resolution");
        assertTrue(image.getHeight() >= 512, "app icon PNG height is high resolution");
        assertTrue(AppIcon.DESKTOP_APP_ID.equals("crtk-workbench"), "workbench desktop app id is stable");
        List<Image> variants = AppIcon.iconImages();
        assertTrue(variants.size() >= 8, "window icon variants include common native sizes");
        for (Image variant : variants) {
            assertTrue(variant.getWidth(null) > 0, "window icon variant has width");
            assertTrue(variant.getHeight(null) > 0, "window icon variant has height");
        }
    }

    /**
     * Verifies Swing and desktop-shell integration use the same workbench
     * identity and taskbar icon path.
     */
    private static void testWorkbenchInstallsDesktopIdentity() {
        String launchCommand = readUtf8(LAUNCH_COMMAND_SOURCE);
        String appIcon = readUtf8(APP_ICON_SOURCE);
        String window = readUtf8(WORKBENCH_WINDOW_SOURCE);
        assertTrue(launchCommand.contains("AppIcon.installDesktopProperties();"),
                "workbench installs desktop identity before Swing startup");
        assertTrue(appIcon.contains("public static final String DESKTOP_APP_ID = \"crtk-workbench\""),
                "app icon exposes stable desktop app id");
        assertTrue(window.contains("setTitle(AppIcon.DESKTOP_APP_NAME);"),
                "workbench window title uses desktop app name");
        assertTrue(appIcon.contains("Taskbar.getTaskbar()"),
                "app icon uses Java taskbar integration");
        assertTrue(appIcon.contains("taskbar.setIconImage(image)"),
                "app icon applies the chemical-board image to the taskbar");
    }

    /**
     * Verifies the installer auto-starts the desktop Workbench only when a GUI
     * session is available, with an explicit opt-out for scripted installs.
     */
    private static void testInstallerAutoLaunchesWorkbenchWhenGuiAvailable() {
        String script = readInstallScript();
        assertTrue(script.contains("AUTO_LAUNCH_WORKBENCH=1"),
                "installer auto-launches Workbench by default");
        assertTrue(script.contains("--no-launch|--no-open"),
                "installer offers an opt-out for auto-launch");
        assertTrue(script.contains("gui_session_available()"),
                "installer checks GUI availability before launching");
        assertTrue(script.contains("[[ -n \"${DISPLAY:-}\" || -n \"${WAYLAND_DISPLAY:-}\" ]]"),
                "installer treats X11 and Wayland displays as GUI sessions");
        assertTrue(script.contains("launch_command=(\"$LAUNCHER\" workbench)"),
                "installer launches the installed Workbench command");
        assertTrue(script.contains("nohup setsid \"${launch_command[@]}\""),
                "installer detaches the launched Workbench process");
    }

    /**
     * Verifies the installer keeps a low-friction prompt for users to support
     * the repository after setup.
     */
    private static void testInstallerIncludesStarPrompt() {
        String script = readInstallScript();
        assertTrue(script.contains("${C_YELLOW}★${C_RESET} Found ChessRTK useful? Please star the repo:"),
                "installer asks users to star the repository with a visible yellow star");
        assertTrue(script.contains("https://github.com/LenniAConrad/chess-rtk"),
                "installer star prompt includes the repository URL");
    }

    /**
     * Verifies optional model downloads are pinned by SHA-256.
     */
    private static void testInstallerVerifiesModelWeightChecksums() {
        String script = readInstallScript();
        assertTrue(script.contains("MODEL_SHA256=("),
                "installer declares expected model SHA-256 values");
        assertTrue(script.contains("sha256_file()"),
                "installer has a reusable SHA-256 helper");
        assertTrue(script.contains("verify_model_sha256()"),
                "installer has a model checksum verifier");
        assertTrue(script.contains("elif ! verify_model_sha256 \"$MODEL_DIR/$file\" \"$expected_sha\"; then"),
                "installer verifies existing model files before treating them as present");
        assertTrue(script.contains("if ! verify_model_sha256 \"$MODEL_DIR/$file\" \"$expected_sha\"; then"),
                "installer verifies downloaded model files before accepting them");
        assertTrue(script.contains("verification_failed=1"),
                "installer marks checksum mismatches as verification failures");
        assertTrue(script.contains("if [[ $verification_failed -ne 0 ]]; then"),
                "installer aborts after a fetched checksum mismatch");
        assertTrue(script.contains("fcf986aea78a22de420ec0f0d1f4cf5b2b8497896aa678ff1e1bee5922fab113"),
                "installer pins NNUE checksum");
        assertTrue(script.contains("c4dd6b62acd3c86be3d6199a32d6119d9144f508f84c823f69881ae0bae41034"),
                "installer pins large LC0 CNN checksum");
        assertTrue(script.contains("b99bec1aba97e96bf03ac8e016578527b983b6653f1adf040452f86c6f3ef348"),
                "installer pins small LC0 CNN checksum");
        assertTrue(script.contains("e6ada9d6c4a769bfab3aa0848d82caeb809aa45f83e6c605fc58a31d21bdd618"),
                "installer pins BT4 checksum");
    }

    /**
     * Verifies the tracked-weight guard fails only when a model artifact is
     * actually tracked by git.
     */
    private static void testTrackedWeightGuardRejectsTrackedModelFiles() {
        Path workspace = createTempDirectory("crtk-weight-guard-");
        try {
            runProcessExpectSuccess(workspace, "git", "init");
            Path model = workspace.resolve("models").resolve("bad.bin");
            createDirectories(model.getParent());
            writeUtf8(model, "test weight placeholder\n");
            runProcessExpectSuccess(workspace, "git", "add", "models/bad.bin");

            ProcessResult tracked = runProcess(workspace, CHECK_NO_WEIGHTS_SCRIPT.toString());
            assertEquals(1, tracked.exitCode(), "tracked model weight exits with failure");
            assertTrue(tracked.stderr().contains("tracked weight files detected"),
                    "tracked model weight failure explains the policy");
            assertTrue(tracked.stderr().contains("models/bad.bin"),
                    "tracked model weight failure names the offending file");

            runProcessExpectSuccess(workspace, "git", "rm", "--cached", "models/bad.bin");
            ProcessResult untracked = runProcess(workspace, CHECK_NO_WEIGHTS_SCRIPT.toString());
            assertEquals(0, untracked.exitCode(), "untracked local model weight is allowed");
        } finally {
            deleteRecursively(workspace);
        }
    }

    /**
     * Reads the installer source.
     *
     * @return installer text
     */
    private static String readInstallScript() {
        return readUtf8(INSTALL_SCRIPT);
    }

    /**
     * Reads a PNG image from disk.
     *
     * @param path image path
     * @return decoded image
     */
    private static BufferedImage readImage(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new AssertionError("could not decode " + path);
            }
            return image;
        } catch (IOException ex) {
            throw new AssertionError("could not read " + path, ex);
        }
    }

    /**
     * Runs a child process that must complete successfully.
     *
     * @param cwd process working directory
     * @param command command and arguments
     */
    private static void runProcessExpectSuccess(Path cwd, String... command) {
        ProcessResult result = runProcess(cwd, command);
        if (result.exitCode() != 0) {
            throw new AssertionError(String.join(" ", command)
                    + " failed with exit " + result.exitCode()
                    + "\nstdout:\n" + result.stdout()
                    + "\nstderr:\n" + result.stderr());
        }
    }

    /**
     * Runs a child process and captures its result.
     *
     * @param cwd process working directory
     * @param command command and arguments
     * @return process result
     */
    private static ProcessResult runProcess(Path cwd, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, stdout, stderr);
        } catch (IOException ex) {
            throw new AssertionError("failed to run " + String.join(" ", command), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while running " + String.join(" ", command), ex);
        }
    }

    /**
     * Deletes a temporary test tree.
     *
     * @param root root path
     */
    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new AssertionError("failed to delete " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw new AssertionError("failed to delete " + root, ex);
        }
    }

    /**
     * Creates a directory tree for a test fixture.
     *
     * @param directory directory path
     */
    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new AssertionError("failed to create " + directory, ex);
        }
    }

    /**
     * Captured process result.
     *
     * @param exitCode process exit code
     * @param stdout standard output
     * @param stderr standard error
     */
    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
