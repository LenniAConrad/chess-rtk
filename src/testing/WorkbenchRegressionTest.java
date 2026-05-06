package testing;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.struct.Game;
import chess.uci.Output;

/**
 * Headless regression checks for workbench support classes.
 */
@SuppressWarnings("java:S2187")
public final class WorkbenchRegressionTest {

    /**
     * Workbench implementation package.
     */
    private static final String WORKBENCH_PACKAGE = "application.gui.workbench.";

    /**
     * Shared standard starting position.
     */
    private static final String START_FEN = Game.STANDARD_START_FEN;

    /**
     * Flag column index in the command option table.
     */
    private static final int COL_FLAG = 1;

    /**
     * Value column index in the command option table.
     */
    private static final int COL_VALUE = 2;

    /**
     * Prevents instantiation.
     */
    private WorkbenchRegressionTest() {
        // utility
    }

    /**
     * Runs all workbench regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testFirstFenLineSkipsNonFenRows();
        testBatchFenValidationReportsLineErrors();
        testCommandOptionConflictsDisableStaleRows();
        testDynamicOptionRefresh();
        testDynamicOptionRefreshSkipsUnchangedValues();
        testEngineTemplateContextFeedsExternalConfigOptions();
        testEngineBatchTasksUseExternalConfigOptions();
        testCommandTemplatesHaveCompactTabLabels();
        testPaletteTokenMatching();
        testOptionFilterTokenMatching();
        testTextAreaScrollPaintsOpaque();
        testScrollPaneUsesSolidCorners();
        testViewportFillWrapperTracksAvailableHeight();
        testDataSurfacesUseSolidBackgrounds();
        testCustomPaintedSurfacesClearBackground();
        testBooleanTableRendererIsStyled();
        testComponentTreeStylingCoversPlainControls();
        testSettingsToggleRowsAreReadable();
        testThemeColorContrast();
        testThemeInstallSetsTooltipColors();
        testCollapsibleInfoSectionTogglesContent();
        testTabbedPaneSwitchesWithoutSnapshotOverlay();
        testButtonHoverTransitionStarts();
        testWorkbenchTimingDefaultsAreSnappy();
        testResetButtonUsesResetIcon();
        testButtonDisabledIconIsMuted();
        testCommandPreviewQuoting();
        testEvalBarMapping();
        testEvalBarAnimation();
        testEvalBarThinkingIsStatic();
        testEngineEvalParsing();
        testLiveEngineStatusFormatting();
        testOptionalPositiveIntegerParsing();
        testAnalysisGraphStoresSamples();
        testAnalysisGraphExportsReportData();
        testAnalysisGraphPaintsOpaqueSurface();
        testBoardHasNoInstructionTooltip();
        testBoardHasNoKeyboardPieceSelector();
        testWindowPositionNavigationRoutingSkipsTextAndDataControls();
        testBoardRightDragTogglesLichessArrowMarkup();
        testBoardRightClickTogglesLichessCircleMarkup();
        testBoardRightDragReplacesMarkupColorLikeLichess();
        testBoardDragEmitsLegalMove();
        testBoardDragInvalidHoverDoesNotPaintRedBox();
        testBoardDragDirtyBoundsIncludesInvalidHoverSquare();
        testBoardMoveAnimationStarts();
        testBoardCaptureAnimationStarts();
        testBoardCastlingAnimationStarts();
        testBoardPaintUsesChessboardJsColors();
        testBoardSuggestedMoveArrowIsLegalAndClean();
        testBoardLegalMovePreviewCanBeHidden();
        testBoardLastMoveAndBestArrowCanBeHidden();
        testBoardNotationAndAnimationsCanBeHidden();
        testBoardCheckHighlightPaintsCheckedKingMarker();
        testBoardTextureCachesRenderedLayer();
        testBoardPieceImageCacheReusesScaledSvg();
        System.out.println("WorkbenchRegressionTest: all checks passed");
    }

    /**
     * Verifies pasted notes before a FEN do not prevent FEN import.
     */
    private static void testFirstFenLineSkipsNonFenRows() {
        String text = "analysis note" + System.lineSeparator() + START_FEN;
        assertEquals(START_FEN, invokeStatic(type("WorkbenchWindow"), "firstFenLine",
                new Class<?>[] { String.class }, text), "first FEN after non-FEN note");
    }

    /**
     * Verifies batch FEN validation surfaces the exact failing row before a
     * command is launched.
     */
    private static void testBatchFenValidationReportsLineErrors() {
        Object summary = invokeStatic(type("WorkbenchWindow"), "validateBatchFenInput",
                new Class<?>[] { String.class }, START_FEN + System.lineSeparator() + "not a fen");
        assertEquals(Integer.valueOf(2), invoke(summary, "rows", new Class<?>[0]), "batch FEN row count");
        assertEquals(Integer.valueOf(1), invoke(summary, "validRows", new Class<?>[0]), "batch valid row count");
        assertEquals(Integer.valueOf(2), invoke(summary, "firstErrorLine", new Class<?>[0]),
                "batch FEN error line");
        assertTrue((Boolean) invoke(summary, "hasError", new Class<?>[0]), "batch FEN error flag");
    }

    /**
     * Verifies mutually-exclusive command-builder rows disable stale selections.
     */
    private static void testCommandOptionConflictsDisableStaleRows() {
        Object options = optionsFor("Generate FENs");
        int exactPieces = rowForFlag(options, "--pieces");
        int minPieces = rowForFlag(options, "--min-pieces");

        invoke(options, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                "8", exactPieces, COL_VALUE);
        assertTrue(hasFlag(enabledArgs(options), "--pieces"), "exact pieces enabled");

        invoke(options, "setValueAt", new Class<?>[] { Object.class, int.class, int.class },
                "4", minPieces, COL_VALUE);
        List<String> args = enabledArgs(options);
        assertFalse(hasFlag(args, "--pieces"), "exact pieces disabled by min pieces");
        assertTrue(hasFlag(args, "--min-pieces"), "min pieces enabled");
    }

    /**
     * Verifies dynamic command values follow the latest shared context.
     */
    private static void testDynamicOptionRefresh() {
        Object options = optionsFor("Best move");
        int fenRow = rowForFlag(options, "--fen");
        String nextFen = "8/8/8/8/8/8/K7/7k b - - 1 1";
        invoke(options, "refreshDynamicValues", new Class<?>[] { type("WorkbenchCommandTemplates$TemplateContext") },
                templateContext(nextFen, "9s", "5", "2", "1"));
        assertEquals(nextFen, String.valueOf(invoke(options, "getValueAt",
                new Class<?>[] { int.class, int.class }, fenRow, COL_VALUE)), "dynamic FEN refresh");
    }

    /**
     * Verifies dynamic option refreshes avoid redundant table repaint events.
     */
    private static void testDynamicOptionRefreshSkipsUnchangedValues() {
        Object options = optionsFor("Best move");
        int[] events = { 0 };
        ((javax.swing.table.AbstractTableModel) options).addTableModelListener(event -> events[0]++);

        invoke(options, "refreshDynamicValues", new Class<?>[] { type("WorkbenchCommandTemplates$TemplateContext") },
                templateContext(START_FEN, "2s", "4", "3", "1"));
        assertEquals(Integer.valueOf(0), Integer.valueOf(events[0]), "unchanged dynamic values do not fire events");

        invoke(options, "refreshDynamicValues", new Class<?>[] { type("WorkbenchCommandTemplates$TemplateContext") },
                templateContext("8/8/8/8/8/8/K7/7k b - - 1 1", "2s", "4", "3", "1"));
        assertTrue(events[0] > 0, "changed dynamic values still fire events");
    }

    /**
     * Verifies engine command templates pick up shared external-engine settings
     * when those optional rows are enabled.
     */
    private static void testEngineTemplateContextFeedsExternalConfigOptions() {
        Object options = optionsFor("Analyze");
        Object context = templateContext(START_FEN, "3s", "4", "2", "8",
                "config/lc0.engine.toml", "1200", "256");
        invoke(options, "refreshDynamicValues", new Class<?>[] { type("WorkbenchCommandTemplates$TemplateContext") },
                context);

        assertEquals("config/lc0.engine.toml", optionValue(options, "--protocol-path"),
                "dynamic engine protocol");
        assertEquals("1200", optionValue(options, "--max-nodes"), "dynamic engine nodes");
        assertEquals("256", optionValue(options, "--hash"), "dynamic engine hash");
    }

    /**
     * Verifies batch engine workflows inherit external-engine settings.
     */
    private static void testEngineBatchTasksUseExternalConfigOptions() {
        Object task = batchTask("Analyze batch");
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) invoke(task, "build",
                new Class<?>[] { java.nio.file.Path.class, type("WorkbenchCommandTemplates$TemplateContext") },
                java.nio.file.Path.of("positions.txt"),
                templateContext(START_FEN, "4s", "5", "3", "6",
                        "config/default.engine.toml", "900", "128"));

        assertTrue(hasFlag(args, "--protocol-path"), "batch protocol flag");
        assertTrue(hasFlag(args, "--max-nodes"), "batch nodes flag");
        assertTrue(hasFlag(args, "--threads"), "batch threads flag");
        assertTrue(hasFlag(args, "--hash"), "batch hash flag");
        assertTrue(hasFlag(args, "--jsonl"), "batch jsonl flag");
    }

    /**
     * Verifies command templates expose stable labels suitable for command tabs.
     */
    @SuppressWarnings("unchecked")
    private static void testCommandTemplatesHaveCompactTabLabels() {
        List<Object> templates = (List<Object>) invokeStatic(type("WorkbenchCommandTemplates"),
                "commandTemplates", new Class<?>[0]);
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("WorkbenchCommandTemplates"),
                "commandModel", new Class<?>[0]);
        assertEquals(Integer.valueOf(model.getSize()), Integer.valueOf(templates.size()),
                "command template tab count");
        assertTrue(templates.size() > 4, "command template tab coverage");
        for (int i = 0; i < templates.size(); i++) {
            String name = String.valueOf(invoke(templates.get(i), "name", new Class<?>[0]));
            assertFalse(name.isBlank(), "command tab label is not blank");
            for (int j = 0; j < i; j++) {
                String prior = String.valueOf(invoke(templates.get(j), "name", new Class<?>[0]));
                assertFalse(name.equals(prior), "command tab labels are unique");
            }
        }
    }

    /**
     * Verifies palette search uses all query tokens.
     */
    private static void testPaletteTokenMatching() {
        Object action = construct(type("WorkbenchCommandPalette$PaletteAction"),
                new Class<?>[] { String.class, String.class, Runnable.class },
                "Run publishing", "Execute the selected book workflow", (Runnable) () -> {
                    // no-op test action
                });
        assertTrue((Boolean) invoke(action, "matches", new Class<?>[] { String.class }, "publish book"),
                "palette multi-token match");
        assertFalse((Boolean) invoke(action, "matches", new Class<?>[] { String.class }, "publish batch"),
                "palette missing token");
    }

    /**
     * Verifies command option filtering matches tokens across row columns.
     */
    private static void testOptionFilterTokenMatching() {
        assertTrue(optionFilterMatches("min pieces", "--min-pieces", "", "Minimum piece count"),
                "option filter split token match");
        assertTrue(optionFilterMatches("output dir", "--output", "workbench-fens", "Output directory"),
                "option filter cross-column match");
        assertFalse(optionFilterMatches("output mate", "--output", "workbench-fens", "Output directory"),
                "option filter missing token");
    }

    /**
     * Verifies text-area scroll panes repaint with solid backgrounds.
     */
    private static void testTextAreaScrollPaintsOpaque() {
        JTextArea area = new JTextArea("report");
        invokeStatic(type("WorkbenchTheme"), "area", new Class<?>[] { JTextArea.class }, area);
        JScrollPane pane = (JScrollPane) invokeStatic(type("WorkbenchUi"), "scroll",
                new Class<?>[] { javax.swing.JComponent.class }, area);
        assertEquals(255, area.getBackground().getAlpha(), "text area alpha");
        assertTrue(pane.getViewport().isOpaque(), "text area viewport opaque");
        assertEquals(area.getBackground(), pane.getViewport().getBackground(), "viewport background");
    }

    /**
     * Verifies non-opaque scroll views still get solid viewport and corner fills.
     */
    private static void testScrollPaneUsesSolidCorners() {
        JPanel view = new JPanel();
        view.setOpaque(false);
        JScrollPane pane = (JScrollPane) invokeStatic(type("WorkbenchUi"), "scroll",
                new Class<?>[] { javax.swing.JComponent.class }, view);
        assertTrue(pane.getViewport().isOpaque(), "solid viewport opaque fallback");
        assertEquals(Integer.valueOf(255), Integer.valueOf(pane.getViewport().getBackground().getAlpha()),
                "viewport solid alpha");
        assertTrue(pane.getCorner(javax.swing.ScrollPaneConstants.UPPER_RIGHT_CORNER).isOpaque(),
                "upper right scroll corner opaque");
        assertEquals(pane.getViewport().getBackground(),
                pane.getCorner(javax.swing.ScrollPaneConstants.LOWER_RIGHT_CORNER).getBackground(),
                "scroll corner background");
    }

    /**
     * Verifies short scroll content expands to the available viewport height.
     */
    private static void testViewportFillWrapperTracksAvailableHeight() {
        JPanel child = new JPanel();
        child.setPreferredSize(new Dimension(200, 100));
        JComponent wrapper = (JComponent) invokeStatic(type("WorkbenchUi"), "fillViewport",
                new Class<?>[] { javax.swing.JComponent.class }, child);
        assertTrue(wrapper instanceof Scrollable, "viewport fill wrapper is scrollable");

        JViewport viewport = new JViewport();
        viewport.setSize(new Dimension(320, 360));
        viewport.setView(wrapper);
        Scrollable scrollable = (Scrollable) wrapper;
        assertTrue(scrollable.getScrollableTracksViewportWidth(), "viewport fill tracks width");
        assertTrue(scrollable.getScrollableTracksViewportHeight(), "short content fills viewport height");

        child.setPreferredSize(new Dimension(200, 520));
        wrapper.revalidate();
        assertFalse(scrollable.getScrollableTracksViewportHeight(), "tall content keeps vertical scrolling");
    }

    /**
     * Verifies table and list data surfaces avoid translucent opaque repaint trails.
     */
    private static void testDataSurfacesUseSolidBackgrounds() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("WorkbenchTheme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        assertTrue(table.isOpaque(), "table opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(table.getBackground().getAlpha()), "table solid alpha");
        assertFalse(table.getShowHorizontalLines(), "table grid artifacts disabled");

        JList<String> list = new JList<>(new String[] { "a" });
        invokeStatic(type("WorkbenchTheme"), "list", new Class<?>[] { JList.class }, list);
        assertTrue(list.isOpaque(), "list opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(list.getBackground().getAlpha()), "list solid alpha");
    }

    /**
     * Verifies custom-painted surfaces clear their own background so partial
     * repaints cannot leave translucent trails.
     */
    private static void testCustomPaintedSurfacesClearBackground() {
        JComponent panel = (JComponent) construct(type("WorkbenchSurfacePanel"),
                new Class<?>[] { java.awt.LayoutManager.class }, new java.awt.BorderLayout());
        assertPaintsOpaqueCorner(panel, 160, 80, "solid panel clears background");

        JComponent board = (JComponent) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        assertPaintsOpaqueCorner(board, 320, 320, "board panel clears margin background");

        JComponent eval = (JComponent) construct(type("WorkbenchEvalBar"), new Class<?>[0]);
        assertPaintsOpaqueCorner(eval, 40, 260, "eval bar clears background");
    }

    /**
     * Verifies boolean table cells do not fall back to Swing's default checkbox styling.
     */
    private static void testBooleanTableRendererIsStyled() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("WorkbenchTheme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        TableCellRenderer renderer = table.getDefaultRenderer(Boolean.class);
        Component cell = renderer.getTableCellRendererComponent(table, Boolean.TRUE, true, false, 0, 0);
        assertTrue(cell instanceof JComponent, "boolean renderer component");
        assertTrue(((JComponent) cell).isOpaque(), "boolean renderer opaque");
        assertEquals(table.getSelectionBackground(), cell.getBackground(), "boolean renderer selected background");
        assertTrue(table.getDefaultEditor(Boolean.class) != null, "boolean table editor installed");
    }

    /**
     * Verifies recursive styling catches plain controls from dialogs and file choosers.
     */
    private static void testComponentTreeStylingCoversPlainControls() {
        JPanel root = new JPanel();
        JTextField field = new JTextField("path");
        JButton button = new JButton("Open");
        JList<String> list = new JList<>(new String[] { "one" });
        JScrollPane pane = new JScrollPane(list);
        root.add(field);
        root.add(button);
        root.add(pane);

        invokeStatic(type("WorkbenchUi"), "styleComponentTree", new Class<?>[] { Component.class }, root);

        assertEquals(Integer.valueOf(255), Integer.valueOf(field.getBackground().getAlpha()),
                "recursive text field solid alpha");
        assertFalse(button.isContentAreaFilled(), "recursive button content area hidden");
        assertTrue(list.isOpaque(), "recursive list opaque");
        assertEquals(list.getBackground(), pane.getViewport().getBackground(), "recursive scroll viewport background");
    }

    /**
     * Verifies settings toggles reserve enough width for full labels.
     */
    private static void testSettingsToggleRowsAreReadable() {
        JComponent toggle = (JComponent) construct(type("WorkbenchToggleBox"), new Class<?>[] { String.class },
                "Legal move preview");
        Dimension size = toggle.getPreferredSize();
        assertTrue(size.width >= 300, "settings toggle row is wide enough for labels");
        assertTrue(size.height <= 36, "settings toggle row remains compact");
    }

    /**
     * Verifies core theme foreground/background pairs meet practical contrast
     * thresholds for extended workbench use.
     */
    private static void testThemeColorContrast() {
        assertThemeContrast("body text on panel", "TEXT", "PANEL_SOLID", 7.0);
        assertThemeContrast("muted text on panel", "MUTED", "PANEL_SOLID", 4.5);
        assertThemeContrast("input text", "TEXT", "INPUT", 7.0);
        assertThemeContrast("terminal text", "TERMINAL_TEXT", "TERMINAL", 7.0);
        assertThemeContrast("primary button text", "PRIMARY_BUTTON_TEXT", "ACCENT", 4.5);
        assertThemeContrast("secondary button text", "SECONDARY_BUTTON_TEXT", "SECONDARY_BUTTON", 7.0);
        assertThemeContrast("tooltip text", "TOOLTIP_TEXT", "TOOLTIP_BG", 7.0);
        assertThemeContrast("success toast text", "STATUS_SUCCESS_TEXT", "STATUS_SUCCESS_BG", 4.5);
        assertThemeContrast("warning toast text", "STATUS_WARNING_TEXT", "STATUS_WARNING_BG", 4.5);
        assertThemeContrast("error toast text", "STATUS_ERROR_TEXT", "STATUS_ERROR_BG", 4.5);
        assertThemeContrast("info toast text", "STATUS_INFO_TEXT", "STATUS_INFO_BG", 4.5);
        assertThemeContrast("disabled button text", "BUTTON_DISABLED_TEXT", "BUTTON_DISABLED_BG", 3.0);
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        assertTrue(moveHighlight.getGreen() > moveHighlight.getRed()
                && moveHighlight.getGreen() > moveHighlight.getBlue(), "move highlight is green");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_LIGHT"), 95.0,
                "move highlight distinguishes from light board squares");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_DARK"), 95.0,
                "move highlight distinguishes from dark board squares");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_LIGHT"), 95.0,
                "best-move arrow distinguishes from light board squares");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_DARK"), 95.0,
                "best-move arrow distinguishes from dark board squares");
    }

    /**
     * Verifies the installed Swing defaults use the workbench palette rather
     * than default look-and-feel colors.
     */
    private static void testThemeInstallSetsTooltipColors() {
        invokeStatic(type("WorkbenchTheme"), "install", new Class<?>[0]);
        assertEquals(themeColor("TOOLTIP_BG"), UIManager.getColor("ToolTip.background"), "tooltip background themed");
        assertEquals(themeColor("TOOLTIP_TEXT"), UIManager.getColor("ToolTip.foreground"), "tooltip foreground themed");
        assertEquals(themeColor("TEXT_SELECTION"), UIManager.getColor("TextField.selectionBackground"),
                "text-field selection background themed");
        assertEquals(themeColor("SELECTION_SOLID"), UIManager.getColor("List.selectionBackground"),
                "list selection background themed");
    }

    /**
     * Verifies collapsible information sections hide and restore their content.
     */
    private static void testCollapsibleInfoSectionTogglesContent() {
        JLabel content = new JLabel("details");
        JComponent section = (JComponent) invokeStatic(type("WorkbenchUi"), "collapsible",
                new Class<?>[] { String.class, javax.swing.JComponent.class, boolean.class },
                "Info", content, true);
        JButton toggle = firstButton(section);
        assertTrue(content.isVisible(), "collapsible content initially visible");
        toggle.doClick();
        assertFalse(content.isVisible(), "collapsible content hidden");
        toggle.doClick();
        assertTrue(content.isVisible(), "collapsible content restored");
    }

    /**
     * Verifies styled tab panes switch immediately without keeping outgoing
     * content snapshots that can ghost over the next tab.
     */
    private static void testTabbedPaneSwitchesWithoutSnapshotOverlay() {
        JTabbedPane tabs = (JTabbedPane) invokeStatic(type("WorkbenchUi"), "tabbedPane",
                new Class<?>[0]);
        JPanel first = new JPanel();
        first.add(new JLabel("first"));
        JPanel second = new JPanel();
        second.add(new JLabel("second"));
        tabs.addTab("A", first);
        tabs.addTab("B", second);
        first.setSize(220, 120);
        first.setBounds(0, 28, 220, 120);
        tabs.setSelectedIndex(1);

        assertEquals(1, tabs.getSelectedIndex(), "tab selection changed");
        assertEquals(JTabbedPane.class, tabs.getClass(), "tab pane has no snapshot overlay subclass");
    }

    /**
     * Verifies styled buttons ease into hover state instead of switching instantly.
     */
    private static void testButtonHoverTransitionStarts() {
        JButton button = (JButton) invokeStatic(type("WorkbenchUi"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Run", true, (ActionListener) event -> {
                    // no-op test listener
        });
        button.getModel().setRollover(true);
        assertTrue((Boolean) invoke(button, "isFillRunning", new Class<?>[0]),
                "button hover transition starts");
    }

    /**
     * Verifies visual timing defaults stay short enough to feel responsive.
     */
    private static void testWorkbenchTimingDefaultsAreSnappy() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        assertTrue((Integer) staticField(type("WorkbenchUi"), "BUTTON_TRANSITION_MS") <= 80,
                "button transitions are short");
        assertTrue((Integer) staticField(type("WorkbenchBoardPanel"), "MOVE_ANIMATION_MS") <= 120,
                "board move animation is short");
        assertTrue((Integer) field(board, "snapbackAnimationMs") <= 100,
                "snapback animation is short");
        assertTrue((Integer) field(board, "snapAnimationMs") <= 70,
                "snap animation is short");
        assertTrue((Integer) field(board, "flipAnimationMs") <= 160,
                "flip animation is short");
        assertTrue((Integer) staticField(type("WorkbenchEvalBar"), "ANIMATION_DURATION_MS") <= 160,
                "eval bar transition is short");
        assertTrue((Integer) staticField(type("WorkbenchWindow"), "EVAL_DEBOUNCE_MS") <= 100,
                "eval refresh debounce is short");
    }

    /**
     * Verifies the Reset button keeps the reset glyph.
     */
    private static void testResetButtonUsesResetIcon() {
        JButton button = (JButton) invokeStatic(type("WorkbenchUi"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Reset", false, (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "reset icon present");
        assertEquals("RESET", String.valueOf(field(button.getIcon(), "kind")), "reset icon kind");
    }

    /**
     * Verifies disabled buttons use a distinct muted icon.
     */
    private static void testButtonDisabledIconIsMuted() {
        JButton button = (JButton) invokeStatic(type("WorkbenchUi"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Stop", false, (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "button icon present");
        assertTrue(button.getDisabledIcon() != null, "disabled button icon present");
        assertFalse(button.getIcon() == button.getDisabledIcon(), "disabled icon distinct");
    }

    /**
     * Verifies command previews quote arguments containing spaces.
     */
    private static void testCommandPreviewQuoting() {
        String command = (String) invokeStatic(type("WorkbenchCommandRunner"), "displayCommand",
                new Class<?>[] { List.class }, List.of("book", "render", "--title", "A B"));
        assertEquals("crtk book render --title \"A B\"", command, "quoted command preview");
    }

    /**
     * Verifies eval-bar score formatting and white-share mapping.
     */
    private static void testEvalBarMapping() {
        assertEquals("+0.42", formatCentipawns(42), "positive eval format");
        assertEquals("-0.42", formatCentipawns(-42), "negative eval format");
        assertClose(0.5, whiteShareForCentipawns(0), 0.0001, "neutral eval mapping");
        assertTrue(whiteShareForCentipawns(120) > 0.5, "positive eval mapping");
        assertTrue(whiteShareForCentipawns(-120) < 0.5, "negative eval mapping");
    }

    /**
     * Verifies eval-bar score changes use a bounded smooth transition.
     */
    private static void testEvalBarAnimation() {
        assertClose(0.0, evalEase(0.0), 0.0001, "eval ease start");
        assertClose(0.5, evalEase(0.5), 0.0001, "eval ease midpoint");
        assertClose(1.0, evalEase(1.0), 0.0001, "eval ease end");
        assertTrue(evalEase(0.75) > evalEase(0.25), "eval ease monotonic");

        Object bar = construct(type("WorkbenchEvalBar"), new Class<?>[0]);
        invoke(bar, "setCentipawns", new Class<?>[] { int.class }, 250);
        Timer timer = (Timer) field(bar, "timer");
        assertTrue(timer.isRunning(), "eval score animation starts");
        timer.stop();
    }

    /**
     * Verifies the pending engine state does not start a loading animation.
     */
    private static void testEvalBarThinkingIsStatic() {
        Object bar = construct(type("WorkbenchEvalBar"), new Class<?>[0]);
        invoke(bar, "setThinking", new Class<?>[0]);
        Timer timer = (Timer) field(bar, "timer");
        assertFalse(timer.isRunning(), "eval thinking timer stopped");
        assertEquals("", field(bar, "label"), "eval thinking label hidden");
    }

    /**
     * Verifies eval parsing from engine analyze output.
     */
    private static void testEngineEvalParsing() {
        Object cp = parseEngineEval("PV1\n  eval: +42\n");
        assertTrue(cp != null, "centipawn eval parsed");
        assertFalse(engineEvalMate(cp), "centipawn eval is not mate");
        assertEquals(Integer.valueOf(42), Integer.valueOf(engineEvalValue(cp)), "centipawn eval value");

        Object mate = parseEngineEval("PV1\n  eval: #-3\n");
        assertTrue(mate != null, "mate eval parsed");
        assertTrue(engineEvalMate(mate), "mate eval flag");
        assertEquals(Integer.valueOf(-3), Integer.valueOf(engineEvalValue(mate)), "mate eval value");
    }

    /**
     * Verifies streamed live-engine updates produce compact board status text.
     */
    private static void testLiveEngineStatusFormatting() {
        Output cp = new Output("info depth 12 multipv 1 score cp 42 nodes 100 pv e2e4 e7e5");
        String status = (String) invokeStatic(type("WorkbenchWindow"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, cp, Move.parse("e2e4"));
        assertEquals("live d12 +42 e2e4", status, "live centipawn status");

        Output mate = new Output("info depth 9 multipv 1 score mate -3 nodes 100 pv g1f3");
        String mateStatus = (String) invokeStatic(type("WorkbenchWindow"), "formatLiveEngineStatus",
                new Class<?>[] { Output.class, short.class }, mate, Move.parse("g1f3"));
        assertEquals("live d9 #-3 g1f3", mateStatus, "live mate status");
    }

    /**
     * Verifies live-engine numeric settings reject non-positive values.
     */
    private static void testOptionalPositiveIntegerParsing() {
        JTextField blank = new JTextField(" ");
        assertEquals(null, invokeStatic(type("WorkbenchWindow"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, blank, "--hash"), "blank optional integer");

        JTextField valid = new JTextField("128");
        assertEquals(Integer.valueOf(128), invokeStatic(type("WorkbenchWindow"), "optionalPositiveInteger",
                new Class<?>[] { JTextField.class, String.class }, valid, "--hash"), "valid optional integer");

        try {
            invokeStatic(type("WorkbenchWindow"), "optionalPositiveInteger",
                    new Class<?>[] { JTextField.class, String.class }, new JTextField("0"), "--hash");
            throw new AssertionError("zero optional integer rejected");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("positive integer"), "invalid optional integer message");
        }
    }

    /**
     * Verifies the analysis graph stores live samples and formats the latest eval.
     */
    private static void testAnalysisGraphStoresSamples() {
        Object graph = construct(type("WorkbenchAnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 nodes 2200 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        assertEquals(Integer.valueOf(2), invoke(graph, "sampleCount", new Class<?>[0]),
                "analysis graph sparse sample count");
        assertEquals("+0.42", invoke(graph, "latestEvalLabel", new Class<?>[0]),
                "analysis graph eval label");
    }

    /**
     * Verifies graph data can be exported for reports and downstream analysis.
     */
    private static void testAnalysisGraphExportsReportData() {
        Object graph = construct(type("WorkbenchAnalysisGraph"), new Class<?>[0]);
        invoke(graph, "resetForPosition", new Class<?>[] { String.class }, START_FEN);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 12 multipv 1 score cp 42 nodes 1000 nps 5000 time 90 pv e2e4 e7e5"),
                Move.parse("e2e4"));
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 13 multipv 1 score cp 15 nodes 2200 nps 7000 time 180 pv g1f3"),
                Move.parse("g1f3"));

        String csv = (String) invoke(graph, "csvText", new Class<?>[0]);
        assertTrue(csv.contains("index,eval_cp,eval,depth,nodes,nps,time_ms,best_move"),
                "analysis CSV header");
        assertTrue(csv.contains("g1f3"), "analysis CSV contains best move");

        String report = (String) invoke(graph, "reportText", new Class<?>[0]);
        assertTrue(report.contains("CRTK Workbench Analysis Report"), "analysis report title");
        assertTrue(report.contains("Samples: 2"), "analysis report sample count");
        assertTrue(report.contains("max 13"), "analysis report max depth");
    }

    /**
     * Verifies the graph paints a solid non-blank surface.
     */
    private static void testAnalysisGraphPaintsOpaqueSurface() {
        JComponent graph = (JComponent) construct(type("WorkbenchAnalysisGraph"), new Class<?>[0]);
        invoke(graph, "addSample", new Class<?>[] { boolean.class, Output.class, short.class },
                Boolean.TRUE,
                new Output("info depth 14 multipv 1 score cp -31 nodes 2000 nps 6500 pv g1f3"),
                Move.parse("g1f3"));
        assertPaintsOpaqueCorner(graph, 360, 260, "analysis graph opaque background");
    }

    /**
     * Verifies the chessboard does not show an instructional tooltip over play.
     */
    private static void testBoardHasNoInstructionTooltip() {
        JComponent board = (JComponent) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getToolTipText(), "board instruction tooltip removed");
    }

    /**
     * Verifies board-local keyboard piece selection is disabled.
     */
    private static void testBoardHasNoKeyboardPieceSelector() {
        JComponent board = (JComponent) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("LEFT")),
                "board left key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT")),
                "board right key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("UP")),
                "board up key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("DOWN")),
                "board down key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER")),
                "board enter key does not select pieces");
        assertEquals(null, board.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("SPACE")),
                "board space key does not select pieces");
    }

    /**
     * Verifies window-level arrow routing does not steal arrows from editors or
     * data controls.
     */
    private static void testWindowPositionNavigationRoutingSkipsTextAndDataControls() {
        assertTrue((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JButton("Run")),
                "button focus may route arrows to positions");
        assertFalse((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTextField()),
                "text fields keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JTable(1, 1)),
                "tables keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JList<>()),
                "lists keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JComboBox<>()),
                "combo boxes keep arrow navigation");
        assertFalse((Boolean) invokeStatic(type("WorkbenchWindow"), "shouldRoutePositionNavigation",
                new Class<?>[] { Component.class }, new JSpinner()),
                "spinners keep arrow navigation");
    }

    /**
     * Verifies right-button drag toggles a Lichess-style arrow.
     */
    private static void testBoardRightDragTogglesLichessArrowMarkup() {
        Component board = (Component) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right drag adds one arrow");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(markup, "from"), "arrow origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(markup, "to"), "arrow target");
        assertEquals("green", field(field(markup, "brush"), "name"), "default arrow brush");

        drawRightArrow(board, from, to, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right drag deletes arrow");
    }

    /**
     * Verifies right-clicking one square toggles a Lichess-style circle marker.
     */
    private static void testBoardRightClickTogglesLichessCircleMarkup() {
        Component board = (Component) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point square = boardPoint(Field.toIndex('d', '4'), true, 640, 640);

        rightClick(board, square, 0);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "right click adds one circle");
        Object markup = markups.get(0);
        assertEquals(Byte.valueOf(Field.toIndex('d', '4')), field(markup, "from"), "circle square");
        assertEquals(Byte.valueOf(Field.NO_SQUARE), field(markup, "to"), "circle has no target");

        rightClick(board, square, 0);
        assertEquals(Integer.valueOf(0), Integer.valueOf(markups.size()), "same right click deletes circle");
    }

    /**
     * Verifies drawing the same endpoints with a modifier replaces the color.
     */
    private static void testBoardRightDragReplacesMarkupColorLikeLichess() {
        Component board = (Component) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        Point from = boardPoint(Field.toIndex('b', '1'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('c', '3'), true, 640, 640);

        drawRightArrow(board, from, to, 0);
        drawRightArrow(board, from, to, MouseEvent.SHIFT_DOWN_MASK);
        List<?> markups = (List<?>) field(board, "boardMarkups");
        assertEquals(Integer.valueOf(1), Integer.valueOf(markups.size()), "same endpoints keep one markup");
        assertEquals("red", field(field(markups.get(0), "brush"), "name"),
                "shift right drag replaces with red brush");
    }

    /**
     * Verifies dragging a piece emits the legal move through the board callback.
     */
    private static void testBoardDragEmitsLegalMove() {
        Component board = (Component) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        List<Short> played = new ArrayList<>();
        invoke(board, "setMoveHandler", new Class<?>[] { type("WorkbenchBoardPanel$MoveHandler") },
                moveHandler(played));

        Point from = boardPoint(Field.toIndex('e', '2'), true, 640, 640);
        Point to = boardPoint(Field.toIndex('e', '4'), true, 640, 640);
        long now = System.currentTimeMillis();
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0));
        board.dispatchEvent(mouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1));

        assertEquals(Integer.valueOf(1), Integer.valueOf(played.size()), "dragged move count");
        assertEquals(Move.toString(Move.parse("e2e4")), Move.toString(played.get(0)), "dragged move");
    }

    /**
     * Verifies invalid drag hovers do not paint a red rejection box.
     */
    private static void testBoardDragInvalidHoverDoesNotPaintRedBox() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);

        byte from = Field.toIndex('e', '2');
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(from));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));
        setField(board, "draggingPiece", Boolean.TRUE);
        setField(board, "dragX", Integer.valueOf(24));
        setField(board, "dragY", Integer.valueOf(24));
        setField(board, "dragHoverSquare", Byte.valueOf(Field.NO_SQUARE));

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point hover = boardPoint(invalidHover, true, 640, 640);
        int sampleX = hover.x - cell / 2 + 2;
        int sampleY = hover.y - cell / 2 + 2;
        Color baseline = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        setField(board, "dragHoverSquare", Byte.valueOf(invalidHover));
        Color actual = new Color(paint(component, 640, 640).getRGB(sampleX, sampleY), true);

        assertColor(baseline, actual, "invalid drag hover leaves square unboxed");
    }

    /**
     * Verifies drag repaint bounds include an invalid hovered square so a stale
     * hover marker cannot be left behind after pointer movement.
     */
    private static void testBoardDragDirtyBoundsIncludesInvalidHoverSquare() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        byte invalidHover = Field.toIndex('f', '3');
        setField(board, "dragSquare", Byte.valueOf(Field.toIndex('e', '2')));
        setField(board, "draggedPiece", Byte.valueOf(Piece.WHITE_PAWN));

        Rectangle boardBounds = (Rectangle) invoke(board, "boardBounds", new Class<?>[0]);
        Rectangle dirty = (Rectangle) invoke(board, "dragRepaintBounds",
                new Class<?>[] { Rectangle.class, byte.class, byte.class, int.class, int.class, boolean.class },
                boardBounds, Field.NO_SQUARE, invalidHover, 12, 12, Boolean.FALSE);

        assertTrue(dirty != null && dirty.contains(boardPoint(invalidHover, true, 640, 640)),
                "drag dirty bounds include invalid hover square");
    }

    /**
     * Verifies setting a played move starts the Java2D glide animation.
     */
    private static void testBoardMoveAnimationStarts() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Position start = new Position(START_FEN);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e2e4");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "move animation active");
        assertEquals(Byte.valueOf(Field.toIndex('e', '2')), field(board, "animatedMoveFrom"), "animated origin");
        assertEquals(Byte.valueOf(Field.toIndex('e', '4')), field(board, "animatedMoveTo"), "animated target");
        double eased = (Double) invokeStatic(type("WorkbenchBoardPanel"), "easeOutCubic",
                new Class<?>[] { double.class }, 0.5d);
        assertTrue(eased > 0.5d && eased < 1.0d, "move animation ease-out curve");
    }

    /**
     * Verifies capture moves retain the victim piece for fade-out drawing.
     */
    private static void testBoardCaptureAnimationStarts() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Position start = new Position("8/8/8/3p4/4P3/8/8/4K2k w - - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e4d5");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertTrue((Boolean) field(board, "moveAnimationActive"), "capture animation active");
        assertEquals(Byte.valueOf(Field.toIndex('d', '5')), field(board, "animatedCaptureSquare"),
                "capture fade square");
        assertFalse(((Byte) field(board, "animatedCapturePiece")).byteValue() == Piece.EMPTY,
                "capture fade piece");
    }

    /**
     * Verifies castling starts a secondary rook glide animation.
     */
    private static void testBoardCastlingAnimationStarts() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Position start = new Position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);

        short move = Move.parse("e1g1");
        Position next = start.copy().play(move);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, next, move);

        assertEquals(Byte.valueOf(Field.toIndex('h', '1')), field(board, "animatedSecondaryMoveFrom"),
                "castle rook origin");
        assertEquals(Byte.valueOf(Field.toIndex('f', '1')), field(board, "animatedSecondaryMoveTo"),
                "castle rook target");
        assertFalse(((Byte) field(board, "animatedSecondaryMovePiece")).byteValue() == Piece.EMPTY,
                "castle rook animated piece");
    }

    /**
     * Verifies the board paints chessboard.js base colors.
     */
    private static void testBoardPaintUsesChessboardJsColors() {
        Component board = (Component) construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        board.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("8/8/8/8/8/8/8/4K2k w - - 0 1"), Move.NO_MOVE);
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        invoke(board, "highlightSquare", new Class<?>[] { byte.class, Color.class },
                Field.toIndex('e', '4'), moveHighlight);

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            board.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        assertColor(new Color(64, 64, 64),
                new Color(image.getRGB(boardX - 1, boardY - 1), true),
                "chessboard.js border");
        assertColor(new Color(240, 217, 181),
                new Color(image.getRGB(boardX + cell / 2, boardY + cell / 2), true),
                "chessboard.js light square");
        assertColor(new Color(181, 136, 99),
                new Color(image.getRGB(boardX + cell + cell / 2, boardY + cell / 2), true),
                "chessboard.js dark square");
        int e4x = boardX + 4 * cell;
        int e4y = boardY + 4 * cell;
        Color e4Center = new Color(image.getRGB(e4x + cell / 2, e4y + cell / 2), true);
        assertFalse(e4Center.equals(moveHighlight), "highlight does not fill square center");
        assertColor(moveHighlight, new Color(image.getRGB(e4x + 2, e4y + 2), true),
                "green inset move highlight");
    }

    /**
     * Verifies suggested best-move arrows are legal and do not leave square clutter.
     */
    private static void testBoardSuggestedMoveArrowIsLegalAndClean() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        short artificialLastMove = Move.parse("g1f3");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), artificialLastMove);

        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e5"));
        assertEquals(Short.valueOf(Move.NO_MOVE), field(board, "suggestedMove"),
                "illegal suggested move rejected");

        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        short best = Move.parse("e2e4");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, best);
        assertEquals(Short.valueOf(best), field(board, "suggestedMove"), "legal suggested move accepted");
        assertEquals(Byte.valueOf(Field.NO_SQUARE), field(board, "selectedSquare"),
                "best arrow clears selected square");

        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color arrowBody = new Color(image.getRGB(boardX + 4 * cell + cell / 2, boardY + 5 * cell + cell / 2), true);
        assertTrue(arrowBody.getBlue() > arrowBody.getRed(), "suggested move arrow is blue");
        Color moveHighlight = themeColor("BOARD_HIGHLIGHT");
        Color e4Edge = new Color(image.getRGB(boardX + 4 * cell + 2, boardY + 4 * cell + 2), true);
        assertFalse(e4Edge.equals(moveHighlight), "best arrow does not add square highlight");
        Color f3Edge = new Color(image.getRGB(boardX + 5 * cell + 2, boardY + 5 * cell + 2), true);
        assertFalse(f3Edge.equals(moveHighlight), "best arrow suppresses stale last-move highlight");
    }

    /**
     * Verifies legal destination previews can be disabled.
     */
    private static void testBoardLegalMovePreviewCanBeHidden() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        invoke(board, "selectSquare", new Class<?>[] { byte.class }, Field.toIndex('e', '2'));
        Color highlight = themeColor("BOARD_HIGHLIGHT");
        Color visibleEdge = boardSquareEdgeColor(component, Field.toIndex('e', '4'));
        assertFalse(visibleEdge.equals(highlight), "legal move preview avoids green square rectangles");
        Color visible = boardSquareCenterColor(component, Field.toIndex('e', '4'));

        invoke(board, "setShowLegalMovePreview", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLegalMovePreview", new Class<?>[0]),
                "legal move preview disabled");
        Color hidden = boardSquareCenterColor(component, Field.toIndex('e', '4'));
        assertColorDistanceAtLeast(visible, hidden, 10.0, "legal move preview hidden");
    }

    /**
     * Verifies last-move highlights and suggested arrows can be disabled.
     */
    private static void testBoardLastMoveAndBestArrowCanBeHidden() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Component component = (Component) board;
        component.setSize(640, 640);
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        Color highlight = themeColor("BOARD_HIGHLIGHT");
        assertColor(highlight, boardSquareEdgeColor(component, Field.toIndex('e', '2')),
                "last move highlight visible by default");

        invoke(board, "setShowLastMoveHighlight", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowLastMoveHighlight", new Class<?>[0]),
                "last move highlight disabled");
        assertFalse(boardSquareEdgeColor(component, Field.toIndex('e', '2')).equals(highlight),
                "last move highlight hidden");

        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position(START_FEN), Move.NO_MOVE);
        Point arrowSample = boardPoint(Field.toIndex('e', '3'), true, 640, 640);
        Color baseline = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        invoke(board, "setShowSuggestedMoveArrow", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowSuggestedMoveArrow", new Class<?>[0]),
                "best move arrow disabled");
        invoke(board, "setSuggestedMove", new Class<?>[] { short.class }, Move.parse("e2e4"));
        Color hiddenArrow = new Color(paint(component, 640, 640).getRGB(arrowSample.x, arrowSample.y), true);
        assertColor(baseline, hiddenArrow, "best move arrow hidden");
    }

    /**
     * Verifies notation and animations can be disabled.
     */
    private static void testBoardNotationAndAnimationsCanBeHidden() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        invoke(board, "setShowNotation", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isShowNotation", new Class<?>[0]), "board notation disabled");

        invoke(board, "setAnimationsEnabled", new Class<?>[] { boolean.class }, Boolean.FALSE);
        assertFalse((Boolean) invoke(board, "isAnimationsEnabled", new Class<?>[0]), "board animations disabled");
        Position start = new Position(START_FEN);
        short move = Move.parse("e2e4");
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start, Move.NO_MOVE);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class }, start.copy().play(move), move);
        assertFalse((Boolean) field(board, "moveAnimationActive"), "move animation suppressed");
    }

    /**
     * Verifies the board paints the checked-king marker.
     */
    private static void testBoardCheckHighlightPaintsCheckedKingMarker() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        invoke(board, "setPosition", new Class<?>[] { Position.class, short.class },
                new Position("4k3/8/8/8/8/8/8/K3R3 b - - 0 1"), Move.NO_MOVE);
        assertEquals(Byte.valueOf(Field.toIndex('e', '8')),
                invoke(board, "checkedKingSquare", new Class<?>[0]),
                "checked king square");

        Component component = (Component) board;
        component.setSize(640, 640);
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }

        int size = Math.min(640 - 64, 640 - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (640 - size) / 2;
        int boardY = (640 - size) / 2;
        Color marker = new Color(image.getRGB(boardX + 4 * cell + 5, boardY + 5), true);
        assertTrue(marker.getGreen() < 210 && marker.getBlue() < 180, "check marker red wash");
    }

    /**
     * Verifies the expensive board layer is cached by board size.
     */
    private static void testBoardTextureCachesRenderedLayer() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Object firstTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object secondTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 576);
        Object largerTexture = invoke(board, "boardTexture", new Class<?>[] { int.class }, 640);

        assertTrue(firstTexture == secondTexture, "same board texture cache reuse");
        assertFalse(firstTexture == largerTexture, "different board texture cache refresh");
    }

    /**
     * Verifies scaled SVG pieces are cached per board cell size.
     */
    private static void testBoardPieceImageCacheReusesScaledSvg() {
        Object board = construct(type("WorkbenchBoardPanel"), new Class<?>[0]);
        Object first = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object second = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 72);
        Object larger = invoke(board, "pieceImage", new Class<?>[] { byte.class, int.class },
                Piece.WHITE_KNIGHT, 80);

        assertTrue(first == second, "same cell piece cache reuse");
        assertFalse(first == larger, "different cell piece cache refresh");
    }

    /**
     * Creates a reflected board move handler.
     *
     * @param played captured moves
     * @return move handler proxy
     */
    private static Object moveHandler(List<Short> played) {
        Class<?> handlerType = type("WorkbenchBoardPanel$MoveHandler");
        return Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> {
                    if ("play".equals(method.getName())) {
                        played.add((Short) args[0]);
                    }
                    return null;
                });
    }

    /**
     * Finds the first button contained in a component tree.
     *
     * @param component root component
     * @return first button
     */
    private static JButton firstButton(Component component) {
        if (component instanceof JButton button) {
            return button;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return firstButton(child);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing button in " + component.getClass().getName());
    }

    /**
     * Paints a component into an ARGB image.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @return painted image
     */
    private static BufferedImage paint(Component component, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Paints a component and verifies the top-left pixel was cleared opaquely.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @param label assertion label
     */
    private static void assertPaintsOpaqueCorner(JComponent component, int width, int height, String label) {
        component.setSize(width, height);
        BufferedImage image = paint(component, width, height);
        int alpha = new Color(image.getRGB(0, 0), true).getAlpha();
        assertEquals(Integer.valueOf(255), Integer.valueOf(alpha), label);
    }

    /**
     * Creates a mouse event for board tests.
     *
     * @param board board component
     * @param id event id
     * @param when event timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @return mouse event
     */
    private static MouseEvent mouse(Component board, int id, long when, int x, int y, int clicks) {
        int modifiers = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Draws one right-button board arrow with optional modifier bits.
     *
     * @param board board component
     * @param from origin point
     * @param to target point
     * @param extraModifiers modifier bits
     */
    private static void drawRightArrow(Component board, Point from, Point to, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1, extraModifiers));
    }

    /**
     * Sends one right-click to a board square.
     *
     * @param board board component
     * @param point click point
     * @param extraModifiers modifier bits
     */
    private static void rightClick(Component board, Point point, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, point.x, point.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 1L, point.x, point.y, 1,
                extraModifiers));
    }

    /**
     * Creates a right-button mouse event.
     *
     * @param board board component
     * @param id event id
     * @param when timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @param extraModifiers modifier bits
     * @return mouse event
     */
    private static MouseEvent rightMouse(
            Component board, int id, long when, int x, int y, int clicks, int extraModifiers) {
        int modifiers = extraModifiers | (id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON3_DOWN_MASK);
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Returns the board-center point for one square under the tested board sizing.
     *
     * @param square square index
     * @param whiteDown true when White is down
     * @param width component width
     * @param height component height
     * @return square center point
     */
    private static Point boardPoint(byte square, boolean whiteDown, int width, int height) {
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (width - size) / 2;
        int boardY = (height - size) / 2;
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return new Point(boardX + col * cell + cell / 2, boardY + row * cell + cell / 2);
    }

    /**
     * Samples the top-left inset highlight pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    private static Color boardSquareEdgeColor(Component board, byte square) {
        int width = board.getWidth();
        int height = board.getHeight();
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point center = boardPoint(square, true, width, height);
        return new Color(paint(board, width, height).getRGB(center.x - cell / 2 + 2, center.y - cell / 2 + 2), true);
    }

    /**
     * Samples the center pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    private static Color boardSquareCenterColor(Component board, byte square) {
        Point center = boardPoint(square, true, board.getWidth(), board.getHeight());
        return new Color(paint(board, board.getWidth(), board.getHeight()).getRGB(center.x, center.y), true);
    }

    /**
     * Creates a command option model for a named template.
     *
     * @param name template name
     * @return populated option table model
     */
    private static Object optionsFor(String name) {
        Object template = template(name);
        Object options = construct(type("WorkbenchOptionTableModel"), new Class<?>[0]);
        invoke(options, "setOptions",
                new Class<?>[] { List.class, type("WorkbenchCommandTemplates$TemplateContext") },
                templateOptions(template), templateContext(START_FEN, "2s", "4", "3", "1"));
        return options;
    }

    /**
     * Finds a command template by display name.
     *
     * @param name template name
     * @return command template
     */
    private static Object template(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("WorkbenchCommandTemplates"),
                "commandModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object template = model.getElementAt(i);
            if (name.equals(invoke(template, "name", new Class<?>[0]))) {
                return template;
            }
        }
        throw new AssertionError("missing command template: " + name);
    }

    /**
     * Finds a batch task by display name.
     *
     * @param name task name
     * @return batch task
     */
    private static Object batchTask(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("WorkbenchCommandTemplates"),
                "batchModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object task = model.getElementAt(i);
            if (name.equals(invoke(task, "name", new Class<?>[0]))) {
                return task;
            }
        }
        throw new AssertionError("missing batch task: " + name);
    }

    /**
     * Finds a row by flag label.
     *
     * @param model option model
     * @param flag flag label
     * @return row index
     */
    private static int rowForFlag(Object model, String flag) {
        int rowCount = (Integer) invoke(model, "getRowCount", new Class<?>[0]);
        for (int row = 0; row < rowCount; row++) {
            if (flag.equals(invoke(model, "getValueAt", new Class<?>[] { int.class, int.class },
                    row, COL_FLAG))) {
                return row;
            }
        }
        throw new AssertionError("missing flag row: " + flag);
    }

    /**
     * Returns the current value for a command option flag.
     *
     * @param model option model
     * @param flag flag label
     * @return value
     */
    private static String optionValue(Object model, String flag) {
        return String.valueOf(invoke(model, "getValueAt",
                new Class<?>[] { int.class, int.class }, rowForFlag(model, flag), COL_VALUE));
    }

    /**
     * Returns whether an argument list contains a flag.
     *
     * @param args argument list
     * @param flag flag
     * @return true when present
     */
    private static boolean hasFlag(List<String> args, String flag) {
        return args.contains(flag);
    }

    /**
     * Returns command args enabled in an option model.
     *
     * @param options option model
     * @return enabled arguments
     */
    @SuppressWarnings("unchecked")
    private static List<String> enabledArgs(Object options) {
        return (List<String>) invoke(options, "enabledArgs", new Class<?>[0]);
    }

    /**
     * Reads a template's option metadata.
     *
     * @param template command template
     * @return option metadata
     */
    @SuppressWarnings("unchecked")
    private static List<Object> templateOptions(Object template) {
        return (List<Object>) invoke(template, "options", new Class<?>[0]);
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @return template context
     */
    private static Object templateContext(String fen, String duration, String depth, String multipv, String threads) {
        return templateContext(fen, duration, depth, multipv, threads, "config/default.engine.toml", "", "");
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @param protocolPath engine protocol path
     * @param nodes node budget
     * @param hash hash MB
     * @return template context
     */
    private static Object templateContext(String fen, String duration, String depth, String multipv, String threads,
            String protocolPath, String nodes, String hash) {
        return construct(type("WorkbenchCommandTemplates$TemplateContext"),
                new Class<?>[] { String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class },
                fen, duration, depth, multipv, threads, protocolPath, nodes, hash);
    }

    /**
     * Runs the workbench option-filter matcher.
     *
     * @param query query text
     * @param values searchable values
     * @return true when the query matches
     */
    private static boolean optionFilterMatches(String query, String... values) {
        return (Boolean) invokeStatic(type("WorkbenchWindow"), "optionFilterMatches",
                new Class<?>[] { String.class, String[].class }, query, values);
    }

    /**
     * Runs the workbench engine-output parser.
     *
     * @param output command output
     * @return parsed eval record
     */
    private static Object parseEngineEval(String output) {
        return invokeStatic(type("WorkbenchWindow"), "parseEngineEval", new Class<?>[] { String.class }, output);
    }

    /**
     * Reads the mate flag from a parsed eval.
     *
     * @param eval parsed eval
     * @return mate flag
     */
    private static boolean engineEvalMate(Object eval) {
        return (Boolean) invoke(eval, "mate", new Class<?>[0]);
    }

    /**
     * Reads the score from a parsed eval.
     *
     * @param eval parsed eval
     * @return score
     */
    private static int engineEvalValue(Object eval) {
        return (Integer) invoke(eval, "value", new Class<?>[0]);
    }

    /**
     * Formats a centipawn value through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return formatted score
     */
    private static String formatCentipawns(int centipawns) {
        return (String) invokeStatic(type("WorkbenchEvalBar"), "formatCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Maps centipawns to white's bar share through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return white share
     */
    private static double whiteShareForCentipawns(int centipawns) {
        return (Double) invokeStatic(type("WorkbenchEvalBar"), "whiteShareForCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Eases animation progress through the workbench eval bar.
     *
     * @param progress linear progress
     * @return eased progress
     */
    private static double evalEase(double progress) {
        return (Double) invokeStatic(type("WorkbenchEvalBar"), "easeInOutCubic",
                new Class<?>[] { double.class }, progress);
    }

    /**
     * Reads one color token from the workbench theme.
     *
     * @param name field name
     * @return color token
     */
    private static Color themeColor(String name) {
        return (Color) staticField(type("WorkbenchTheme"), name);
    }

    /**
     * Loads a workbench implementation class.
     *
     * @param name simple or nested class name
     * @return class
     */
    private static Class<?> type(String name) {
        try {
            return Class.forName(WORKBENCH_PACKAGE + name);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("missing workbench type " + name, ex);
        }
    }

    /**
     * Constructs a package-private workbench type.
     *
     * @param type type
     * @param parameterTypes constructor parameter types
     * @param args constructor arguments
     * @return instance
     */
    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not construct " + type.getName(), ex);
        }
    }

    /**
     * Invokes a package-private static method.
     *
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeStatic(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(null, owner, name, parameterTypes, args);
    }

    /**
     * Invokes a package-private instance method.
     *
     * @param target target instance
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(target, target.getClass(), name, parameterTypes, args);
    }

    /**
     * Reads a private field from a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @return field value
     */
    private static Object field(Object target, String name) {
        try {
            java.lang.reflect.Field reflectedField = target.getClass().getDeclaredField(name);
            reflectedField.setAccessible(true);
            return reflectedField.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Writes a private field on a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @param value replacement value
     */
    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field reflectedField = target.getClass().getDeclaredField(name);
            reflectedField.setAccessible(true);
            reflectedField.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not write " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Reads a static field from a package-private workbench class.
     *
     * @param owner declaring type
     * @param name field name
     * @return field value
     */
    private static Object staticField(Class<?> owner, String name) {
        try {
            java.lang.reflect.Field reflectedField = owner.getDeclaredField(name);
            reflectedField.setAccessible(true);
            return reflectedField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Invokes a reflected method.
     *
     * @param target target instance or null for static methods
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    private static Object invokeMethod(
            Object target,
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not invoke " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Preserves the original failure thrown by a reflected method.
     *
     * @param ex invocation wrapper
     */
    private static void rethrowCause(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
    }

    /**
     * Verifies a condition is true.
     *
     * @param condition condition
     * @param label assertion label
     */
    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }

    /**
     * Verifies a condition is false.
     *
     * @param condition condition
     * @param label assertion label
     */
    private static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + ": expected false");
        }
    }

    /**
     * Verifies floating-point closeness.
     *
     * @param expected expected value
     * @param actual actual value
     * @param tolerance accepted absolute tolerance
     * @param label assertion label
     */
    private static void assertClose(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies a theme foreground/background pair has sufficient contrast.
     *
     * @param label assertion label
     * @param foregroundName foreground token name
     * @param backgroundName background token name
     * @param minimumRatio minimum accepted contrast ratio
     */
    private static void assertThemeContrast(String label, String foregroundName, String backgroundName,
            double minimumRatio) {
        Color foreground = themeColor(foregroundName);
        Color background = themeColor(backgroundName);
        double ratio = contrastRatio(foreground, background);
        if (ratio < minimumRatio) {
            throw new AssertionError(label + ": contrast " + ratio + " below " + minimumRatio
                    + " for " + foregroundName + " on " + backgroundName);
        }
    }

    /**
     * Calculates WCAG contrast ratio for two opaque colors.
     *
     * @param first first color
     * @param second second color
     * @return contrast ratio
     */
    private static double contrastRatio(Color first, Color second) {
        double firstLum = relativeLuminance(first);
        double secondLum = relativeLuminance(second);
        double light = Math.max(firstLum, secondLum);
        double dark = Math.min(firstLum, secondLum);
        return (light + 0.05) / (dark + 0.05);
    }

    /**
     * Calculates relative luminance for an sRGB color.
     *
     * @param color color
     * @return relative luminance
     */
    private static double relativeLuminance(Color color) {
        return 0.2126 * linearChannel(color.getRed())
                + 0.7152 * linearChannel(color.getGreen())
                + 0.0722 * linearChannel(color.getBlue());
    }

    /**
     * Converts one sRGB channel to linear light.
     *
     * @param channel 0..255 channel value
     * @return linearized channel
     */
    private static double linearChannel(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    /**
     * Verifies two colors are visually separated enough for a non-text mark.
     *
     * @param first first color
     * @param second second color
     * @param minimum minimum Euclidean RGB distance
     * @param label assertion label
     */
    private static void assertColorDistanceAtLeast(Color first, Color second, double minimum, String label) {
        double distance = colorDistance(first, second);
        if (distance < minimum) {
            throw new AssertionError(label + ": distance " + distance + " below " + minimum);
        }
    }

    /**
     * Calculates Euclidean RGB distance.
     *
     * @param first first color
     * @param second second color
     * @return RGB distance
     */
    private static double colorDistance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    /**
     * Verifies exact RGB color equality.
     *
     * @param expected expected color
     * @param actual actual color
     * @param label assertion label
     */
    private static void assertColor(Color expected, Color actual, String label) {
        if (expected.getRGB() != actual.getRGB()) {
            throw new AssertionError(label + ": expected " + colorText(expected) + ", got " + colorText(actual));
        }
    }

    /**
     * Returns a compact RGB color label.
     *
     * @param color color
     * @return RGB label
     */
    private static String colorText(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Verifies object equality.
     *
     * @param expected expected value
     * @param actual actual value
     * @param label assertion label
     */
    private static void assertEquals(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
