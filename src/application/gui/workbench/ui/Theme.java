package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFormattedTextField;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.JTextComponent;

/**
 * Native Swing styling helpers for the CRTK Workbench.
 */

public final class Theme {

    /**
     * Theme logger.
     */
    private static final Logger LOGGER = Logger.getLogger(Theme.class.getName());

    /**
     * Available workbench color modes.
     */
    public enum Mode {
        /**
         * VS Code-inspired light palette.
         */
        LIGHT("light", "Light"),

        /**
         * VS Code-inspired dark palette.
         */
        DARK("dark", "Dark");

        /**
         * Stable preference value.
         */
        private final String id;

        /**
         * Display label.
         */
        private final String label;

        /**
         * Creates one theme mode.
         *
         * @param id stable preference value
         * @param label display label
         */
        Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        /**
         * Returns the stable preference value.
         *
         * @return preference id
         */
        public String id() {
            return id;
        }

        /**
         * Returns the display label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Parses a persisted theme mode.
         *
         * @param value stored value
         * @return parsed mode, defaulting to {@link #LIGHT}
         */
        public static Mode fromPreference(String value) {
            if (value != null) {
                for (Mode candidate : values()) {
                    if (candidate.id.equalsIgnoreCase(value) || candidate.name().equalsIgnoreCase(value)) {
                        return candidate;
                    }
                }
            }
            return LIGHT;
        }
    }

    /**
     * UI density presets. Every workbench font is built through
     * {@link #font(float, int)} / {@link #mono(float)} / {@link #consoleMono(float)},
     * so a single per-density scale factor applied there rescales the whole app
     * without touching the named {@code FONT_*} design tokens (which the UI
     * regression test pins to fixed sizes). {@link #DENSE} is the historical
     * default and renders at the unscaled base sizes.
     */
    public enum Density {
        /**
         * Slightly larger, more readable type and controls.
         */
        COMFORTABLE("comfortable", "Comfortable", 1.15f),

        /**
         * Tighter type for fitting more on screen.
         */
        COMPACT("compact", "Compact", 0.92f),

        /**
         * The original dense workbench scale (unchanged baseline).
         */
        DENSE("dense", "Dense", 1.0f);

        /**
         * Stable preference value.
         */
        private final String id;

        /**
         * Display label.
         */
        private final String label;

        /**
         * Font-size multiplier applied at the font factories.
         */
        private final float fontScale;

        /**
         * Creates one density preset.
         *
         * @param id stable preference value
         * @param label display label
         * @param fontScale font-size multiplier
         */
        Density(String id, String label, float fontScale) {
            this.id = id;
            this.label = label;
            this.fontScale = fontScale;
        }

        /**
         * Returns the stable preference value.
         *
         * @return preference id
         */
        public String id() {
            return id;
        }

        /**
         * Returns the display label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Returns the font-size multiplier.
         *
         * @return scale factor
         */
        public float fontScale() {
            return fontScale;
        }

        /**
         * Parses a persisted density preset.
         *
         * @param value stored value
         * @return parsed density, defaulting to {@link #DENSE}
         */
        public static Density fromPreference(String value) {
            if (value != null) {
                for (Density candidate : values()) {
                    if (candidate.id.equalsIgnoreCase(value) || candidate.name().equalsIgnoreCase(value)) {
                        return candidate;
                    }
                }
            }
            return DENSE;
        }
    }

    /**
     * Semantic foreground roles that can be refreshed after palette changes.
     */
    public enum ForegroundRole {
        /**
         * Primary readable text.
         */
        TEXT,

        /**
         * Secondary readable text.
         */
        MUTED,

        /**
         * Success text.
         */
        SUCCESS,

        /**
         * Warning text.
         */
        WARNING,

        /**
         * Error text.
         */
        ERROR,

        /**
         * Informational text.
         */
        INFO,

        /**
         * Terminal text.
         */
        TERMINAL
    }

    /**
     * Shared action hierarchy for Workbench buttons.
     */
    public enum ButtonVariant {
        /**
         * High-emphasis action.
         */
        PRIMARY,

        /**
         * Standard toolbar/form action.
         */
        SECONDARY,

        /**
         * Low-emphasis action that should not add another visible surface until
         * hover or focus.
         */
        GHOST,

        /**
         * Stop, resign, clear, and other destructive actions.
         */
        DESTRUCTIVE
    }

    /**
     * Active color mode.
     */
    private static Mode mode = Mode.LIGHT;

    /**
     * Active UI density. Defaults to {@link Density#DENSE} so the baseline look
     * is unchanged until a user opts into a roomier scale.
     */
    private static Density density = Density.DENSE;

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
     * Client property for empty text-control placeholder copy.
     */
    static final String PLACEHOLDER_PROPERTY = Theme.class.getName() + ".placeholder";

    /**
     * Client property storing a component's semantic foreground role.
     */
    private static final String FOREGROUND_ROLE_PROPERTY = Theme.class.getName() + ".foregroundRole";

    /**
     * VS Code macOS light primary foreground.
     */
    private static final Color PASTEL_INK = new Color(0x1F1F24);

    /**
     * VS Code macOS light secondary foreground.
     */
    private static final Color PASTEL_MUTED = new Color(0x6E6E73);

    /**
     * VS Code macOS light chrome/widget background.
     */
    private static final Color PASTEL_CHROME = new Color(0xF4F4F5);

    /**
     * VS Code macOS light hover/subtle-fill base.
     */
    private static final Color PASTEL_SUBTLE = new Color(0xECECEF);

    /**
     * VS Code Light (Visual Studio) document/editor surface.
     */
    private static final Color PASTEL_DOCUMENT = new Color(255, 255, 255);

    /**
     * VS Code macOS light widget/menu border.
     */
    private static final Color PASTEL_BORDER = new Color(0xDADADF);

    /**
     * macOS-style action blue, deepened from Apple's bright system blue so that
     * white primary-button text clears the AA 4.5:1 contrast bar in light mode.
     */
    private static final Color PASTEL_BLUE = new Color(0x0A5CC0);

    /**
     * macOS system blue hover.
     */
    private static final Color PASTEL_BLUE_HOVER = new Color(0x0077ED);

    /**
     * macOS system blue pressed.
     */
    private static final Color PASTEL_BLUE_PRESSED = new Color(0x006EDB);

    /**
     * Accessible blue text for informational surfaces.
     */
    private static final Color PASTEL_BLUE_TEXT = PASTEL_BLUE;

    /**
     * Positive vibrant pastel green.
     */
    private static final Color PASTEL_GREEN = new Color(134, 221, 163);

    /**
     * Accessible positive text for green surfaces.
     */
    private static final Color PASTEL_GREEN_TEXT = new Color(23, 97, 61);

    /**
     * Warning vibrant pastel amber.
     */
    private static final Color PASTEL_AMBER = new Color(255, 205, 92);

    /**
     * Accessible warning text for amber surfaces.
     */
    private static final Color PASTEL_AMBER_TEXT = new Color(110, 76, 15);

    /**
     * Error vibrant pastel coral.
     */
    private static final Color PASTEL_CORAL = new Color(255, 139, 134);

    /**
     * Accessible error text for coral surfaces.
     */
    private static final Color PASTEL_CORAL_TEXT = new Color(148, 44, 43);

    /**
     * Policy vibrant pastel purple.
     */
    private static final Color PASTEL_PURPLE = new Color(204, 154, 245);

    /**
     * VS Code macOS dark primary foreground.
     */
    private static final Color DARK_INK = new Color(0xE8E8E8);

    /**
     * VS Code macOS dark secondary foreground.
     */
    private static final Color DARK_MUTED = new Color(0xA1A1A1);

    /**
     * VS Code macOS dark title/sidebar chrome background.
     */
    private static final Color DARK_CHROME = new Color(0x2C2C2C);

    /**
     * VS Code macOS dark widget border.
     */
    private static final Color DARK_SUBTLE = new Color(0x3A3A3A);

    /**
     * VS Code Dark (Visual Studio) editor surface.
     */
    private static final Color DARK_DOCUMENT = new Color(0x1E1E1E);

    /**
     * VS Code macOS dark popup/dropdown surface.
     */
    private static final Color DARK_ELEVATED = new Color(0x252525);

    /**
     * VS Code macOS dark menu separator and input border.
     */
    private static final Color DARK_BORDER = new Color(0x484848);

    /**
     * macOS-style action blue for dark mode: brighter than the light accent so
     * it pops on dark chrome, while still clearing AA 4.5:1 for white text.
     */
    private static final Color DARK_ACCENT = new Color(0x0A6EE4);

    /**
     * macOS system blue dark hover.
     */
    private static final Color DARK_ACCENT_HOVER = new Color(0x2997FF);

    /**
     * macOS system blue dark pressed.
     */
    private static final Color DARK_ACCENT_PRESSED = new Color(0x0077ED);

    /**
     * Dark-mode vibrant pastel positive accent.
     */
    private static final Color DARK_GREEN = new Color(127, 227, 159);

    /**
     * Dark-mode vibrant pastel warning accent.
     */
    private static final Color DARK_AMBER = new Color(255, 208, 102);

    /**
     * Dark-mode vibrant pastel error accent.
     */
    private static final Color DARK_CORAL = new Color(255, 139, 134);

    /**
     * Dark-mode vibrant pastel policy accent.
     */
    private static final Color DARK_PURPLE = new Color(213, 150, 255);

    /**
     * Dark success text.
     */
    private static final Color DARK_SUCCESS_TEXT = DARK_GREEN;

    /**
     * Dark warning text.
     */
    private static final Color DARK_WARNING_TEXT = DARK_AMBER;

    /**
     * Dark error text.
     */
    private static final Color DARK_ERROR_TEXT = DARK_CORAL;

    /**
     * Dark informational text.
     */
    private static final Color DARK_INFO_TEXT = new Color(0x4DAAFC);

    /**
     * Root background color.
     */
    public static Color BG = PASTEL_CHROME;

    /**
     * Fully transparent component background.
     */
    public static Color TRANSPARENT = new Color(PASTEL_DOCUMENT.getRed(), PASTEL_DOCUMENT.getGreen(),
            PASTEL_DOCUMENT.getBlue(), 0);

    /**
     * Primary panel color.
     */
    public static Color PANEL = PASTEL_DOCUMENT;

    /**
     * Solid panel fallback for Swing components that must be fully opaque.
     */
    public static Color PANEL_SOLID = blendOver(PANEL, BG);

    /**
     * Soft top highlight for elevated, opaque floating chrome (command palette,
     * dialogs). A faint light edge near the top reads as the macOS "frosted"
     * language without translucent bodies that would muddy text over content.
     */
    public static Color GLASS_HIGHLIGHT = new Color(255, 255, 255, 112);

    /**
     * Root backdrop top wash, shown at window edges and during resize.
     */
    public static Color BACKDROP_TOP = new Color(0xF8F8FA);

    /**
     * Root backdrop bottom wash, shown at window edges and during resize.
     */
    public static Color BACKDROP_BOTTOM = new Color(0xEFEFF3);

    /**
     * Elevated panel color.
     */
    public static Color ELEVATED = PASTEL_CHROME;

    /**
     * Solid elevated fallback for data surfaces and scroll viewports.
     */
    public static Color ELEVATED_SOLID = blendOver(ELEVATED, BG);

    /**
     * Raised-card surface. A card sits one step above the page so it reads as a
     * distinct surface — essential in dark mode, where the page, document, and
     * chrome tiers otherwise sit within a few luma points of one another and
     * cards look painted-on. Light mode keeps the familiar white card.
     */
    public static Color CARD = blendOver(PANEL, BG);

    /**
     * Hairline border drawn around a raised card. Strong enough to define the
     * card edge on its own: flat, near-black dark UIs lean on a visible border
     * far more than on a drop shadow.
     */
    public static Color CARD_BORDER = PASTEL_BORDER;

    /**
     * Line color.
     */
    public static Color LINE = PASTEL_BORDER;

    /**
     * Primary text color.
     */
    public static Color TEXT = PASTEL_INK;

    /**
     * Secondary text color.
     */
    public static Color MUTED = PASTEL_MUTED;

    /**
     * Accent color.
     */
    public static Color ACCENT = PASTEL_BLUE;

    /**
     * Table and tree selection color.
     */
    public static Color SELECTION = new Color(0xDDEBFF);

    /**
     * Solid selection fallback for opaque renderers.
     */
    public static Color SELECTION_SOLID = blendOver(SELECTION, BG);

    /**
     * Primary button hover color.
     */
    public static Color ACCENT_HOVER = PASTEL_BLUE_HOVER;

    /**
     * Primary button pressed color.
     */
    public static Color ACCENT_PRESSED = PASTEL_BLUE_PRESSED;

    /**
     * Secondary button color.
     */
    public static Color SECONDARY_BUTTON = PASTEL_CHROME;

    /**
     * Secondary button hover color.
     */
    public static Color SECONDARY_BUTTON_HOVER = PASTEL_SUBTLE;

    /**
     * Secondary button pressed color.
     */
    public static Color SECONDARY_BUTTON_PRESSED = new Color(0xDFDFE4);

    /**
     * Secondary button text color.
     */
    public static Color SECONDARY_BUTTON_TEXT = PASTEL_INK;

    /**
     * Ghost button resting fill.
     */
    public static Color GHOST_BUTTON = new Color(0, 0, 0, 0);

    /**
     * Ghost button hover fill.
     */
    public static Color GHOST_BUTTON_HOVER = PASTEL_SUBTLE;

    /**
     * Ghost button pressed fill.
     */
    public static Color GHOST_BUTTON_PRESSED = new Color(0xDFDFE4);

    /**
     * Ghost button text color.
     */
    public static Color GHOST_BUTTON_TEXT = PASTEL_INK;

    /**
     * Destructive button resting fill.
     */
    public static Color DESTRUCTIVE_BUTTON = new Color(0xFFF1F0);

    /**
     * Destructive button hover fill.
     */
    public static Color DESTRUCTIVE_BUTTON_HOVER = new Color(0xFFE4E2);

    /**
     * Destructive button pressed fill.
     */
    public static Color DESTRUCTIVE_BUTTON_PRESSED = new Color(0xC42B1C);

    /**
     * Destructive button text color.
     */
    public static Color DESTRUCTIVE_BUTTON_TEXT = new Color(0xB3261E);

    /**
     * Disabled button fill color.
     */
    public static Color BUTTON_DISABLED_BG = PASTEL_CHROME;

    /**
     * Disabled button border color.
     */
    public static Color BUTTON_DISABLED_BORDER = PASTEL_BORDER;

    /**
     * Disabled button text color.
     */
    public static Color BUTTON_DISABLED_TEXT = PASTEL_MUTED;

    /**
     * Input border color.
     */
    public static Color INPUT_BORDER = new Color(0xC6C6CC);

    /**
     * Input focus ring color.
     */
    public static Color INPUT_FOCUS = PASTEL_BLUE;

    /**
     * Global keyboard focus-ring color.
     */
    public static Color FOCUS_RING = new Color(PASTEL_BLUE.getRed(), PASTEL_BLUE.getGreen(),
            PASTEL_BLUE.getBlue(), 95);

    /**
     * Disabled input background color.
     */
    public static Color INPUT_DISABLED = PASTEL_CHROME;

    /**
     * Toggle-off background color.
     */
    public static Color TOGGLE_BG = PASTEL_SUBTLE;

    /**
     * Toggle-off border color.
     */
    public static Color TOGGLE_BORDER = INPUT_BORDER;

    /**
     * Toggle-off track color.
     */
    public static Color TOGGLE_TRACK = PASTEL_MUTED;

    /**
     * Toggle-on background color.
     */
    public static Color TOGGLE_ON_BG = new Color(0xD5EBFF);

    /**
     * Toggle-on track color.
     */
    public static Color TOGGLE_ON_TRACK = PASTEL_BLUE;

    /**
     * Toggle thumb color.
     */
    public static Color TOGGLE_THUMB = PASTEL_DOCUMENT;

    /**
     * Text field background color.
     */
    public static Color INPUT = PASTEL_DOCUMENT;

    /**
     * Text area background color.
     */
    public static Color TEXT_AREA = PASTEL_DOCUMENT;

    /**
     * Terminal background color.
     */
    public static Color TERMINAL = PASTEL_DOCUMENT;

    /**
     * Terminal text color.
     */
    public static Color TERMINAL_TEXT = PASTEL_INK;

    /**
     * Selection color for text controls.
     */
    public static Color TEXT_SELECTION = new Color(0xADD6FF);

    /**
     * Primary button text color.
     */
    public static Color PRIMARY_BUTTON_TEXT = Color.WHITE;

    /**
     * Board light square color.
     */
    public static Color BOARD_LIGHT = new Color(240, 217, 181);

    /**
     * Board dark square color.
     */
    public static Color BOARD_DARK = new Color(181, 136, 99);

    /**
     * Standard dark chrome color reused by subtle overlays.
     */
    public static Color BOARD_SHADOW = new Color(64, 64, 64);

    /**
     * Chessboard.js board edge color.
     */
    public static Color BOARD_EDGE = new Color(64, 64, 64);

    /**
     * Coordinate text color used on light squares.
     */
    public static Color COORD_ON_LIGHT = BOARD_DARK;

    /**
     * Coordinate text color used on dark squares.
     */
    public static Color COORD_ON_DARK = BOARD_LIGHT;

    /**
     * Shared translucent board markup highlight.
     */
    public static Color BOARD_HIGHLIGHT = new Color(246, 222, 93, 86);

    /**
     * Translucent last-move square fill.
     */
    public static Color LAST_MOVE_EDGE = BOARD_HIGHLIGHT;

    /**
     * Translucent selected-square fill.
     */
    public static Color SELECTED_EDGE = new Color(101, 143, 74, 112);

    /**
     * Quiet legal-target marker fill.
     */
    public static Color LEGAL_TARGET = new Color(77, 103, 50, 158);

    /**
     * Capture legal-target marker fill.
     */
    public static Color LEGAL_CAPTURE_FILL = new Color(77, 103, 50, 58);

    /**
     * Capture legal-target marker edge.
     */
    public static Color LEGAL_CAPTURE_EDGE = new Color(77, 103, 50, 132);

    /**
     * Suggested-move arrow color.
     */
    public static Color BOARD_ARROW = new Color(143, 189, 232);

    /**
     * Check highlight radial core.
     */
    public static Color CHECK_CORE = new Color(PASTEL_CORAL.getRed(), PASTEL_CORAL.getGreen(),
            PASTEL_CORAL.getBlue(), 245);

    /**
     * Check highlight glow.
     */
    public static Color CHECK_GLOW = new Color(242, 181, 176, 209);

    /**
     * Check highlight square fill.
     */
    public static Color CHECK_FILL = new Color(PASTEL_CORAL.getRed(), PASTEL_CORAL.getGreen(),
            PASTEL_CORAL.getBlue(), 190);

    /**
     * Check highlight square edge.
     */
    public static Color CHECK_EDGE = new Color(242, 181, 176, 105);

    /**
     * Eval-bar dark side fill.
     */
    public static Color EVAL_BLACK = Color.BLACK;

    /**
     * Eval-bar light side fill.
     */
    public static Color EVAL_WHITE = Color.WHITE;

    /**
     * Eval-bar frame color.
     */
    public static Color EVAL_FRAME = new Color(17, 17, 17);

    /**
     * Eval-bar divider color.
     */
    public static Color EVAL_DIVIDER = new Color(128, 128, 128, 176);

    /**
     * Tab strip selected accent underline.
     */
    public static Color TAB_ACCENT_UNDERLINE = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 255);

    /**
     * Tab strip rollover fill.
     */
    public static Color TAB_HOVER = new Color(PASTEL_DOCUMENT.getRed(), PASTEL_DOCUMENT.getGreen(),
            PASTEL_DOCUMENT.getBlue(), 255);

    /**
     * Tab strip resting fill.
     */
    public static Color TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);

    /**
     * Scrollbar track fill.
     */
    public static Color SCROLLBAR_TRACK = new Color(PASTEL_CHROME.getRed(), PASTEL_CHROME.getGreen(),
            PASTEL_CHROME.getBlue(), 0);

    /**
     * Scrollbar thumb resting fill.
     */
    public static Color SCROLLBAR_THUMB = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(),
            PASTEL_MUTED.getBlue(), 80);

    /**
     * Scrollbar thumb hover fill.
     */
    public static Color SCROLLBAR_THUMB_HOVER = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(),
            PASTEL_MUTED.getBlue(), 130);

    /**
     * Tooltip surface color.
     */
    public static Color TOOLTIP_BG = PASTEL_CHROME;

    /**
     * Tooltip text color.
     */
    public static Color TOOLTIP_TEXT = PASTEL_INK;

    /**
     * Tooltip border color.
     */
    public static Color TOOLTIP_BORDER = PASTEL_BORDER;

    // Status families use pastel action-color tints for the surface, with
    // darker ink variants for readable text.

    /**
     * Success feedback surface.
     */
    public static Color STATUS_SUCCESS_BG = new Color(239, 250, 243);

    /**
     * Success feedback border.
     */
    public static Color STATUS_SUCCESS_BORDER = PASTEL_GREEN;

    /**
     * Success feedback text.
     */
    public static Color STATUS_SUCCESS_TEXT = PASTEL_GREEN_TEXT;

    /**
     * Warning feedback surface.
     */
    public static Color STATUS_WARNING_BG = new Color(255, 246, 216);

    /**
     * Warning feedback border.
     */
    public static Color STATUS_WARNING_BORDER = PASTEL_AMBER;

    /**
     * Warning feedback text.
     */
    public static Color STATUS_WARNING_TEXT = PASTEL_AMBER_TEXT;

    /**
     * Error feedback surface.
     */
    public static Color STATUS_ERROR_BG = new Color(255, 241, 240);

    /**
     * Error feedback border.
     */
    public static Color STATUS_ERROR_BORDER = PASTEL_CORAL;

    /**
     * Error feedback text.
     */
    public static Color STATUS_ERROR_TEXT = PASTEL_CORAL_TEXT;

    /**
     * Informational feedback surface.
     */
    public static Color STATUS_INFO_BG = new Color(0xEAF4FF);

    /**
     * Informational feedback border.
     */
    public static Color STATUS_INFO_BORDER = PASTEL_BLUE;

    /**
     * Informational feedback text.
     */
    public static Color STATUS_INFO_TEXT = PASTEL_BLUE_TEXT;

    /**
     * Ready feedback surface.
     */
    public static Color STATUS_READY_BG = STATUS_INFO_BG;

    /**
     * Ready feedback border.
     */
    public static Color STATUS_READY_BORDER = STATUS_INFO_BORDER;

    /**
     * Ready feedback text.
     */
    public static Color STATUS_READY_TEXT = STATUS_INFO_TEXT;

    /**
     * Running feedback surface.
     */
    public static Color STATUS_RUNNING_BG = STATUS_INFO_BG;

    /**
     * Running feedback border.
     */
    public static Color STATUS_RUNNING_BORDER = STATUS_INFO_BORDER;

    /**
     * Running feedback text.
     */
    public static Color STATUS_RUNNING_TEXT = STATUS_INFO_TEXT;

    /**
     * Complete feedback surface.
     */
    public static Color STATUS_COMPLETE_BG = STATUS_SUCCESS_BG;

    /**
     * Complete feedback border.
     */
    public static Color STATUS_COMPLETE_BORDER = STATUS_SUCCESS_BORDER;

    /**
     * Complete feedback text.
     */
    public static Color STATUS_COMPLETE_TEXT = STATUS_SUCCESS_TEXT;

    /**
     * Missing feedback surface.
     */
    public static Color STATUS_MISSING_BG = STATUS_WARNING_BG;

    /**
     * Missing feedback border.
     */
    public static Color STATUS_MISSING_BORDER = STATUS_WARNING_BORDER;

    /**
     * Missing feedback text.
     */
    public static Color STATUS_MISSING_TEXT = STATUS_WARNING_TEXT;

    /**
     * Not-run feedback surface.
     */
    public static Color STATUS_NOT_RUN_BG = PASTEL_CHROME;

    /**
     * Not-run feedback border.
     */
    public static Color STATUS_NOT_RUN_BORDER = PASTEL_BORDER;

    /**
     * Not-run feedback text.
     */
    public static Color STATUS_NOT_RUN_TEXT = PASTEL_MUTED;

    /**
     * Paused feedback surface.
     */
    public static Color STATUS_PAUSED_BG = STATUS_WARNING_BG;

    /**
     * Paused feedback border.
     */
    public static Color STATUS_PAUSED_BORDER = STATUS_WARNING_BORDER;

    /**
     * Paused feedback text.
     */
    public static Color STATUS_PAUSED_TEXT = STATUS_WARNING_TEXT;

    /**
     * Stale feedback surface.
     */
    public static Color STATUS_STALE_BG = STATUS_WARNING_BG;

    /**
     * Stale feedback border.
     */
    public static Color STATUS_STALE_BORDER = STATUS_WARNING_BORDER;

    /**
     * Stale feedback text.
     */
    public static Color STATUS_STALE_TEXT = STATUS_WARNING_TEXT;

    /**
     * Code-preview surface.
     */
    public static Color CODE_BLOCK_BG = PASTEL_CHROME;

    /**
     * Code-preview border.
     */
    public static Color CODE_BLOCK_BORDER = PASTEL_BORDER;

    /**
     * Code-preview text.
     */
    public static Color CODE_BLOCK_TEXT = PASTEL_INK;

    /**
     * Compact logo tile fill.
     */
    public static Color LOGO_BACKGROUND = new Color(PASTEL_PURPLE.getRed(), PASTEL_PURPLE.getGreen(),
            PASTEL_PURPLE.getBlue(), 230);

    /**
     * Compact logo mark fill.
     */
    public static Color LOGO_MARK = PASTEL_CORAL;

    /**
     * Toggle focus-ring color.
     */
    public static Color TOGGLE_FOCUS = new Color(INPUT_FOCUS.getRed(), INPUT_FOCUS.getGreen(),
            INPUT_FOCUS.getBlue(), 95);

    /**
     * Client-property key marking accent-styled buttons.
     */
    public static final String CLIENT_PRIMARY = "workbench.primary";

    /**
     * Client-property key marking the explicit button hierarchy variant.
     */
    public static final String CLIENT_BUTTON_VARIANT = "workbench.buttonVariant";

    /**
     * Client-property key carrying an explicit icon kind for a button.
     */
    public static final String CLIENT_ICON_KIND = "workbench.icon";

    /**
     * Client-property key marking icon-only buttons that must keep tooltip text.
     */
    public static final String CLIENT_ICON_ONLY = "workbench.iconOnly";

    /**
     * Client-property key marking flat command-tab toggle buttons.
     */
    private static final String CLIENT_COMMAND_TAB = "workbench.commandTab";

    /**
     * Client-property key marking a text field/area that must stay transparent
     * (e.g. an inline preview) so a theme refresh only re-colours it instead of
     * stamping it with the opaque input surface.
     */
    public static final String CLIENT_TRANSPARENT_FIELD = "workbench.transparentField";

    /**
     * Client-property key marking read-only code/command preview blocks.
     */
    public static final String CLIENT_CODE_BLOCK = "workbench.codeBlock";

    // ------------------------------------------------------------------
    // Spacing scale
    //
    // A single 4px-based scale every panel should lay out against, so gaps,
    // struts, insets and borders stay visually consistent instead of each
    // call site inventing its own magic number.
    // ------------------------------------------------------------------

    /**
     * Extra-small spacing step (4px) — tight strut between tightly-coupled
     * lines such as a title and its subtitle.
     */
    public static final int SPACE_XS = 4;

    /**
     * Small spacing step (8px) — the default gap between sibling controls in
     * a toolbar or button row.
     */
    public static final int SPACE_SM = 8;

    /**
     * Medium spacing step (12px) — panel padding and the gap between distinct
     * control groups.
     */
    public static final int SPACE_MD = 12;

    /**
     * Large spacing step (16px) — separation between major sections.
     */
    public static final int SPACE_LG = 16;

    /**
     * Extra-large spacing step (24px) — generous section breaks.
     */
    public static final int SPACE_XL = 24;

    /**
     * Shared corner radius for compact custom-painted chrome (toggle switches,
     * segmented selectors, chips). VS Code on macOS is still dense, but its
     * controls are softer than the near-square Windows/Linux chrome.
     */
    public static final int RADIUS = 7;

    /**
     * Standard height for compact toolbar controls (combos, segmented
     * switchers, toggles) so a control row lines up on a single baseline.
     */
    public static final int CONTROL_HEIGHT = 32;

    /**
     * Taller control height for a single full-width hero call-to-action (the
     * Play tab's "New Game"), so the intent reads as deliberate rather than a
     * magic number.
     */
    public static final int CONTROL_HEIGHT_TALL = 40;

    /**
     * Shared row height for data tables (datasets, MCTS root moves, dashboard
     * jobs) so every grid reads with the same comfortable, scannable density.
     */
    public static final int TABLE_ROW_HEIGHT = 28;

    /**
     * Named type scale so headings, body, and captions read as a deliberate
     * hierarchy instead of ad-hoc point sizes. Snaps to the sizes already
     * dominant in the app to avoid a disruptive reflow.
     */
    public static final int FONT_PAGE_TITLE = 20;

    /**
     * Backward-compatible title token.
     */
    public static final int FONT_TITLE = FONT_PAGE_TITLE;

    /**
     * Section heading size.
     */
    public static final int FONT_SECTION_TITLE = 13;

    /**
     * Default body / control size.
     */
    public static final int FONT_BODY = 13;

    /**
     * Default control text size.
     */
    public static final int FONT_CONTROL = 13;

    /**
     * Dense table text size.
     */
    public static final int FONT_DENSE_TABLE = 12;

    /**
     * Caption / helper-text size.
     */
    public static final int FONT_CAPTION = 11;

    /**
     * Metadata text size.
     */
    public static final int FONT_METADATA = 11;

    /**
     * Monospace command/FEN text size.
     */
    public static final int FONT_MONO = 12;

    /**
     * Uppercase eyebrow / micro-label size.
     */
    public static final int FONT_MICRO = FONT_METADATA;

    /**
     * Base z-order layer.
     */
    public static final int Z_BASE = 0;

    /**
     * Floating chrome z-order layer.
     */
    public static final int Z_FLOATING = 100;

    /**
     * Modal overlay z-order layer.
     */
    public static final int Z_MODAL = 200;

    /**
     * Command palette z-order layer.
     */
    public static final int Z_PALETTE = 300;

    /**
     * Toast z-order layer.
     */
    public static final int Z_TOAST = 400;

    /**
     * Maximum content width for report-style tabs that read better as a column
     * than stretched edge-to-edge. Operational surfaces (the dashboard grid,
     * data tables) deliberately ignore this and use the full canvas.
     */
    public static final int CONTENT_MAX_WIDTH = 1440;

    // ------------------------------------------------------------------
    // Neural-network visualization palette
    //
    // The accent colors the NNUE / CNN / BT4 / OTIS views paint activations and
    // data-flow branches with. Kept here (rather than as private literals in
    // TensorViz) so the network views speak the same color language
    // as the rest of the workbench chrome.
    // ------------------------------------------------------------------

    // Content keeps color only where it carries sign or category. Signed
    // tensors use green/red, architecture/data-flow uses blue, and the policy
    // branch uses the pastel purple family.

    /**
     * Positive-activation accent (gain / "up").
     */
    public static Color NN_POSITIVE = PASTEL_GREEN;

    /**
     * Negative-activation accent (loss / "down").
     */
    public static Color NN_NEGATIVE = PASTEL_CORAL;

    /**
     * Trunk / data-flow accent.
     */
    public static Color NN_TRUNK = PASTEL_BLUE;

    /**
     * Interaction focus accent for selected NN cells, feature anchors, and
     * graph nodes.
     */
    public static Color NN_FOCUS = PASTEL_BLUE;

    /**
     * Policy-branch accent.
     */
    public static Color NN_POLICY = PASTEL_PURPLE;

    /**
     * Value-branch accent.
     */
    public static Color NN_VALUE = PASTEL_GREEN;

    /**
     * Neutral fill for cells carrying no signal.
     */
    public static Color NN_NEUTRAL = PASTEL_SUBTLE;

    /**
     * Lightest signed-heatmap fill (near zero).
     */
    public static Color NN_HEAT_ZERO = PASTEL_SUBTLE;

    /**
     * Prevents instantiation.
     */
    private Theme() {
        // utility
    }

    /**
     * Returns the active color mode.
     *
     * @return active mode
     */
    public static Mode mode() {
        return mode;
    }

    /**
     * Returns whether the dark palette is active.
     *
     * @return true in dark mode
     */
    public static boolean isDark() {
        return mode == Mode.DARK;
    }

    /**
     * Returns the active UI density.
     *
     * @return active density
     */
    public static Density density() {
        return density;
    }

    /**
     * Switches the active UI density. Callers that want existing realized
     * components to follow the new scale should rescale their live component
     * trees with {@link #rescaleFonts(Component, double)} using the ratio
     * returned here, then revalidate.
     *
     * @param value requested density
     * @return the font-scale ratio from the previous density to the new one
     *         (1.0 when unchanged), suitable for {@link #rescaleFonts}
     */
    public static double setDensity(Density value) {
        Density next = value == null ? Density.DENSE : value;
        double ratio = next.fontScale() / density.fontScale();
        if (next != density) {
            density = next;
            clearFontCaches();
        }
        return ratio;
    }

    /**
     * Switches the active color mode.
     *
     * @param value requested mode
     */
    public static void setMode(Mode value) {
        mode = value == null ? Mode.LIGHT : value;
        if (mode == Mode.DARK) {
            applyDarkPalette();
        } else {
            applyLightPalette();
        }
    }

    /**
     * Applies and records a semantic foreground role.
     *
     * @param component target component
     * @param role foreground role
     */
    public static void foreground(JComponent component, ForegroundRole role) {
        if (component == null) {
            return;
        }
        ForegroundRole resolved = role == null ? ForegroundRole.TEXT : role;
        component.putClientProperty(FOREGROUND_ROLE_PROPERTY, resolved);
        component.setForeground(foregroundColor(resolved));
    }

    /**
     * Returns the active color for a semantic foreground role.
     *
     * @param role foreground role
     * @return active color
     */
    public static Color foregroundColor(ForegroundRole role) {
        return switch (role == null ? ForegroundRole.TEXT : role) {
            case MUTED -> MUTED;
            case SUCCESS -> STATUS_SUCCESS_TEXT;
            case WARNING -> STATUS_WARNING_TEXT;
            case ERROR -> STATUS_ERROR_TEXT;
            case INFO -> STATUS_INFO_TEXT;
            case TERMINAL -> TERMINAL_TEXT;
            case TEXT -> TEXT;
        };
    }

    /**
     * Applies the current palette to an existing component tree.
     *
     * @param component root component
     */
    public static void refreshComponentTree(Component component) {
        if (component == null) {
            return;
        }
        refreshComponent(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                refreshComponentTree(child);
            }
        }
        component.repaint();
    }

    /**
     * Refreshes one existing component after a palette switch.
     *
     * @param component component
     */
    private static void refreshComponent(Component component) {
        if (component instanceof JComponent jComponent) {
            refreshBorder(jComponent);
        }
        if (component instanceof BackdropPanel) {
            component.setBackground(BG);
        } else if (component instanceof SurfacePanel) {
            component.setBackground(PANEL_SOLID);
            component.setForeground(TEXT);
        } else if (component instanceof ConsoleLike console) {
            console.applyConsoleTheme();
        } else if (component instanceof JTextArea area) {
            if (Boolean.TRUE.equals(area.getClientProperty(CLIENT_CODE_BLOCK))) {
                codeBlock(area);
            } else if (Boolean.TRUE.equals(area.getClientProperty(CLIENT_TRANSPARENT_FIELD))) {
                // Inline preview: keep it transparent so the host surface shows
                // through; only re-resolve the palette-sensitive colours.
                area.setForeground(TEXT);
                area.setCaretColor(MUTED);
                area.setSelectionColor(SELECTION_SOLID);
            } else {
                area(area);
            }
        } else if (component instanceof JFormattedTextField field
                && field.getParent() instanceof JSpinner.DefaultEditor editor) {
            Ui.styleSpinnerEditor(editor);
        } else if (component instanceof JTextField field) {
            field(field);
        } else if (component instanceof JTextPane pane) {
            pane.setBackground(TEXT_AREA);
            pane.setForeground(TEXT);
            pane.setCaretColor(TEXT);
            pane.setSelectionColor(TEXT_SELECTION);
            pane.setSelectedTextColor(TEXT);
        } else if (component instanceof JComboBox<?> combo) {
            Ui.styleCombo(combo);
        } else if (component instanceof JSlider slider) {
            Ui.styleSlider(slider);
        } else if (component instanceof JSpinner spinner) {
            Ui.styleSpinner(spinner);
        } else if (component instanceof JTable table) {
            table(table, Math.max(24, table.getRowHeight()));
        } else if (component instanceof JList<?> list) {
            list(list);
        } else if (component instanceof JTabbedPane tabs) {
            Ui.styleTabs(tabs);
        } else if (component instanceof JScrollPane pane) {
            Ui.refreshScrollPaneTheme(pane);
        } else if (component instanceof ToggleBox toggle) {
            toggle.setForeground(TEXT);
            toggle.setFont(font(13, Font.PLAIN));
        } else if (component instanceof JMenuBar menuBar) {
            menuBar.setOpaque(true);
            menuBar.setBackground(BG);
            menuBar.setForeground(TEXT);
            menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
            menuBar.setFont(font(12, Font.PLAIN));
        } else if (component instanceof JMenuItem item) {
            item.setOpaque(true);
            item.setBackground(item.getParent() instanceof JMenuBar ? BG : PANEL_SOLID);
            item.setForeground(TEXT);
            item.setFont(font(12, Font.PLAIN));
            item.setBorder(pad(5, 10, 5, 10));
        } else if (component instanceof AbstractButton button) {
            if (Boolean.TRUE.equals(button.getClientProperty(CLIENT_COMMAND_TAB))) {
                applyCommandTabState(button);
                } else if (button.getClientProperty(CLIENT_BUTTON_VARIANT) instanceof ButtonVariant variant) {
                button(button, variant);
            } else if (button.getClientProperty(CLIENT_PRIMARY) instanceof Boolean value) {
                button(button, value.booleanValue());
            } else {
                button.setForeground(TEXT);
                button.setFont(font(13, Font.PLAIN));
            }
        } else if (component instanceof JLabel label) {
            refreshForeground(label);
        } else if (component instanceof JComponent jComponent
                && !(component.getParent() instanceof JScrollPane)) {
            // A scroll pane's own viewport and corner fillers are owned by
            // Ui.refreshScrollPaneTheme (which honours the declared embedding
            // surface). Reassigning PANEL_SOLID here would clobber that and bring
            // back the darker box-in-a-card on every theme switch.
            refreshForeground(jComponent);
            if (jComponent.isOpaque()) {
                jComponent.setBackground(PANEL_SOLID);
            } else {
                jComponent.setBackground(BG);
            }
        }
    }

    /**
     * Rebuilds stale workbench line borders with the active palette.
     *
     * @param component component whose border should be refreshed
     */
    private static void refreshBorder(JComponent component) {
        Border border = component.getBorder();
        Border refreshed = refreshedBorder(border);
        if (refreshed != border) {
            component.setBorder(refreshed);
        }
    }

    /**
     * Returns an equivalent border using the active line color when possible.
     *
     * @param border source border
     * @return refreshed border, or the original border when it is unrelated to
     *         workbench chrome
     */
    private static Border refreshedBorder(Border border) {
        if (border instanceof CompoundBorder compound) {
            Border outside = refreshedBorder(compound.getOutsideBorder());
            Border inside = refreshedBorder(compound.getInsideBorder());
            if (outside != compound.getOutsideBorder() || inside != compound.getInsideBorder()) {
                return BorderFactory.createCompoundBorder(outside, inside);
            }
        } else if (border instanceof LineBorder line && isWorkbenchLineColor(line.getLineColor())) {
            return BorderFactory.createLineBorder(currentLineColor(line.getLineColor()),
                    line.getThickness(), line.getRoundedCorners());
        } else if (border instanceof MatteBorder matte && isWorkbenchLineColor(matte.getMatteColor())) {
            Insets insets = matte.getBorderInsets();
            return BorderFactory.createMatteBorder(insets.top, insets.left,
                    insets.bottom, insets.right, currentLineColor(matte.getMatteColor()));
        }
        return border;
    }

    /**
     * Returns whether a color is one of the workbench line tokens.
     *
     * @param color border color
     * @return true when the color should follow {@link #LINE}
     */
    private static boolean isWorkbenchLineColor(Color color) {
        if (color == null) {
            return false;
        }
        int rgb = color.getRGB() & 0x00ff_ffff;
        return rgb == (PASTEL_BORDER.getRGB() & 0x00ff_ffff)
                || rgb == 0xe5e5e5
                || rgb == 0xd4d4d4
                || rgb == 0xdadadf
                || rgb == 0xc6c6cc
                || rgb == 0xe1e5eb
                || rgb == 0xc7cdd7
                || rgb == (DARK_SUBTLE.getRGB() & 0x00ff_ffff)
                || rgb == (DARK_BORDER.getRGB() & 0x00ff_ffff)
                || rgb == 0x303031
                || rgb == 0x3a3a3c
                || rgb == 0x373737
                || rgb == 0x454545
                || rgb == 0x48484a
                || rgb == 0x2b2b2b
                || rgb == 0x3c3c3c;
    }

    /**
     * Returns the active line color while preserving source alpha.
     *
     * @param source source color
     * @return active line color
     */
    private static Color currentLineColor(Color source) {
        if (source == null || source.getAlpha() == LINE.getAlpha()) {
            return LINE;
        }
        return new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), source.getAlpha());
    }

    /**
     * Refreshes semantic foreground color for one component.
     *
     * @param component component
     */
    public static void refreshForeground(JComponent component) {
        if (component == null) {
            return;
        }
        Object role = component.getClientProperty(FOREGROUND_ROLE_PROPERTY);
        if (role instanceof ForegroundRole foregroundRole) {
            component.setForeground(foregroundColor(foregroundRole));
            return;
        }
        component.setForeground(foregroundColor(inferForegroundRole(component.getForeground())));
    }

    /**
     * Infers a foreground role for older components that predate explicit
     * foreground-role client properties.
     *
     * @param color current foreground color
     * @return inferred role
     */
    private static ForegroundRole inferForegroundRole(Color color) {
        if (sameColor(color, PASTEL_MUTED) || sameColor(color, DARK_MUTED)) {
            return ForegroundRole.MUTED;
        }
        if (sameColor(color, PASTEL_GREEN_TEXT) || sameColor(color, DARK_SUCCESS_TEXT)
                || sameColor(color, STATUS_SUCCESS_TEXT)) {
            return ForegroundRole.SUCCESS;
        }
        if (sameColor(color, PASTEL_AMBER_TEXT) || sameColor(color, DARK_WARNING_TEXT)
                || sameColor(color, STATUS_WARNING_TEXT)) {
            return ForegroundRole.WARNING;
        }
        if (sameColor(color, PASTEL_CORAL_TEXT) || sameColor(color, DARK_ERROR_TEXT)
                || sameColor(color, STATUS_ERROR_TEXT)) {
            return ForegroundRole.ERROR;
        }
        if (sameColor(color, PASTEL_BLUE_TEXT) || sameColor(color, DARK_INFO_TEXT)
                || sameColor(color, STATUS_INFO_TEXT)) {
            return ForegroundRole.INFO;
        }
        if (sameColor(color, TERMINAL_TEXT)) {
            return ForegroundRole.TERMINAL;
        }
        return ForegroundRole.TEXT;
    }

    /**
     * Returns whether two colors have equal RGB and alpha channels.
     *
     * @param first first color
     * @param second second color
     * @return true when equal
     */
    private static boolean sameColor(Color first, Color second) {
        return first != null && second != null && first.getRGB() == second.getRGB();
    }

    /**
     * Small local interface for terminal-like components without introducing a
     * dependency from the theme package back to command components.
     */
    public interface ConsoleLike {
        /**
         * Applies terminal colors after a theme change.
         */
        void applyConsoleTheme();
    }

    /**
     * Restores the light palette.
     */
    private static void applyLightPalette() {
        BG = PASTEL_CHROME;
        TRANSPARENT = new Color(PASTEL_DOCUMENT.getRed(), PASTEL_DOCUMENT.getGreen(), PASTEL_DOCUMENT.getBlue(), 0);
        PANEL = PASTEL_DOCUMENT;
        PANEL_SOLID = blendOver(PANEL, BG);
        GLASS_HIGHLIGHT = new Color(255, 255, 255, 112);
        BACKDROP_TOP = new Color(0xF8F8FA);
        BACKDROP_BOTTOM = new Color(0xEFEFF3);
        ELEVATED = PASTEL_CHROME;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        CARD = blendOver(PANEL, BG);
        CARD_BORDER = PASTEL_BORDER;
        LINE = PASTEL_BORDER;
        TEXT = PASTEL_INK;
        MUTED = PASTEL_MUTED;
        ACCENT = PASTEL_BLUE;
        SELECTION = new Color(0xDDEBFF);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = PASTEL_BLUE_HOVER;
        ACCENT_PRESSED = PASTEL_BLUE_PRESSED;
        SECONDARY_BUTTON = PASTEL_CHROME;
        SECONDARY_BUTTON_HOVER = PASTEL_SUBTLE;
        SECONDARY_BUTTON_PRESSED = new Color(0xDFDFE4);
        SECONDARY_BUTTON_TEXT = PASTEL_INK;
        GHOST_BUTTON = new Color(0, 0, 0, 0);
        GHOST_BUTTON_HOVER = PASTEL_SUBTLE;
        GHOST_BUTTON_PRESSED = new Color(0xDFDFE4);
        GHOST_BUTTON_TEXT = PASTEL_INK;
        DESTRUCTIVE_BUTTON = new Color(0xFFF1F0);
        DESTRUCTIVE_BUTTON_HOVER = new Color(0xFFE4E2);
        DESTRUCTIVE_BUTTON_PRESSED = new Color(0xFFD6D2);
        DESTRUCTIVE_BUTTON_TEXT = new Color(0xB3261E);
        BUTTON_DISABLED_BG = PASTEL_CHROME;
        BUTTON_DISABLED_BORDER = PASTEL_BORDER;
        BUTTON_DISABLED_TEXT = PASTEL_MUTED;
        INPUT_BORDER = new Color(0xC6C6CC);
        INPUT_FOCUS = PASTEL_BLUE;
        FOCUS_RING = withAlpha(INPUT_FOCUS, 95);
        INPUT_DISABLED = PASTEL_CHROME;
        TOGGLE_BG = PASTEL_SUBTLE;
        TOGGLE_BORDER = INPUT_BORDER;
        TOGGLE_TRACK = PASTEL_MUTED;
        TOGGLE_ON_BG = new Color(0xD5EBFF);
        TOGGLE_ON_TRACK = PASTEL_BLUE;
        TOGGLE_THUMB = PASTEL_DOCUMENT;
        INPUT = PASTEL_DOCUMENT;
        TEXT_AREA = PASTEL_DOCUMENT;
        TERMINAL = PASTEL_DOCUMENT;
        TERMINAL_TEXT = PASTEL_INK;
        TEXT_SELECTION = new Color(0xADD6FF);
        PRIMARY_BUTTON_TEXT = Color.WHITE;
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(PASTEL_SUBTLE.getRed(), PASTEL_SUBTLE.getGreen(), PASTEL_SUBTLE.getBlue(), 255);
        TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);
        SCROLLBAR_TRACK = new Color(PASTEL_CHROME.getRed(), PASTEL_CHROME.getGreen(), PASTEL_CHROME.getBlue(), 0);
        SCROLLBAR_THUMB = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 80);
        SCROLLBAR_THUMB_HOVER = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 130);
        TOOLTIP_BG = PASTEL_CHROME;
        TOOLTIP_TEXT = PASTEL_INK;
        TOOLTIP_BORDER = PASTEL_BORDER;
        STATUS_SUCCESS_BG = new Color(239, 250, 243);
        STATUS_SUCCESS_BORDER = PASTEL_GREEN;
        STATUS_SUCCESS_TEXT = PASTEL_GREEN_TEXT;
        STATUS_WARNING_BG = new Color(255, 246, 216);
        STATUS_WARNING_BORDER = PASTEL_AMBER;
        STATUS_WARNING_TEXT = PASTEL_AMBER_TEXT;
        STATUS_ERROR_BG = new Color(255, 241, 240);
        STATUS_ERROR_BORDER = PASTEL_CORAL;
        STATUS_ERROR_TEXT = PASTEL_CORAL_TEXT;
        STATUS_INFO_BG = new Color(0xEAF4FF);
        STATUS_INFO_BORDER = PASTEL_BLUE;
        STATUS_INFO_TEXT = PASTEL_BLUE_TEXT;
        STATUS_READY_BG = STATUS_INFO_BG;
        STATUS_READY_BORDER = STATUS_INFO_BORDER;
        STATUS_READY_TEXT = STATUS_INFO_TEXT;
        STATUS_RUNNING_BG = STATUS_INFO_BG;
        STATUS_RUNNING_BORDER = STATUS_INFO_BORDER;
        STATUS_RUNNING_TEXT = STATUS_INFO_TEXT;
        STATUS_COMPLETE_BG = STATUS_SUCCESS_BG;
        STATUS_COMPLETE_BORDER = STATUS_SUCCESS_BORDER;
        STATUS_COMPLETE_TEXT = STATUS_SUCCESS_TEXT;
        STATUS_MISSING_BG = STATUS_WARNING_BG;
        STATUS_MISSING_BORDER = STATUS_WARNING_BORDER;
        STATUS_MISSING_TEXT = STATUS_WARNING_TEXT;
        STATUS_NOT_RUN_BG = PASTEL_CHROME;
        STATUS_NOT_RUN_BORDER = PASTEL_BORDER;
        STATUS_NOT_RUN_TEXT = PASTEL_MUTED;
        STATUS_PAUSED_BG = STATUS_WARNING_BG;
        STATUS_PAUSED_BORDER = STATUS_WARNING_BORDER;
        STATUS_PAUSED_TEXT = STATUS_WARNING_TEXT;
        STATUS_STALE_BG = STATUS_WARNING_BG;
        STATUS_STALE_BORDER = STATUS_WARNING_BORDER;
        STATUS_STALE_TEXT = STATUS_WARNING_TEXT;
        CODE_BLOCK_BG = PASTEL_CHROME;
        CODE_BLOCK_BORDER = PASTEL_BORDER;
        CODE_BLOCK_TEXT = PASTEL_INK;
        LOGO_BACKGROUND = new Color(PASTEL_PURPLE.getRed(), PASTEL_PURPLE.getGreen(), PASTEL_PURPLE.getBlue(), 230);
        LOGO_MARK = PASTEL_CORAL;
        TOGGLE_FOCUS = withAlpha(INPUT_FOCUS, 95);
        NN_POSITIVE = PASTEL_GREEN;
        NN_NEGATIVE = PASTEL_CORAL;
        NN_TRUNK = PASTEL_BLUE;
        NN_FOCUS = PASTEL_BLUE;
        NN_POLICY = PASTEL_PURPLE;
        NN_VALUE = PASTEL_GREEN;
        NN_NEUTRAL = PASTEL_SUBTLE;
        NN_HEAT_ZERO = PASTEL_SUBTLE;
    }

    /**
     * Applies the dark palette.
     */
    private static void applyDarkPalette() {
        BG = DARK_CHROME;
        TRANSPARENT = new Color(DARK_DOCUMENT.getRed(), DARK_DOCUMENT.getGreen(), DARK_DOCUMENT.getBlue(), 0);
        PANEL = DARK_DOCUMENT;
        PANEL_SOLID = blendOver(PANEL, BG);
        GLASS_HIGHLIGHT = new Color(255, 255, 255, 24);
        BACKDROP_TOP = new Color(0x303030);
        BACKDROP_BOTTOM = new Color(0x242424);
        ELEVATED = DARK_ELEVATED;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        // One restrained surface level on a deep canvas, defined by a crisp
        // hairline rather than a big lighter fill — a refined, grown-up
        // separation instead of a stack of chunky lighter-grey boxes.
        CARD = new Color(0x232323);
        CARD_BORDER = new Color(0x3E3E3E);
        LINE = DARK_SUBTLE;
        TEXT = DARK_INK;
        MUTED = DARK_MUTED;
        ACCENT = DARK_ACCENT;
        SELECTION = new Color(0x264F78);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = DARK_ACCENT_HOVER;
        ACCENT_PRESSED = DARK_ACCENT_PRESSED;
        SECONDARY_BUTTON = new Color(0x363636);
        SECONDARY_BUTTON_HOVER = new Color(0x424242);
        SECONDARY_BUTTON_PRESSED = new Color(0x4B4B4B);
        SECONDARY_BUTTON_TEXT = DARK_INK;
        GHOST_BUTTON = new Color(0, 0, 0, 0);
        GHOST_BUTTON_HOVER = new Color(0x373737);
        GHOST_BUTTON_PRESSED = new Color(0x424242);
        GHOST_BUTTON_TEXT = DARK_INK;
        DESTRUCTIVE_BUTTON = withAlpha(new Color(0xF07167), 18);
        DESTRUCTIVE_BUTTON_HOVER = withAlpha(new Color(0xF07167), 34);
        DESTRUCTIVE_BUTTON_PRESSED = withAlpha(new Color(0xF07167), 52);
        DESTRUCTIVE_BUTTON_TEXT = new Color(0xF07167);
        BUTTON_DISABLED_BG = DARK_DOCUMENT;
        BUTTON_DISABLED_BORDER = DARK_SUBTLE;
        BUTTON_DISABLED_TEXT = new Color(0x868686);
        INPUT_BORDER = DARK_BORDER;
        INPUT_FOCUS = DARK_ACCENT;
        FOCUS_RING = withAlpha(INPUT_FOCUS, 120);
        INPUT_DISABLED = new Color(0x323232);
        TOGGLE_BG = DARK_ELEVATED;
        TOGGLE_BORDER = DARK_BORDER;
        TOGGLE_TRACK = DARK_MUTED;
        TOGGLE_ON_BG = withAlpha(new Color(0x2489DB), 130);
        TOGGLE_ON_TRACK = DARK_ACCENT;
        TOGGLE_THUMB = DARK_INK;
        INPUT = DARK_ELEVATED;
        TEXT_AREA = DARK_DOCUMENT;
        TERMINAL = DARK_DOCUMENT;
        TERMINAL_TEXT = DARK_INK;
        TEXT_SELECTION = new Color(0x264F78);
        PRIMARY_BUTTON_TEXT = Color.WHITE;
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(0x373737);
        TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);
        SCROLLBAR_TRACK = new Color(DARK_CHROME.getRed(), DARK_CHROME.getGreen(), DARK_CHROME.getBlue(), 0);
        SCROLLBAR_THUMB = new Color(DARK_MUTED.getRed(), DARK_MUTED.getGreen(), DARK_MUTED.getBlue(), 82);
        SCROLLBAR_THUMB_HOVER = new Color(DARK_MUTED.getRed(), DARK_MUTED.getGreen(), DARK_MUTED.getBlue(), 150);
        TOOLTIP_BG = DARK_ELEVATED;
        TOOLTIP_TEXT = DARK_INK;
        TOOLTIP_BORDER = DARK_BORDER;
        STATUS_SUCCESS_BG = DARK_ELEVATED;
        STATUS_SUCCESS_BORDER = DARK_GREEN;
        STATUS_SUCCESS_TEXT = DARK_SUCCESS_TEXT;
        STATUS_WARNING_BG = DARK_ELEVATED;
        STATUS_WARNING_BORDER = DARK_AMBER;
        STATUS_WARNING_TEXT = DARK_WARNING_TEXT;
        STATUS_ERROR_BG = DARK_ELEVATED;
        STATUS_ERROR_BORDER = DARK_CORAL;
        STATUS_ERROR_TEXT = DARK_ERROR_TEXT;
        STATUS_INFO_BG = DARK_ELEVATED;
        STATUS_INFO_BORDER = DARK_ACCENT;
        STATUS_INFO_TEXT = DARK_INFO_TEXT;
        STATUS_READY_BG = STATUS_INFO_BG;
        STATUS_READY_BORDER = STATUS_INFO_BORDER;
        STATUS_READY_TEXT = STATUS_INFO_TEXT;
        STATUS_RUNNING_BG = STATUS_INFO_BG;
        STATUS_RUNNING_BORDER = STATUS_INFO_BORDER;
        STATUS_RUNNING_TEXT = STATUS_INFO_TEXT;
        STATUS_COMPLETE_BG = STATUS_SUCCESS_BG;
        STATUS_COMPLETE_BORDER = STATUS_SUCCESS_BORDER;
        STATUS_COMPLETE_TEXT = STATUS_SUCCESS_TEXT;
        STATUS_MISSING_BG = STATUS_WARNING_BG;
        STATUS_MISSING_BORDER = STATUS_WARNING_BORDER;
        STATUS_MISSING_TEXT = STATUS_WARNING_TEXT;
        STATUS_NOT_RUN_BG = DARK_ELEVATED;
        STATUS_NOT_RUN_BORDER = DARK_BORDER;
        STATUS_NOT_RUN_TEXT = DARK_MUTED;
        STATUS_PAUSED_BG = STATUS_WARNING_BG;
        STATUS_PAUSED_BORDER = STATUS_WARNING_BORDER;
        STATUS_PAUSED_TEXT = STATUS_WARNING_TEXT;
        STATUS_STALE_BG = STATUS_WARNING_BG;
        STATUS_STALE_BORDER = STATUS_WARNING_BORDER;
        STATUS_STALE_TEXT = STATUS_WARNING_TEXT;
        CODE_BLOCK_BG = DARK_DOCUMENT;
        CODE_BLOCK_BORDER = DARK_BORDER;
        CODE_BLOCK_TEXT = DARK_INK;
        LOGO_BACKGROUND = new Color(DARK_PURPLE.getRed(), DARK_PURPLE.getGreen(), DARK_PURPLE.getBlue(), 230);
        LOGO_MARK = DARK_CORAL;
        TOGGLE_FOCUS = withAlpha(INPUT_FOCUS, 120);
        NN_POSITIVE = DARK_GREEN;
        NN_NEGATIVE = DARK_CORAL;
        NN_TRUNK = DARK_ACCENT;
        NN_FOCUS = DARK_ACCENT;
        NN_POLICY = DARK_PURPLE;
        NN_VALUE = DARK_GREEN;
        NN_NEUTRAL = DARK_SUBTLE;
        NN_HEAT_ZERO = DARK_SUBTLE;
    }

    /**
     * Keeps chessboard and eval-bar colors stable across UI themes.
     */
    private static void setFixedBoardAndEvalColors() {
        BOARD_LIGHT = new Color(240, 217, 181);
        BOARD_DARK = new Color(181, 136, 99);
        BOARD_SHADOW = new Color(64, 64, 64);
        BOARD_EDGE = new Color(64, 64, 64);
        COORD_ON_LIGHT = BOARD_DARK;
        COORD_ON_DARK = BOARD_LIGHT;
        BOARD_HIGHLIGHT = new Color(246, 222, 93, 86);
        LAST_MOVE_EDGE = BOARD_HIGHLIGHT;
        SELECTED_EDGE = new Color(101, 143, 74, 112);
        LEGAL_TARGET = new Color(77, 103, 50, 158);
        LEGAL_CAPTURE_FILL = new Color(77, 103, 50, 58);
        LEGAL_CAPTURE_EDGE = new Color(77, 103, 50, 132);
        BOARD_ARROW = new Color(143, 189, 232);
        CHECK_CORE = new Color((isDark() ? DARK_CORAL : PASTEL_CORAL).getRed(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getGreen(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getBlue(), 245);
        CHECK_GLOW = withAlpha(isDark() ? DARK_CORAL : PASTEL_CORAL, 209);
        CHECK_FILL = new Color((isDark() ? DARK_CORAL : PASTEL_CORAL).getRed(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getGreen(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getBlue(), 190);
        CHECK_EDGE = withAlpha(isDark() ? DARK_CORAL : PASTEL_CORAL, 105);
        EVAL_BLACK = Color.BLACK;
        EVAL_WHITE = Color.WHITE;
        EVAL_FRAME = isDark() ? new Color(238, 238, 238) : new Color(17, 17, 17);
        EVAL_DIVIDER = new Color(128, 128, 128, 176);
    }

    /**
     * Creates a uniform empty padding border.
     *
     * @param all padding applied to every edge
     * @return border
     */
    public static Border pad(int all) {
        return BorderFactory.createEmptyBorder(all, all, all, all);
    }

    /**
     * Creates an empty padding border with symmetric vertical and horizontal
     * insets.
     *
     * @param vertical top and bottom padding
     * @param horizontal left and right padding
     * @return border
     */
    public static Border pad(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Returns a color with a replaced alpha channel.
     *
     * @param color color
     * @param alpha alpha value
     * @return color with alpha
     */
    public static Color withAlpha(Color color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
    }

    /**
     * Linearly interpolates between two colors in sRGB space.
     *
     * @param from start color
     * @param to end color
     * @param amount blend amount from 0 (from) to 1 (to)
     * @return blended color
     */
    public static Color lerp(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    /**
     * Paints a restrained macOS-style "frosted glass" elevated card: a soft
     * drop shadow, a lightly translucent rounded surface, a faint top highlight,
     * and a low-contrast border that warms toward the accent as the hover
     * amount rises. Reused by elevated surfaces (dashboard cards, floating
     * chrome) so glass treatment stays consistent. The surface body stays
     * near-opaque so text over it remains legible without true backdrop blur.
     *
     * @param g graphics context (its color/hints are modified; pass a scratch copy)
     * @param width component width
     * @param height component height
     * @param arc corner radius
     * @param hover hover amount from 0 (resting) to 1 (fully hovered)
     */
    public static void paintElevatedCard(Graphics2D g, int width, int height, int arc, float hover) {
        float h = Math.max(0f, Math.min(1f, hover));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int shadow = 3;
        int w = Math.max(0, width - 1);
        int surfaceHeight = Math.max(0, height - 1 - shadow);
        // Dark mode leans on the crisp hairline, not a heavy drop shadow, so
        // cards read as refined panels rather than chunky floating tiles.
        int baseShadowAlpha = isDark() ? 48 : 26;
        int shadowAlpha = Math.round(baseShadowAlpha * (0.75f + 0.45f * h));
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha))));
        g.fillRoundRect(2, shadow, Math.max(0, w - 3), surfaceHeight, arc, arc);
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha / 2))));
        g.fillRoundRect(1, Math.max(0, shadow - 1), Math.max(0, w - 1), surfaceHeight, arc, arc);
        // Opaque raised surface (CARD) sits a step above the page; in dark mode
        // that step is what makes the card read, so keep it solid rather than
        // translucent.
        g.setColor(CARD);
        g.fillRoundRect(0, 0, w, surfaceHeight, arc, arc);
        g.setColor(GLASS_HIGHLIGHT);
        g.drawLine(arc / 2, 1, Math.max(arc / 2, w - arc / 2), 1);
        g.setColor(lerp(CARD_BORDER, ACCENT, h * 0.85f));
        g.drawRoundRect(0, 0, w, surfaceHeight, arc, arc);
    }

    /**
     * Returns a CSS hex string for Swing HTML snippets.
     *
     * @param color color
     * @return #rrggbb color string
     */
    public static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Installs the platform look and feel on macOS, otherwise the stable
     * cross-platform look and feel, plus consistent workbench defaults.
     */
    public static void install() {
        try {
            UIManager.setLookAndFeel(preferredLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | javax.swing.UnsupportedLookAndFeelException ex) {
            LOGGER.log(Level.FINE, "Preferred LookAndFeel unavailable; keeping default LookAndFeel.", ex);
        }
        installFontDefaults();
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TabbedPane.background", BG);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", PANEL);
        UIManager.put("TabbedPane.contentAreaColor", BG);
        UIManager.put("TabbedPane.focus", LINE);
        UIManager.put("Focus.color", FOCUS_RING);
        UIManager.put("Button.focus", FOCUS_RING);
        UIManager.put("TextField.focus", FOCUS_RING);
        UIManager.put("ComboBox.focus", FOCUS_RING);
        UIManager.put("Table.background", ELEVATED_SOLID);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("Table.selectionBackground", SELECTION_SOLID);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Tree.background", ELEVATED_SOLID);
        UIManager.put("Tree.foreground", TEXT);
        UIManager.put("Tree.selectionBackground", SELECTION_SOLID);
        UIManager.put("Tree.selectionForeground", TEXT);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", PANEL_SOLID);
        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", TEXT_SELECTION);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("TextField.inactiveBackground", INPUT_DISABLED);
        UIManager.put("TextArea.background", TEXT_AREA);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", TEXT_SELECTION);
        UIManager.put("TextArea.selectionForeground", TEXT);
        UIManager.put("TextArea.inactiveBackground", INPUT_DISABLED);
        UIManager.put("TextPane.selectionBackground", TEXT_SELECTION);
        UIManager.put("TextPane.selectionForeground", TEXT);
        UIManager.put("ComboBox.background", ELEVATED_SOLID);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", SELECTION_SOLID);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.selectionBackground", SELECTION_SOLID);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Button.disabledText", BUTTON_DISABLED_TEXT);
        UIManager.put("TextField.inactiveForeground", MUTED);
        UIManager.put("TextArea.inactiveForeground", MUTED);
        UIManager.put("CheckBox.background", PANEL_SOLID);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("RadioButton.background", PANEL_SOLID);
        UIManager.put("RadioButton.foreground", TEXT);
        UIManager.put("MenuBar.background", BG);
        UIManager.put("MenuBar.foreground", TEXT);
        UIManager.put("Menu.background", BG);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("Menu.selectionBackground", SELECTION_SOLID);
        UIManager.put("Menu.selectionForeground", TEXT);
        UIManager.put("MenuItem.background", PANEL_SOLID);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", SELECTION_SOLID);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("CheckBoxMenuItem.background", PANEL_SOLID);
        UIManager.put("CheckBoxMenuItem.foreground", TEXT);
        UIManager.put("CheckBoxMenuItem.selectionBackground", SELECTION_SOLID);
        UIManager.put("CheckBoxMenuItem.selectionForeground", TEXT);
        UIManager.put("RadioButtonMenuItem.background", PANEL_SOLID);
        UIManager.put("RadioButtonMenuItem.foreground", TEXT);
        UIManager.put("RadioButtonMenuItem.selectionBackground", SELECTION_SOLID);
        UIManager.put("RadioButtonMenuItem.selectionForeground", TEXT);
        UIManager.put("PopupMenu.background", PANEL_SOLID);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(LINE));
        UIManager.put("OptionPane.background", PANEL_SOLID);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("FileChooser.background", BG);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listViewBackground", ELEVATED_SOLID);
        UIManager.put("FileChooser.listViewBorder", BorderFactory.createLineBorder(LINE));
        UIManager.put("FileChooser.lookInLabelText", "Location");
        UIManager.put("FileChooser.saveInLabelText", "Location");
        UIManager.put("FileChooser.fileNameLabelText", "File");
        UIManager.put("FileChooser.folderNameLabelText", "Folder");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Type");
        UIManager.put("FileChooser.acceptAllFileFilterText", "All files");
        UIManager.put("FileChooser.openButtonText", "Open");
        UIManager.put("FileChooser.saveButtonText", "Save");
        UIManager.put("FileChooser.cancelButtonText", "Cancel");
        UIManager.put("FileChooser.fileNameHeaderText", "Name");
        UIManager.put("FileChooser.fileSizeHeaderText", "Size");
        UIManager.put("FileChooser.fileTypeHeaderText", "Type");
        UIManager.put("FileChooser.fileDateHeaderText", "Modified");
        UIManager.put("FileChooser.upFolderToolTipText", "Parent folder");
        UIManager.put("FileChooser.homeFolderToolTipText", "Home");
        UIManager.put("FileChooser.newFolderToolTipText", "New folder");
        UIManager.put("FileChooser.listViewButtonToolTipText", "List view");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Details view");
        FileChooserIcons.installDefaults();
        UIManager.put("ToolTip.background", TOOLTIP_BG);
        UIManager.put("ToolTip.foreground", TOOLTIP_TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(TOOLTIP_BORDER));
    }

    /**
     * Returns the look and feel that best matches the current desktop.
     *
     * @return Swing look-and-feel class name
     */
    private static String preferredLookAndFeelClassName() {
        return isMacOs()
                ? UIManager.getSystemLookAndFeelClassName()
                : UIManager.getCrossPlatformLookAndFeelClassName();
    }

    /**
     * Returns whether the runtime is on macOS.
     *
     * @return true on macOS
     */
    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("mac");
    }

    /**
     * Installs default fonts for native Swing components that may be created
     * outside the workbench factories.
     */
    private static void installFontDefaults() {
        Font uiFont = font(FONT_CONTROL, Font.PLAIN);
        Font smallUiFont = font(FONT_DENSE_TABLE, Font.PLAIN);
        Font boldSmallUiFont = font(FONT_DENSE_TABLE, Font.BOLD);
        Font codeFont = mono(FONT_MONO);
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
        UIManager.put("List.font", mono(FONT_MONO));
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
     * Block, shade, and box-drawing glyphs that CLI progress bars rely on. A
     * console font must render every one of these or progress output shows
     * missing-glyph boxes (the resolved interface mono, e.g. Ubuntu Sans Mono,
     * lacks the eighth-blocks and heavy box rules).
     */
    private static final String CONSOLE_GLYPH_PROBE =
            "█▉▊▋▌▍▎▏" // full + left eighth blocks
            + "▀▄▐░▒▓"          // half blocks + shades
            + "─│┌┐└┘"          // light box drawing
            + "━┃";                                  // heavy box drawing

    /**
     * Resolved console/terminal family: a real monospace that covers every
     * {@link #CONSOLE_GLYPH_PROBE} glyph, or the logical monospace (a composite
     * font with full Unicode fallback) when no installed candidate qualifies.
     */
    private static final String CONSOLE_FONT_FAMILY = resolveConsoleFontFamily();

    /**
     * Resolves the first installed monospace candidate that can render all
     * progress-bar glyphs, falling back to the logical monospace.
     *
     * @return console font family
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
     * Resolves the first installed font family from a candidate stack.
     *
     * @param candidates preferred font families
     * @param fallback generic AWT fallback
     * @return installed font family or fallback
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
     * Returns the default UI font.
     *
     * @param size font size
     * @param style font style
     * @return font
     */
    public static Font font(float size, int style) {
        return cachedFont(UI_FONT_CACHE, UI_FONT_FAMILY, style, scaledSize(size));
    }

    /**
     * Returns a monospaced font.
     *
     * @param size font size
     * @return font
     */
    public static Font mono(float size) {
        return cachedFont(MONO_FONT_CACHE, MONO_FONT_FAMILY, Font.PLAIN, scaledSize(size));
    }

    /**
     * Returns the console/terminal monospaced font — like {@link #mono(float)}
     * but guaranteed to render block, shade, and box-drawing progress glyphs.
     *
     * @param size font size
     * @return console monospaced font
     */
    public static Font consoleMono(float size) {
        return cachedFont(CONSOLE_FONT_CACHE, CONSOLE_FONT_FAMILY, Font.PLAIN, scaledSize(size));
    }

    /**
     * Returns a cached immutable font instance.
     *
     * @param cache target cache
     * @param family font family
     * @param style font style
     * @param size scaled point size
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
     * Returns the cache key for one style/size pair.
     *
     * @param style font style
     * @param size scaled point size
     * @return cache key
     */
    private static Long fontCacheKey(int style, int size) {
        return Long.valueOf((((long) style) << Integer.SIZE) ^ (size & 0xffff_ffffL));
    }

    /**
     * Clears cached fonts after a density-scale change.
     */
    private static void clearFontCaches() {
        clearFontCache(UI_FONT_CACHE);
        clearFontCache(MONO_FONT_CACHE);
        clearFontCache(CONSOLE_FONT_CACHE);
    }

    /**
     * Clears one synchronized font cache.
     *
     * @param cache cache to clear
     */
    private static void clearFontCache(Map<Long, Font> cache) {
        synchronized (cache) {
            cache.clear();
        }
    }

    /**
     * Applies the active {@link Density} scale to a base font size and rounds to
     * a whole point size (clamped to a readable minimum).
     *
     * @param size unscaled base size
     * @return density-scaled, rounded point size
     */
    private static int scaledSize(float size) {
        return Math.max(7, Math.round(size * density.fontScale()));
    }

    /**
     * Scales a fixed pixel dimension by the active {@link Density} so chrome that
     * sits beside scaled text (decorative marks, min-heights, struts) keeps its
     * proportions at non-default densities.
     *
     * @param px base pixel size
     * @return density-scaled pixel size
     */
    public static int scaledPx(int px) {
        return Math.round(px * density.fontScale());
    }

    /**
     * Re-seeds the UIManager font defaults at the active density. Lighter than a
     * full {@link #install()} for a density change: only later-created Swing
     * components need the refreshed defaults, since live components are rescaled
     * directly by {@link #rescaleFonts(Component, double)}.
     */
    public static void refreshFontDefaults() {
        installFontDefaults();
    }

    /**
     * Rescales the fonts of an existing component tree by a fixed ratio. Used
     * when switching {@link Density} at runtime so already-realized components
     * follow the new scale; the ratio is the value returned by
     * {@link #setDensity(Density)}. Custom-painted text that reads
     * {@link #font(float, int)} at paint time picks up the new scale on its own,
     * so this only needs to touch the per-component font Swing stores.
     *
     * @param component root component (may be null)
     * @param ratio multiplier from the previous scale to the new one
     */
    public static void rescaleFonts(Component component, double ratio) {
        if (component == null || ratio <= 0 || ratio == 1.0) {
            return;
        }
        Font current = component.getFont();
        if (current != null) {
            // Round to a whole point size so a rescaled component stays close to
            // what the font() factory would build fresh at the new density
            // (within ~1pt across repeated toggles) instead of accumulating
            // unbounded fractional drift.
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
     * Creates an empty padding border.
     *
     * @param top top padding
     * @param left left padding
     * @param bottom bottom padding
     * @param right right padding
     * @return border
     */
    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Styles a standard panel-like component.
     *
     * @param component component to style
     */
    public static void stylePanel(JComponent component) {
        component.setOpaque(false);
        component.setBackground(PANEL);
        component.setForeground(TEXT);
        component.setBorder(pad(SPACE_MD));
    }

    /**
     * Styles a text field.
     *
     * @param field text field
     */
    public static void field(JTextField field) {
        field.setUI(new PlaceholderTextFieldUI());
        field.setOpaque(true);
        field.setBackground(INPUT);
        field.setForeground(TEXT);
        field.setDisabledTextColor(MUTED);
        field.setCaretColor(ACCENT);
        field.setSelectionColor(TEXT_SELECTION);
        field.setSelectedTextColor(TEXT);
        field.setBorder(inputBorder(false));
        field.setFont(font(FONT_CONTROL, Font.PLAIN));
        installFocusBorder(field);
        installEnabledBackground(field, INPUT);
    }

    /**
     * Styles a text area.
     *
     * @param area text area
     */
    public static void area(JTextArea area) {
        area.setUI(new PlaceholderTextAreaUI());
        area.setOpaque(true);
        area.setBackground(TEXT_AREA);
        area.setForeground(TEXT);
        area.setDisabledTextColor(MUTED);
        area.setCaretColor(ACCENT);
        area.setSelectionColor(TEXT_SELECTION);
        area.setSelectedTextColor(TEXT);
        area.setBorder(inputBorder(false));
        area.setFont(mono(FONT_MONO));
        installFocusBorder(area);
        installEnabledBackground(area, TEXT_AREA);
    }

    /**
     * Styles a read-only command/code preview block.
     *
     * @param area text area
     */
    public static void codeBlock(JTextArea area) {
        area.putClientProperty(CLIENT_CODE_BLOCK, Boolean.TRUE);
        area.setOpaque(true);
        area.setBackground(CODE_BLOCK_BG);
        area.setForeground(CODE_BLOCK_TEXT);
        area.setCaretColor(CODE_BLOCK_TEXT);
        area.setSelectionColor(TEXT_SELECTION);
        area.setSelectedTextColor(CODE_BLOCK_TEXT);
        area.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(CODE_BLOCK_BORDER),
                pad(SPACE_SM)));
        area.setFont(mono(FONT_MONO));
        installFocusBorder(area);
        installEnabledBackground(area, CODE_BLOCK_BG);
    }

    /**
     * Adds placeholder copy to an empty text component without changing its value.
     *
     * @param component text component
     * @param text placeholder text
     */
    public static void placeholder(JTextComponent component, String text) {
        String value = text == null ? "" : text;
        component.putClientProperty(PLACEHOLDER_PROPERTY, value);
        if (component.getToolTipText() == null || component.getToolTipText().isBlank()) {
            component.setToolTipText(value);
        }
        component.repaint();
    }

    /**
     * Styles a terminal-like text area.
     *
     * @param area text area
     */
    public static void styleTerminal(JTextArea area) {
        area.setOpaque(true);
        area.setBackground(TERMINAL);
        area.setForeground(TERMINAL_TEXT);
        area.setCaretColor(TERMINAL_TEXT);
        area.setSelectionColor(TEXT_SELECTION);
        area.setSelectedTextColor(TERMINAL_TEXT);
        area.setBorder(inputBorder(false));
        area.setFont(mono(FONT_MONO));
        installFocusBorder(area);
    }

    /**
     * Styles a button.
     *
     * @param button button
     * @param primary whether to use accent styling
     */
    public static void button(AbstractButton button, boolean primary) {
        button(button, primary ? ButtonVariant.PRIMARY : ButtonVariant.SECONDARY);
    }

    /**
     * Styles a button with an explicit hierarchy variant.
     *
     * @param button button
     * @param variant action hierarchy variant
     */
    public static void button(AbstractButton button, ButtonVariant variant) {
        ButtonVariant requested = buttonVariant(variant);
        ButtonVariant resolved = requested == ButtonVariant.PRIMARY
                ? requested
                : destructiveActionLabel(button == null ? null : button.getText())
                        ? ButtonVariant.DESTRUCTIVE
                        : requested;
        button.putClientProperty(CLIENT_BUTTON_VARIANT, resolved);
        button.putClientProperty(CLIENT_PRIMARY, Boolean.valueOf(resolved == ButtonVariant.PRIMARY));
        if (resolved == ButtonVariant.DESTRUCTIVE) {
            button.putClientProperty(CLIENT_ICON_KIND, SvgIcon.Kind.DESTRUCTIVE);
        } else if (!Boolean.TRUE.equals(button.getClientProperty(CLIENT_ICON_ONLY))) {
            button.putClientProperty(CLIENT_ICON_KIND, null);
        }
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setIcon(SvgIcon.forButton(button, resolved));
        button.setIconTextGap(resolved == ButtonVariant.DESTRUCTIVE ? 5 : 6);
        button.setMargin(resolved == ButtonVariant.DESTRUCTIVE
                ? new Insets(6, 9, 6, 10)
                : new Insets(6, 11, 6, 11));
        button.setFont(font(FONT_CONTROL, resolved == ButtonVariant.PRIMARY ? Font.BOLD : Font.PLAIN));
        button.setBackground(buttonBackground(resolved));
        button.setForeground(buttonText(resolved));
        button.setDisabledIcon(SvgIcon.disabledForButton(button));
        button.setBorder(pad(5, 8, 5, 8));
    }

    /**
     * Returns whether a visible action label should use destructive styling by
     * default. These controls intentionally match the console Clear/Stop hold
     * buttons: red outline, red text, and the circular destructive icon lane.
     *
     * @param text button label
     * @return true for stop/clear/resign actions
     */
    public static boolean destructiveActionLabel(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("stop")
                || normalized.startsWith("stop ")
                || normalized.equals("resign")
                || normalized.startsWith("resign ")
                || normalized.equals("clear")
                || normalized.startsWith("clear ")
                || normalized.equals("delete")
                || normalized.startsWith("delete ")
                || normalized.equals("remove")
                || normalized.startsWith("remove ")
                || normalized.equals("abort")
                || normalized.startsWith("abort ")
                || normalized.equals("kill")
                || normalized.startsWith("kill ")
                || normalized.equals("wipe")
                || normalized.startsWith("wipe ")
                || normalized.equals("cancel scan")
                || normalized.equals("cancel search")
                || normalized.equals("cancel run")
                || normalized.equals("cancel command")
                || normalized.equals("cancel job")
                || normalized.equals("cancel gauntlet");
    }

    /**
     * Returns the resting button background.
     *
     * @param primary whether to use primary styling
     * @return background color
     */
    public static Color buttonBackground(boolean primary) {
        return buttonBackground(primary ? ButtonVariant.PRIMARY : ButtonVariant.SECONDARY);
    }

    /**
     * Returns the resting button background.
     *
     * @param variant action hierarchy variant
     * @return background color
     */
    public static Color buttonBackground(ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> ACCENT;
            case SECONDARY -> SECONDARY_BUTTON;
            case GHOST -> GHOST_BUTTON;
            case DESTRUCTIVE -> DESTRUCTIVE_BUTTON;
        };
    }

    /**
     * Returns the hover button background.
     *
     * @param primary whether to use primary styling
     * @return hover color
     */
    public static Color buttonHover(boolean primary) {
        return buttonHover(primary ? ButtonVariant.PRIMARY : ButtonVariant.SECONDARY);
    }

    /**
     * Returns the hover button background.
     *
     * @param variant action hierarchy variant
     * @return hover color
     */
    public static Color buttonHover(ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> ACCENT_HOVER;
            case SECONDARY -> SECONDARY_BUTTON_HOVER;
            case GHOST -> GHOST_BUTTON_HOVER;
            case DESTRUCTIVE -> DESTRUCTIVE_BUTTON_HOVER;
        };
    }

    /**
     * Returns the pressed button background.
     *
     * @param primary whether to use primary styling
     * @return pressed color
     */
    public static Color buttonPressed(boolean primary) {
        return buttonPressed(primary ? ButtonVariant.PRIMARY : ButtonVariant.SECONDARY);
    }

    /**
     * Returns the pressed button background.
     *
     * @param variant action hierarchy variant
     * @return pressed color
     */
    public static Color buttonPressed(ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> ACCENT_PRESSED;
            case SECONDARY -> SECONDARY_BUTTON_PRESSED;
            case GHOST -> GHOST_BUTTON_PRESSED;
            case DESTRUCTIVE -> DESTRUCTIVE_BUTTON_PRESSED;
        };
    }

    /**
     * Returns a button border color.
     *
     * @param primary whether to use primary styling
     * @return border color
     */
    public static Color buttonBorder(boolean primary) {
        return buttonBorder(primary ? ButtonVariant.PRIMARY : ButtonVariant.SECONDARY);
    }

    /**
     * Returns a button border color.
     *
     * @param variant action hierarchy variant
     * @return border color
     */
    public static Color buttonBorder(ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> ACCENT_PRESSED;
            case SECONDARY -> INPUT_BORDER;
            case GHOST -> withAlpha(INPUT_BORDER, isDark() ? 90 : 72);
            case DESTRUCTIVE -> DESTRUCTIVE_BUTTON_PRESSED;
        };
    }

    /**
     * Returns button text color.
     *
     * @param variant action hierarchy variant
     * @return text color
     */
    public static Color buttonText(ButtonVariant variant) {
        return switch (buttonVariant(variant)) {
            case PRIMARY -> PRIMARY_BUTTON_TEXT;
            case SECONDARY -> SECONDARY_BUTTON_TEXT;
            case GHOST -> GHOST_BUTTON_TEXT;
            case DESTRUCTIVE -> DESTRUCTIVE_BUTTON_TEXT;
        };
    }

    /**
     * Reads a button's explicit variant, falling back to the legacy primary
     * property for components styled before the variant API existed.
     *
     * @param button button
     * @return resolved variant
     */
    public static ButtonVariant buttonVariant(AbstractButton button) {
        if (button != null && button.getClientProperty(CLIENT_BUTTON_VARIANT) instanceof ButtonVariant variant) {
            return variant;
        }
        if (button != null && Boolean.TRUE.equals(button.getClientProperty(CLIENT_PRIMARY))) {
            return ButtonVariant.PRIMARY;
        }
        return ButtonVariant.SECONDARY;
    }

    /**
     * Normalizes a possibly null button variant.
     *
     * @param variant requested variant
     * @return non-null variant
     */
    private static ButtonVariant buttonVariant(ButtonVariant variant) {
        return variant == null ? ButtonVariant.SECONDARY : variant;
    }

    /**
     * Styles a table as a compact solid data surface.
     *
     * @param table table
     * @param rowHeight row height
     */
    public static void table(JTable table, int rowHeight) {
        DataTableStyler.style(table, rowHeight);
    }

    /**
     * Returns whether a styled table row is currently hovered.
     *
     * @param table source table
     * @param row view row
     * @return true when the row is the active hover row
     */
    public static boolean isHoveredTableRow(JTable table, int row) {
        return table != null && row >= 0 && DataTableStyler.hoverRow(table) == row;
    }

    /**
     * Styles a list as a solid data surface.
     *
     * @param list list
     */
    public static void list(JList<?> list) {
        list.setOpaque(true);
        list.setBackground(ELEVATED_SOLID);
        list.setForeground(TEXT);
        list.setSelectionBackground(SELECTION_SOLID);
        list.setSelectionForeground(TEXT);
        list.setFont(mono(12));
        list.setFixedCellHeight(23);
    }

    /**
     * Creates a section label.
     *
     * @param text label text
     * @return label
     */
    public static JLabel section(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        foreground(label, ForegroundRole.TEXT);
        label.setFont(font(FONT_SECTION_TITLE, Font.BOLD));
        label.setBorder(pad(0, 0, 4, 0));
        return label;
    }

    /**
     * Creates a top-level panel/card title in sentence case: larger and quieter
     * than the uppercase {@link #section} eyebrow. Use for the primary heading
     * of a card or page; keep {@code section} for grouped sub-blocks so the two
     * read as distinct tiers instead of a wall of identical micro-labels.
     *
     * @param text title text
     * @return styled title label
     */
    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        foreground(label, ForegroundRole.TEXT);
        label.setFont(font(FONT_TITLE, Font.BOLD));
        return label;
    }

    /**
     * Builds a card header row: a {@link #section} eyebrow on the left and an
     * optional trailing affordance (count badge, status dot, backend name) on
     * the right. Centralises the header rhythm so every card reads the same.
     *
     * @param title header text
     * @param trailing optional right-aligned component, or {@code null}
     * @return header row component
     */
    public static JComponent cardHeader(String title, JComponent trailing) {
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(SPACE_SM, 0));
        row.setOpaque(false);
        row.add(section(title), java.awt.BorderLayout.WEST);
        if (trailing != null) {
            trailing.setOpaque(false);
            row.add(trailing, java.awt.BorderLayout.EAST);
        }
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,
                row.getPreferredSize().height));
        return row;
    }

    /**
     * Styles a toggle button as a flat command-selector tab: a quiet pill that
     * turns accent-coloured when selected.
     *
     * @param tab toggle button to style
     */
    public static void commandTab(AbstractButton tab) {
        tab.putClientProperty(CLIENT_COMMAND_TAB, Boolean.TRUE);
        tab.setFocusPainted(false);
        tab.setContentAreaFilled(false);
        tab.setBorderPainted(false);
        tab.setOpaque(true);
        tab.setBorder(pad(5, 12, 5, 12));
        reserveCommandTabSize(tab);
        applyCommandTabState(tab);
        tab.addItemListener(event -> applyCommandTabState(tab));
    }

    /**
     * Applies command-tab colors for the active theme without adding listeners.
     *
     * @param tab command tab
     */
    private static void applyCommandTabState(AbstractButton tab) {
        boolean on = tab.isSelected();
        tab.setBackground(on ? SELECTION_SOLID : ELEVATED_SOLID);
        tab.setForeground(on ? STATUS_INFO_TEXT : MUTED);
        tab.setFont(font(12, on ? Font.BOLD : Font.PLAIN));
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? ACCENT : LINE),
                pad(4, 11, 4, 11)));
    }

    /**
     * Reserves the selected-state text width so bold command tabs do not move
     * neighboring buttons when pressed.
     *
     * @param tab command tab
     */
    private static void reserveCommandTabSize(AbstractButton tab) {
        Font previousFont = tab.getFont();
        Border previousBorder = tab.getBorder();
        tab.setFont(font(12, Font.BOLD));
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT),
                pad(4, 11, 4, 11)));
        Dimension preferred = tab.getPreferredSize();
        tab.setPreferredSize(preferred);
        tab.setMinimumSize(preferred);
        tab.setFont(previousFont);
        tab.setBorder(previousBorder);
    }

    /**
     * Builds an input border for the current focus state.
     *
     * @param focused whether the control has focus
     * @return input border
     */
    private static Border inputBorder(boolean focused) {
        return InputChrome.border(focused, false, false);
    }

    /**
     * Installs focus-driven input border painting.
     *
     * @param component text component
     */
    private static void installFocusBorder(JComponent component) {
        InputChrome.install(component, false);
    }

    /**
     * Installs enabled-state background changes on a text component.
     *
     * @param component text component
     * @param enabledBackground enabled background color
     */
    private static void installEnabledBackground(JTextComponent component, Color enabledBackground) {
        InputChrome.installEnabledBackground(component, enabledBackground);
    }

    /**
     * Blends a translucent foreground color onto an opaque background.
     *
     * @param foreground foreground color
     * @param background opaque background color
     * @return solid blended color
     */
    private static Color blendOver(Color foreground, Color background) {
        double alpha = foreground.getAlpha() / 255.0;
        int red = blendChannel(foreground.getRed(), background.getRed(), alpha);
        int green = blendChannel(foreground.getGreen(), background.getGreen(), alpha);
        int blue = blendChannel(foreground.getBlue(), background.getBlue(), alpha);
    return new Color(red, green, blue);
    }

    /**
     * Blends one color channel.
     *
     * @param foreground foreground channel
     * @param background background channel
     * @param alpha foreground alpha from zero to one
     * @return blended channel
     */
    private static int blendChannel(int foreground, int background, double alpha) {
        return (int) Math.round(foreground * alpha + background * (1.0 - alpha));
    }

}
