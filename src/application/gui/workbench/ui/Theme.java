/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
import javax.swing.JScrollPane;
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
     * Active color mode.
     */
    private static Mode mode = Mode.LIGHT;

    /**
     * Client property for empty text-control placeholder copy.
     */
    static final String PLACEHOLDER_PROPERTY = Theme.class.getName() + ".placeholder";

    /**
     * Client property storing a component's semantic foreground role.
     */
    private static final String FOREGROUND_ROLE_PROPERTY = Theme.class.getName() + ".foregroundRole";

    /**
     * Primary ink for light surfaces.
     */
    private static final Color PASTEL_INK = new Color(59, 59, 59);

    /**
     * Secondary ink for light surfaces.
     */
    private static final Color PASTEL_MUTED = new Color(97, 97, 97);

    /**
     * Light workbench chrome.
     */
    private static final Color PASTEL_CHROME = new Color(248, 248, 248);

    /**
     * Light outline/subtle-fill base.
     */
    private static final Color PASTEL_SUBTLE = new Color(243, 243, 243);

    /**
     * Light document/editor surface.
     */
    private static final Color PASTEL_DOCUMENT = Color.WHITE;

    /**
     * Single neutral workbench border.
     */
    private static final Color PASTEL_BORDER = new Color(229, 229, 229);

    /**
     * Light vibrant pastel action blue.
     */
    private static final Color PASTEL_BLUE = new Color(142, 203, 244);

    /**
     * Light vibrant pastel action blue hover fill.
     */
    private static final Color PASTEL_BLUE_HOVER = new Color(124, 192, 239);

    /**
     * Light vibrant pastel action blue pressed fill.
     */
    private static final Color PASTEL_BLUE_PRESSED = new Color(101, 179, 232);

    /**
     * Accessible blue text for informational surfaces.
     */
    private static final Color PASTEL_BLUE_TEXT = new Color(36, 94, 143);

    /**
     * Positive vibrant pastel green.
     */
    private static final Color PASTEL_GREEN = new Color(155, 222, 172);

    /**
     * Accessible positive text for green surfaces.
     */
    private static final Color PASTEL_GREEN_TEXT = new Color(35, 109, 66);

    /**
     * Warning vibrant pastel amber.
     */
    private static final Color PASTEL_AMBER = new Color(255, 209, 102);

    /**
     * Accessible warning text for amber surfaces.
     */
    private static final Color PASTEL_AMBER_TEXT = new Color(118, 80, 24);

    /**
     * Error vibrant pastel coral.
     */
    private static final Color PASTEL_CORAL = new Color(255, 159, 154);

    /**
     * Accessible error text for coral surfaces.
     */
    private static final Color PASTEL_CORAL_TEXT = new Color(157, 49, 46);

    /**
     * Policy vibrant pastel purple.
     */
    private static final Color PASTEL_PURPLE = new Color(213, 166, 243);

    /**
     * Dark primary ink.
     */
    private static final Color DARK_INK = new Color(204, 204, 204);

    /**
     * Dark secondary ink.
     */
    private static final Color DARK_MUTED = new Color(157, 157, 157);

    /**
     * Dark workbench chrome.
     */
    private static final Color DARK_CHROME = new Color(24, 24, 24);

    /**
     * Dark subtle surface.
     */
    private static final Color DARK_SUBTLE = new Color(43, 43, 43);

    /**
     * Dark editor/panel surface.
     */
    private static final Color DARK_DOCUMENT = new Color(31, 31, 31);

    /**
     * Dark elevated surface.
     */
    private static final Color DARK_ELEVATED = new Color(49, 49, 49);

    /**
     * Dark border color.
     */
    private static final Color DARK_BORDER = new Color(60, 60, 60);

    /**
     * Dark-mode vibrant pastel primary accent.
     */
    private static final Color DARK_ACCENT = new Color(121, 200, 255);

    /**
     * Dark-mode vibrant pastel accent hover.
     */
    private static final Color DARK_ACCENT_HOVER = new Color(143, 211, 255);

    /**
     * Dark-mode vibrant pastel accent pressed.
     */
    private static final Color DARK_ACCENT_PRESSED = new Color(93, 184, 244);

    /**
     * Dark-mode vibrant pastel positive accent.
     */
    private static final Color DARK_GREEN = new Color(126, 227, 157);

    /**
     * Dark-mode vibrant pastel warning accent.
     */
    private static final Color DARK_AMBER = new Color(255, 211, 110);

    /**
     * Dark-mode vibrant pastel error accent.
     */
    private static final Color DARK_CORAL = new Color(255, 143, 138);

    /**
     * Dark-mode vibrant pastel policy accent.
     */
    private static final Color DARK_PURPLE = new Color(217, 155, 255);

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
    private static final Color DARK_INFO_TEXT = DARK_ACCENT;

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
    public static Color SELECTION = new Color(234, 244, 255);

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
    public static Color SECONDARY_BUTTON = PASTEL_BORDER;

    /**
     * Secondary button hover color.
     */
    public static Color SECONDARY_BUTTON_HOVER = new Color(204, 204, 204);

    /**
     * Secondary button pressed color.
     */
    public static Color SECONDARY_BUTTON_PRESSED = new Color(206, 206, 206);

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
    public static Color INPUT_BORDER = new Color(206, 206, 206);

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
    public static Color TOGGLE_BORDER = INPUT_BORDER;

    /**
     * Toggle-off track color.
     */
    public static Color TOGGLE_TRACK = PASTEL_MUTED;

    /**
     * Toggle-on background color.
     */
    public static Color TOGGLE_ON_BG = new Color(214, 234, 248);

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
    public static Color TEXT_SELECTION = new Color(205, 230, 250, 255);

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
     * Chessboard.js inset square-highlight edge color.
     */
    public static Color BOARD_HIGHLIGHT = Color.YELLOW;

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
    public static Color STATUS_SUCCESS_BG = new Color(238, 248, 241);

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
    public static Color STATUS_WARNING_BG = new Color(255, 247, 224);

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
    public static Color STATUS_INFO_BG = new Color(234, 244, 255);

    /**
     * Informational feedback border.
     */
    public static Color STATUS_INFO_BORDER = PASTEL_BLUE;

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
            Ui.styleCombo(combo);
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
            Object primary = button.getClientProperty(CLIENT_PRIMARY);
            if (primary instanceof Boolean value) {
                button(button, value.booleanValue());
            } else {
                button.setForeground(TEXT);
                button.setFont(font(13, Font.PLAIN));
            }
        } else if (component instanceof JLabel label) {
            refreshForeground(label);
        } else if (component instanceof JComponent jComponent) {
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
                || rgb == (DARK_SUBTLE.getRGB() & 0x00ff_ffff)
                || rgb == (DARK_BORDER.getRGB() & 0x00ff_ffff);
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
     * Infers a foreground role for legacy components that predate explicit
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
        ELEVATED = PASTEL_DOCUMENT;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        LINE = PASTEL_BORDER;
        TEXT = PASTEL_INK;
        MUTED = PASTEL_MUTED;
        ACCENT = PASTEL_BLUE;
        SELECTION = new Color(229, 246, 255);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = PASTEL_BLUE_HOVER;
        ACCENT_PRESSED = PASTEL_BLUE_PRESSED;
        SECONDARY_BUTTON = PASTEL_BORDER;
        SECONDARY_BUTTON_HOVER = new Color(204, 204, 204);
        SECONDARY_BUTTON_PRESSED = new Color(206, 206, 206);
        SECONDARY_BUTTON_TEXT = PASTEL_INK;
        BUTTON_DISABLED_BG = PASTEL_SUBTLE;
        BUTTON_DISABLED_BORDER = PASTEL_BORDER;
        BUTTON_DISABLED_TEXT = PASTEL_MUTED;
        INPUT_BORDER = new Color(206, 206, 206);
        INPUT_FOCUS = PASTEL_BLUE;
        INPUT_DISABLED = PASTEL_SUBTLE;
        TOGGLE_BG = PASTEL_SUBTLE;
        TOGGLE_BORDER = INPUT_BORDER;
        TOGGLE_TRACK = PASTEL_MUTED;
        TOGGLE_ON_BG = new Color(210, 239, 255);
        TOGGLE_ON_TRACK = PASTEL_BLUE;
        TOGGLE_THUMB = PASTEL_DOCUMENT;
        INPUT = PASTEL_DOCUMENT;
        TEXT_AREA = PASTEL_DOCUMENT;
        TERMINAL = PASTEL_DOCUMENT;
        TERMINAL_TEXT = PASTEL_INK;
        TEXT_SELECTION = new Color(194, 230, 255, 255);
        PRIMARY_BUTTON_TEXT = PASTEL_INK;
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(PASTEL_DOCUMENT.getRed(), PASTEL_DOCUMENT.getGreen(), PASTEL_DOCUMENT.getBlue(), 255);
        TAB_IDLE = new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 255);
        SCROLLBAR_TRACK = new Color(PASTEL_CHROME.getRed(), PASTEL_CHROME.getGreen(), PASTEL_CHROME.getBlue(), 0);
        SCROLLBAR_THUMB = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 80);
        SCROLLBAR_THUMB_HOVER = new Color(PASTEL_MUTED.getRed(), PASTEL_MUTED.getGreen(), PASTEL_MUTED.getBlue(), 130);
        TOOLTIP_BG = PASTEL_DOCUMENT;
        TOOLTIP_TEXT = PASTEL_INK;
        TOOLTIP_BORDER = PASTEL_BORDER;
        STATUS_SUCCESS_BG = new Color(241, 255, 245);
        STATUS_SUCCESS_BORDER = PASTEL_GREEN;
        STATUS_SUCCESS_TEXT = PASTEL_GREEN_TEXT;
        STATUS_WARNING_BG = new Color(255, 247, 223);
        STATUS_WARNING_BORDER = PASTEL_AMBER;
        STATUS_WARNING_TEXT = PASTEL_AMBER_TEXT;
        STATUS_ERROR_BG = new Color(255, 241, 240);
        STATUS_ERROR_BORDER = PASTEL_CORAL;
        STATUS_ERROR_TEXT = PASTEL_CORAL_TEXT;
        STATUS_INFO_BG = new Color(239, 248, 255);
        STATUS_INFO_BORDER = PASTEL_BLUE;
        STATUS_INFO_TEXT = PASTEL_BLUE_TEXT;
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
        ELEVATED = DARK_ELEVATED;
        ELEVATED_SOLID = blendOver(ELEVATED, BG);
        LINE = DARK_SUBTLE;
        TEXT = DARK_INK;
        MUTED = DARK_MUTED;
        ACCENT = DARK_ACCENT;
        SELECTION = withAlpha(DARK_ACCENT, 72);
        SELECTION_SOLID = blendOver(SELECTION, BG);
        ACCENT_HOVER = DARK_ACCENT_HOVER;
        ACCENT_PRESSED = DARK_ACCENT_PRESSED;
        SECONDARY_BUTTON = DARK_ELEVATED;
        SECONDARY_BUTTON_HOVER = DARK_SUBTLE;
        SECONDARY_BUTTON_PRESSED = DARK_SUBTLE;
        SECONDARY_BUTTON_TEXT = DARK_INK;
        BUTTON_DISABLED_BG = DARK_SUBTLE;
        BUTTON_DISABLED_BORDER = DARK_SUBTLE;
        BUTTON_DISABLED_TEXT = new Color(133, 133, 133);
        INPUT_BORDER = DARK_BORDER;
        INPUT_FOCUS = DARK_ACCENT;
        INPUT_DISABLED = DARK_SUBTLE;
        TOGGLE_BG = DARK_SUBTLE;
        TOGGLE_BORDER = DARK_BORDER;
        TOGGLE_TRACK = DARK_MUTED;
        TOGGLE_ON_BG = withAlpha(DARK_ACCENT, 130);
        TOGGLE_ON_TRACK = DARK_ACCENT_HOVER;
        TOGGLE_THUMB = DARK_INK;
        INPUT = DARK_ELEVATED;
        TEXT_AREA = DARK_DOCUMENT;
        TERMINAL = DARK_DOCUMENT;
        TERMINAL_TEXT = DARK_INK;
        TEXT_SELECTION = new Color(62, 86, 105, 255);
        PRIMARY_BUTTON_TEXT = PASTEL_INK;
        setFixedBoardAndEvalColors();
        TAB_ACCENT_UNDERLINE = withAlpha(ACCENT, 255);
        TAB_HOVER = new Color(DARK_DOCUMENT.getRed(), DARK_DOCUMENT.getGreen(), DARK_DOCUMENT.getBlue(), 255);
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
        BOARD_HIGHLIGHT = Color.YELLOW;
        LAST_MOVE_EDGE = BOARD_HIGHLIGHT;
        SELECTED_EDGE = BOARD_HIGHLIGHT;
        LEGAL_TARGET = withAlpha(TEXT, 86);
        LEGAL_CAPTURE_EDGE = withAlpha(TEXT, 112);
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
        UIManager.put("MenuBar.background", BG);
        UIManager.put("MenuBar.foreground", TEXT);
        UIManager.put("Menu.background", BG);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("MenuItem.background", PANEL_SOLID);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", SELECTION_SOLID);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("RadioButtonMenuItem.background", PANEL_SOLID);
        UIManager.put("RadioButtonMenuItem.foreground", TEXT);
        UIManager.put("RadioButtonMenuItem.selectionBackground", SELECTION_SOLID);
        UIManager.put("RadioButtonMenuItem.selectionForeground", TEXT);
        UIManager.put("PopupMenu.background", PANEL_SOLID);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(LINE));
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
        DataTableStyler.style(table, rowHeight);
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
        foreground(label, ForegroundRole.TEXT);
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
        reserveCommandTabSize(tab);
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
