package testing;

import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression checks for installer-generated launcher safety.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class InstallScriptRegressionTest {

    /**
     * Installer script path.
     */
    private static final Path INSTALL_SCRIPT = Path.of("install.sh");

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
     * Reads the installer source.
     *
     * @return installer text
     */
    private static String readInstallScript() {
        try {
            return Files.readString(INSTALL_SCRIPT);
        } catch (IOException ex) {
            throw new AssertionError("could not read " + INSTALL_SCRIPT, ex);
        }
    }
}
