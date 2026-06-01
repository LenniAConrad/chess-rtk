package testing;

import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;

import application.gui.workbench.ui.AppIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        testInstallerIncludesStarPrompt();
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
        String generator = readFile(ACTIVE_APP_ICON_GENERATOR);
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
        String generator = readFile(OTIS_ICON_GENERATOR);
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
        String launchCommand = readFile(LAUNCH_COMMAND_SOURCE);
        String appIcon = readFile(APP_ICON_SOURCE);
        String window = readFile(WORKBENCH_WINDOW_SOURCE);
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
     * Reads the installer source.
     *
     * @return installer text
     */
    private static String readInstallScript() {
        return readFile(INSTALL_SCRIPT);
    }

    /**
     * Reads a UTF-8 text file.
     *
     * @param path file path
     * @return file text
     */
    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new AssertionError("could not read " + path, ex);
        }
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
}
