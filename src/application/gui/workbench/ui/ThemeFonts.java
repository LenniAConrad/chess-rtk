package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
 * Font resolution and density-scaling support for {@link Theme}.
 */
final class ThemeFonts {

    /**
     * Cached UI fonts keyed by style and density-scaled point size.
     */
    private static final Map<Long, Font> UI_FONT_CACHE = new HashMap<>();

    /**
     * Cached monospace fonts keyed by density-scaled point size.
     */
    private static final Map<Long, Font> MONO_FONT_CACHE = new HashMap<>();

    /**
     * Cached console monospace fonts keyed by density-scaled point size.
     */
    private static final Map<Long, Font> CONSOLE_FONT_CACHE = new HashMap<>();

    /**
     * VS Code macOS-inspired system UI font candidates, ordered by platform
     * fit and then broadly available Linux/Windows fallbacks.
     */
    private static final String[] UI_FONT_CANDIDATES = {
        "SF Pro Text",
        "SF Pro Display",
        ".AppleSystemUIFont",
        "Helvetica Neue",
        "Segoe UI",
        "Segoe WPC",
        "Ubuntu Sans",
        "Ubuntu",
        "Noto Sans",
        "Cantarell",
        "Inter",
        "IBM Plex Sans",
        "DejaVu Sans",
        "Arial"
    };

    /**
     * Code and terminal font candidates for FENs, logs, commands, and reports.
     */
    private static final String[] MONO_FONT_CANDIDATES = {
        "SF Mono",
        "Menlo",
        "Monaco",
        "Cascadia Mono",
        "Cascadia Code",
        "JetBrains Mono",
        "Fira Code",
        "Source Code Pro",
        "Ubuntu Sans Mono",
        "Ubuntu Mono",
        "Noto Sans Mono",
        "DejaVu Sans Mono",
        "Liberation Mono",
        "Consolas"
    };

    /**
     * Resolved family for interface chrome and ordinary controls.
     */
    private static final String UI_FONT_FAMILY = resolveFontFamily(UI_FONT_CANDIDATES, Font.SANS_SERIF);

    /**
     * Resolved family for code-like text, command output, and dense data.
     */
    private static final String MONO_FONT_FAMILY = resolveFontFamily(MONO_FONT_CANDIDATES, Font.MONOSPACED);

    /**
     * Block, shade, and box-drawing glyphs that CLI progress bars rely on.
     */
    private static final String CONSOLE_GLYPH_PROBE =
            "█▉▊▋▌▍▎▏"
            + "▀▄▐░▒▓"
            + "─│┌┐└┘"
            + "━┃";

    /**
     * Resolved console/terminal family.
     */
    private static final String CONSOLE_FONT_FAMILY = resolveConsoleFontFamily();

    /**
     * Prevents instantiation.
     */
    private ThemeFonts() {
        // utility
    }

    /**
     * Installs defaults.
     */
    static void installDefaults() {
        Font uiFont = font(Theme.FONT_CONTROL, Font.PLAIN);
        Font smallUiFont = font(Theme.FONT_DENSE_TABLE, Font.PLAIN);
        Font boldSmallUiFont = font(Theme.FONT_DENSE_TABLE, Font.BOLD);
        Font codeFont = mono(Theme.FONT_MONO);
        UIManager.put("Label.font", smallUiFont);
        UIManager.put("Button.font", uiFont);
        UIManager.put("ToggleButton.font", uiFont);
        UIManager.put("CheckBox.font", smallUiFont);
        UIManager.put("RadioButton.font", smallUiFont);
        UIManager.put("ComboBox.font", uiFont);
        UIManager.put("TextField.font", uiFont);
        UIManager.put("FormattedTextField.font", uiFont);
        UIManager.put("PasswordField.font", uiFont);
        UIManager.put("TextArea.font", codeFont);
        UIManager.put("TextPane.font", codeFont);
        UIManager.put("EditorPane.font", codeFont);
        UIManager.put("List.font", mono(Theme.FONT_MONO));
        UIManager.put("Table.font", smallUiFont);
        UIManager.put("TableHeader.font", boldSmallUiFont);
        UIManager.put("Tree.font", smallUiFont);
        UIManager.put("MenuBar.font", smallUiFont);
        UIManager.put("Menu.font", smallUiFont);
        UIManager.put("MenuItem.font", smallUiFont);
        UIManager.put("PopupMenu.font", smallUiFont);
        UIManager.put("TabbedPane.font", boldSmallUiFont);
        UIManager.put("FileChooser.font", uiFont);
        UIManager.put("FileChooser.listFont", uiFont);
        UIManager.put("OptionPane.messageFont", uiFont);
        UIManager.put("OptionPane.buttonFont", uiFont);
        UIManager.put("ToolTip.font", smallUiFont);
    }

    /**
     * Returns a cached UI font scaled for the active density.
     *
     * @param size base font size
     * @param style AWT font style
     * @return scaled UI font
     */
    static Font font(float size, int style) {
        return cachedFont(UI_FONT_CACHE, UI_FONT_FAMILY, style, scaledSize(size));
    }

    /**
     * Returns a cached monospaced font scaled for the active density.
     *
     * @param size base font size
     * @return scaled monospaced font
     */
    static Font mono(float size) {
        return cachedFont(MONO_FONT_CACHE, MONO_FONT_FAMILY, Font.PLAIN, scaledSize(size));
    }

    /**
     * Returns a cached console monospaced font scaled for the active density.
     *
     * @param size base font size
     * @return scaled console font
     */
    static Font consoleMono(float size) {
        return cachedFont(CONSOLE_FONT_CACHE, CONSOLE_FONT_FAMILY, Font.PLAIN, scaledSize(size));
    }

    /**
     * Clears caches.
     */
    static void clearCaches() {
        clearFontCache(UI_FONT_CACHE);
        clearFontCache(MONO_FONT_CACHE);
        clearFontCache(CONSOLE_FONT_CACHE);
    }

    /**
     * Returns a pixel value scaled by the active density.
     *
     * @param px base pixel value
     * @return scaled pixel value
     */
    static int scaledPx(int px) {
        return Math.round(px * Theme.density().fontScale());
    }

    /**
     * Rescales a component subtree after a density change.
     *
     * @param component Swing component
     * @param ratio old-to-new font-size ratio
     */
    static void rescaleFonts(Component component, double ratio) {
        if (component == null || ratio <= 0 || ratio == 1.0) {
            return;
        }
        Font current = component.getFont();
        if (current != null) {
            int next = Math.max(7, Math.round((float) (current.getSize2D() * ratio)));
            component.setFont(current.deriveFont((float) next));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                rescaleFonts(child, ratio);
            }
        }
    }

    /**
     * Returns the resolve console font family.
     *
     * @return resolve console font family text
     */
    private static String resolveConsoleFontFamily() {
        try {
            String[] available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            for (String candidate : MONO_FONT_CANDIDATES) {
                for (String name : available) {
                    if (candidate.equalsIgnoreCase(name)
                            && new Font(name, Font.PLAIN, 13).canDisplayUpTo(CONSOLE_GLYPH_PROBE) == -1) {
                        return name;
                    }
                }
            }
        } catch (java.awt.HeadlessException ex) {
            // fall through to the composite logical monospace
        }
        return Font.MONOSPACED;
    }

    /**
     * Returns the resolve font family.
     *
     * @param candidates source candidates
     * @param fallback default used when input is absent or invalid
     * @return resolve font family text
     */
    private static String resolveFontFamily(String[] candidates, String fallback) {
        try {
            String[] available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            for (String candidate : candidates) {
                for (String name : available) {
                    if (candidate.equalsIgnoreCase(name)) {
                        return name;
                    }
                }
            }
        } catch (java.awt.HeadlessException ex) {
            // fall through to default
        }
        return fallback;
    }

    /**
     * Returns the cached font.
     *
     * @param cache analysis cache
     * @param family font family
     * @param style font style
     * @param size size in pixels or points
     * @return cached font
     */
    private static Font cachedFont(Map<Long, Font> cache, String family, int style, int size) {
        Long key = fontCacheKey(style, size);
        synchronized (cache) {
            Font existing = cache.get(key);
            if (existing != null) {
                return existing;
            }
            Font created = new Font(family, style, size);
            cache.put(key, created);
            return created;
        }
    }

    /**
     * Returns the font cache key.
     *
     * @param style font style
     * @param size size in pixels or points
     * @return font cache key
     */
    private static Long fontCacheKey(int style, int size) {
        return Long.valueOf((((long) style) << Integer.SIZE) ^ (size & 0xffff_ffffL));
    }

    /**
     * Clears font cache.
     *
     * @param cache analysis cache
     */
    private static void clearFontCache(Map<Long, Font> cache) {
        synchronized (cache) {
            cache.clear();
        }
    }

    /**
     * Returns the scaled size.
     *
     * @param size size in pixels or points
     * @return scaled size
     */
    private static int scaledSize(float size) {
        return Math.max(7, Math.round(size * Theme.density().fontScale()));
    }
}
