package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

/**
 * Native Swing styling helpers for the CRTK Workbench.
 */

public final class Theme {

    /**
     * Available workbench color modes.
     */
    public enum Mode {
        /**
         * VS Code-inspired light palette.
         */
        LIGHT("light", "Light"),

        /**
         * Low-saturation neutral dark palette.
         */
        DARK("dark", "Dark"),

        /**
         * Cold blue dark palette.
         */
        DARK_BLUE("dark-blue", "Dark Blue");

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
         * Returns whether this mode uses dark surfaces.
         *
         * @return true for dark modes
         */
        public boolean isDark() {
            return this != LIGHT;
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
     * Surface role for a top-level Workbench pane. Lets a single
     * {@link SurfacePanel} primitive serve both the editor-document tone used by
     * dashboards and the chrome backdrop that boards/engine surfaces lay cards on
     * top of, so every surface root flows through the same shared contract.
     */
    public enum Surface {
        /**
         * Document-tone editor surface. Use for dashboards, reports, and other
         * surfaces whose own background should sit one elevation above the
         * window chrome.
         */
        PANEL,

        /**
         * Editor backdrop. Use for surfaces whose page background acts as the
         * chrome behind raised {@code PANEL_SOLID} cards, rails, and inspectors
         * (boards, engine workspaces, dataset overviews).
         */
        BACKDROP
    }

    /**
     * Client property for empty text-control placeholder copy.
     */
    static final String PLACEHOLDER_PROPERTY = Theme.class.getName() + ".placeholder";

    /**
     * Client property storing a component's semantic foreground role.
     */
    static final String FOREGROUND_ROLE_PROPERTY = Theme.class.getName() + ".foregroundRole";

    /**
     * VS Code macOS light primary foreground.
     */
    static final Color PASTEL_INK = new Color(0x1F1F24);

    /**
     * VS Code macOS light secondary foreground.
     */
    static final Color PASTEL_MUTED = new Color(0x6E6E73);

    /**
     * VS Code macOS light chrome/widget background.
     */
    static final Color PASTEL_CHROME = new Color(0xF4F4F5);

    /**
     * VS Code macOS light hover/subtle-fill base.
     */
    static final Color PASTEL_SUBTLE = new Color(0xECECEF);

    /**
     * VS Code Light (Visual Studio) document/editor surface.
     */
    static final Color PASTEL_DOCUMENT = new Color(255, 255, 255);

    /**
     * VS Code macOS light widget/menu border.
     */
    static final Color PASTEL_BORDER = new Color(0xDADADF);

    /**
     * macOS-style action blue, deepened from Apple's bright system blue so that
     * white primary-button text clears the AA 4.5:1 contrast bar in light mode.
     */
    static final Color PASTEL_BLUE = new Color(0x0A5CC0);

    /**
     * macOS system blue hover.
     */
    static final Color PASTEL_BLUE_HOVER = new Color(0x0077ED);

    /**
     * macOS system blue pressed.
     */
    static final Color PASTEL_BLUE_PRESSED = new Color(0x006EDB);

    /**
     * Accessible blue text for informational surfaces.
     */
    static final Color PASTEL_BLUE_TEXT = PASTEL_BLUE;

    /**
     * Positive vibrant pastel green.
     */
    static final Color PASTEL_GREEN = new Color(134, 221, 163);

    /**
     * Accessible positive text for green surfaces.
     */
    static final Color PASTEL_GREEN_TEXT = new Color(23, 97, 61);

    /**
     * Warning vibrant pastel amber.
     */
    static final Color PASTEL_AMBER = new Color(255, 205, 92);

    /**
     * Accessible warning text for amber surfaces.
     */
    static final Color PASTEL_AMBER_TEXT = new Color(110, 76, 15);

    /**
     * Error vibrant pastel coral.
     */
    static final Color PASTEL_CORAL = new Color(255, 139, 134);

    /**
     * Accessible error text for coral surfaces.
     */
    static final Color PASTEL_CORAL_TEXT = new Color(148, 44, 43);

    /**
     * Policy vibrant pastel purple.
     */
    static final Color PASTEL_PURPLE = new Color(204, 154, 245);

    /**
     * Neutral dark primary foreground.
     */
    static final Color DARK_INK = new Color(0xD4D4D4);

    /**
     * Neutral dark secondary foreground.
     */
    static final Color DARK_MUTED = new Color(0xA0A0A0);

    /**
     * Neutral dark title/sidebar chrome background.
     */
    static final Color DARK_CHROME = new Color(0x181818);

    /**
     * Neutral dark widget border.
     */
    static final Color DARK_SUBTLE = new Color(0x333333);

    /**
     * Neutral dark editor surface.
     */
    static final Color DARK_DOCUMENT = new Color(0x1E1E1E);

    /**
     * Neutral dark popup/dropdown surface.
     */
    static final Color DARK_ELEVATED = new Color(0x252526);

    /**
     * Neutral dark menu separator and input border.
     */
    static final Color DARK_BORDER = new Color(0x3C3C3C);

    /**
     * macOS-style action blue for dark mode: brighter than the light accent so
     * it pops on dark chrome, while still clearing AA 4.5:1 for white text.
     */
    static final Color DARK_ACCENT = new Color(0x2E74D0);

    /**
     * macOS system blue dark hover.
     */
    static final Color DARK_ACCENT_HOVER = new Color(0x2997FF);

    /**
     * macOS system blue dark pressed.
     */
    static final Color DARK_ACCENT_PRESSED = new Color(0x0077ED);

    /**
     * Dark-mode vibrant pastel positive accent.
     */
    static final Color DARK_GREEN = new Color(127, 227, 159);

    /**
     * Dark-mode vibrant pastel warning accent.
     */
    static final Color DARK_AMBER = new Color(255, 208, 102);

    /**
     * Dark-mode vibrant pastel error accent.
     */
    static final Color DARK_CORAL = new Color(255, 139, 134);

    /**
     * Dark-mode vibrant pastel policy accent.
     */
    static final Color DARK_PURPLE = new Color(213, 150, 255);

    /**
     * Dark success text.
     */
    static final Color DARK_SUCCESS_TEXT = DARK_GREEN;

    /**
     * Dark warning text.
     */
    static final Color DARK_WARNING_TEXT = DARK_AMBER;

    /**
     * Dark error text.
     */
    static final Color DARK_ERROR_TEXT = DARK_CORAL;

    /**
     * Dark informational text.
     */
    static final Color DARK_INFO_TEXT = new Color(0x4DAAFC);

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
    public static Color BOARD_ARROW = LEGAL_TARGET;

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
    static final String CLIENT_COMMAND_TAB = "workbench.commandTab";

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
     * Toast z-order layer.
     */
    public static final int Z_TOAST = 400;

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
        return ThemeState.mode();
    }

    /**
     * Returns whether the dark palette is active.
     *
     * @return true in dark mode
     */
    public static boolean isDark() {
        return ThemeState.isDark();
    }

    /**
     * Returns the active UI density.
     *
     * @return active density
     */
    public static Density density() {
        return ThemeState.density();
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
        return ThemeState.setDensity(value);
    }

    /**
     * Switches the active color mode.
     *
     * @param value requested mode
     */
    public static void setMode(Mode value) {
        ThemeState.setMode(value);
    }

    /**
     * Applies and records a semantic foreground role.
     *
     * @param component target component
     * @param role foreground role
     */
    public static void foreground(JComponent component, ForegroundRole role) {
        ThemeForeground.apply(component, role);
    }

    /**
     * Returns the active color for a semantic foreground role.
     *
     * @param role foreground role
     * @return active color
     */
    public static Color foregroundColor(ForegroundRole role) {
        return ThemeForeground.color(role);
    }

    /**
     * Applies the current palette to an existing component tree.
     *
     * @param component root component
     */
    public static void refreshComponentTree(Component component) {
        ThemeRefresh.refreshComponentTree(component);
    }

    /**
     * Refreshes semantic foreground color for one component.
     *
     * @param component Swing component
     */
    public static void refreshForeground(JComponent component) {
        ThemeRefresh.refreshForeground(component);
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
     * Creates a uniform empty padding border.
     *
     * @param all padding applied to every edge
     * @return border
     */
    public static Border pad(int all) {
        return ThemeBorders.pad(all);
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
        return ThemeBorders.pad(vertical, horizontal);
    }

    /**
     * Creates a single-pixel line border in a theme line color. Feature panels
     * should use this instead of calling {@link javax.swing.BorderFactory}
     * directly so the theme-refresh logic can recolor the border on a light/dark
     * switch.
     *
     * @param color line color, normally a {@code Theme} token
     * @return line border
     */
    public static Border lineBorder(Color color) {
        return ThemeBorders.lineBorder(color);
    }

    /**
     * Creates a line border of the given thickness in a theme line color.
     *
     * @param color line color, normally a {@code Theme} token
     * @param thickness line thickness in pixels
     * @return line border
     */
    public static Border lineBorder(Color color, int thickness) {
        return ThemeBorders.lineBorder(color, thickness);
    }

    /**
     * Returns a color with a replaced alpha channel.
     *
     * @param color display color
     * @param alpha opacity value
     * @return color with alpha
     */
    public static Color withAlpha(Color color, int alpha) {
        return ThemeColors.withAlpha(color, alpha);
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
        return ThemeColors.lerp(from, to, amount);
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
        ThemeColors.paintElevatedCard(g, width, height, arc, hover);
    }

    /**
     * Returns a CSS hex string for Swing HTML snippets.
     *
     * @param color display color
     * @return #rrggbb color string
     */
    public static String css(Color color) {
        return ThemeColors.css(color);
    }

    /**
     * Installs the platform look and feel on macOS, otherwise the stable
     * cross-platform look and feel, plus consistent workbench defaults.
     */
    public static void install() {
        ThemeInstaller.install();
    }

    /**
     * Returns the default UI font.
     *
     * @param size font size
     * @param style font style
     * @return font
     */
    public static Font font(float size, int style) {
        return ThemeFonts.font(size, style);
    }

    /**
     * Returns a monospaced font.
     *
     * @param size font size
     * @return font
     */
    public static Font mono(float size) {
        return ThemeFonts.mono(size);
    }

    /**
     * Returns the console/terminal monospaced font — like {@link #mono(float)}
     * but guaranteed to render block, shade, and box-drawing progress glyphs.
     *
     * @param size font size
     * @return console monospaced font
     */
    public static Font consoleMono(float size) {
        return ThemeFonts.consoleMono(size);
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
        return ThemeFonts.scaledPx(px);
    }

    /**
     * Re-seeds the UIManager font defaults at the active density. Lighter than a
     * full {@link #install()} for a density change: only later-created Swing
     * components need the refreshed defaults, since live components are rescaled
     * directly by {@link #rescaleFonts(Component, double)}.
     */
    public static void refreshFontDefaults() {
        ThemeFonts.installDefaults();
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
        ThemeFonts.rescaleFonts(component, ratio);
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
        return ThemeBorders.pad(top, left, bottom, right);
    }

    /**
     * Styles a standard panel-like component.
     *
     * @param component component to style
     */
    public static void stylePanel(JComponent component) {
        ThemeComponents.stylePanel(component);
    }

    /**
     * Styles a text field.
     *
     * @param field text field
     */
    public static void field(JTextField field) {
        ThemeComponents.field(field);
    }

    /**
     * Styles a text area.
     *
     * @param area text area
     */
    public static void area(JTextArea area) {
        ThemeComponents.area(area);
    }

    /**
     * Styles a read-only command/code preview block.
     *
     * @param area text area
     */
    public static void codeBlock(JTextArea area) {
        ThemeComponents.codeBlock(area);
    }

    /**
     * Adds placeholder copy to an empty text component without changing its value.
     *
     * @param component text component
     * @param text placeholder text
     */
    public static void placeholder(JTextComponent component, String text) {
        ThemeComponents.placeholder(component, text);
    }

    /**
     * Styles a terminal-like text area.
     *
     * @param area text area
     */
    public static void styleTerminal(JTextArea area) {
        ThemeComponents.styleTerminal(area);
    }

    /**
     * Styles a button.
     *
     * @param button button component
     * @param primary whether to use accent styling
     */
    public static void button(AbstractButton button, boolean primary) {
        ThemeComponents.button(button, primary);
    }

    /**
     * Styles a button with an explicit hierarchy variant.
     *
     * @param button button component
     * @param variant action hierarchy variant
     */
    public static void button(AbstractButton button, ButtonVariant variant) {
        ThemeComponents.button(button, variant);
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
        return ThemeComponents.destructiveActionLabel(text);
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
        return ThemeComponents.buttonBackground(variant);
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
        return ThemeComponents.buttonHover(variant);
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
        return ThemeComponents.buttonPressed(variant);
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
        return ThemeComponents.buttonBorder(variant);
    }

    /**
     * Returns button text color.
     *
     * @param variant action hierarchy variant
     * @return text color
     */
    public static Color buttonText(ButtonVariant variant) {
        return ThemeComponents.buttonText(variant);
    }

    /**
     * Reads a button's explicit variant, falling back to the legacy primary
     * property for components styled before the variant API existed.
     *
     * @param button button component
     * @return resolved variant
     */
    public static ButtonVariant buttonVariant(AbstractButton button) {
        return ThemeComponents.buttonVariant(button);
    }

    /**
     * Styles a table as a compact solid data surface.
     *
     * @param table table component
     * @param rowHeight row height in pixels
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
     * @param list list component
     */
    public static void list(JList<?> list) {
        ThemeComponents.list(list);
    }

    /**
     * Creates a section label.
     *
     * @param text label text
     * @return label
     */
    public static JLabel section(String text) {
        return ThemeComponents.section(text);
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
        return ThemeComponents.sectionTitle(text);
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
        return ThemeComponents.cardHeader(title, trailing);
    }

    /**
     * Styles a toggle button as a flat command-selector tab: a quiet pill that
     * turns accent-coloured when selected.
     *
     * @param tab toggle button to style
     */
    public static void commandTab(AbstractButton tab) {
        ThemeComponents.commandTab(tab);
    }

    /**
     * Blends a translucent foreground color onto an opaque background.
     *
     * @param foreground foreground color
     * @param background opaque background color
     * @return solid blended color
     */
    static Color blendOver(Color foreground, Color background) {
        return ThemeColors.blendOver(foreground, background);
    }

}
