package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingConstants;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * Native Swing styling helpers for the CRTK Workbench.
 */
final class WorkbenchTheme {

    /**
     * Theme logger.
     */
    private static final Logger LOGGER = Logger.getLogger(WorkbenchTheme.class.getName());

    /**
     * Client property for empty text-control placeholder copy.
     */
    private static final String PLACEHOLDER_PROPERTY = WorkbenchTheme.class.getName() + ".placeholder";

    /**
     * Root background color (VS Code Light Modern workbench grey).
     */
    static final Color BG = new Color(243, 243, 243);

    /**
     * Fully transparent component background.
     */
    static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    /**
     * Primary panel color (VS Code editor white).
     */
    static final Color PANEL = new Color(255, 255, 255);

    /**
     * Solid panel fallback for Swing components that must be fully opaque.
     */
    static final Color PANEL_SOLID = blendOver(PANEL, BG);

    /**
     * Elevated panel color.
     */
    static final Color ELEVATED = new Color(255, 255, 255);

    /**
     * Solid elevated fallback for data surfaces and scroll viewports.
     */
    static final Color ELEVATED_SOLID = blendOver(ELEVATED, BG);

    /**
     * Line color (VS Code panel border).
     */
    static final Color LINE = new Color(229, 229, 229);

    /**
     * Primary text color (VS Code foreground).
     */
    static final Color TEXT = new Color(59, 59, 59);

    /**
     * Secondary text color (VS Code description foreground).
     */
    static final Color MUTED = new Color(110, 110, 110);

    /**
     * Accent color (VS Code Light Modern focus blue).
     */
    static final Color ACCENT = new Color(0, 95, 184);

    /**
     * Table and tree selection color (VS Code light list selection).
     */
    static final Color SELECTION = new Color(204, 228, 248);

    /**
     * Solid selection fallback for opaque renderers.
     */
    static final Color SELECTION_SOLID = blendOver(SELECTION, BG);

    /**
     * Primary button hover color.
     */
    static final Color ACCENT_HOVER = new Color(26, 115, 199);

    /**
     * Primary button pressed color.
     */
    static final Color ACCENT_PRESSED = new Color(0, 76, 146);

    /**
     * Secondary button color.
     */
    static final Color SECONDARY_BUTTON = new Color(255, 255, 255);

    /**
     * Secondary button hover color.
     */
    static final Color SECONDARY_BUTTON_HOVER = new Color(240, 240, 240);

    /**
     * Secondary button pressed color.
     */
    static final Color SECONDARY_BUTTON_PRESSED = new Color(229, 229, 229);

    /**
     * Secondary button text color.
     */
    static final Color SECONDARY_BUTTON_TEXT = new Color(59, 59, 59);

    /**
     * Disabled button fill color.
     */
    static final Color BUTTON_DISABLED_BG = new Color(240, 240, 240);

    /**
     * Disabled button border color.
     */
    static final Color BUTTON_DISABLED_BORDER = new Color(229, 229, 229);

    /**
     * Disabled button text color.
     */
    static final Color BUTTON_DISABLED_TEXT = new Color(124, 124, 124);

    /**
     * Input border color (VS Code input border).
     */
    static final Color INPUT_BORDER = new Color(206, 206, 206);

    /**
     * Input focus ring color (VS Code focus blue).
     */
    static final Color INPUT_FOCUS = new Color(0, 95, 184);

    /**
     * Disabled input background color.
     */
    static final Color INPUT_DISABLED = new Color(240, 240, 240);

    /**
     * Toggle-off background color.
     */
    static final Color TOGGLE_BG = new Color(240, 240, 240);

    /**
     * Toggle-off border color.
     */
    static final Color TOGGLE_BORDER = new Color(206, 206, 206);

    /**
     * Toggle-off track color.
     */
    static final Color TOGGLE_TRACK = new Color(142, 142, 142);

    /**
     * Toggle-on background color.
     */
    static final Color TOGGLE_ON_BG = new Color(204, 228, 248);

    /**
     * Toggle-on track color.
     */
    static final Color TOGGLE_ON_TRACK = new Color(0, 95, 184);

    /**
     * Toggle thumb color.
     */
    static final Color TOGGLE_THUMB = new Color(255, 255, 255);

    /**
     * Text field background color.
     */
    static final Color INPUT = new Color(255, 255, 255);

    /**
     * Text area background color.
     */
    static final Color TEXT_AREA = new Color(255, 255, 255);

    /**
     * Terminal background color.
     */
    static final Color TERMINAL = new Color(255, 255, 255);

    /**
     * Terminal text color.
     */
    static final Color TERMINAL_TEXT = new Color(59, 59, 59);

    /**
     * Selection color for text controls (VS Code editor selection blue).
     */
    static final Color TEXT_SELECTION = new Color(173, 214, 255, 255);

    /**
     * Primary button text color.
     */
    static final Color PRIMARY_BUTTON_TEXT = Color.WHITE;

    /**
     * Board light square color.
     */
    static final Color BOARD_LIGHT = new Color(240, 217, 181);

    /**
     * Board dark square color.
     */
    static final Color BOARD_DARK = new Color(181, 136, 99);

    /**
     * Standard dark chrome color reused by subtle overlays.
     */
    static final Color BOARD_SHADOW = new Color(64, 64, 64);

    /**
     * Chessboard.js board edge color.
     */
    static final Color BOARD_EDGE = new Color(64, 64, 64);

    /**
     * Coordinate text color used on light squares.
     */
    static final Color COORD_ON_LIGHT = BOARD_DARK;

    /**
     * Coordinate text color used on dark squares.
     */
    static final Color COORD_ON_DARK = BOARD_LIGHT;

    /**
     * Shared inset move-highlight edge color.
     */
    static final Color BOARD_HIGHLIGHT = new Color(47, 143, 78);

    /**
     * Last-move highlight edge.
     */
    static final Color LAST_MOVE_EDGE = BOARD_HIGHLIGHT;

    /**
     * Selected square edge.
     */
    static final Color SELECTED_EDGE = BOARD_HIGHLIGHT;

    /**
     * Quiet legal-target marker fill.
     */
    static final Color LEGAL_TARGET = new Color(42, 45, 48, 92);

    /**
     * Capture legal-target marker edge.
     */
    static final Color LEGAL_CAPTURE_EDGE = new Color(42, 45, 48, 112);

    /**
     * Suggested-move arrow color.
     */
    static final Color BOARD_ARROW = new Color(50, 104, 168);

    /**
     * Check highlight radial core.
     */
    static final Color CHECK_CORE = new Color(231, 78, 78, 245);

    /**
     * Check highlight glow.
     */
    static final Color CHECK_GLOW = new Color(186, 26, 26, 209);

    /**
     * Check highlight square fill.
     */
    static final Color CHECK_FILL = new Color(214, 54, 54, 56);

    /**
     * Check highlight square edge.
     */
    static final Color CHECK_EDGE = new Color(123, 18, 18, 56);

    /**
     * Eval-bar dark side fill.
     */
    static final Color EVAL_BLACK = Color.BLACK;

    /**
     * Eval-bar light side fill.
     */
    static final Color EVAL_WHITE = Color.WHITE;

    /**
     * Eval-bar frame color.
     */
    static final Color EVAL_FRAME = new Color(0, 0, 0, 168);

    /**
     * Eval-bar divider color.
     */
    static final Color EVAL_DIVIDER = new Color(128, 128, 128, 176);

    /**
     * Tab strip selected accent underline (VS Code active-tab accent).
     */
    static final Color TAB_ACCENT_UNDERLINE = new Color(0, 95, 184, 255);

    /**
     * Tab strip rollover fill.
     */
    static final Color TAB_HOVER = new Color(240, 240, 240, 255);

    /**
     * Tab strip resting fill.
     */
    static final Color TAB_IDLE = new Color(243, 243, 243, 255);

    /**
     * Scrollbar track fill.
     */
    static final Color SCROLLBAR_TRACK = new Color(0, 0, 0, 0);

    /**
     * Scrollbar thumb resting fill (VS Code light slider).
     */
    static final Color SCROLLBAR_THUMB = new Color(100, 100, 100, 80);

    /**
     * Scrollbar thumb hover fill.
     */
    static final Color SCROLLBAR_THUMB_HOVER = new Color(100, 100, 100, 130);

    /**
     * Tooltip surface color (VS Code light hover widget).
     */
    static final Color TOOLTIP_BG = new Color(255, 255, 255);

    /**
     * Tooltip text color.
     */
    static final Color TOOLTIP_TEXT = new Color(59, 59, 59);

    /**
     * Tooltip border color.
     */
    static final Color TOOLTIP_BORDER = new Color(200, 200, 200);

    /**
     * Success feedback surface.
     */
    static final Color STATUS_SUCCESS_BG = new Color(236, 247, 235);

    /**
     * Success feedback border.
     */
    static final Color STATUS_SUCCESS_BORDER = new Color(166, 202, 157);

    /**
     * Success feedback text.
     */
    static final Color STATUS_SUCCESS_TEXT = new Color(37, 86, 45);

    /**
     * Warning feedback surface.
     */
    static final Color STATUS_WARNING_BG = new Color(255, 246, 222);

    /**
     * Warning feedback border.
     */
    static final Color STATUS_WARNING_BORDER = new Color(223, 181, 91);

    /**
     * Warning feedback text.
     */
    static final Color STATUS_WARNING_TEXT = new Color(96, 68, 17);

    /**
     * Error feedback surface.
     */
    static final Color STATUS_ERROR_BG = new Color(253, 236, 236);

    /**
     * Error feedback border.
     */
    static final Color STATUS_ERROR_BORDER = new Color(222, 142, 142);

    /**
     * Error feedback text.
     */
    static final Color STATUS_ERROR_TEXT = new Color(136, 42, 42);

    /**
     * Informational feedback surface.
     */
    static final Color STATUS_INFO_BG = new Color(237, 244, 248);

    /**
     * Informational feedback border.
     */
    static final Color STATUS_INFO_BORDER = new Color(166, 185, 196);

    /**
     * Informational feedback text.
     */
    static final Color STATUS_INFO_TEXT = new Color(47, 71, 84);

    /**
     * Compact logo tile fill.
     */
    static final Color LOGO_BACKGROUND = new Color(25, 31, 38, 230);

    /**
     * Compact logo mark fill.
     */
    static final Color LOGO_MARK = new Color(218, 45, 45);

    /**
     * Toggle focus-ring color.
     */
    static final Color TOGGLE_FOCUS = new Color(INPUT_FOCUS.getRed(), INPUT_FOCUS.getGreen(),
            INPUT_FOCUS.getBlue(), 95);

    /**
     * Client-property key marking accent-styled buttons.
     */
    static final String CLIENT_PRIMARY = "workbench.primary";

    /**
     * Client-property key carrying an explicit icon kind for a button.
     */
    static final String CLIENT_ICON_KIND = "workbench.icon";

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
    static final int SPACE_XS = 4;

    /**
     * Small spacing step (8px) — the default gap between sibling controls in
     * a toolbar or button row.
     */
    static final int SPACE_SM = 8;

    /**
     * Medium spacing step (12px) — panel padding and the gap between distinct
     * control groups.
     */
    static final int SPACE_MD = 12;

    /**
     * Large spacing step (16px) — separation between major sections.
     */
    static final int SPACE_LG = 16;

    /**
     * Extra-large spacing step (24px) — generous section breaks.
     */
    static final int SPACE_XL = 24;

    /**
     * Shared corner radius for compact custom-painted chrome (toggle switches,
     * segmented selectors, chips). Keeps rounded controls visually consistent
     * with one another.
     */
    static final int RADIUS = 6;

    /**
     * Standard height for compact toolbar controls (combos, segmented
     * switchers, toggles) so a control row lines up on a single baseline.
     */
    static final int CONTROL_HEIGHT = 30;

    // ------------------------------------------------------------------
    // Neural-network visualization palette
    //
    // The accent colours the NNUE / CNN / BT4 views paint activations and
    // data-flow branches with. Kept here (rather than as private literals in
    // WorkbenchTensorViz) so the network views speak the same colour language
    // as the rest of the workbench chrome.
    // ------------------------------------------------------------------

    /**
     * Positive-activation accent (gain / "up").
     */
    static final Color NN_POSITIVE = new Color(38, 130, 75);

    /**
     * Negative-activation accent (loss / "down").
     */
    static final Color NN_NEGATIVE = new Color(178, 53, 53);

    /**
     * Trunk / data-flow accent.
     */
    static final Color NN_TRUNK = new Color(196, 121, 47);

    /**
     * Policy-branch accent.
     */
    static final Color NN_POLICY = new Color(48, 102, 168);

    /**
     * Value-branch accent.
     */
    static final Color NN_VALUE = new Color(150, 60, 142);

    /**
     * Neutral fill for cells carrying no signal.
     */
    static final Color NN_NEUTRAL = new Color(232, 236, 240);

    /**
     * Lightest signed-heatmap fill (near zero).
     */
    static final Color NN_HEAT_ZERO = new Color(240, 244, 247);

    /**
     * Prevents instantiation.
     */
    private WorkbenchTheme() {
        // utility
    }

    /**
     * Creates a uniform empty padding border.
     *
     * @param all padding applied to every edge
     * @return border
     */
    static Border pad(int all) {
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
    static Border pad(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Returns a color with a replaced alpha channel.
     *
     * @param color color
     * @param alpha alpha value
     * @return color with alpha
     */
    static Color withAlpha(Color color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
    }

    /**
     * Installs the native look and feel plus consistent defaults.
     */
    static void install() {
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
    static Font font(float size, int style) {
        return new Font(UI_FONT_FAMILY, style, Math.round(size));
    }

    /**
     * Returns a monospaced font.
     *
     * @param size font size
     * @return font
     */
    static Font mono(float size) {
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
    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Styles a standard panel-like component.
     *
     * @param component component to style
     */
    static void stylePanel(JComponent component) {
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
    static void field(JTextField field) {
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
    static void area(JTextArea area) {
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
    static void placeholder(JTextComponent component, String text) {
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
    static void styleTerminal(JTextArea area) {
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
    static void button(AbstractButton button, boolean primary) {
        button.putClientProperty(CLIENT_PRIMARY, Boolean.valueOf(primary));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setIcon(WorkbenchSvgIcon.forButton(button, primary));
        button.setIconTextGap(6);
        button.setMargin(new Insets(6, 11, 6, 11));
        button.setFont(font(13, primary ? Font.BOLD : Font.PLAIN));
        button.setBackground(buttonBackground(primary));
        button.setForeground(primary ? PRIMARY_BUTTON_TEXT : SECONDARY_BUTTON_TEXT);
        button.setDisabledIcon(WorkbenchSvgIcon.disabledForButton(button));
        button.setBorder(pad(5, 8, 5, 8));
    }

    /**
     * Returns the resting button background.
     *
     * @param primary whether to use primary styling
     * @return background color
     */
    static Color buttonBackground(boolean primary) {
        return primary ? ACCENT : SECONDARY_BUTTON;
    }

    /**
     * Returns the hover button background.
     *
     * @param primary whether to use primary styling
     * @return hover color
     */
    static Color buttonHover(boolean primary) {
        return primary ? ACCENT_HOVER : SECONDARY_BUTTON_HOVER;
    }

    /**
     * Returns the pressed button background.
     *
     * @param primary whether to use primary styling
     * @return pressed color
     */
    static Color buttonPressed(boolean primary) {
        return primary ? ACCENT_PRESSED : SECONDARY_BUTTON_PRESSED;
    }

    /**
     * Returns a button border color.
     *
     * @param primary whether to use primary styling
     * @return border color
     */
    static Color buttonBorder(boolean primary) {
        return primary ? ACCENT_PRESSED : INPUT_BORDER;
    }

    /**
     * Styles a table as a compact solid data surface.
     *
     * @param table table
     * @param rowHeight row height
     */
    static void table(JTable table, int rowHeight) {
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
        WorkbenchTableCellRenderer textRenderer = new WorkbenchTableCellRenderer();
        table.setDefaultRenderer(Object.class, textRenderer);
        table.setDefaultRenderer(String.class, textRenderer);
        table.setDefaultRenderer(Number.class, textRenderer);
        table.setDefaultRenderer(Boolean.class, new WorkbenchBooleanCellRenderer());
        table.setDefaultEditor(Boolean.class, new DefaultCellEditor(tableBooleanEditor()));
    }

    /**
     * Styles a list as a solid data surface.
     *
     * @param list list
     */
    static void list(JList<?> list) {
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
    static JLabel section(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(font(12, Font.BOLD));
        return label;
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
    private static final class WorkbenchTableCellRenderer extends DefaultTableCellRenderer {

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
    private static final class WorkbenchBooleanCellRenderer extends JComponent implements TableCellRenderer {

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
