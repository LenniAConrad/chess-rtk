package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
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
         * Existing pastel light palette.
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
     * Active color mode.
     */
    private static Mode mode = Mode.LIGHT;

    /**
     * Client property for empty text-control placeholder copy.
     */
    private static final String PLACEHOLDER_PROPERTY = Theme.class.getName() + ".placeholder";

    /**
     * Primary ink for pastel surfaces.
     */
    private static final Color PASTEL_INK = new Color(36, 49, 58);

    /**
     * Secondary ink for pastel surfaces.
     */
    private static final Color PASTEL_MUTED = new Color(104, 115, 125);

    /**
     * Pastel workbench chrome.
     */
    private static final Color PASTEL_CHROME = new Color(244, 242, 247);

    /**
     * Pastel outline/subtle-fill base.
     */
    private static final Color PASTEL_SUBTLE = new Color(250, 248, 252);

    /**
     * Pastel document/editor surface.
     */
    private static final Color PASTEL_DOCUMENT = new Color(255, 253, 254);

    /**
     * Single lavender-gray workbench border.
     */
    private static final Color PASTEL_BORDER = new Color(221, 216, 230);

    /**
     * Pastel action blue.
     */
    private static final Color PASTEL_BLUE = new Color(120, 174, 221);

    /**
     * Pastel action blue hover fill.
     */
    private static final Color PASTEL_BLUE_HOVER = new Color(139, 187, 230);

    /**
     * Pastel action blue pressed fill.
     */
    private static final Color PASTEL_BLUE_PRESSED = new Color(102, 162, 212);

    /**
     * Accessible blue text for pastel informational surfaces.
     */
    private static final Color PASTEL_BLUE_TEXT = new Color(49, 95, 130);

    /**
     * Pastel positive green.
     */
    private static final Color PASTEL_GREEN = new Color(158, 214, 165);

    /**
     * Accessible positive text for pastel green surfaces.
     */
    private static final Color PASTEL_GREEN_TEXT = new Color(45, 107, 73);

    /**
     * Pastel warning amber.
     */
    private static final Color PASTEL_AMBER = new Color(232, 199, 121);

    /**
     * Accessible warning text for pastel amber surfaces.
     */
    private static final Color PASTEL_AMBER_TEXT = new Color(106, 84, 30);

    /**
     * Pastel error coral.
     */
    private static final Color PASTEL_CORAL = new Color(240, 170, 164);

    /**
     * Accessible error text for pastel coral surfaces.
     */
    private static final Color PASTEL_CORAL_TEXT = new Color(138, 60, 58);

    /**
     * Pastel policy purple.
     */
    private static final Color PASTEL_PURPLE = new Color(203, 183, 234);

    /**
     * Dark primary ink.
     */
    private static final Color DARK_INK = new Color(238, 238, 232);

    /**
     * Dark secondary ink.
     */
    private static final Color DARK_MUTED = new Color(160, 166, 151);

    /**
     * Dark workbench chrome.
     */
    private static final Color DARK_CHROME = new Color(15, 17, 14);

    /**
     * Dark subtle surface.
     */
    private static final Color DARK_SUBTLE = new Color(21, 24, 20);

    /**
     * Dark editor/panel surface.
     */
    private static final Color DARK_DOCUMENT = new Color(18, 21, 17);

    /**
     * Dark elevated surface.
     */
    private static final Color DARK_ELEVATED = new Color(24, 28, 23);

    /**
     * Dark border color.
     */
    private static final Color DARK_BORDER = new Color(45, 52, 43);

    /**
     * Dark primary accent.
     */
    private static final Color DARK_ACCENT = new Color(109, 211, 179);

    /**
     * Dark accent hover.
     */
    private static final Color DARK_ACCENT_HOVER = new Color(128, 226, 196);

    /**
     * Dark accent pressed.
     */
    private static final Color DARK_ACCENT_PRESSED = new Color(71, 173, 143);

    /**
     * Dark positive accent.
     */
    private static final Color DARK_GREEN = new Color(109, 211, 179);

    /**
     * Dark warning accent.
     */
    private static final Color DARK_AMBER = new Color(230, 196, 112);

    /**
     * Dark error accent.
     */
    private static final Color DARK_CORAL = new Color(240, 139, 128);

    /**
     * Dark policy accent.
     */
    private static final Color DARK_PURPLE = new Color(185, 149, 206);

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
     * Elevated panel color.
     */
    public static Color ELEVATED = PASTEL_DOCUMENT;

    /**
     * Solid elevated fallback for data surfaces and scroll viewports.
     */
    public static Color ELEVATED_SOLID = blendOver(ELEVATED, BG);

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
    public static Color SELECTION = new Color(229, 241, 252);

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
    public static Color SECONDARY_BUTTON = PASTEL_DOCUMENT;

    /**
     * Secondary button hover color.
     */
    public static Color SECONDARY_BUTTON_HOVER = PASTEL_SUBTLE;

    /**
     * Secondary button pressed color.
     */
    public static Color SECONDARY_BUTTON_PRESSED = PASTEL_CHROME;

    /**
     * Secondary button text color.
     */
    public static Color SECONDARY_BUTTON_TEXT = PASTEL_INK;

    /**
     * Disabled button fill color.
     */
    public static Color BUTTON_DISABLED_BG = PASTEL_SUBTLE;

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
    public static Color INPUT_BORDER = PASTEL_BORDER;

    /**
     * Input focus ring color.
     */
    public static Color INPUT_FOCUS = PASTEL_BLUE;

    /**
     * Disabled input background color.
     */
    public static Color INPUT_DISABLED = PASTEL_SUBTLE;

    /**
     * Toggle-off background color.
     */
    public static Color TOGGLE_BG = PASTEL_SUBTLE;

    /**
     * Toggle-off border color.
     */
    public static Color TOGGLE_BORDER = PASTEL_BORDER;

    /**
     * Toggle-off track color.
     */
    public static Color TOGGLE_TRACK = PASTEL_MUTED;

    /**
     * Toggle-on background color.
     */
    public static Color TOGGLE_ON_BG = SELECTION;

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
    public static Color TEXT_SELECTION = new Color(224, 239, 252, 255);

    /**
     * Primary button text color.
     */
    public static Color PRIMARY_BUTTON_TEXT = PASTEL_INK;

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
     * Shared inset move-highlight edge color.
     */
    public static Color BOARD_HIGHLIGHT = new Color(85, 184, 125);

    /**
     * Last-move highlight edge.
     */
    public static Color LAST_MOVE_EDGE = BOARD_HIGHLIGHT;

    /**
     * Selected square edge.
     */
    public static Color SELECTED_EDGE = BOARD_HIGHLIGHT;

    /**
     * Quiet legal-target marker fill.
     */
    public static Color LEGAL_TARGET = new Color(PASTEL_INK.getRed(), PASTEL_INK.getGreen(), PASTEL_INK.getBlue(), 86);

    /**
     * Capture legal-target marker edge.
     */
    public static Color LEGAL_CAPTURE_EDGE = new Color(PASTEL_INK.getRed(), PASTEL_INK.getGreen(),
            PASTEL_INK.getBlue(), 112);

    /**
     * Suggested-move arrow color.
     */
    public static Color BOARD_ARROW = new Color(111, 168, 220);

    /**
     * Check highlight radial core.
     */
    public static Color CHECK_CORE = new Color(PASTEL_CORAL.getRed(), PASTEL_CORAL.getGreen(),
            PASTEL_CORAL.getBlue(), 245);

    /**
     * Check highlight glow.
     */
    public static Color CHECK_GLOW = new Color(200, 104, 99, 209);

    /**
     * Check highlight square fill.
     */
    public static Color CHECK_FILL = new Color(PASTEL_CORAL.getRed(), PASTEL_CORAL.getGreen(),
            PASTEL_CORAL.getBlue(), 140);

    /**
     * Check highlight square edge.
     */
    public static Color CHECK_EDGE = new Color(200, 104, 99, 70);

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
    public static Color EVAL_FRAME = new Color(0, 0, 0, 168);

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
    public static Color TAB_HOVER = new Color(PASTEL_SUBTLE.getRed(), PASTEL_SUBTLE.getGreen(),
            PASTEL_SUBTLE.getBlue(), 255);

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
    public static Color TOOLTIP_BG = PASTEL_DOCUMENT;

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
    public static Color STATUS_SUCCESS_BG = new Color(239, 248, 240);

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
    public static Color STATUS_WARNING_BG = new Color(255, 245, 223);

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
    public static Color STATUS_ERROR_BG = new Color(255, 240, 239);

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
    public static Color STATUS_INFO_BG = new Color(232, 242, 251);

    /**
     * Informational feedback border.
     */
    public static Color STATUS_INFO_BORDER = new Color(182, 216, 242);

    /**
     * Informational feedback text.
     */
    public static Color STATUS_INFO_TEXT = PASTEL_BLUE_TEXT;

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
     * Client-property key carrying an explicit icon kind for a button.
     */
    public static final String CLIENT_ICON_KIND = "workbench.icon";

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
     * segmented selectors, chips). Kept small for the crisp, near-square VS
     * Code look.
     */
    public static final int RADIUS = 3;

    /**
     * Standard height for compact toolbar controls (combos, segmented
     * switchers, toggles) so a control row lines up on a single baseline.
     */
    public static final int CONTROL_HEIGHT = 30;

    // ------------------------------------------------------------------
    // Neural-network visualization palette
    //
    // The accent colors the NNUE / CNN / BT4 views paint activations and
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
        if (component instanceof BackdropPanel) {
            component.setBackground(BG);
        } else if (component instanceof SurfacePanel) {
            component.setBackground(PANEL_SOLID);
            component.setForeground(TEXT);
        } else if (component instanceof ConsoleLike console) {
            console.applyConsoleTheme();
        } else if (component instanceof JTextArea area) {
            area(area);
        } else if (component instanceof JTextField field) {
            field(field);
        } else if (component instanceof JTextPane pane) {
            pane.setBackground(TEXT_AREA);
            pane.setForeground(TEXT);
            pane.setCaretColor(TEXT);
            pane.setSelectionColor(TEXT_SELECTION);
            pane.setSelectedTextColor(TEXT);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setBackground(ELEVATED_SOLID);
            combo.setForeground(TEXT);
            combo.setBorder(BorderFactory.createLineBorder(INPUT_BORDER));
        } else if (component instanceof JSpinner spinner) {
            spinner.setBackground(PANEL_SOLID);
            spinner.setForeground(TEXT);
            spinner.setBorder(BorderFactory.createLineBorder(INPUT_BORDER));
        } else if (component instanceof JTable table) {
            table(table, Math.max(24, table.getRowHeight()));
        } else if (component instanceof JList<?> list) {
            list(list);
        } else if (component instanceof JTabbedPane tabs) {
            tabs.setBackground(BG);
            tabs.setForeground(TEXT);
        } else if (component instanceof JScrollPane pane) {
            pane.setBackground(BG);
            JViewport viewport = pane.getViewport();
            if (viewport != null) {
                viewport.setBackground(PANEL_SOLID);
            }
        } else if (component instanceof ToggleBox toggle) {
            toggle.setForeground(TEXT);
            toggle.setFont(font(13, Font.PLAIN));
        } else if (component instanceof AbstractButton button) {
            Object primary = button.getClientProperty(CLIENT_PRIMARY);
            if (primary instanceof Boolean value) {
                button(button, value.booleanValue());
            } else {
                button.setForeground(TEXT);
                button.setFont(font(13, Font.PLAIN));
            }
        } else if (component instanceof JLabel label) {
            label.setForeground(TEXT);
        } else if (component instanceof JComponent jComponent) {
            jComponent.setForeground(TEXT);
            if (jComponent.isOpaque()) {
                jComponent.setBackground(PANEL_SOLID);
            } else {
                jComponent.setBackground(BG);
            }
        }
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
        ELEVATED = PASTEL_DOCUMENT;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        LINE = PASTEL_BORDER;
        TEXT = PASTEL_INK;
        MUTED = PASTEL_MUTED;
        ACCENT = PASTEL_BLUE;
        SELECTION = new Color(229, 241, 252);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = PASTEL_BLUE_HOVER;
        ACCENT_PRESSED = PASTEL_BLUE_PRESSED;
        SECONDARY_BUTTON = PASTEL_DOCUMENT;
        SECONDARY_BUTTON_HOVER = PASTEL_SUBTLE;
        SECONDARY_BUTTON_PRESSED = PASTEL_CHROME;
        SECONDARY_BUTTON_TEXT = PASTEL_INK;
        BUTTON_DISABLED_BG = PASTEL_SUBTLE;
        BUTTON_DISABLED_BORDER = PASTEL_BORDER;
        BUTTON_DISABLED_TEXT = PASTEL_MUTED;
        INPUT_BORDER = PASTEL_BORDER;
        INPUT_FOCUS = PASTEL_BLUE;
        INPUT_DISABLED = PASTEL_SUBTLE;
        TOGGLE_BG = PASTEL_SUBTLE;
        TOGGLE_BORDER = PASTEL_BORDER;
        TOGGLE_TRACK = PASTEL_MUTED;
        TOGGLE_ON_BG = SELECTION;
        TOGGLE_ON_TRACK = PASTEL_BLUE;
        TOGGLE_THUMB = PASTEL_DOCUMENT;
        INPUT = PASTEL_DOCUMENT;
        TEXT_AREA = PASTEL_DOCUMENT;
        TERMINAL = PASTEL_DOCUMENT;
        TERMINAL_TEXT = PASTEL_INK;
        TEXT_SELECTION = new Color(224, 239, 252, 255);
        PRIMARY_BUTTON_TEXT = PASTEL_INK;
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(PASTEL_SUBTLE.getRed(), PASTEL_SUBTLE.getGreen(), PASTEL_SUBTLE.getBlue(), 255);
        TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);
        SCROLLBAR_TRACK = new Color(PASTEL_CHROME.getRed(), PASTEL_CHROME.getGreen(), PASTEL_CHROME.getBlue(), 0);
        SCROLLBAR_THUMB = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 80);
        SCROLLBAR_THUMB_HOVER = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 130);
        TOOLTIP_BG = PASTEL_DOCUMENT;
        TOOLTIP_TEXT = PASTEL_INK;
        TOOLTIP_BORDER = PASTEL_BORDER;
        STATUS_SUCCESS_BG = new Color(239, 248, 240);
        STATUS_SUCCESS_BORDER = PASTEL_GREEN;
        STATUS_SUCCESS_TEXT = PASTEL_GREEN_TEXT;
        STATUS_WARNING_BG = new Color(255, 245, 223);
        STATUS_WARNING_BORDER = PASTEL_AMBER;
        STATUS_WARNING_TEXT = PASTEL_AMBER_TEXT;
        STATUS_ERROR_BG = new Color(255, 240, 239);
        STATUS_ERROR_BORDER = PASTEL_CORAL;
        STATUS_ERROR_TEXT = PASTEL_CORAL_TEXT;
        STATUS_INFO_BG = new Color(232, 242, 251);
        STATUS_INFO_BORDER = new Color(182, 216, 242);
        STATUS_INFO_TEXT = PASTEL_BLUE_TEXT;
        LOGO_BACKGROUND = new Color(PASTEL_PURPLE.getRed(), PASTEL_PURPLE.getGreen(), PASTEL_PURPLE.getBlue(), 230);
        LOGO_MARK = PASTEL_CORAL;
        TOGGLE_FOCUS = withAlpha(INPUT_FOCUS, 95);
        NN_POSITIVE = PASTEL_GREEN;
        NN_NEGATIVE = PASTEL_CORAL;
        NN_TRUNK = PASTEL_BLUE;
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
        ELEVATED = DARK_ELEVATED;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        LINE = DARK_BORDER;
        TEXT = DARK_INK;
        MUTED = DARK_MUTED;
        ACCENT = DARK_ACCENT;
        SELECTION = new Color(35, 76, 58);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = DARK_ACCENT_HOVER;
        ACCENT_PRESSED = DARK_ACCENT_PRESSED;
        SECONDARY_BUTTON = DARK_ELEVATED;
        SECONDARY_BUTTON_HOVER = new Color(30, 35, 28);
        SECONDARY_BUTTON_PRESSED = DARK_BORDER;
        SECONDARY_BUTTON_TEXT = DARK_INK;
        BUTTON_DISABLED_BG = DARK_SUBTLE;
        BUTTON_DISABLED_BORDER = DARK_BORDER;
        BUTTON_DISABLED_TEXT = new Color(116, 124, 111);
        INPUT_BORDER = DARK_BORDER;
        INPUT_FOCUS = DARK_ACCENT;
        INPUT_DISABLED = DARK_SUBTLE;
        TOGGLE_BG = DARK_SUBTLE;
        TOGGLE_BORDER = DARK_BORDER;
        TOGGLE_TRACK = DARK_MUTED;
        TOGGLE_ON_BG = SELECTION;
        TOGGLE_ON_TRACK = DARK_ACCENT_HOVER;
        TOGGLE_THUMB = DARK_INK;
        INPUT = DARK_DOCUMENT;
        TEXT_AREA = DARK_DOCUMENT;
        TERMINAL = new Color(12, 14, 11);
        TERMINAL_TEXT = DARK_INK;
        TEXT_SELECTION = new Color(35, 76, 58, 255);
        PRIMARY_BUTTON_TEXT = new Color(9, 12, 9);
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(DARK_ELEVATED.getRed(), DARK_ELEVATED.getGreen(), DARK_ELEVATED.getBlue(), 255);
        TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);
        SCROLLBAR_TRACK = new Color(DARK_CHROME.getRed(), DARK_CHROME.getGreen(), DARK_CHROME.getBlue(), 0);
        SCROLLBAR_THUMB = new Color(DARK_MUTED.getRed(), DARK_MUTED.getGreen(), DARK_MUTED.getBlue(), 82);
        SCROLLBAR_THUMB_HOVER = new Color(DARK_MUTED.getRed(), DARK_MUTED.getGreen(), DARK_MUTED.getBlue(), 150);
        TOOLTIP_BG = DARK_ELEVATED;
        TOOLTIP_TEXT = DARK_INK;
        TOOLTIP_BORDER = DARK_BORDER;
        STATUS_SUCCESS_BG = new Color(20, 49, 37);
        STATUS_SUCCESS_BORDER = DARK_GREEN;
        STATUS_SUCCESS_TEXT = new Color(183, 235, 207);
        STATUS_WARNING_BG = new Color(62, 50, 24);
        STATUS_WARNING_BORDER = DARK_AMBER;
        STATUS_WARNING_TEXT = new Color(246, 219, 151);
        STATUS_ERROR_BG = new Color(70, 31, 30);
        STATUS_ERROR_BORDER = DARK_CORAL;
        STATUS_ERROR_TEXT = new Color(255, 190, 180);
        STATUS_INFO_BG = new Color(24, 47, 43);
        STATUS_INFO_BORDER = DARK_ACCENT;
        STATUS_INFO_TEXT = new Color(183, 235, 220);
        LOGO_BACKGROUND = new Color(DARK_PURPLE.getRed(), DARK_PURPLE.getGreen(), DARK_PURPLE.getBlue(), 230);
        LOGO_MARK = DARK_CORAL;
        TOGGLE_FOCUS = withAlpha(INPUT_FOCUS, 120);
        NN_POSITIVE = DARK_GREEN;
        NN_NEGATIVE = DARK_CORAL;
        NN_TRUNK = DARK_ACCENT;
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
        BOARD_HIGHLIGHT = new Color(85, 184, 125);
        LAST_MOVE_EDGE = BOARD_HIGHLIGHT;
        SELECTED_EDGE = BOARD_HIGHLIGHT;
        LEGAL_TARGET = withAlpha(TEXT, 86);
        LEGAL_CAPTURE_EDGE = withAlpha(TEXT, 112);
        BOARD_ARROW = new Color(111, 168, 220);
        CHECK_CORE = new Color((isDark() ? DARK_CORAL : PASTEL_CORAL).getRed(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getGreen(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getBlue(), 245);
        CHECK_GLOW = new Color(200, 104, 99, 209);
        CHECK_FILL = new Color((isDark() ? DARK_CORAL : PASTEL_CORAL).getRed(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getGreen(),
                (isDark() ? DARK_CORAL : PASTEL_CORAL).getBlue(), 140);
        CHECK_EDGE = new Color(200, 104, 99, 70);
        EVAL_BLACK = Color.BLACK;
        EVAL_WHITE = Color.WHITE;
        EVAL_FRAME = new Color(0, 0, 0, 168);
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
     * Returns a CSS hex string for Swing HTML snippets.
     *
     * @param color color
     * @return #rrggbb color string
     */
    public static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Installs the native look and feel plus consistent defaults.
     */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | javax.swing.UnsupportedLookAndFeelException ex) {
            LOGGER.log(Level.FINE, "Cross-platform LookAndFeel unavailable; keeping default LookAndFeel.", ex);
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TabbedPane.background", BG);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", PANEL);
        UIManager.put("TabbedPane.contentAreaColor", BG);
        UIManager.put("TabbedPane.focus", LINE);
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
        UIManager.put("OptionPane.background", PANEL_SOLID);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", font(13, Font.PLAIN));
        UIManager.put("OptionPane.buttonFont", font(13, Font.PLAIN));
        UIManager.put("FileChooser.background", BG);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listViewBackground", ELEVATED_SOLID);
        UIManager.put("FileChooser.listViewBorder", BorderFactory.createLineBorder(LINE));
        UIManager.put("ToolTip.background", TOOLTIP_BG);
        UIManager.put("ToolTip.foreground", TOOLTIP_TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(TOOLTIP_BORDER));
        UIManager.put("ToolTip.font", font(12, Font.PLAIN));
    }

    /**
     * Resolved family for the workbench UI font. Falls back to the platform's
     * default sans-serif when {@code IBM Plex Sans} is not installed (so
     * stock systems do not silently drop to Serif/Dialog).
     */
    private static final String UI_FONT_FAMILY = resolveFontFamily();

    /**
     * Resolves the font family at startup, preferring IBM Plex Sans.
     *
     * @return font family
     */
    private static String resolveFontFamily() {
        try {
            String[] available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            for (String name : available) {
                if ("IBM Plex Sans".equals(name)) {
                    return "IBM Plex Sans";
                }
            }
        } catch (java.awt.HeadlessException ex) {
            // fall through to default
        }
        return Font.SANS_SERIF;
    }

    /**
     * Returns the default UI font.
     *
     * @param size font size
     * @param style font style
     * @return font
     */
    public static Font font(float size, int style) {
    return new Font(UI_FONT_FAMILY, style, Math.round(size));
    }

    /**
     * Returns a monospaced font.
     *
     * @param size font size
     * @return font
     */
    public static Font mono(float size) {
    return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
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
        component.setBorder(pad(10, 10, 10, 10));
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
        field.setFont(mono(13));
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
        area.setFont(mono(13));
        installFocusBorder(area);
        installEnabledBackground(area, TEXT_AREA);
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
        area.setFont(mono(13));
        installFocusBorder(area);
    }

    /**
     * Styles a button.
     *
     * @param button button
     * @param primary whether to use accent styling
     */
    public static void button(AbstractButton button, boolean primary) {
        button.putClientProperty(CLIENT_PRIMARY, Boolean.valueOf(primary));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setIcon(SvgIcon.forButton(button, primary));
        button.setIconTextGap(6);
        button.setMargin(new Insets(6, 11, 6, 11));
        button.setFont(font(13, primary ? Font.BOLD : Font.PLAIN));
        button.setBackground(buttonBackground(primary));
        button.setForeground(primary ? PRIMARY_BUTTON_TEXT : SECONDARY_BUTTON_TEXT);
        button.setDisabledIcon(SvgIcon.disabledForButton(button));
        button.setBorder(pad(5, 8, 5, 8));
    }

    /**
     * Returns the resting button background.
     *
     * @param primary whether to use primary styling
     * @return background color
     */
    public static Color buttonBackground(boolean primary) {
        return primary ? ACCENT : SECONDARY_BUTTON;
    }

    /**
     * Returns the hover button background.
     *
     * @param primary whether to use primary styling
     * @return hover color
     */
    public static Color buttonHover(boolean primary) {
        return primary ? ACCENT_HOVER : SECONDARY_BUTTON_HOVER;
    }

    /**
     * Returns the pressed button background.
     *
     * @param primary whether to use primary styling
     * @return pressed color
     */
    public static Color buttonPressed(boolean primary) {
        return primary ? ACCENT_PRESSED : SECONDARY_BUTTON_PRESSED;
    }

    /**
     * Returns a button border color.
     *
     * @param primary whether to use primary styling
     * @return border color
     */
    public static Color buttonBorder(boolean primary) {
        return primary ? ACCENT_PRESSED : INPUT_BORDER;
    }

    /**
     * Styles a table as a compact solid data surface.
     *
     * @param table table
     * @param rowHeight row height
     */
    public static void table(JTable table, int rowHeight) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(rowHeight);
        table.setOpaque(true);
        table.setBackground(ELEVATED_SOLID);
        table.setForeground(TEXT);
        table.setGridColor(LINE);
        table.setSelectionBackground(SELECTION_SOLID);
        table.setSelectionForeground(TEXT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBorder(pad(0, 0, 0, 0));
        table.setFont(font(12, Font.PLAIN));
        table.getTableHeader().setOpaque(true);
        table.getTableHeader().setBackground(ELEVATED_SOLID);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setFont(font(11, Font.BOLD));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        TableCellRendererImpl textRenderer = new TableCellRendererImpl();
        table.setDefaultRenderer(Object.class, textRenderer);
        table.setDefaultRenderer(String.class, textRenderer);
        table.setDefaultRenderer(Number.class, textRenderer);
        table.setDefaultRenderer(Boolean.class, new BooleanCellRendererImpl());
        table.setDefaultEditor(Boolean.class, new DefaultCellEditor(tableBooleanEditor()));
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
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(font(12, Font.BOLD));
        return label;
    }

    /**
     * Styles a toggle button as a flat command-selector tab: a quiet pill that
     * turns accent-coloured when selected.
     *
     * @param tab toggle button to style
     */
    public static void commandTab(AbstractButton tab) {
        tab.setFocusPainted(false);
        tab.setContentAreaFilled(false);
        tab.setBorderPainted(false);
        tab.setOpaque(true);
        tab.setBorder(pad(5, 12, 5, 12));
        Runnable apply = () -> {
            boolean on = tab.isSelected();
            tab.setBackground(on ? SELECTION_SOLID : ELEVATED_SOLID);
            tab.setForeground(on ? STATUS_INFO_TEXT : MUTED);
            tab.setFont(font(12, on ? Font.BOLD : Font.PLAIN));
            tab.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(on ? ACCENT : LINE),
                    pad(4, 11, 4, 11)));
        };
        apply.run();
        tab.addItemListener(event -> apply.run());
    }

    /**
     * Builds an input border for the current focus state.
     *
     * @param focused whether the control has focus
     * @return input border
     */
    private static Border inputBorder(boolean focused) {
        Border line = BorderFactory.createLineBorder(focused ? INPUT_FOCUS : INPUT_BORDER);
        Border inner = pad(7, 9, 7, 9);
        return BorderFactory.createCompoundBorder(line, inner);
    }

    /**
     * Installs focus-driven input border painting.
     *
     * @param component text component
     */
    private static void installFocusBorder(JComponent component) {
        component.addFocusListener(new FocusAdapter() {
            /**
             * Applies the focused border.
             *
             * @param event focus event
             */
            @Override
            public void focusGained(FocusEvent event) {
                component.setBorder(inputBorder(true));
            }

            /**
             * Restores the resting border.
             *
             * @param event focus event
             */
            @Override
            public void focusLost(FocusEvent event) {
                component.setBorder(inputBorder(false));
            }
        });
    }

    /**
     * Installs enabled-state background changes on a text component.
     *
     * @param component text component
     * @param enabledBackground enabled background color
     */
    private static void installEnabledBackground(JTextComponent component, Color enabledBackground) {
        component.addPropertyChangeListener("enabled", event -> component.setBackground(
                component.isEnabled() ? enabledBackground : INPUT_DISABLED));
    }

    /**
     * Creates the editor used by boolean table cells.
     *
     * @return styled checkbox editor
     */
    private static JCheckBox tableBooleanEditor() {
        JCheckBox editor = new JCheckBox();
        editor.setHorizontalAlignment(SwingConstants.CENTER);
        editor.setOpaque(true);
        editor.setBackground(ELEVATED_SOLID);
        editor.setForeground(TEXT);
        editor.setFocusPainted(false);
        editor.setBorder(pad(0, 0, 0, 0));
        return editor;
    }

    /**
     * Compact text table renderer that avoids default gray and blue cells.
     */
    private static final class TableCellRendererImpl extends DefaultTableCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns a styled table cell component.
         *
         * @param table source table
         * @param value cell value
         * @param selected whether the row is selected
         * @param focused whether the cell has focus
         * @param row row index
         * @param column column index
         * @return renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setOpaque(true);
            setFont(table.getFont());
            setForeground(table.isEnabled() ? TEXT : BUTTON_DISABLED_TEXT);
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(pad(0, 8, 0, 8));
            return this;
        }
    }

    /**
     * Custom boolean table renderer matching the workbench palette.
     */
    private static final class BooleanCellRendererImpl extends JComponent implements TableCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Checkbox glyph size.
         */
        private static final int BOX_SIZE = 15;

        /**
         * Current checked state.
         */
        private boolean checked;

        /**
         * Current selected-row state.
         */
        private boolean rowSelected;

        /**
         * Current table enabled state.
         */
        private boolean tableEnabled;

        /**
         * Returns a styled boolean cell component.
         *
         * @param table source table
         * @param value cell value
         * @param selected whether the row is selected
         * @param focused whether the cell has focus
         * @param row row index
         * @param column column index
         * @return renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                int row, int column) {
            checked = Boolean.TRUE.equals(value);
            rowSelected = selected;
            tableEnabled = table.isEnabled();
            setOpaque(true);
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setToolTipText(checked ? "enabled" : "disabled");
            return this;
        }

        /**
         * Paints the boolean chip.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                int size = Math.min(BOX_SIZE, Math.max(8, Math.min(getWidth() - 6, getHeight() - 6)));
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                Color fill = checked ? ACCENT : rowSelected ? ELEVATED_SOLID : INPUT_DISABLED;
                Color border = checked ? ACCENT_PRESSED : INPUT_BORDER;
                if (!tableEnabled) {
                    fill = BUTTON_DISABLED_BG;
                    border = BUTTON_DISABLED_BORDER;
                }
                g.setColor(fill);
                g.fillRoundRect(x, y, size, size, 5, 5);
                g.setColor(border);
                g.drawRoundRect(x, y, size, size, 5, 5);
                if (checked) {
                    g.setColor(PRIMARY_BUTTON_TEXT);
                    g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int left = x + Math.max(4, size / 4);
                    int midX = x + size / 2 - 1;
                    int right = x + size - Math.max(4, size / 4);
                    int midY = y + size - Math.max(4, size / 4);
                    int topY = y + Math.max(4, size / 4);
                    g.drawLine(left, midY - 1, midX, y + size - 4);
                    g.drawLine(midX, y + size - 4, right, topY);
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Text-field UI that paints placeholder copy when empty.
     */
    private static final class PlaceholderTextFieldUI extends BasicTextFieldUI {

        @Override
        protected void paintSafely(Graphics graphics) {
            super.paintSafely(graphics);
            paintPlaceholder(graphics, getComponent(), true);
        }
    }

    /**
     * Text-area UI that paints placeholder copy when empty.
     */
    private static final class PlaceholderTextAreaUI extends BasicTextAreaUI {

        @Override
        protected void paintSafely(Graphics graphics) {
            super.paintSafely(graphics);
            paintPlaceholder(graphics, getComponent(), false);
        }
    }

    /**
     * Paints placeholder copy for an empty text component.
     *
     * @param graphics graphics
     * @param component text component
     * @param verticalCenter true to center vertically
     */
    private static void paintPlaceholder(Graphics graphics, JTextComponent component, boolean verticalCenter) {
        if (component == null || !component.getText().isEmpty()) {
            return;
        }
        Object value = component.getClientProperty(PLACEHOLDER_PROPERTY);
        if (!(value instanceof String placeholder) || placeholder.isBlank()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setFont(component.getFont());
            g.setColor(withAlpha(MUTED, component.isEnabled() ? 150 : 110));
            FontMetrics metrics = g.getFontMetrics();
            Insets insets = component.getInsets();
            int x = insets.left + 2;
            int y = verticalCenter
                    ? Math.max(insets.top + metrics.getAscent(),
                            (component.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent())
                    : insets.top + metrics.getAscent() + 1;
            g.drawString(placeholder, x, y);
        } finally {
            g.dispose();
        }
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
