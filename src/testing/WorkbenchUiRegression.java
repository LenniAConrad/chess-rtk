package testing;

import application.cli.PathOps;
import static testing.TestSupport.readUtf8;
import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeSelectionModel;

import application.gui.workbench.board.AnnotationGlyphs;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.board.BoardExporter;
import application.gui.workbench.board.BoardMarkup;
import application.gui.workbench.board.BoardMarkupTool;
import application.gui.workbench.draw.DrawPanel;
import application.gui.workbench.layout.LazyPanel;
import application.gui.workbench.layout.FlatTabbedPaneUI;
import application.gui.workbench.layout.EditorLayoutCommands;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.command.CommandForm;
import application.gui.workbench.command.Console;
import application.gui.workbench.command.CommandTemplates;
import application.gui.workbench.dataset.DatasetChart;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.FieldValidator;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SettingsChipRow;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SwitchedWorkspace;
import application.gui.workbench.ui.TagCloud;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import application.gui.workbench.ui.WorkspaceMode;
import application.gui.workbench.window.LayoutMenu;
import chess.core.Field;
import chess.core.Piece;
import chess.images.assets.PieceSet;
import chess.images.assets.Shapes;
import application.gui.workbench.window.SettingsMenu;
import chess.core.Move;
import chess.core.Position;


/**
 * Theme, control, editor-shell, and layout regression checks.
 */
final class WorkbenchUiRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchUiRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testTextPlaceholdersDoNotSetValues();
        testPaletteTokenMatching();
        testCommandPaletteIsInFrameOverlay();
        testCommandPaletteGroupsActionsByCategory();
        testWorkbenchDropFileFilterAcceptsOnlyFenPgnTxt();
        testOptionFilterTokenMatching();
        testTextAreaScrollPaintsOpaque();
        testScrollPaneUsesSolidCorners();
        testWorkbenchRawScrollPanesUseSharedStyling();
        testPopupMenusUseSharedStyling();
        testPopupMenuItemTextRendersWhenNotHovered();
        testWorkbenchRawPopupMenusUseSharedStyling();
        testWorkbenchRawStandardControlsUseSharedStyling();
        testViewportFillWrapperTracksAvailableHeight();
        testCenteredViewportAvoidsHorizontalScrolling();
        testDataSurfacesUseSolidBackgrounds();
        testTagCloudGroupsAndWrapsTags();
        testCustomPaintedSurfacesClearBackground();
        testBooleanTableRendererIsStyled();
        testComponentTreeStylingCoversPlainControls();
        testPlainCheckboxUsesWorkbenchGlyph();
        testProgressBarUsesWorkbenchChrome();
        testDesignTokenLayerExposesWorkbenchScale();
        testThemeCachesSharedFonts();
        testSharedUiPrimitivesAreAvailable();
        testSegmentedSwitcherHandlesEmptyLabels();
        testSegmentedSwitcherPaintsToPreferredWidth();
        testChipGroupHandlesEmptyLabels();
        testButtonVariantsExposeActionHierarchy();
        testIconOnlyButtonsRequireTooltipText();
        testStatusBadgeVariantsAreAvailable();
        testWorkspaceHeaderPrimitiveAndShellRegistration();
        testDisabledComboUsesThemeBackground();
        testEnabledComboFillsArrowGutter();
        testComboPopupUsesSharedScrollStyling();
        testSharedTreeStyling();
        testSpinnerEditorUsesReadableInputColors();
        testStyledSpinnerCommitsValidEdits();
        testSpinnerFillsArrowGutter();
        testGameLineImportInputKeepsMultilineHeight();
        testSettingsToggleRowsAreReadable();
        testSettingsSliderRefreshKeepsReadableColors();
        testSettingsMenuExposesThemeModes();
        testDisplaySettingsUseLichessStyleMenuGroups();
        testSettingsTextStaysReadableInBothModes();
        testLayoutMenuExposesUsefulWorkbenchControls();
        testToggleSwitchAnimatesStateChanges();
        testStatusBadgeAnimatesStateChanges();
        testStatusBadgeCanReserveStableTextWidth();
        testSegmentedSwitcherAnimatesSelection();
        testSwitchedWorkspaceRejectsMismatchedModes();
        testSwitchedWorkspaceRejectsInvalidEagerMode();
        testSwitchedWorkspaceUsesModeDescriptors();
        testSwitchedWorkspaceCardHostClearsOldFrames();
        testSplitPaneSashAnimatesHover();
        testSplitPanesClearOldFrames();
        testWorkbenchSplitPanesUseSharedStyler();
        testPlayTabUsesCompactMoveHistory();
        testChartsRevealNewData();
        testCommandFormLeadColumnAligns();
        testCommandFormFlagsWrapAcrossColumns();
        testCommandFormFlagsDisclosureIsBounded();
        testThemeColorContrast();
        testThemeUsesVscodeVisualStudioColorTokens();
        testNetworkPaletteUsesSemanticFocusColor();
        testNetworkArchitectureBlocksKeepReadableNeutralFill();
        testNnueAtlasPlaneLabelsUseUniformColor();
        testHorizontalMetricBarKeepsLabelLaneClear();
        testBoardMarkupBrushesUseFixedDistinctColors();
        testDrawPanelBuildsAnnotationControls();
        testDrawModeLeftDragAddsAnnotation();
        testDrawGlyphEvaluationAnnotationsReplaceSiblings();
        testDrawGlyphResultAnnotationsReplaceSiblings();
        testThemeRefreshPreservesLabelRoles();
        testThemeRefreshUpdatesLineBorders();
        testThemeRefreshRestoresCustomControlUis();
        testThemeInstallSetsTooltipColors();
        testThemeUsesDeliberateFontStacks();
        testFileChooserIconsUseThemePalette();
        testToastUsesBottomRightPlacement();
        testToastFadeAppliesToTextAndChromeTogether();
        testCollapsibleInfoSectionTogglesContent();
        testBoundedCollapsibleSectionScrollsInternally();
        testCollapsibleSectionDefersNestedScrollbarsDuringAnimation();
        testPuzzleHeaderControlsAvoidClippingAtNarrowWidth();
        testLazyPanelDefersConstruction();
        testCommandTabsReserveSelectedTextWidth();
        testTabbedPaneUsesScrollableSingleRowTabs();
        testTabbedPaneSwitchesWithoutSnapshotOverlay();
        testTabbedPaneRolloverIgnoresEmptyPanes();
        testConsoleAndLogsAreMovableWorkbenchTabs();
        testPieceSetsRenderDistinctly();
        testConsoleStatusLinesRenderBadges();
        testEvalBarDrawsScoreLabel();
        testNumericFieldValidatorFlagsBadInput();
        WorkbenchUiEditorRegression.run();
    }

    /**
     * Verifies that {@link FieldValidator} flags non-numeric input on a styled
     * field — switching it to the error border and an explanatory tooltip — and
     * clears the warning once a usable value (or blank default) is restored.
     */
    private static void testNumericFieldValidatorFlagsBadInput() {
        Theme.setMode(Theme.Mode.LIGHT);
        JTextField field = new JTextField("3000");
        field.setToolTipText("Nodes per move");
        Theme.field(field);
        FieldValidator validator = FieldValidator.attach(field,
                FieldValidator.wholeNumber(1, Long.MAX_VALUE, true));
        assertTrue(validator.valid(), "whole-number field accepts plain digits");
        assertEquals("Nodes per move", field.getToolTipText(),
                "valid field keeps its resting tooltip");

        field.setText("160numbererrr");
        assertFalse(validator.valid(), "letters in a number field are rejected");
        assertTrue(validator.problem() != null && !validator.problem().isBlank(),
                "invalid field reports a reason");
        assertEquals(validator.problem(), field.getToolTipText(),
                "invalid field explains itself through its tooltip");
        assertColor(themeColor("STATUS_ERROR_BORDER"), inputBorderLineColor(field),
                "invalid field paints the error border colour");

        field.setText("");
        assertTrue(validator.valid(), "blank is accepted as use-the-default");
        assertEquals("Nodes per move", field.getToolTipText(),
                "cleared field restores its resting tooltip");

        field.setText("0");
        assertFalse(validator.valid(), "a value below the minimum is rejected");

        field.setText("-5");
        assertFalse(validator.valid(), "a negative value in a non-negative field is rejected");

        field.setText("99999999999999999999");
        assertFalse(validator.valid(), "a value too large for a long is rejected");

        JTextField grouped = new JTextField("50,000");
        Theme.field(grouped);
        FieldValidator groupedValidator = FieldValidator.attach(grouped,
                FieldValidator.groupedWholeNumber(100, 1_000_000, true));
        assertTrue(groupedValidator.valid(), "grouped digits with separators are accepted");
        grouped.setText("5");
        assertFalse(groupedValidator.valid(), "an out-of-range grouped number is rejected");

        JTextField disabled = new JTextField("nonsense");
        Theme.field(disabled);
        disabled.setEnabled(false);
        FieldValidator disabledValidator = FieldValidator.attach(disabled,
                FieldValidator.wholeNumber(1, Long.MAX_VALUE, false));
        assertTrue(disabledValidator.valid(), "a disabled field is never flagged");

        JTextField duration = new JTextField("1s");
        Theme.field(duration);
        FieldValidator durationValidator = FieldValidator.attach(duration,
                FieldValidator.numberWithOptionalUnit(true, "ms", "s", "m", "h"));
        assertTrue(durationValidator.valid(), "a duration with a unit suffix is accepted");
        duration.setText("500ms");
        assertTrue(durationValidator.valid(), "the longer 'ms' suffix wins over 'm'");
        duration.setText("200");
        assertTrue(durationValidator.valid(), "a plain number with no unit is accepted");
        duration.setText("200msx");
        assertFalse(durationValidator.valid(), "trailing junk after a unit is rejected");
        duration.setText("");
        assertTrue(durationValidator.valid(), "a blank unit field falls back to its default");
    }

    /**
     * Reads the painted outline colour of a styled input control's border.
     *
     * @param component styled input control
     * @return rounded-input border line colour
     */
    private static Color inputBorderLineColor(JComponent component) {
        javax.swing.border.Border border = component.getBorder();
        if (border instanceof javax.swing.border.CompoundBorder compound) {
            border = compound.getOutsideBorder();
        }
        try {
            java.lang.reflect.Field line = border.getClass().getDeclaredField("line");
            line.setAccessible(true);
            return (Color) line.get(border);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("cannot read input border colour", ex);
        }
    }

    /**
     * Verifies the foundation token layer exposes the requested compact
     * Workbench scale.
     */
    private static void testDesignTokenLayerExposesWorkbenchScale() {
        assertBetween((Integer) staticField(Theme.class, "FONT_PAGE_TITLE"), 18, 22,
                "page title token");
        assertBetween((Integer) staticField(Theme.class, "FONT_SECTION_TITLE"), 13, 14,
                "section title token");
        assertBetween((Integer) staticField(Theme.class, "FONT_CONTROL"), 12, 13,
                "control text token");
        assertBetween((Integer) staticField(Theme.class, "FONT_DENSE_TABLE"), 12, 13,
                "dense table token");
        assertBetween((Integer) staticField(Theme.class, "FONT_METADATA"), 11, 11,
                "metadata token");
        assertBetween((Integer) staticField(Theme.class, "FONT_MONO"), 12, 13,
                "monospace token");
        assertTrue((Integer) staticField(Theme.class, "RADIUS") <= 8,
                "controls use compact radius");
        assertTrue((Integer) staticField(Theme.class, "Z_BASE")
                < (Integer) staticField(Theme.class, "Z_FLOATING"), "z-index base below floating");
        assertTrue((Integer) staticField(Theme.class, "Z_MODAL")
                < (Integer) staticField(Theme.class, "Z_TOAST"), "z-index modal below toast");
    }

    /**
     * Verifies paint-heavy font factories reuse immutable font instances and
     * refresh them when density changes.
     */
    private static void testThemeCachesSharedFonts() {
        Theme.setDensity(Theme.Density.DENSE);
        Font first = Theme.font(Theme.FONT_BODY, Font.BOLD);
        Font second = Theme.font(Theme.FONT_BODY, Font.BOLD);
        assertTrue(first == second, "UI font factory reuses cached font instances");
        assertTrue(Theme.mono(Theme.FONT_MONO) == Theme.mono(Theme.FONT_MONO),
                "mono font factory reuses cached font instances");
        assertTrue(Theme.consoleMono(Theme.FONT_MONO) == Theme.consoleMono(Theme.FONT_MONO),
                "console font factory reuses cached font instances");

        Theme.setDensity(Theme.Density.COMFORTABLE);
        Font comfortable = Theme.font(Theme.FONT_BODY, Font.BOLD);
        assertFalse(first == comfortable, "density change clears cached UI fonts");
        Theme.setDensity(Theme.Density.DENSE);
    }

    /**
     * Verifies named primitives route through the shared UI package.
     */
    private static void testSharedUiPrimitivesAreAvailable() {
        assertEquals("SegmentedSwitcher",
                Ui.segmentedControl("One", "Two").getClass().getSimpleName(),
                "segmented control primitive");
        assertEquals("CommandBlock", Ui.commandBlock("crtk engine bestmove").getClass().getSimpleName(),
                "command block primitive");
        assertEquals("FieldRow", Ui.fieldRow("FEN", new JTextField(), 72).getClass().getSimpleName(),
                "field row primitive");
        assertEquals("SurfacePanel", Ui.panel(new java.awt.BorderLayout()).getClass().getSimpleName(),
                "panel primitive");
        assertEquals("EmptyState", Ui.emptyState("No runs", "Run a command").getClass().getSimpleName(),
                "empty state primitive");
        assertEquals("SectionHeader", Ui.sectionHeader("Engine", "Ready", null).getClass().getSimpleName(),
                "section header primitive");
        assertEquals("WorkspaceHeader",
                Ui.workspaceHeader("Run", "Current FEN", new JPanel()).getClass().getSimpleName(),
                "workspace header primitive");
        assertEquals(JTabbedPane.class, Ui.tabbedPane().getClass(), "tabs primitive stays standard Swing pane");
        assertEquals("Card", Ui.card("Card", new JPanel()).getClass().getSimpleName(), "card primitive");
        String themeColors = readSource(Path.of("src/application/gui/workbench/ui/ThemeColors.java"));
        String backdrop = readSource(Path.of("src/application/gui/workbench/ui/BackdropPanel.java"));
        assertFalse(themeColors.contains("GradientPaint"),
                "shared card chrome uses a flat fill instead of a gradient");
        assertFalse(backdrop.contains("GradientPaint"),
                "root Workbench backdrop uses a flat fill instead of a gradient");
        JComponent card = Ui.card("Command", new JPanel());
        card.setSize(240, 120);
        card.doLayout();
        BufferedImage image = paint(card, 240, 120);
        int upper = image.getRGB(120, 20);
        int middle = image.getRGB(120, 60);
        int lower = image.getRGB(120, 100);
        assertEquals(Integer.valueOf(upper), Integer.valueOf(middle),
                "shared card interior is vertically flat at the middle");
        assertEquals(Integer.valueOf(upper), Integer.valueOf(lower),
                "shared card interior is vertically flat at the bottom");
    }

    /**
     * Verifies the segmented selector behaves predictably when a caller has no
     * labels yet.
     */
    private static void testSegmentedSwitcherHandlesEmptyLabels() {
        SegmentedSwitcher direct = new SegmentedSwitcher(null);
        assertEquals(Integer.valueOf(-1), Integer.valueOf(direct.getSelectedIndex()),
                "null-labelled segmented switcher has no selection");
        direct.setSelectedIndex(0);
        assertEquals(Integer.valueOf(-1), Integer.valueOf(direct.getSelectedIndex()),
                "empty segmented switcher ignores impossible selection");

        SegmentedSwitcher fromFactory = Ui.segmentedControl((String[]) null);
        assertEquals(Integer.valueOf(-1), Integer.valueOf(fromFactory.getSelectedIndex()),
                "factory null labels have no selected segment");
        paint(fromFactory, Math.max(1, fromFactory.getPreferredSize().width),
                fromFactory.getPreferredSize().height);
    }

    /**
     * Verifies the segmented switcher measures with the same geometry it paints,
     * avoiding a blank trailing lane to the right of the final segment.
     */
    private static void testSegmentedSwitcherPaintsToPreferredWidth() {
        SegmentedSwitcher switcher = Ui.segmentedControl("Network", "Search", "Gauntlet");
        Dimension size = switcher.getPreferredSize();
        switcher.setSize(size);
        BufferedImage image = paint(switcher, size.width, size.height);
        int rightEdgeAlpha = new Color(image.getRGB(size.width - 1, size.height / 2), true).getAlpha();
        assertTrue(rightEdgeAlpha > 0, "segmented switcher paints through its preferred right edge");
    }

    /**
     * Verifies an empty chip set is inert rather than an array-bounds crash.
     */
    private static void testChipGroupHandlesEmptyLabels() {
        ChipGroup empty = new ChipGroup(List.of());
        assertEquals(Integer.valueOf(-1), Integer.valueOf(empty.getSelectedIndex()),
                "empty chip group has no selection");
        empty.setSelectedIndex(0);
        assertEquals(Integer.valueOf(-1), Integer.valueOf(empty.getSelectedIndex()),
                "empty chip group ignores impossible selection");
        paint(empty, Math.max(1, empty.getPreferredSize().width), empty.getPreferredSize().height);
    }

    /**
     * Verifies the workbench shell has one shared workspace-header primitive and
     * that major routes register through it.
     */
    private static void testWorkspaceHeaderPrimitiveAndShellRegistration() {
        WorkspaceHeader header = new WorkspaceHeader("Board / Analyze",
                "Black to move · 28 legal moves", new JPanel());
        assertEquals("Board / Analyze", header.title(), "workspace header title");
        assertEquals("Black to move · 28 legal moves", header.context(), "workspace header context");
        String lifecycle = readSource(Path.of("src/application/gui/workbench/window/WindowLifecycle.java"));
        String boardLayer = readSource(Path.of("src/application/gui/workbench/window/WindowBoardLayer.java"));
        String commandLayer = readSource(Path.of("src/application/gui/workbench/window/WindowCommandLayer.java"));
        String lazyPanel = readSource(Path.of("src/application/gui/workbench/layout/LazyPanel.java"));
        String shellFrame = readSource(Path.of("src/application/gui/workbench/window/ShellFrame.java"));
        assertTrue(lifecycle.contains("new RegisteredView(\"Dashboard\", dashboardPanel)"),
                "Dashboard remains a major shell route");
        assertTrue(lifecycle.contains("new RegisteredView(\"Engine Lab\""),
                "Engine route uses the Engine Lab UI label");
        assertTrue(lifecycle.contains("refreshGlobalJobStatus"),
                "global job status is wired into the shell");
        assertTrue(boardLayer.contains("new SwitchedWorkspace(\"Board\""),
                "Board workspace uses a titled shell header");
        assertTrue(boardLayer.contains("new SwitchedWorkspace(\"Engine Lab\""),
                "Engine Lab workspace uses a titled shell header");
        assertTrue(commandLayer.contains("runHeader = new WorkspaceHeader"),
                "Run surface owns a workspace header");
        assertTrue(commandLayer.contains("createRunSettingsColumn()"),
                "Run builder has a dedicated settings column");
        assertTrue(commandLayer.contains("createRunOutputColumn()"),
                "Run builder has a dedicated preview/output column");
        assertTrue(commandLayer.contains("Command Preview"),
                "Run builder exposes a readable command preview card");
        assertTrue(commandLayer.contains("Raw Output / Log"),
                "Run builder keeps raw command output accessible");
        assertTrue(lazyPanel.contains("Ui.emptyState(\"Loading \" + name"),
                "lazy shell placeholders use the shared empty state");
        assertFalse(shellFrame.contains("ActivityRail"),
                "right inspector does not expose a non-functional icon rail");
        assertFalse(shellFrame.contains("inspectorTabs()"),
                "right inspector does not expose non-functional tab labels");
        assertFalse(shellFrame.contains("Inspector views"),
                "right inspector has no decorative rail tooltip");
        assertTrue(shellFrame.contains("content.add(sectionLabel(\"INSPECTOR\"));"),
                "right inspector keeps a clear single-pane heading");
        assertTrue(shellFrame.contains("inspectorFields.add(sectionLabel(\"OPENING\"));"),
                "right inspector keeps sectioned opening details");
        assertTrue(shellFrame.contains("private static final int ACTIVITY_BAR_WIDTH = 48"),
                "left shell uses a VS Code-width activity bar");
        assertTrue(shellFrame.contains("private JComponent buildSidebar()"),
                "left shell separates the activity bar from the Explorer sidebar");
        assertTrue(shellFrame.contains("private JComponent buildActivityBar()"),
                "left shell exposes a real activity bar");
        assertTrue(shellFrame.contains("activityButton(\"Explorer\", WindowBase.TAB_DASHBOARD"),
                "activity bar exposes an Explorer route");
        assertTrue(shellFrame.contains("activityButton(\"Run\", WindowBase.TAB_RUN"),
                "activity bar exposes a Run route");
        assertTrue(shellFrame.contains("activityButton(\"Studies\", WindowBase.TAB_STUDIES"),
                "activity bar exposes a Studies route");
        assertTrue(shellFrame.contains("activityButton(\"Logs\", WindowBase.TAB_LOGS"),
                "activity bar exposes a Logs route");
        assertTrue(shellFrame.contains("content.add(sectionLabel(\"EXPLORER\"));"),
                "left sidebar uses the VS Code Explorer container label");
        assertTrue(shellFrame.contains("workbench.activity"),
                "activity bar controls expose stable action identifiers");
        assertTrue(shellFrame.contains("private static final class ActivityButton"),
                "activity bar uses a shared custom-painted button primitive");
        assertTrue(shellFrame.contains("navRow(\"My Studies\", WindowBase.TAB_STUDIES"),
                "left navigator exposes a Study entry");
        assertTrue(shellFrame.contains("this::studyCount, false, this::openStudyLibrary"),
                "Study navigator entry uses the local study repository action");
        assertTrue(lifecycle.contains("() -> openBoard(BOARD_STUDY)"),
                "Study navigator action opens the Board surface in Study mode");
        assertTrue(shellFrame.contains("navigatorSection(\"LIBRARY\", true"),
                "left navigator groups core routes in an expandable Library section");
        assertTrue(shellFrame.contains("navigatorSection(\"STUDIES\", true"),
                "left navigator keeps Studies as a first-class expandable section");
        assertTrue(shellFrame.contains("navigatorSection(\"PUBLICATIONS\", false"),
                "left navigator can keep lower-priority route groups collapsed by default");
        assertTrue(shellFrame.contains("private static final class NavigatorSection"),
                "left navigator owns a reusable collapsible section primitive");
        assertTrue(shellFrame.contains("private static final class NavigatorSectionHeader"),
                "left navigator section headers expose a focused toggle control");
        assertTrue(occurrences(shellFrame, "public AccessibleContext getAccessibleContext()") >= 3,
                "custom-painted shell controls create accessible contexts before metadata is written");
        assertTrue(shellFrame.contains("putClientProperty(\"workbench.actionId\", getName())"),
                "navigator controls expose stable action identifiers");
        assertTrue(shellFrame.contains("setRunDockCollapsed(!runDockCollapsed)"),
                "bottom Run Center exposes a collapse/expand action");
        assertTrue(shellFrame.contains("workbench.runDock.toggle"),
                "Run Center toggle has a stable component/action id");
        assertTrue(shellFrame.contains("runDockBody.setVisible(!collapsed)"),
                "Run Center collapse hides the dock body instead of leaving dead space");
    }

    /**
     * Verifies action buttons carry explicit hierarchy variants.
     */
    private static void testButtonVariantsExposeActionHierarchy() {
        JButton primary = Ui.button("Run", Theme.ButtonVariant.PRIMARY, event -> {
            // no-op test listener
        });
        JButton secondary = Ui.button("Copy", Theme.ButtonVariant.SECONDARY, event -> {
            // no-op test listener
        });
        JButton ghost = Ui.ghostButton("Details", event -> {
            // no-op test listener
        });
        JButton destructive = Ui.destructiveButton("Stop", event -> {
            // no-op test listener
        });
        JButton clear = Ui.button("Clear", false, event -> {
            // no-op test listener
        });
        JButton clearFlags = Ui.button("Clear Flags", false, event -> {
            // no-op test listener
        });
        JButton resign = Ui.button("Resign", false, event -> {
            // no-op test listener
        });
        JButton rawStop = new JButton("Stop");
        Theme.button(rawStop, false);
        HoldButton holdResign = new HoldButton("Resign", () -> {
            // no-op test listener
        });
        assertEquals(Theme.ButtonVariant.PRIMARY,
                primary.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "primary variant");
        assertEquals(Theme.ButtonVariant.SECONDARY,
                secondary.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "secondary variant");
        assertEquals(Theme.ButtonVariant.GHOST,
                ghost.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "ghost variant");
        assertEquals(Theme.ButtonVariant.DESTRUCTIVE,
                destructive.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "destructive variant");
        assertEquals(Theme.ButtonVariant.DESTRUCTIVE,
                clear.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "clear uses destructive variant");
        assertEquals(Theme.ButtonVariant.DESTRUCTIVE,
                clearFlags.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "clear-prefix action uses destructive variant");
        assertEquals(Theme.ButtonVariant.DESTRUCTIVE,
                resign.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "resign uses destructive variant");
        assertEquals(Theme.ButtonVariant.DESTRUCTIVE,
                rawStop.getClientProperty(Theme.CLIENT_BUTTON_VARIANT), "raw styled stop uses destructive variant");
        assertEquals("DESTRUCTIVE",
                String.valueOf(clear.getClientProperty(Theme.CLIENT_ICON_KIND)),
                "clear uses destructive icon lane");
        assertTrue(Theme.destructiveActionLabel("Delete selected"),
                "delete action labels are destructive");
        assertTrue(Theme.destructiveActionLabel("Cancel Scan"),
                "cancel scan action labels are destructive");
        assertTrue(((Boolean) field(holdResign, "danger")).booleanValue(),
                "hold resign auto-uses destructive styling");
    }

    /**
     * Verifies icon-only controls always expose tooltip/accessibility text.
     */
    private static void testIconOnlyButtonsRequireTooltipText() {
        JButton button = Ui.iconButton("Back", event -> {
            // no-op test listener
        });
        assertEquals("", button.getText(), "icon button hides visible text");
        assertEquals("Back", button.getToolTipText(), "icon button tooltip");
        assertEquals("Back", button.getAccessibleContext().getAccessibleName(),
                "icon button accessible name");
        assertEquals(Boolean.TRUE, button.getClientProperty(Theme.CLIENT_ICON_ONLY),
                "icon-only marker");
    }

    /**
     * Verifies the full status badge variant set is available.
     */
    private static void testStatusBadgeVariantsAreAvailable() {
        StatusBadge badge = new StatusBadge();
        for (StatusBadge.Variant variant : StatusBadge.Variant.values()) {
            badge.set(variant.name().toLowerCase(java.util.Locale.ROOT), variant);
            assertTrue(badge.getPreferredSize().width > 0, variant + " badge has width");
        }
        assertTrue(StatusBadge.Variant.values().length >= 9, "status badge exposes requested variants");
    }

    /**
     * Verifies a numeric token is inside an inclusive range.
     *
     * @param value source value
     * @param min minimum accepted value
     * @param max maximum accepted value
     * @param label assertion label
     */
    private static void assertBetween(int value, int min, int max, String label) {
        assertTrue(value >= min && value <= max,
                label + " in [" + min + ", " + max + "], got " + value);
    }


    /**
     * Verifies each piece set renders a non-empty knight and that the three
     * sets are visually distinct from one another.
     */
    private static void testPieceSetsRenderDistinctly() {
        Theme.setMode(Theme.Mode.LIGHT);
        java.util.Map<PieceSet, java.awt.image.BufferedImage> renders = new java.util.EnumMap<>(PieceSet.class);
        for (PieceSet set : PieceSet.values()) {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            Shapes.drawPiece(set, Piece.WHITE_KNIGHT, g, 0, 0, 80, 80);
            g.dispose();
            assertTrue(maxAlpha(image) > 200, set.label() + " set renders an opaque knight");
            renders.put(set, image);
        }
        assertTrue(differingPixels(renders.get(PieceSet.SLATE), renders.get(PieceSet.STAUNTON)) > 200,
                "Slate and Staunton knights are visually distinct");
        assertTrue(differingPixels(renders.get(PieceSet.SLATE), renders.get(PieceSet.OUTLINE)) > 200,
                "Slate and Outline knights are visually distinct");
    }

    /**
     * Counts pixels that differ between two equally-sized images.
     *
     * @param first first image
     * @param second second image
     * @return count of differing pixels
     */
    private static int differingPixels(java.awt.image.BufferedImage first, java.awt.image.BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Verifies finished-command status lines carry a tinted badge background in
     * the styled document, while plain output stays unbadged.
     */
    private static void testConsoleStatusLinesRenderBadges() {
        Theme.setMode(Theme.Mode.DARK);
        Console console = new Console();
        console.applyConsoleTheme();
        console.appendOutput("plain engine output line\n");
        console.appendOutput("[exit 0] engine bestmove done\n");
        console.appendOutput("2026-06-24 11:36:43 SEVERE display failed\n");
        console.appendOutput("SEVERE FEN: not-a-fen\n");
        console.appendOutput("2026-06-24 11:36:43 WARNING Engine stopped unexpectedly\n");
        console.appendOutput("WARNING Retry scheduled\n");
        console.appendOutput("2026-06-24 11:36:43 INFO Loaded config\n");
        console.appendOutput("CONFIG Logger initialized\n");
        console.appendSectionHeader("workbench.log    modified 2026-06-23 20:00:00    1.0 KiB");
        javax.swing.text.StyledDocument doc = console.getStyledDocument();
        javax.swing.text.AttributeSet plain = doc.getCharacterElement(1).getAttributes();
        int secondLine = doc.getDefaultRootElement().getElement(1).getStartOffset();
        javax.swing.text.AttributeSet badge = doc.getCharacterElement(secondLine + 1).getAttributes();
        int severeLine = doc.getDefaultRootElement().getElement(2).getStartOffset();
        javax.swing.text.AttributeSet severe = doc.getCharacterElement(severeLine + 1).getAttributes();
        int severeContinuationLine = doc.getDefaultRootElement().getElement(3).getStartOffset();
        javax.swing.text.AttributeSet severeContinuation =
                doc.getCharacterElement(severeContinuationLine + 1).getAttributes();
        int warningLine = doc.getDefaultRootElement().getElement(4).getStartOffset();
        javax.swing.text.AttributeSet warning = doc.getCharacterElement(warningLine + 1).getAttributes();
        int warningContinuationLine = doc.getDefaultRootElement().getElement(5).getStartOffset();
        javax.swing.text.AttributeSet warningContinuation =
                doc.getCharacterElement(warningContinuationLine + 1).getAttributes();
        int infoLine = doc.getDefaultRootElement().getElement(6).getStartOffset();
        javax.swing.text.AttributeSet info = doc.getCharacterElement(infoLine + 1).getAttributes();
        int configLine = doc.getDefaultRootElement().getElement(7).getStartOffset();
        javax.swing.text.AttributeSet config = doc.getCharacterElement(configLine + 1).getAttributes();
        int sectionLine = doc.getDefaultRootElement().getElement(8).getStartOffset();
        javax.swing.text.AttributeSet section = doc.getCharacterElement(sectionLine + 1).getAttributes();
        assertTrue(plain.getAttribute(javax.swing.text.StyleConstants.Background) == null,
                "plain console output carries no badge background");
        assertTrue(badge.getAttribute(javax.swing.text.StyleConstants.Background) instanceof java.awt.Color,
                "exit-status console line carries a tinted badge background");
        assertEquals(Theme.STATUS_ERROR_TEXT, javax.swing.text.StyleConstants.getForeground(severe),
                "timestamped SEVERE log lines use the error foreground");
        assertEquals(Theme.STATUS_ERROR_TEXT, javax.swing.text.StyleConstants.getForeground(severeContinuation),
                "SEVERE continuation log lines use the error foreground");
        assertEquals(Theme.STATUS_WARNING_TEXT, javax.swing.text.StyleConstants.getForeground(warning),
                "warning log lines use the warning foreground");
        assertEquals(Theme.STATUS_WARNING_TEXT, javax.swing.text.StyleConstants.getForeground(warningContinuation),
                "WARNING continuation log lines use the warning foreground");
        assertEquals(Theme.MUTED, javax.swing.text.StyleConstants.getForeground(info),
                "routine INFO log lines use the muted foreground");
        assertEquals(Theme.MUTED, javax.swing.text.StyleConstants.getForeground(config),
                "routine CONFIG log lines use the muted foreground");
        assertTrue(javax.swing.text.StyleConstants.isBold(section),
                "console section header uses a stronger text weight");
        assertEquals(Integer.valueOf(1), Integer.valueOf(console.getHighlighter().getHighlights().length),
                "console section header installs one full-line highlight");
        assertFalse(console.getText().contains("===="),
                "console section headers do not use ASCII fence separators");
    }

    /**
     * Verifies the eval bar paints a numeric readout at the leading side's end.
     */
    private static void testEvalBarDrawsScoreLabel() {
        Theme.setMode(Theme.Mode.LIGHT);
        EvalBar bar = new EvalBar();
        bar.setCentipawns(150);
        bar.setSize(22, 300);
        java.awt.image.BufferedImage image = paint(bar, 22, 300);
        int darkLabelPixels = 0;
        for (int y = 270; y < 298; y++) {
            for (int x = 3; x < 19; x++) {
                java.awt.Color pixel = new java.awt.Color(image.getRGB(x, y), true);
                if (pixel.getRed() < 110 && pixel.getGreen() < 110 && pixel.getBlue() < 110) {
                    darkLabelPixels++;
                }
            }
        }
        assertTrue(darkLabelPixels > 0, "eval bar draws a dark score label over the white (White-ahead) end");
    }

    /**
     * Verifies placeholder examples are hints, not real field values.
     */
    private static void testTextPlaceholdersDoNotSetValues() {
        JTextField field = new JTextField();
        invokeStatic(type("Ui"), "placeholder",
                new Class<?>[] { javax.swing.text.JTextComponent.class, String.class },
                field, "path/to/input.pgn");
        assertEquals("", field.getText(), "placeholder leaves text empty");
        assertEquals("path/to/input.pgn", field.getToolTipText(), "placeholder tooltip");
    }

    /**
     * Verifies palette search uses all query tokens.
     */
    private static void testPaletteTokenMatching() {
        // PaletteAction gained a leading category field, and now exposes
        // match() returning a MatchResult (null for no match) instead of
        // the older boolean matches().
        Object action = construct(type("CommandPalette$PaletteAction"),
                new Class<?>[] { String.class, String.class, String.class, Runnable.class },
                "", "Run publishing", "Execute the selected book workflow", (Runnable) () -> {
                    // no-op test action
                });
        assertTrue(invoke(action, "match", new Class<?>[] { String.class }, "publish") != null,
                "palette title match");
        assertTrue(invoke(action, "match", new Class<?>[] { String.class }, "execute workflow") != null,
                "palette detail-fallback match");
        assertTrue(invoke(action, "match", new Class<?>[] { String.class }, "zzzzqqqq") == null,
                "palette unmatched query returns null");
    }

    /**
     * Verifies the command palette is an in-frame overlay, not a top-level
     * dialog window.
     */
    private static void testCommandPaletteIsInFrameOverlay() {
        Class<?> palette = type("CommandPalette");
        assertTrue(JPanel.class.isAssignableFrom(palette), "command palette is a Swing panel");
        assertFalse(javax.swing.JDialog.class.isAssignableFrom(palette),
                "command palette is not a separate dialog");
    }

    /**
     * Verifies the command palette groups blank actions by category with
     * dividers, and that the command-template selector is a single dropdown
     * (the former 14-button category strip was consolidated to one combo).
     */
    private static void testCommandPaletteGroupsActionsByCategory() {
        String paletteSource = readSource(Path.of("src/application/gui/workbench/command/CommandPalette.java"));
        String commandLayerSource = readSource(
                Path.of("src/application/gui/workbench/window/WindowCommandLayer.java"));
        assertTrue(paletteSource.contains("addGroupedRows"),
                "command palette groups blank actions by category");
        assertTrue(paletteSource.contains("PaletteRow.Divider.INSTANCE"),
                "command palette uses dividers between action groups");
        assertTrue(paletteSource.contains("new PaletteRow.ActionRow(action, NO_HITS, false)"),
                "grouped command rows do not repeat the category prefix");
        assertTrue(commandLayerSource.contains("commandCombo"),
                "command template selector is consolidated into a single dropdown");
    }

    /**
     * Verifies Console and Logs live in the split editor instead of a fixed
     * bottom dock, so users can drag, split, and resize them like other tabs.
     */
    private static void testConsoleAndLogsAreMovableWorkbenchTabs() {
        String lifecycle = readSource(Path.of("src/application/gui/workbench/window/WindowLifecycle.java"));
        String base = readSource(Path.of("src/application/gui/workbench/window/WindowBase.java"));
        String dashboardActions = readSource(
                Path.of("src/application/gui/workbench/window/WindowDashboardActions.java"));
        // Console and Logs are now first-class top-level surfaces: each is its
        // own movable RegisteredView with a duplicate factory, so they can be
        // split, docked side-by-side, resized, and duplicated like the other
        // surfaces — not Run modes and not a fixed bottom dock.
        assertTrue(lifecycle.contains("new RegisteredView(\"Run\", createRunWorkspaceTab())"),
                "Run is registered as a movable workbench tab");
        assertTrue(lifecycle.contains("new RegisteredView(\"Console\", createConsolePanel(), "
                + "this::createDetachedConsolePanel)"),
                "Console is a first-class, duplicable top-level surface");
        assertTrue(lifecycle.contains("this::createDetachedLogTab"),
                "Logs is a first-class, duplicable top-level surface");
        assertFalse(lifecycle.contains("buildBottomDock"),
                "Console and Logs are not hosted in a fixed bottom dock");
        assertTrue(base.contains("protected static final int TAB_CONSOLE = 6;"),
                "Console has a stable top-level tab index");
        assertTrue(base.contains("protected static final int TAB_LOGS = 7;"),
                "Logs has a stable top-level tab index");
        assertTrue(dashboardActions.contains("window.selectTab(WindowBase.TAB_CONSOLE);"),
                "Dashboard opens the Console surface directly");
        assertTrue(lifecycle.contains("selectTab(TAB_LOGS)"),
                "the Logs action focuses the top-level Logs surface");
    }

    /**
     * Verifies workbench file drops only read explicit FEN/PGN/text payloads.
     */
    private static void testWorkbenchDropFileFilterAcceptsOnlyFenPgnTxt() {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("crtk-drop-filter");
            java.nio.file.Path pgn = java.nio.file.Files.writeString(dir.resolve("line.pgn"), "1. e4 *");
            java.nio.file.Path fen = java.nio.file.Files.writeString(dir.resolve("position.FEN"), START_FEN);
            java.nio.file.Path txt = java.nio.file.Files.writeString(dir.resolve("notes.txt"), START_FEN);
            java.nio.file.Path png = java.nio.file.Files.write(dir.resolve("image.png"), new byte[] { 1, 2, 3 });

            Class<?> windowLifecycle = type("WindowLifecycle");
            Class<?>[] signature = new Class<?>[] { java.nio.file.Path.class };
            assertTrue((Boolean) invokeStatic(windowLifecycle, "isSupportedDroppedGameFile", signature, pgn),
                    "drop accepts PGN files");
            assertTrue((Boolean) invokeStatic(windowLifecycle, "isSupportedDroppedGameFile", signature, fen),
                    "drop accepts FEN files case-insensitively");
            assertTrue((Boolean) invokeStatic(windowLifecycle, "isSupportedDroppedGameFile", signature, txt),
                    "drop accepts text files");
            assertFalse((Boolean) invokeStatic(windowLifecycle, "isSupportedDroppedGameFile", signature, png),
                    "drop rejects unrelated files");
            assertFalse((Boolean) invokeStatic(windowLifecycle, "isSupportedDroppedGameFile", signature, dir),
                    "drop rejects directories");
        } catch (java.io.IOException ex) {
            throw new AssertionError("drop filter fixture setup", ex);
        }
    }

    /**
     * Verifies command option filtering matches tokens across row columns.
     */
    private static void testOptionFilterTokenMatching() {
        assertTrue(optionFilterMatches("min pieces", "--min-pieces", "", "Minimum piece count"),
                "option filter split token match");
        String defaultFenDir = PathOps.dumpPath("workbench-fens").toString();
        assertTrue(optionFilterMatches("output dir", "--output", defaultFenDir, "Output directory"),
                "option filter cross-column match");
        assertFalse(optionFilterMatches("output mate", "--output", defaultFenDir, "Output directory"),
                "option filter missing token");
    }

    /**
     * Verifies text-area scroll panes repaint with solid backgrounds.
     */
    private static void testTextAreaScrollPaintsOpaque() {
        JTextArea area = new JTextArea("report");
        invokeStatic(type("Theme"), "area", new Class<?>[] { JTextArea.class }, area);
        JScrollPane pane = (JScrollPane) invokeStatic(type("Ui"), "scroll",
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
        JScrollPane pane = (JScrollPane) invokeStatic(type("Ui"), "scroll",
                new Class<?>[] { javax.swing.JComponent.class }, view);
        assertTrue(pane.getViewport().isOpaque(), "solid viewport opaque fallback");
        assertEquals(Integer.valueOf(255), Integer.valueOf(pane.getViewport().getBackground().getAlpha()),
                "viewport solid alpha");
        assertTrue(pane.getCorner(javax.swing.ScrollPaneConstants.UPPER_RIGHT_CORNER).isOpaque(),
                "upper right scroll corner opaque");
        assertEquals(pane.getViewport().getBackground(),
                pane.getCorner(javax.swing.ScrollPaneConstants.LOWER_RIGHT_CORNER).getBackground(),
                "scroll corner background");

        JTextArea text = new JTextArea("line 1\nline 2\nline 3");
        text.setOpaque(false);
        JScrollPane declared = new JScrollPane(text);
        Ui.refreshScrollPaneTheme(declared, () -> Theme.CARD);
        assertEquals(Theme.CARD, declared.getViewport().getBackground(),
                "declared scroll viewport uses caller surface");
        assertEquals(Theme.CARD, declared.getVerticalScrollBar().getBackground(),
                "declared vertical scrollbar track uses caller surface");
        assertEquals(Theme.CARD, declared.getHorizontalScrollBar().getBackground(),
                "declared horizontal scrollbar track uses caller surface");
        assertTrue(declared.getVerticalScrollBar().getUI().getClass().getName().contains("StyledScrollBarUI"),
                "declared vertical scrollbar uses shared workbench UI");
        declared.getVerticalScrollBar().setBackground(Color.WHITE);
        Ui.refreshScrollPaneTheme(declared, () -> Theme.CARD);
        assertEquals(Theme.CARD, declared.getVerticalScrollBar().getBackground(),
                "scrollbar refresh restores the caller surface after look-and-feel defaults");
        declared.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        declared.setSize(120, 72);
        declared.doLayout();
        BufferedImage image = paint(declared, 120, 72);
        Color gutter = new Color(image.getRGB(116, 68), true);
        assertTrue(colorDistance(gutter, Color.WHITE) > 80.0,
                "painted scrollbar gutter avoids default white look-and-feel fill");
        assertTrue(colorDistance(gutter, Theme.CARD) < 12.0,
                "painted scrollbar gutter uses the declared surface");
        assertEquals(Theme.CARD,
                declared.getCorner(javax.swing.ScrollPaneConstants.LOWER_RIGHT_CORNER).getBackground(),
                "declared scroll corner uses caller surface");
    }

    /**
     * Verifies production workbench scroll panes install the shared scrollbar
     * chrome even when a panel must construct the scroll pane directly.
     */
    private static void testWorkbenchRawScrollPanesUseSharedStyling() {
        Path root = Path.of("src/application/gui/workbench");
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .forEach(file -> assertRawScrollPaneStyling(root, file));
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to scan workbench scroll panes", ex);
        }
    }

    /**
     * Verifies raw scroll panes in one source file use shared styling.
     *
     * @param root workbench source root
     * @param file source file to inspect
     */
    private static void assertRawScrollPaneStyling(Path root, Path file) {
        String source = readSource(file);
        int scrollPaneCreations = occurrences(source, "new JScrollPane(");
        if (scrollPaneCreations == 0) {
            return;
        }
        if ("ui/ScrollPaneStyler.java".equals(root.relativize(file).toString())) {
            return;
        }
        int styledScrollPanes = occurrences(source, "styleScrollPane(")
                + occurrences(source, "refreshScrollPaneTheme(");
        assertTrue(styledScrollPanes >= scrollPaneCreations,
                root.relativize(file) + " styles every raw JScrollPane through Ui");
        if ("command/CommandPalette.java".equals(root.relativize(file).toString())) {
            assertTrue(source.contains("Ui.styleScrollPane(resultScroll, () -> Theme.ELEVATED_SOLID)"),
                    "command palette scrollbars use the palette surface color");
            assertFalse(source.contains("setCorner("),
                    "command palette does not install ad hoc transparent scroll corners");
        }
    }

    /**
     * Verifies shared popup styling covers menus, normal items, check items,
     * and separators.
     */
    private static void testPopupMenusUseSharedStyling() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem item = new JMenuItem("Item");
        JCheckBoxMenuItem check = new JCheckBoxMenuItem("Check");
        JSeparator separator = new JSeparator();
        popup.add(item);
        popup.add(check);
        popup.add(separator);

        Ui.stylePopupMenu(popup);

        assertTrue(popup.isOpaque(), "popup menu is opaque");
        assertEquals(themeColor("PANEL_SOLID"), popup.getBackground(), "popup menu background");
        assertEquals(themeColor("TEXT"), item.getForeground(), "popup item foreground");
        assertTrue(check.getIcon() != null, "popup check item uses workbench glyph");
        assertEquals(themeColor("LINE"), separator.getForeground(), "popup separator line color");
    }

    /**
     * Verifies a styled popup-menu item actually paints its label when it is
     * not hovered. The custom menu-item UI fills the row background and must
     * restore the graphics color, because BasicMenuItemUI paints unselected
     * label text with the inherited color — otherwise labels render invisibly
     * (white-on-white) until the row is armed on hover.
     */
    private static void testPopupMenuItemTextRendersWhenNotHovered() {
        for (Theme.Mode mode : Theme.Mode.values()) {
            Theme.setMode(mode);
            JPopupMenu popup = new JPopupMenu();
            JMenuItem item = new JMenuItem("Open PGN");
            popup.add(item);
            Ui.stylePopupMenu(popup);
            item.getModel().setArmed(false);
            item.setSize(180, 28);
            BufferedImage image = paint(item, 180, 28);
            int background = item.getBackground().getRGB() & 0xFFFFFF;
            int labelPixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != background) {
                        labelPixels++;
                    }
                }
            }
            assertTrue(labelPixels > 20,
                    mode + " popup item paints a visible label when not hovered");
        }
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies production workbench popup menus go through shared styling.
     */
    private static void testWorkbenchRawPopupMenusUseSharedStyling() {
        Path root = Path.of("src/application/gui/workbench");
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .forEach(file -> assertRawPopupMenuStyling(root, file));
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to scan workbench popup menus", ex);
        }
    }

    /**
     * Verifies raw popup menus in one source file use shared styling.
     *
     * @param root workbench source root
     * @param file source file to inspect
     */
    private static void assertRawPopupMenuStyling(Path root, Path file) {
        String source = readSource(file);
        int popupCreations = occurrences(source, "new JPopupMenu(")
                + occurrences(source, "new javax.swing.JPopupMenu(");
        if (popupCreations == 0) {
            return;
        }
        int styledPopups = occurrences(source, "stylePopupMenu(")
                + occurrences(source, "PopupMenus.style(")
                + occurrences(source, "stylePopupTree(")
                + occurrences(source, "styleMenuTree(");
        assertTrue(styledPopups >= popupCreations,
                root.relativize(file) + " styles every raw JPopupMenu through shared helpers");
    }

    /**
     * Verifies common Swing controls either use a shared factory or immediately
     * enter the shared styling path. This keeps future panels from reintroducing
     * platform-default tab strips, combo boxes, sliders, or data tables.
     */
    private static void testWorkbenchRawStandardControlsUseSharedStyling() {
        Path root = Path.of("src/application/gui/workbench");
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .forEach(file -> assertRawStandardControlStyling(root, file));
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to scan workbench standard controls", ex);
        }
    }

    /**
     * Verifies raw standard controls in one source file use shared styling.
     *
     * @param root workbench source root
     * @param file source file to inspect
     */
    private static void assertRawStandardControlStyling(Path root, Path file) {
        String source = readSource(file);
        String relative = root.relativize(file).toString().replace(File.separatorChar, '/');

        int tabbedPaneCreations = occurrences(source, "new JTabbedPane(")
                + occurrences(source, "new javax.swing.JTabbedPane(");
        if (tabbedPaneCreations > 0) {
            assertTrue("ui/Ui.java".equals(relative) || "ui/Tabs.java".equals(relative),
                    relative + " creates tab panes through Ui.tabbedPane() / Tabs.create()");
        }

        int comboCreations = occurrences(source, "new JComboBox");
        if (comboCreations > 0) {
            assertTrue(source.contains("styleCombo(")
                    || source.contains("styleCombos(")
                    || source.contains("styleComponentTree(")
                    || source.contains("refreshComponentTree("),
                    relative + " styles every raw JComboBox through shared helpers");
        }

        int sliderCreations = occurrences(source, "new JSlider(")
                + occurrences(source, "new javax.swing.JSlider(");
        if (sliderCreations > 0) {
            assertTrue(source.contains("styleSlider(")
                    || source.contains("styleComponentTree(")
                    || source.contains("refreshComponentTree("),
                    relative + " styles every raw JSlider through shared helpers");
        }

        int tableCreations = occurrences(source, "new JTable(")
                + occurrences(source, "new javax.swing.JTable(");
        if (tableCreations > 0) {
            assertTrue(source.contains("Theme.table(")
                    || source.contains("styleComponentTree(")
                    || windowBaseTablesAreStyledByLayers(root, relative),
                    relative + " styles every raw JTable through shared helpers");
        }
    }

    /**
     * Returns whether WindowBase tables are styled by their layer classes.
     *
     * @param root workbench source root
     * @param relative source path relative to the workbench root
     * @return true when layer styling covers WindowBase tables
     */
    private static boolean windowBaseTablesAreStyledByLayers(Path root, String relative) {
        if (!"window/WindowBase.java".equals(relative)) {
            return false;
        }
        String boardLayer = readSource(root.resolve("window/WindowBoardLayer.java"));
        String commandLayer = readSource(root.resolve("window/WindowCommandLayer.java"));
        return boardLayer.contains("Theme.table(movesTable")
                && commandLayer.contains("Theme.table(gameTable");
    }

    /**
     * Verifies Play mode docks the one shared inline move list (the same
     * annotation-aware {@code AnalyzeVariationTreePanel} the Analyze tree uses)
     * rather than a separate move-list component.
     */
    private static void testPlayTabUsesCompactMoveHistory() {
        String board = readSource(Path.of("src/application/gui/workbench/window/WindowBoardLayer.java"));
        assertTrue(board.contains("new AnalyzeVariationTreePanel(gameModel, this::showGameRow)"),
                "Play tab docks the one shared inline move list");
        assertFalse(board.contains("new MoveListPanel("),
                "Play tab no longer uses a separate move-list component");
    }

    /**
     * Reads one source file for source-shape assertions.
     *
     * @param file source file
     * @return source text
     */
    private static String readSource(Path file) {
        return readUtf8(file);
    }

    /**
     * Verifies short scroll content expands to the available viewport height.
     */
    private static void testViewportFillWrapperTracksAvailableHeight() {
        JPanel child = new JPanel();
        child.setPreferredSize(new Dimension(200, 100));
        JComponent wrapper = (JComponent) invokeStatic(type("Ui"), "fillViewport",
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
     * Verifies centered report content tracks compact viewport widths rather
     * than forcing a horizontal scrollbar.
     */
    private static void testCenteredViewportAvoidsHorizontalScrolling() {
        JPanel child = new JPanel();
        child.setPreferredSize(new Dimension(1440, 260));
        JComponent wrapper = Ui.centeredViewport(child, 1440);
        assertTrue(wrapper instanceof Scrollable, "centered viewport wrapper is scrollable");

        JScrollPane pane = Ui.scroll(wrapper);
        pane.setSize(1040, 320);
        pane.doLayout();
        wrapper.doLayout();

        assertTrue(((Scrollable) wrapper).getScrollableTracksViewportWidth(),
                "centered viewport tracks compact widths");
        assertFalse(pane.getHorizontalScrollBar().isVisible(),
                "centered viewport does not require horizontal scrolling");
        assertTrue(child.getBounds().width <= pane.getViewport().getExtentSize().width,
                "centered child fits inside compact viewport");
    }

    /**
     * Verifies table and list data surfaces avoid translucent opaque repaint trails.
     */
    private static void testDataSurfacesUseSolidBackgrounds() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("Theme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        assertTrue(table.isOpaque(), "table opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(table.getBackground().getAlpha()), "table solid alpha");
        assertFalse(table.getShowHorizontalLines(), "table grid artifacts disabled");
        Component header = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, "Header", false, false, -1, 0);
        assertEquals(themeColor("PANEL_SOLID"), header.getBackground(), "table header background themed");
        assertEquals(themeColor("MUTED"), header.getForeground(), "table header foreground themed");

        JList<String> list = new JList<>(new String[] { "a" });
        invokeStatic(type("Theme"), "list", new Class<?>[] { JList.class }, list);
        assertTrue(list.isOpaque(), "list opaque");
        assertEquals(Integer.valueOf(255), Integer.valueOf(list.getBackground().getAlpha()), "list solid alpha");
    }

    /**
     * Verifies the shared tag cloud paints categorized, wrapped chips.
     */
    private static void testTagCloudGroupsAndWrapsTags() {
        TagCloud full = new TagCloud();
        full.setTags(List.of(
                "FACT: castle_rights=KQkq",
                "META: phase=opening",
                "PIECE: activity=low_mobility side=black piece=bishop square=c8",
                "MOVE: legal=20"));
        assertEquals(Integer.valueOf(4), Integer.valueOf(full.tagCount()), "full tag cloud stores parsed tags");
        full.setSize(420, 180);
        assertEquals(null, full.getToolTipText(), "tag cloud has no aggregate tooltip");
        MouseEvent chipHover = new MouseEvent(full, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, 16, 35, 0, false);
        String chipTooltip = full.getToolTipText(chipHover);
        assertTrue(chipTooltip != null && chipTooltip.contains("FACT")
                && chipTooltip.contains("castle_rights=KQkq"),
                "tag cloud tooltip names the category and the raw payload");
        MouseEvent backgroundHover = new MouseEvent(full, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, 5, 5, 0, false);
        assertEquals(null, full.getToolTipText(backgroundHover), "tag cloud background has no tooltip");
        BufferedImage fullImage = paint(full, 420, 180);
        assertTrue(maxAlpha(fullImage) > 180, "full tag cloud paints chip pixels");

        TagCloud compact = new TagCloud(TagCloud.Mode.COMPACT);
        compact.setTags(List.of("FACT: center_control=balanced", "META: source=engine"));
        compact.setSize(280, 68);
        BufferedImage compactImage = paint(compact, 280, 68);
        assertTrue(maxAlpha(compactImage) > 180, "compact tag cloud paints dashboard chips");
    }

    /**
     * Verifies custom-painted surfaces clear their own background so partial
     * repaints cannot leave translucent trails.
     */
    private static void testCustomPaintedSurfacesClearBackground() {
        JComponent panel = (JComponent) construct(type("SurfacePanel"),
                new Class<?>[] { java.awt.LayoutManager.class }, new java.awt.BorderLayout());
        assertPaintsOpaqueCorner(panel, 160, 80, "solid panel clears background");

        JComponent board = (JComponent) construct(type("BoardPanel"), new Class<?>[0]);
        assertPaintsOpaqueCorner(board, 320, 320, "board panel clears margin background");

        JComponent eval = (JComponent) construct(type("EvalBar"), new Class<?>[0]);
        assertPaintsOpaqueCorner(eval, 40, 260, "eval bar clears background");
    }

    /**
     * Verifies boolean table cells do not fall back to Swing's default checkbox styling.
     */
    private static void testBooleanTableRendererIsStyled() {
        JTable table = new JTable(1, 1);
        invokeStatic(type("Theme"), "table", new Class<?>[] { JTable.class, int.class }, table, 24);
        TableCellRenderer renderer = table.getDefaultRenderer(Boolean.class);
        Component cell = renderer.getTableCellRendererComponent(table, Boolean.TRUE, true, false, 0, 0);
        assertTrue(cell instanceof JComponent, "boolean renderer component");
        assertTrue(((JComponent) cell).isOpaque(), "boolean renderer opaque");
        assertEquals(table.getSelectionBackground(), cell.getBackground(), "boolean renderer selected background");
        assertTrue(table.getDefaultEditor(Boolean.class) != null, "boolean table editor installed");
        Component editor = table.getDefaultEditor(Boolean.class)
                .getTableCellEditorComponent(table, Boolean.TRUE, true, 0, 0);
        assertTrue(editor instanceof JCheckBox, "boolean table editor checkbox");
        assertTrue(((JCheckBox) editor).getIcon() != null, "boolean table editor uses workbench glyph");
        assertFalse(((JCheckBox) editor).isFocusPainted(), "boolean table editor hides platform focus paint");
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
        JTree tree = new JTree();
        root.add(field);
        root.add(button);
        root.add(pane);
        root.add(tree);

        invokeStatic(type("Ui"), "styleComponentTree", new Class<?>[] { Component.class }, root);

        assertEquals(Integer.valueOf(255), Integer.valueOf(field.getBackground().getAlpha()),
                "recursive text field solid alpha");
        assertFalse(button.isContentAreaFilled(), "recursive button content area hidden");
        assertTrue(list.isOpaque(), "recursive list opaque");
        assertEquals(list.getBackground(), pane.getViewport().getBackground(), "recursive scroll viewport background");
        assertEquals("None", tree.getClientProperty("JTree.lineStyle"), "recursive tree shared style");
    }

    /**
     * Verifies plain checkboxes use the workbench glyph instead of the
     * platform look-and-feel checkbox painter.
     */
    private static void testPlainCheckboxUsesWorkbenchGlyph() {
        JCheckBox box = new JCheckBox("K");
        invokeStatic(type("Ui"), "styleCheckBox", new Class<?>[] { JCheckBox.class }, box);
        assertTrue(box.getIcon() != null, "plain checkbox icon installed");
        assertEquals(box.getIcon(), box.getSelectedIcon(), "plain checkbox selected icon");
        assertEquals(Integer.valueOf(17), Integer.valueOf(box.getIcon().getIconWidth()),
                "plain checkbox glyph width");
        assertFalse(box.isFocusPainted(), "plain checkbox hides platform focus paint");
    }

    /**
     * Verifies progress indicators use the compact workbench progress chrome.
     */
    private static void testProgressBarUsesWorkbenchChrome() {
        JProgressBar bar = new JProgressBar(0, 100);
        invokeStatic(type("Ui"), "styleProgressBar", new Class<?>[] { JProgressBar.class }, bar);
        bar.setValue(60);
        bar.setSize(100, 8);

        assertFalse(bar.isOpaque(), "progress bar is non-opaque");
        assertFalse(bar.isBorderPainted(), "progress bar border is custom-painted");
        assertTrue(bar.getPreferredSize().height <= 8, "progress bar remains compact");

        BufferedImage image = paint(bar, 100, 8);
        Color fill = new Color(image.getRGB(24, 4), true);
        assertTrue(fill.getAlpha() > 200, "progress fill paints opaque pixels");
        assertColorDistanceAtLeast(fill, themeColor("INPUT_DISABLED"), 20.0,
                "progress fill differs from track");
    }

    /**
     * Verifies disabled combo boxes paint with theme tokens instead of the
     * platform look-and-feel's light selected-value background.
     */
    private static void testDisabledComboUsesThemeBackground() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        JComboBox<String> combo = new JComboBox<>(new String[] { "Default language" });
        invokeStatic(type("Ui"), "styleCombo", new Class<?>[] { JComboBox.class }, combo);
        combo.setEnabled(false);
        combo.setSize(220, 32);
        combo.doLayout();

        Color sample = new Color(paint(combo, 220, 32).getRGB(170, 16), true);
        Color expected = themeColor("INPUT_DISABLED");
        if (colorDistance(sample, expected) > 6.0) {
            throw new AssertionError("disabled combo background: expected near " + colorText(expected)
                    + ", got " + colorText(sample));
        }
        assertColorDistanceAtLeast(sample, Color.WHITE, 80.0, "disabled combo avoids white background");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies enabled combo boxes fill the full input well, including the
     * gutter before the arrow button.
     */
    private static void testEnabledComboFillsArrowGutter() {
        Theme.setMode(Theme.Mode.DARK);
        JComboBox<String> combo = new JComboBox<>(new String[] { "endgame" });
        Ui.styleCombo(combo);
        combo.setSize(280, 32);
        combo.doLayout();

        BufferedImage image = paint(combo, 280, 32);
        Color expected = themeColor("INPUT");
        assertInputPixel(image, 150, 16, expected, "combo middle input fill");
        assertInputPixel(image, 250, 16, expected, "combo arrow gutter input fill");
        assertInputPixel(image, 276, 10, expected, "combo right input fill");

        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies combo dropdown popups use shared list and scrollbar chrome.
     */
    private static void testComboPopupUsesSharedScrollStyling() {
        Theme.setMode(Theme.Mode.DARK);
        JComboBox<String> combo = new JComboBox<>(new String[] {
                "record export training-jsonl", "record export csv", "record validate", "record audit",
                "engine bestmove", "engine analyze", "engine perft", "position view", "position describe",
                "puzzle mine", "puzzle study", "book render", "book pdf", "config validate", "doctor all" });
        Ui.styleCombo(combo);
        Object child = combo.getUI().getAccessibleChild(combo, 0);
        assertTrue(child instanceof Component, "combo popup accessible child");

        Component popup = (Component) child;
        JScrollPane scroll = firstDescendant(popup, JScrollPane.class);
        JList<?> list = firstDescendant(popup, JList.class);
        assertTrue(scroll != null, "combo popup has a scroll pane");
        assertTrue(list != null, "combo popup has a list");
        assertTrue(scroll.getVerticalScrollBar().getUI().getClass().getName().contains("StyledScrollBarUI"),
                "combo popup uses shared scrollbar UI");
        assertEquals(themeColor("ELEVATED_SOLID"), list.getBackground(), "combo popup list background");
        assertEquals(themeColor("SELECTION_SOLID"), list.getSelectionBackground(), "combo popup selection");

        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies tree views use shared Workbench tree styling instead of platform
     * connector lines and colors.
     */
    private static void testSharedTreeStyling() {
        JTree tree = new JTree();
        Ui.styleTree(tree);

        assertEquals(themeColor("PANEL_SOLID"), tree.getBackground(), "tree shared background");
        assertEquals(themeColor("TEXT"), tree.getForeground(), "tree shared foreground");
        assertEquals("None", tree.getClientProperty("JTree.lineStyle"), "tree connector lines disabled");
        assertEquals(Integer.valueOf(TreeSelectionModel.SINGLE_TREE_SELECTION),
                Integer.valueOf(tree.getSelectionModel().getSelectionMode()), "tree single selection");
        assertTrue(tree.getRowHeight() >= 24, "tree row height is stable");
    }

    /**
     * Verifies one input-control pixel is near the expected input fill.
     *
     * @param image painted control image
     * @param x sample x
     * @param y sample y
     * @param expected expected color
     * @param label assertion label
     */
    private static void assertInputPixel(BufferedImage image, int x, int y, Color expected, String label) {
        Color sample = new Color(image.getRGB(x, y), true);
        if (colorDistance(sample, expected) > 6.0) {
            throw new AssertionError(label + ": expected near " + colorText(expected)
                    + ", got " + colorText(sample));
        }
    }

    /**
     * Verifies spinner editors use readable input colors without a nested
     * text-field border.
     */
    private static void testSpinnerEditorUsesReadableInputColors() {
        Theme.setMode(Theme.Mode.DARK);
        JSpinner spinner = new JSpinner();
        Ui.styleIntegerSpinner(spinner);
        Theme.refreshComponentTree(spinner);
        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        assertEquals(themeColor("TEXT"), field.getForeground(), "spinner editor foreground");
        assertEquals(themeColor("INPUT"), field.getBackground(), "spinner editor background");
        assertColorDistanceAtLeast(field.getForeground(), field.getBackground(), 80.0,
                "spinner editor text contrast");
        assertTrue(field.getBorder().getBorderInsets(field).left <= 8,
                "spinner editor avoids nested input border");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies styled number spinners commit valid typed edits immediately.
     */
    private static void testStyledSpinnerCommitsValidEdits() {
        JSpinner spinner = new JSpinner(new javax.swing.SpinnerNumberModel(4, 1, 99, 1));
        Ui.styleSpinner(spinner);
        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setText("6");
        assertEquals(Integer.valueOf(6), spinner.getValue(), "styled spinner commits valid edit");
    }

    /**
     * Verifies spinner controls fill the full input well, including the arrow
     * gutter used by number spinners.
     */
    private static void testSpinnerFillsArrowGutter() {
        Theme.setMode(Theme.Mode.DARK);
        JSpinner spinner = new JSpinner();
        spinner.setValue(Integer.valueOf(300));
        Ui.styleIntegerSpinner(spinner);
        spinner.setSize(100, 32);
        spinner.doLayout();

        BufferedImage image = paint(spinner, 100, 32);
        Color expected = themeColor("INPUT");
        assertInputPixel(image, 52, 16, expected, "spinner middle input fill");
        assertInputPixel(image, 81, 7, expected, "spinner upper arrow gutter input fill");
        assertInputPixel(image, 81, 25, expected, "spinner lower arrow gutter input fill");
        assertInputPixel(image, 97, 16, expected, "spinner right input fill");

        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the Game tab's import-line editor keeps enough height for
     * algebraic movetext and variations instead of collapsing into one line.
     */
    private static void testGameLineImportInputKeepsMultilineHeight() {
        JTextArea input = new JTextArea();
        JScrollPane pane = (JScrollPane) invokeStatic(type("window.WindowBoardLayer"),
                "configureGameInputScroll", new Class<?>[] { JTextArea.class }, input);
        assertEquals(Integer.valueOf(5), Integer.valueOf(input.getRows()), "game input reserves five rows");
        assertTrue(input.getLineWrap(), "game input wraps movetext");
        assertTrue(input.getWrapStyleWord(), "game input wraps on words");
        assertTrue(pane.getPreferredSize().height >= 90, "game input scroll preferred height");
        assertTrue(pane.getMinimumSize().height >= 70, "game input scroll minimum height");

        String board = readSource(Path.of("src/application/gui/workbench/window/WindowBoardLayer.java"));
        String game = readSource(Path.of("src/application/gui/workbench/window/WindowGameLayer.java"));
        assertTrue(board.contains("private final JTextArea gameFenPreview = Ui.commandBlock(\"\");"),
                "game line owns a shared read-only FEN preview field");
        assertTrue(board.contains("private final JTextArea gamePgnPreview = Ui.commandBlock(\"\");"),
                "game line owns a shared read-only PGN preview area");
        assertTrue(board.contains("private JComponent createGameNotationPreview()"),
                "game line builds a FEN/PGN preview block");
        assertTrue(board.contains("gameFenPreview.setEditable(false);"),
                "FEN preview is read-only");
        assertTrue(board.contains("gamePgnPreview.setEditable(false);"),
                "PGN preview is read-only");
        assertTrue(board.contains("gameFenPreview.setRows(1);"),
                "FEN preview stays a compact single-line strip");
        assertTrue(board.contains("gamePgnPreview.setRows(2);"),
                "PGN preview stays compact like the lichess reference");
        assertTrue(board.contains("JScrollPane fenScroll = scroll(gameFenPreview, () -> Theme.INPUT);"),
                "FEN preview scrolls instead of clipping long positions");
        assertTrue(board.contains("grid(panel, label(\"FEN\")"),
                "FEN preview exposes a visible label");
        assertTrue(board.contains("grid(panel, label(\"PGN\")"),
                "PGN preview exposes a visible label");
        assertTrue(board.contains("gameFenPreview.setText(gameModel.currentPosition().toString());"),
                "FEN preview follows selected variation position");
        assertTrue(board.contains("gamePgnPreview.setText(gameModel.pgn());"),
                "PGN preview follows current game export");
        assertTrue(game.contains("refreshGameNotationPreview();"),
                "game-state refresh keeps FEN/PGN preview synchronized");
    }

    /**
     * Verifies settings chip rows reserve enough width for full labels.
     */
    private static void testSettingsToggleRowsAreReadable() {
        SettingsChipRow row = new SettingsChipRow("Legal move preview",
                "Show selected-piece destinations and legal drag targets", true, value -> {
                    // no-op test callback
                });
        Dimension size = row.getPreferredSize();
        assertTrue(size.width >= 300, "settings toggle row is wide enough for labels");
        assertTrue(size.height >= 44, "settings toggle row reserves help text");
        assertTrue(size.height <= 50, "settings toggle row remains compact");
        assertTrue(row.isSelected(), "settings chip row starts selected");
    }

    /**
     * Verifies settings sliders keep readable theme colors after palette
     * refreshes.
     */
    private static void testSettingsSliderRefreshKeepsReadableColors() {
        Theme.setMode(Theme.Mode.LIGHT);
        JSlider slider = new JSlider(0, 100, 30);
        Ui.styleSlider(slider);

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(slider);
        assertEquals(themeColor("TEXT"), slider.getForeground(), "settings slider dark foreground");
        assertEquals(themeColor("PANEL_SOLID"), slider.getBackground(), "settings slider dark background");

        Theme.setMode(Theme.Mode.LIGHT);
        Theme.refreshComponentTree(slider);
        assertEquals(themeColor("TEXT"), slider.getForeground(), "settings slider light foreground");
        assertEquals(themeColor("PANEL_SOLID"), slider.getBackground(), "settings slider light background");
    }

    /**
     * Verifies the top-level menu uses useful VS Code-style command groups
     * instead of a single Settings entrypoint.
     */
    private static void testSettingsMenuExposesThemeModes() {
        Theme.Mode[] activeMode = { Theme.Mode.LIGHT };
        boolean[] displaySettingsOpened = { false };
        boolean[] engineSettingsOpened = { false };
        boolean[] commandPaletteOpened = { false };
        boolean[] logsOpened = { false };
        boolean[] analyzeOpened = { false };
        boolean[] runBuiltCommand = { false };
        boolean[] soundEnabled = { true };
        SettingsMenu menu = new SettingsMenu(new SettingsMenu.Controller() {
            /**
             * {@inheritDoc}
             */
            @Override
            public Theme.Mode themeMode() {
                return activeMode[0];
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setThemeMode(Theme.Mode mode) {
                activeMode[0] = mode;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean soundEnabled() {
                return soundEnabled[0];
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setSoundEnabled(boolean enabled) {
                soundEnabled[0] = enabled;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showDisplaySettings() {
                displaySettingsOpened[0] = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showEngineSettings() {
                engineSettingsOpened[0] = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showSoundSettings() {
                // not needed for this regression
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showCommandPalette() {
                commandPaletteOpened[0] = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openLogsDirectory() {
                logsOpened[0] = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openAnalyze() {
                analyzeOpened[0] = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runBuiltCommand() {
                runBuiltCommand[0] = true;
            }
        });

        JMenuBar bar = menu.component();
        List<String> menuNames = new java.util.ArrayList<>();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu current = bar.getMenu(i);
            if (current != null) {
                menuNames.add(current.getText());
            }
        }
        assertEquals(List.of("File", "Edit", "Selection", "View", "Go", "Run", "Terminal", "Help"),
                menuNames, "top menu groups");
        String settingsMenuSource = readSource(Path.of("src/application/gui/workbench/window/SettingsMenu.java"));
        String settingsDialogSource =
                readSource(Path.of("src/application/gui/workbench/window/SettingsDialog.java"));
        String titleBarChromeSource = readSource(Path.of("src/application/gui/workbench/window/TitleBarChrome.java"));
        assertFalse(settingsMenuSource.contains("TitleBarChrome.brand"),
                "top menu no longer reserves space for the unusable project chrome");
        assertFalse(titleBarChromeSource.contains("Alpha Project"),
                "title bar no longer paints a fake project chip");
        assertFalse(titleBarChromeSource.contains("paintTrafficLights"),
                "title bar no longer paints decorative traffic-light dots");
        assertTrue(settingsDialogSource.contains("Ui.iconButton(\"x\", \"Close settings\""),
                "settings overlay uses a compact close icon");
        assertTrue(settingsDialogSource.contains("close.setActionCommand(\"workbench.settings.close\");"),
                "settings overlay close icon has a stable action command");

        clickMenuItem(menuByText(bar, "File"), "Settings…");
        clickMenuItem(menuByText(bar, "Edit"), "Command Palette…");
        clickMenuItem(menuByText(bar, "View"), "Analyze");
        clickMenuItem(menuByText(bar, "View"), "Engine Settings…");
        clickMenuItem(menuByText(bar, "Run"), "Run Built Command");
        clickMenuItem(menuByText(bar, "Terminal"), "Open Logs Folder");

        assertTrue(displaySettingsOpened[0], "file settings item opens display settings");
        assertTrue(commandPaletteOpened[0], "edit command palette item routes to palette");
        assertTrue(analyzeOpened[0], "view analyze item routes to analyze tab");
        assertTrue(engineSettingsOpened[0], "view engine settings item opens engine settings");
        assertTrue(runBuiltCommand[0], "run menu launches built command");
        assertTrue(logsOpened[0], "terminal menu opens logs folder");
        assertEquals(Theme.Mode.LIGHT, activeMode[0], "theme mode unchanged by menubar entry");
        assertEquals(Boolean.TRUE, Boolean.valueOf(soundEnabled[0]), "sound preference unchanged by menubar");
    }

    /**
     * Verifies the Display settings content keeps its lichess-style grouped
     * menu shape while still wiring real Workbench preferences.
     */
    private static void testDisplaySettingsUseLichessStyleMenuGroups() {
        String source = readSource(Path.of("src/application/gui/workbench/window/WindowBoardLayer.java"));
        assertTrue(source.contains("Ui.card(\"Keyboard shortcuts\", createKeyboardShortcutSettings())"),
                "display settings expose a keyboard-shortcuts group");
        assertTrue(source.contains("Ui.card(\"General\", createGeneralSettings())"),
                "display settings expose a general group");
        assertTrue(source.contains("Ui.card(\"Move list\", createMoveListSettings())"),
                "display settings expose a move-list group");
        assertTrue(source.contains("Ui.card(\"Board\", createBoardMenuSettings())"),
                "display settings expose a board group");
        assertTrue(source.contains("Ui.card(\"Appearance\", createAppearanceSettings())"),
                "display settings keep theme and piece controls reachable");
        assertTrue(source.contains("Ui.card(\"Sound\", createSoundSettings())"),
                "display settings keep sound controls reachable");
        assertTrue(source.contains("menuToggleRow(\"Show server analysis\""),
                "server analysis is a real menu toggle");
        assertTrue(source.contains("this::setLiveExternalEngineEnabled"),
                "server analysis toggle uses the live-engine state path");
        assertTrue(source.contains("menuToggleRow(\"Show evaluation gauge\""),
                "evaluation gauge is a real menu toggle");
        assertTrue(source.contains("menuToggleRow(\"Show best move arrows\""),
                "best-move arrows are a real board toggle");
        assertTrue(source.contains("menuToggleRow(\"Show undefended pieces\""),
                "undefended-piece overlays are a real board toggle");
        assertTrue(source.contains("menuToggleRow(\"Show pinned pieces\""),
                "pinned-piece overlays are a real board toggle");
        assertTrue(source.contains("menuToggleRow(\"Show checkable king\""),
                "checkable-king overlays are a real board toggle");
        assertTrue(source.contains("disabledMenuToggleRow(\"Enable variation hiding\""),
                "unsupported variation hiding is shown as a disabled state");
        assertTrue(source.contains("toggle.setActionCommand(displaySettingActionId(text));"),
                "display menu toggles receive stable action ids");
        assertTrue(source.contains("context.setAccessibleName(text);"),
                "display menu toggles receive accessible names");
        assertTrue(source.contains("context.setAccessibleDescription(tooltip);"),
                "display menu toggles receive accessible descriptions");
        assertTrue(source.contains("return \"workbench.display.\" + key;"),
                "display setting action ids use the workbench.display namespace");
    }

    /**
     * Clicks a menu item whose label starts with the supplied text. Menu items
     * may include shortcut hints after the visible command name.
     *
     * @param menu menu to search
     * @param label item label prefix
     */
    private static void clickMenuItem(JMenu menu, String label) {
        JPopupMenu popup = menu.getPopupMenu();
        for (Component component : popup.getComponents()) {
            if (component instanceof JMenuItem item && item.getText().startsWith(label)) {
                item.doClick();
                return;
            }
        }
        throw new AssertionError("missing menu item " + label + " in " + menu.getText());
    }

    /**
     * Finds a menu by label while ignoring custom title-bar components mounted
     * into the same {@link JMenuBar}.
     *
     * @param bar menu bar
     * @param text menu label
     * @return matching menu
     */
    private static JMenu menuByText(JMenuBar bar, String text) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && text.equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("missing menu " + text);
    }

    /**
     * Verifies settings text remains readable in every palette.
     */
    private static void testSettingsTextStaysReadableInBothModes() {
        Theme.Mode original = Theme.mode();
        for (Theme.Mode mode : Theme.Mode.values()) {
            Theme.setMode(mode);
            Theme.install();
            SettingsChipRow row = new SettingsChipRow("Legal move preview",
                    "Show selected-piece destinations and legal drag targets", true, value -> {
                        // no-op test callback
                    });
            Theme.refreshComponentTree(row);
            assertReadableTextTree(row, themeColor("PANEL_SOLID"), mode.label() + " settings row");

            SettingsMenu menu = new SettingsMenu(settingsMenuControllerForContrast());
            menu.refreshTheme();
            assertReadableTextTree(menu.component(), themeColor("BG"), mode.label() + " settings menu");
            assertReadableTextTree(menu.component().getMenu(0).getPopupMenu(),
                    themeColor("PANEL_SOLID"), mode.label() + " settings popup");
        }
        Theme.setMode(original);
        Theme.install();
    }

    /**
     * Creates a no-op settings-menu controller for UI contrast tests.
     *
     * @return controller
     */
    private static SettingsMenu.Controller settingsMenuControllerForContrast() {
        return new SettingsMenu.Controller() {
            /**
             * {@inheritDoc}
             */
            @Override
            public Theme.Mode themeMode() {
                return Theme.mode();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setThemeMode(Theme.Mode mode) {
                Theme.setMode(mode);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean soundEnabled() {
                return true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setSoundEnabled(boolean enabled) {
                // no-op test controller
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showDisplaySettings() {
                // no-op test controller
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showEngineSettings() {
                // no-op test controller
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showSoundSettings() {
                // no-op test controller
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showCommandPalette() {
                // no-op test controller
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openLogsDirectory() {
                // no-op test controller
            }
        };
    }

    /**
     * Recursively verifies visible text components have readable contrast.
     *
     * @param component component tree root
     * @param inheritedBackground background inherited from the parent
     * @param label assertion label
     */
    private static void assertReadableTextTree(Component component, Color inheritedBackground, String label) {
        Color background = readableBackground(component, inheritedBackground);
        if (component instanceof JLabel textLabel && hasVisibleText(textLabel.getText())) {
            assertReadableText(textLabel.getForeground(), background, label + " label " + textLabel.getText());
        } else if (component instanceof AbstractButton button && hasVisibleText(button.getText())) {
            assertReadableText(button.getForeground(), background, label + " button " + button.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                assertReadableTextTree(child, background, label);
            }
        }
    }

    /**
     * Returns the background a text component is visually painted on.
     *
     * @param component component to inspect
     * @param inherited parent background
     * @return effective readable background
     */
    private static Color readableBackground(Component component, Color inherited) {
        Color background = component.getBackground();
        if (component instanceof AbstractButton && background != null && background.getAlpha() > 0) {
            return background;
        }
        if (component instanceof JComponent jComponent
                && jComponent.isOpaque()
                && background != null
                && background.getAlpha() > 0) {
            return background;
        }
        return inherited == null ? themeColor("PANEL_SOLID") : inherited;
    }

    /**
     * Returns whether a Swing text value should be checked.
     *
     * @param text component text
     * @return true when text is non-blank
     */
    private static boolean hasVisibleText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Verifies one foreground/background pair is readable.
     *
     * @param foreground foreground color
     * @param background background color
     * @param label assertion label
     */
    private static void assertReadableText(Color foreground, Color background, String label) {
        assertTrue(contrastRatio(foreground, background) >= 4.5d,
                label + " has readable contrast");
    }

    /**
     * Verifies the compact layout menu exposes the useful supported workbench
     * layout controls without depending on pointer drag gestures.
     */
    private static void testLayoutMenuExposesUsefulWorkbenchControls() {
        boolean[] statusVisible = { true };
        int[] splitRight = { 0 };
        int[] splitDown = { 0 };
        int[] splitLeft = { 0 };
        int[] splitUp = { 0 };
        int[] detachTab = { 0 };
        int[] restoreTabs = { 0 };
        int[] closeOthers = { 0 };
        LayoutMenu menu = new LayoutMenu(new LayoutMenu.Controller() {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean statusBarVisible() {
                return statusVisible[0];
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setStatusBarVisible(boolean visible) {
                statusVisible[0] = visible;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitRight() {
                splitRight[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitDown() {
                splitDown[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitLeft() {
                splitLeft[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitUp() {
                splitUp[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void detachTab() {
                detachTab[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void reopenAllTabs() {
                restoreTabs[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void closeOtherTabs() {
                closeOthers[0]++;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int openTabCount() {
                return 7;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int visibleGroupCount() {
                return 2;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean hasRestorableTabs() {
                return true;
            }
        });

        JComponent component = menu.component();
        assertEquals(Integer.valueOf(4), Integer.valueOf(component.getComponentCount()),
                "layout toolbar exposes four chrome buttons");
        assertEquals("Customize layout and visibility", ((JButton) component.getComponent(0)).getToolTipText(),
                "layout toolbar starts with customize button");
        assertEquals("workbench.layout.customize", ((JButton) component.getComponent(0)).getActionCommand(),
                "layout customize button exposes a command id");
        assertEquals(EditorLayoutCommands.SPLIT_RIGHT_TOOLTIP,
                ((JButton) component.getComponent(1)).getToolTipText(),
                "layout split-right tooltip names the tab action");
        assertEquals(EditorLayoutCommands.SPLIT_RIGHT, ((JButton) component.getComponent(1)).getActionCommand(),
                "layout split-right button exposes a command id");
        assertEquals(EditorLayoutCommands.SPLIT_DOWN, ((JButton) component.getComponent(2)).getActionCommand(),
                "layout split-down button exposes a command id");
        assertEquals(EditorLayoutCommands.TABS_RESTORE_CLOSED,
                ((JButton) component.getComponent(3)).getActionCommand(),
                "layout restore button reuses the editor restore command id");
        ((JButton) component.getComponent(1)).doClick();
        ((JButton) component.getComponent(2)).doClick();
        ((JButton) component.getComponent(3)).doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitRight[0]),
                "layout toolbar split-right button works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitDown[0]),
                "layout toolbar split-down button works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(restoreTabs[0]),
                "layout toolbar restore button works");

        JPopupMenu popup = (JPopupMenu) invoke(menu, "buildPopup", new Class<?>[0]);
        assertEquals("Status Bar", popupItem(popup, "Status Bar").getText(),
                "layout popup exposes status-bar visibility");
        JCheckBoxMenuItem statusItem = (JCheckBoxMenuItem) popupItem(popup, "Status Bar");
        assertTrue(statusItem.isSelected(), "status-bar row reflects controller state");
        assertEquals("workbench.layout.statusBar.visible", statusItem.getActionCommand(),
                "status-bar row exposes a stable command id");
        assertEquals("Status Bar", statusItem.getAccessibleContext().getAccessibleName(),
                "status-bar row exposes an accessible name");
        assertThemedCheckIcon(statusItem, "status-bar row");
        assertWorkbenchMenuChrome(statusItem, "status-bar row");
        statusItem.doClick();
        assertFalse(statusVisible[0], "status-bar row toggles controller state");

        assertEquals(EditorLayoutCommands.TAB_SPLIT_LEFT,
                popupItem(popup, EditorLayoutCommands.TAB_SPLIT_LEFT_LABEL).getActionCommand(),
                "layout popup split-left item reuses editor command id");
        assertEquals(EditorLayoutCommands.TAB_SPLIT_UP,
                popupItem(popup, EditorLayoutCommands.TAB_SPLIT_UP_LABEL).getActionCommand(),
                "layout popup split-up item reuses editor command id");
        assertEquals(EditorLayoutCommands.TAB_DETACH,
                popupItem(popup, EditorLayoutCommands.TAB_DETACH_LABEL).getActionCommand(),
                "layout popup detach item reuses editor command id");
        assertEquals(EditorLayoutCommands.TAB_CLOSE_OTHERS,
                popupItem(popup, EditorLayoutCommands.TAB_CLOSE_OTHERS_LABEL).getActionCommand(),
                "layout popup close-others item reuses editor command id");
        popupItem(popup, "Split Tab Left").doClick();
        popupItem(popup, "Split Tab Up").doClick();
        popupItem(popup, EditorLayoutCommands.TAB_DETACH_LABEL).doClick();
        popupItem(popup, EditorLayoutCommands.TAB_CLOSE_OTHERS_LABEL).doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitLeft[0]),
                "layout popup split-left item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitUp[0]),
                "layout popup split-up item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(detachTab[0]),
                "layout popup detach item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(closeOthers[0]),
                "layout popup close-others item works");

        boolean[] limitedCanSplit = { false };
        boolean[] limitedRestorable = { false };
        int[] limitedSplitRight = { 0 };
        int[] limitedRestoreTabs = { 0 };
        LayoutMenu limited = new LayoutMenu(new LayoutMenu.Controller() {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean statusBarVisible() {
                return true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void setStatusBarVisible(boolean visible) {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitRight() {
                limitedSplitRight[0]++;
                limitedCanSplit[0] = false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitDown() {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitLeft() {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitUp() {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void detachTab() {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void reopenAllTabs() {
                limitedRestoreTabs[0]++;
                limitedRestorable[0] = false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void closeOtherTabs() {
                // no-op
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int openTabCount() {
                return 1;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int visibleGroupCount() {
                return 1;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean canSplitActiveTab() {
                return limitedCanSplit[0];
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean hasRestorableTabs() {
                return limitedRestorable[0];
            }
        });
        JComponent limitedComponent = limited.component();
        assertFalse(limitedComponent.getComponent(1).isEnabled(),
                "single-tab layout disables split-right toolbar action");
        assertTrue(((JButton) limitedComponent.getComponent(1)).getToolTipText().contains("duplicate-capable"),
                "disabled split-right toolbar action explains recovery");
        assertFalse(limitedComponent.getComponent(3).isEnabled(),
                "layout disables restore toolbar action when nothing can be restored");
        limitedCanSplit[0] = true;
        limitedRestorable[0] = true;
        limited.refreshControlStates();
        assertTrue(limitedComponent.getComponent(1).isEnabled(),
                "layout refresh enables split-right when the selected tab becomes splittable");
        assertTrue(limitedComponent.getComponent(3).isEnabled(),
                "layout refresh enables restore when hidden tabs appear");
        ((JButton) limitedComponent.getComponent(1)).doClick();
        ((JButton) limitedComponent.getComponent(3)).doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(limitedSplitRight[0]),
                "enabled limited split-right toolbar action runs once");
        assertEquals(Integer.valueOf(1), Integer.valueOf(limitedRestoreTabs[0]),
                "enabled limited restore toolbar action runs once");
        assertFalse(limitedComponent.getComponent(1).isEnabled(),
                "split-right toolbar action refreshes disabled state after it runs");
        assertFalse(limitedComponent.getComponent(3).isEnabled(),
                "restore toolbar action refreshes disabled state after it runs");
        JPopupMenu limitedPopup = (JPopupMenu) invoke(limited, "buildPopup", new Class<?>[0]);
        JMenuItem disabledSplit = popupItem(limitedPopup, EditorLayoutCommands.TAB_SPLIT_RIGHT_LABEL);
        assertFalse(disabledSplit.isEnabled(), "layout popup disables unavailable split action");
        assertEquals(EditorLayoutCommands.SPLIT_DISABLED_TOOLTIP, disabledSplit.getToolTipText(),
                "layout popup disabled split action explains recovery");
        JMenuItem disabledCloseOthers = popupItem(limitedPopup, EditorLayoutCommands.TAB_CLOSE_OTHERS_LABEL);
        assertFalse(disabledCloseOthers.isEnabled(), "layout popup disables close-others for a single tab");
        assertEquals(EditorLayoutCommands.CLOSE_OTHERS_DISABLED_TOOLTIP, disabledCloseOthers.getToolTipText(),
                "layout popup disabled close-others action explains recovery");
    }

    /**
     * Finds a popup menu item by label.
     *
     * @param popup popup menu
     * @param text item text
     * @return matching item
     */
    private static JMenuItem popupItem(JPopupMenu popup, String text) {
        for (Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("missing popup item " + text);
    }

    /**
     * Verifies a layout checkbox menu item uses the custom theme-aware glyph.
     *
     * @param item checkbox menu item
     * @param label assertion label
     */
    private static void assertThemedCheckIcon(JCheckBoxMenuItem item, String label) {
        Icon icon = item.getIcon();
        assertTrue(icon != null, label + " check icon present");
        assertTrue(icon.getClass().getName().contains("MenuGlyphs"), label + " check icon is themed");
        assertEquals(icon, item.getSelectedIcon(), label + " selected icon is themed");
        assertEquals(icon, item.getDisabledIcon(), label + " disabled icon is themed");
        assertTrue(icon.getIconWidth() >= 14, label + " icon width");
        assertTrue(icon.getIconHeight() >= 14, label + " icon height");
    }

    /**
     * Verifies a popup item uses workbench menu chrome and theme selection
     * colors instead of platform look-and-feel highlights.
     *
     * @param item menu item
     * @param label assertion label
     */
    private static void assertWorkbenchMenuChrome(JMenuItem item, String label) {
        assertTrue(item.getUI().getClass().getName().contains("MenuGlyphs"),
                label + " uses workbench menu UI");
        item.setSize(220, Math.max(24, item.getPreferredSize().height));
        item.getModel().setArmed(true);
        BufferedImage image = paint(item, item.getWidth(), item.getHeight());
        Color sample = new Color(image.getRGB(item.getWidth() - 6, Math.max(2, item.getHeight() / 2)), true);
        assertColor(themeColor("SELECTION_SOLID"), sample, label + " selection background");
        item.getModel().setArmed(false);
    }

    /**
     * Verifies switch toggles animate the thumb/track rather than snapping
     * directly to their final state.
     */
    private static void testToggleSwitchAnimatesStateChanges() {
        JCheckBox toggle = (JCheckBox) construct(type("ToggleBox"),
                new Class<?>[] { String.class, boolean.class }, "Follow leaf", true);
        assertEquals(Double.valueOf(0.0), (Double) field(toggle, "visualProgress"),
                "toggle starts visually off");

        toggle.setSelected(true);
        Timer timer = (Timer) field(toggle, "animationTimer");
        assertTrue(timer.isRunning(), "toggle starts animation timer when turned on");
        assertEquals(Double.valueOf(1.0), (Double) field(toggle, "animationTargetProgress"),
                "toggle animates toward on state");

        setField(toggle, "animationStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(toggle, "tickAnimation", new Class<?>[0]);
        assertFalse(timer.isRunning(), "toggle animation stops at final frame");
        assertTrue((Double) field(toggle, "visualProgress") > 0.99,
                "toggle finishes visually on");

        toggle.setSelected(false);
        assertTrue(timer.isRunning(), "toggle starts animation timer when turned off");
        assertEquals(Double.valueOf(0.0), (Double) field(toggle, "animationTargetProgress"),
                "toggle animates toward off state");
        timer.stop();
    }

    /**
     * Verifies status badges ease state changes and keep busy feedback alive.
     */
    private static void testStatusBadgeAnimatesStateChanges() {
        JComponent badge = (JComponent) construct(type("StatusBadge"), new Class<?>[0]);
        invoke(badge, "busy", new Class<?>[] { String.class }, "loading model");
        Timer timer = (Timer) field(badge, "animationTimer");
        assertTrue(timer.isRunning(), "busy status badge starts animation");
        setField(badge, "transitionStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(badge, "tickAnimation", new Class<?>[0]);
        assertFalse(timer.isRunning(), "hidden busy status badge does not keep a background timer");

        invoke(badge, "success", new Class<?>[] { String.class }, "loaded");
        setField(badge, "transitionStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(badge, "tickAnimation", new Class<?>[0]);
        assertFalse(timer.isRunning(), "settled non-busy status badge stops animation");
    }

    /**
     * Verifies status badges can reserve a stable text lane for rapidly
     * changing progress streams.
     */
    private static void testStatusBadgeCanReserveStableTextWidth() {
        JComponent badge = (JComponent) construct(type("StatusBadge"), new Class<?>[0]);
        invoke(badge, "setFixedTextWidth", new Class<?>[] { int.class }, Integer.valueOf(320));
        invoke(badge, "busy", new Class<?>[] { String.class }, "short");
        Dimension shortSize = badge.getPreferredSize();

        invoke(badge, "busy", new Class<?>[] { String.class },
                "a much longer streamed MCTS status line that should not resize ".repeat(4));
        assertEquals(shortSize, badge.getPreferredSize(), "fixed status badge width stays stable");

        invoke(badge, "setFixedTextWidth", new Class<?>[] { int.class }, Integer.valueOf(0));
        assertTrue(badge.getPreferredSize().width > shortSize.width,
                "clearing the fixed lane restores content-based sizing");
    }

    /**
     * Verifies segmented switchers glide their active underline to a new tab.
     */
    private static void testSegmentedSwitcherAnimatesSelection() {
        JComponent switcher = (JComponent) construct(type("SegmentedSwitcher"),
                new Class<?>[] { String[].class }, (Object) new String[] { "Overview", "Trace", "Atlas" });
        Dimension size = switcher.getPreferredSize();
        switcher.setSize(size);
        paint(switcher, size.width, size.height);

        invoke(switcher, "setSelectedIndex", new Class<?>[] { int.class }, 2);
        Timer timer = (Timer) field(switcher, "selectionTimer");
        assertTrue(timer.isRunning(), "segmented switcher starts selection animation");
        assertFalse(field(switcher, "indicatorStartX").equals(field(switcher, "indicatorTargetX")),
                "segmented switcher has a real underline travel distance");
        setField(switcher, "indicatorAnimationStartedAt",
                Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(switcher, "tickSelectionAnimation", new Class<?>[0]);
        assertFalse(timer.isRunning(), "segmented switcher stops after final frame");
    }

    /**
     * Verifies switched workspaces fail fast when labels and lazy builders drift
     * out of sync.
     */
    private static void testSwitchedWorkspaceRejectsMismatchedModes() {
        try {
            new SwitchedWorkspace(new String[] { "One", "Two" }, List.of(() -> new JPanel()), 0);
            throw new AssertionError("mismatched switched workspace modes should fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("labels and builders differ"),
                    "mismatched switched workspace mode counts explain the failure");
        }
    }

    /**
     * Verifies switched workspaces reject an eager mode outside the registered
     * mode list.
     */
    private static void testSwitchedWorkspaceRejectsInvalidEagerMode() {
        try {
            new SwitchedWorkspace(List.of(new WorkspaceMode("One", () -> new JPanel())), 1);
            throw new AssertionError("invalid eager mode should fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("eager mode out of range"),
                    "invalid eager mode explains the failure");
        }
    }

    /**
     * Verifies switched workspaces can be registered as mode descriptors instead
     * of parallel label and builder collections.
     */
    private static void testSwitchedWorkspaceUsesModeDescriptors() {
        AtomicInteger built = new AtomicInteger();
        SwitchedWorkspace workspace = new SwitchedWorkspace(List.of(
                new WorkspaceMode("One", () -> {
                    built.addAndGet(1);
                    return new JPanel();
                }),
                new WorkspaceMode("Two", () -> {
                    built.addAndGet(10);
                    return new JPanel();
                })), 0);
        assertEquals(Integer.valueOf(1), Integer.valueOf(built.get()),
                "eager descriptor mode builds at startup");
        workspace.setMode(1);
        assertEquals(Integer.valueOf(11), Integer.valueOf(built.get()),
                "descriptor mode builds lazily when selected");
        assertTrue(workspace.isBuilt(1), "descriptor mode build state is tracked");
        workspace.setMode(1);
        assertEquals(Integer.valueOf(11), Integer.valueOf(built.get()),
                "descriptor modes are cached after first build");
    }

    /**
     * Verifies switched workspaces clear the card host itself when a transparent
     * mode replaces a graphics-heavy mode, preventing stale board thumbnails
     * from leaking into the next panel.
     */
    private static void testSwitchedWorkspaceCardHostClearsOldFrames() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            JPanel boardLike = new JPanel() {
                /**
                 * Serialization identifier for Swing compatibility.
                 */
                private static final long serialVersionUID = 1L;

                /**
                 * Paints a high-contrast board-like old frame.
                 *
                 * @param graphics drawing context
                 */
                @Override
                protected void paintComponent(java.awt.Graphics graphics) {
                    for (int y = 0; y < getHeight(); y += 12) {
                        for (int x = 0; x < getWidth(); x += 12) {
                            graphics.setColor(((x + y) / 12) % 2 == 0
                                    ? new Color(0xB8, 0x88, 0x58)
                                    : new Color(0xEC, 0xD9, 0xB8));
                            graphics.fillRect(x, y, 12, 12);
                        }
                    }
                }
            };
            boardLike.setOpaque(false);
            JPanel transparentNext = new JPanel();
            transparentNext.setOpaque(false);
            SwitchedWorkspace workspace = new SwitchedWorkspace(List.of(
                    new WorkspaceMode("Tree", () -> boardLike),
                    new WorkspaceMode("Other", () -> transparentNext)), 0);
            JPanel body = (JPanel) field(workspace, "body");
            assertTrue(body.isOpaque(), "switched workspace body clears old card pixels");
            body.setSize(160, 100);
            body.doLayout();
            BufferedImage buffer = new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = buffer.createGraphics();
            try {
                body.paint(graphics);
                assertTrue(countExactRgb(buffer, 0xB88858) > 100,
                        "first card paints board-like old frame");
                workspace.setMode(1);
                body.setSize(160, 100);
                body.doLayout();
                body.paint(graphics);
            } finally {
                graphics.dispose();
            }
            assertEquals(Integer.valueOf(0), Integer.valueOf(countExactRgb(buffer, 0xB88858)),
                    "transparent replacement card does not retain old board pixels");
            assertEquals(Integer.valueOf(0), Integer.valueOf(countExactRgb(buffer, 0xECD9B8)),
                    "transparent replacement card clears old light-square pixels");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Counts exact RGB pixels in an image.
     *
     * @param image rendered image
     * @param rgb RGB value without alpha
     * @return matching pixel count
     */
    private static int countExactRgb(BufferedImage image, int rgb) {
        int count = 0;
        int target = rgb & 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == target) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Verifies split-pane sashes ease into their hover color.
     */
    private static void testSplitPaneSashAnimatesHover() {
        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        invokeStatic(type("SplitPaneStyler"), "style", new Class<?>[] { JSplitPane.class }, pane);
        javax.swing.plaf.basic.BasicSplitPaneDivider divider =
                ((javax.swing.plaf.basic.BasicSplitPaneUI) pane.getUI()).getDivider();

        invoke(divider, "setHover", new Class<?>[] { boolean.class }, true);
        Timer timer = (Timer) field(divider, "transitionTimer");
        assertTrue(timer.isRunning(), "split sash starts hover animation");
        assertEquals(Double.valueOf(1.0d), field(divider, "transitionTargetProgress"),
                "split sash animates toward active state");
        setField(divider, "transitionStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(divider, "tickTransition", new Class<?>[0]);
        assertFalse(timer.isRunning(), "split sash stops after hover transition");
    }

    /**
     * Verifies split panes clear their full surface before painting children so
     * stale board/tree pixels cannot leak through transparent content panes or
     * the sash.
     */
    private static void testSplitPanesClearOldFrames() {
        Theme.Mode previous = Theme.mode();
        try {
            Theme.setMode(Theme.Mode.DARK);
            JPanel left = new JPanel();
            JPanel right = new JPanel();
            left.setOpaque(false);
            right.setOpaque(false);
            JSplitPane pane = SplitPaneStyler.styledHorizontalSplit(left, right, 0.5);
            pane.setSize(180, 96);
            pane.doLayout();
            assertTrue(pane.isOpaque(), "split pane clears its workbench background");

            BufferedImage buffer = new BufferedImage(180, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D oldFrame = buffer.createGraphics();
            try {
                for (int y = 0; y < buffer.getHeight(); y += 12) {
                    for (int x = 0; x < buffer.getWidth(); x += 12) {
                        oldFrame.setColor(((x + y) / 12) % 2 == 0
                                ? new Color(0xB8, 0x88, 0x58)
                                : new Color(0xEC, 0xD9, 0xB8));
                        oldFrame.fillRect(x, y, 12, 12);
                    }
                }
                pane.paint(oldFrame);
            } finally {
                oldFrame.dispose();
            }
            assertEquals(Integer.valueOf(0), Integer.valueOf(countExactRgb(buffer, 0xB88858)),
                    "split pane clears old dark-square pixels");
            assertEquals(Integer.valueOf(0), Integer.valueOf(countExactRgb(buffer, 0xECD9B8)),
                    "split pane clears old light-square pixels");
        } finally {
            Theme.setMode(previous);
        }
    }

    /**
     * Verifies production workbench split panes use the shared sash styling
     * helper instead of the native Swing divider defaults.
     */
    private static void testWorkbenchSplitPanesUseSharedStyler() {
        Path root = Path.of("src/application/gui/workbench");
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .forEach(file -> assertSplitPaneStyling(root, file));
        } catch (java.io.IOException ex) {
            throw new AssertionError("unable to scan workbench split panes", ex);
        }
    }

    /**
     * Verifies split panes in one source file use the shared split-pane styler.
     *
     * @param root workbench source root
     * @param file source file to inspect
     */
    private static void assertSplitPaneStyling(Path root, Path file) {
        String source = readSource(file);
        int splitPaneCreations = occurrences(source, "new JSplitPane(");
        if (splitPaneCreations == 0) {
            return;
        }
        int styledSplitPanes = occurrences(source, "SplitPaneStyler.style(");
        assertTrue(styledSplitPanes >= splitPaneCreations,
                root.relativize(file) + " styles every JSplitPane through SplitPaneStyler");
    }

    /**
     * Counts non-overlapping occurrences of text in source.
     *
     * @param source source text
     * @param needle text to count
     * @return occurrence count
     */
    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /**
     * Verifies chart widgets reveal new values instead of popping them in.
     */
    private static void testChartsRevealNewData() {
        JComponent mini = (JComponent) construct(type("MiniChart"), new Class<?>[0]);
        invoke(mini, "setLine", new Class<?>[] { float[].class },
                (Object) new float[] { 0.1f, 0.4f, -0.2f });
        Timer miniTimer = (Timer) field(mini, "revealTimer");
        assertTrue(miniTimer.isRunning(), "mini chart starts reveal animation");
        miniTimer.stop();

        DatasetChart dataset = new DatasetChart();
        dataset.setBars(List.of(new DatasetChart.Bar("valid", 7L, DatasetChart.Role.SUCCESS)));
        Timer datasetTimer = (Timer) field(dataset, "barRevealTimer");
        assertTrue(datasetTimer.isRunning(), "dataset chart starts reveal animation");
        datasetTimer.stop();
    }

    /**
     * Verifies the command-builder required-row lead column pins its control to
     * the shared lead width so stacked required rows line up.
     */
    private static void testCommandFormLeadColumnAligns() {
        JComponent toggle = (JComponent) construct(type("ToggleBox"),
                new Class<?>[] { String.class, boolean.class }, "--quiet", true);
        JComponent holder = (JComponent) invokeStatic(type("CommandForm"), "fixedLead",
                new Class<?>[] { javax.swing.JComponent.class }, toggle);
        holder.setSize(holder.getPreferredSize());
        holder.doLayout();

        int leadWidth = (Integer) staticField(type("CommandForm"), "LEAD_WIDTH");
        assertEquals(Integer.valueOf(leadWidth), Integer.valueOf(toggle.getWidth()),
                "required lead column pins its control to the lead width");
    }

    /**
     * Verifies command-builder flag cells flow into additional columns when the
     * optional section is wide and stack into one column when it is narrow.
     */
    private static void testCommandFormFlagsWrapAcrossColumns() {
        JPanel flow = new JPanel(new application.gui.workbench.ui.WrappingFlowLayout(
                java.awt.FlowLayout.LEFT, 12, 4));
        for (int i = 0; i < 6; i++) {
            JPanel cell = new JPanel();
            cell.setPreferredSize(new Dimension(220, 36));
            flow.add(cell);
        }

        flow.setSize(1600, 200);
        flow.doLayout();
        Component first = flow.getComponent(0);
        Component second = flow.getComponent(1);
        assertEquals(Integer.valueOf(first.getY()), Integer.valueOf(second.getY()),
                "flag cells share a row when wide");
        assertTrue(second.getX() > first.getX(), "second cell sits to the right of the first");

        flow.setSize(244, 400);
        flow.doLayout();
        assertTrue(flow.getComponent(1).getY() > flow.getComponent(0).getY(),
                "flag cells stack into one column when narrow");
    }

    /**
     * Verifies the Run command-builder Flags disclosure scrolls internally
     * instead of expanding the whole settings card to the height of every flag.
     */
    private static void testCommandFormFlagsDisclosureIsBounded() {
        CommandForm form = new CommandForm();
        CommandTemplates.CommandTemplate template = CommandTemplates.commandTemplates().stream()
                .filter(candidate -> "Generate FENs".equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Generate FENs template"));
        form.setTemplate(template, new CommandTemplates.TemplateContext(
                START_FEN, "4s", "5", "3", "6", "config/default.engine.toml", "900", "128"));

        JComponent disclosure = (JComponent) field(form, "optionalDisclosure");
        assertTrue(Ui.setCollapsibleExpanded(disclosure, true), "command flags uses shared collapsible section");
        JScrollPane scroll = firstDescendant(disclosure, JScrollPane.class);
        assertTrue(scroll != null, "command flags disclosure owns a bounded scroll pane");
        assertEquals(Integer.valueOf(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),
                Integer.valueOf(scroll.getHorizontalScrollBarPolicy()),
                "command flags avoid horizontal scrolling");
        int cap = (Integer) staticField(type("CommandForm"), "FLAGS_MAX_EXPANDED_HEIGHT");
        assertTrue(scroll.getPreferredSize().height <= cap,
                "command flags internal scroll pane respects the shared cap");
        assertEquals(Integer.valueOf(disclosure.getPreferredSize().height),
                Integer.valueOf(disclosure.getMaximumSize().height),
                "command flags disclosure does not absorb spare vertical space");
    }

    /**
     * Verifies core theme foreground/background pairs meet practical contrast
     * thresholds for extended workbench use.
     */
    private static void testThemeColorContrast() {
        Class<?> modeType = type("Theme$Mode");
        for (String modeName : new String[] { "LIGHT", "DARK", "DARK_BLUE" }) {
            invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                    enumValue(modeType, modeName));
            assertCurrentThemeContrast(modeName.toLowerCase(java.util.Locale.ROOT));
        }
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies the workbench uses its macOS-flavored neutral and accent tokens
     * consistently across light and dark modes.
     */
    private static void testThemeUsesVscodeVisualStudioColorTokens() {
        Theme.setMode(Theme.Mode.LIGHT);
        assertColor(new Color(0xF4F4F5), themeColor("BG"), "light macOS widget background");
        assertColor(Color.WHITE, themeColor("PANEL_SOLID"), "light editor background");
        assertColor(new Color(0xF4F4F5), themeColor("ELEVATED_SOLID"), "light dropdown background");
        assertColor(new Color(0xDADADF), themeColor("LINE"), "light widget border");
        assertColor(new Color(0xC6C6CC), themeColor("INPUT_BORDER"), "light input border");
        assertColor(new Color(0xECECEF), themeColor("TAB_HOVER"), "light tab hover");
        assertColor(new Color(0xF4F4F5), themeColor("TAB_IDLE"), "light inactive tab");
        assertColor(new Color(0x1F1F24), themeColor("TEXT"), "light foreground");
        assertColor(new Color(0x6E6E73), themeColor("MUTED"), "light muted foreground");
        assertColor(new Color(0x0A5CC0), themeColor("ACCENT"), "light macOS focus accent");
        assertColor(new Color(0xD5EBFF), themeColor("TOGGLE_ON_BG"), "light active option fill");

        Theme.setMode(Theme.Mode.DARK);
        assertColor(new Color(0x181818), themeColor("BG"), "dark neutral menu background");
        assertColor(new Color(0x1E1E1E), themeColor("PANEL_SOLID"), "dark neutral editor background");
        assertColor(new Color(0x252526), themeColor("ELEVATED_SOLID"), "dark neutral dropdown background");
        assertColor(new Color(0x333333), themeColor("LINE"), "dark neutral widget border");
        assertColor(new Color(0x3C3C3C), themeColor("INPUT_BORDER"), "dark neutral menu/input border");
        assertColor(new Color(0x333333), themeColor("TAB_HOVER"), "dark neutral tab hover");
        assertColor(new Color(0x181818), themeColor("TAB_IDLE"), "dark neutral inactive tab");
        assertColor(new Color(0xD4D4D4), themeColor("TEXT"), "dark neutral foreground");
        assertColor(new Color(0xA0A0A0), themeColor("MUTED"), "dark neutral muted foreground");
        assertColor(new Color(0x2E74D0), themeColor("ACCENT"), "dark neutral focus accent");
        assertColor(new Color(45, 133, 211, 145), themeColor("TOGGLE_ON_BG"),
                "dark neutral active option fill");

        Theme.setMode(Theme.Mode.DARK_BLUE);
        assertColor(new Color(0x0A1118), themeColor("BG"), "dark blue menu background");
        assertColor(new Color(0x0D1822), themeColor("PANEL_SOLID"), "dark blue editor background");
        assertColor(new Color(0x122131), themeColor("ELEVATED_SOLID"), "dark blue dropdown background");
        assertColor(new Color(0x203449), themeColor("LINE"), "dark blue widget border");
        assertColor(new Color(0x2A4560), themeColor("INPUT_BORDER"), "dark blue menu/input border");
        assertColor(new Color(0x203449), themeColor("TAB_HOVER"), "dark blue tab hover");
        assertColor(new Color(0x0A1118), themeColor("TAB_IDLE"), "dark blue inactive tab");
        assertColor(new Color(0xDDEBFA), themeColor("TEXT"), "dark blue foreground");
        assertColor(new Color(0x91A5B8), themeColor("MUTED"), "dark blue muted foreground");
        assertColor(new Color(0x2E74D0), themeColor("ACCENT"), "dark blue focus accent");
        assertColor(new Color(46, 116, 208, 145), themeColor("TOGGLE_ON_BG"),
                "dark blue active option fill");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies NN visual helpers use the semantic NN palette instead of the
     * generic application accent.
     */
    private static void testNetworkPaletteUsesSemanticFocusColor() {
        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
        assertColor(themeColor("NN_FOCUS"), TensorViz.FOCUS, "light NN focus alias");
        assertColor(TensorViz.FOCUS, TensorViz.sequentialRamp(1.0f), "light sequential NN ramp");

        Theme.setMode(Theme.Mode.DARK);
        TensorViz.refreshPalette();
        assertColor(themeColor("NN_FOCUS"), TensorViz.FOCUS, "dark NN focus alias");
        assertColor(TensorViz.FOCUS, TensorViz.sequentialRamp(1.0f), "dark sequential NN ramp");
        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
    }

    /**
     * Verifies NN architecture blocks keep high-chroma semantic colors out of
     * large text-bearing backgrounds.
     */
    private static void testNetworkArchitectureBlocksKeepReadableNeutralFill() {
        Theme.setMode(Theme.Mode.DARK);
        TensorViz.refreshPalette();
        BufferedImage image = new BufferedImage(160, 90, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        TensorViz.drawAbstractBlock(graphics, new java.awt.Rectangle(10, 10, 140, 64),
                "value", "W/D/L", 1.0f, TensorViz.VALUE);
        graphics.dispose();

        Color fill = new Color(image.getRGB(80, 56), true);
        assertTrue(colorDistance(fill, themeColor("PANEL_SOLID")) < colorDistance(fill, TensorViz.VALUE),
                "dark NN architecture block stays closer to neutral panel than green accent");
        assertTrue(contrastRatio(themeColor("TEXT"), fill) >= 4.5,
                "dark NN architecture title remains readable");
        assertTrue(contrastRatio(themeColor("MUTED"), fill) >= 4.5,
                "dark NN architecture subtitle remains readable");

        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
    }

    /**
     * Verifies wrapped NNUE atlas bank labels do not inherit the selected-plane
     * or heatmap overlay color from the previous bank.
     */
    private static void testNnueAtlasPlaneLabelsUseUniformColor() {
        Object view = construct(type("NnueView"), new Class<?>[0]);
        BufferedImage image = new BufferedImage(160, 24, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setFont(Theme.font(9, Font.BOLD));
        graphics.setColor(TensorViz.FOCUS);
        invokeOn(type("NnueAtlasView"), view, "paintAtlasPlaneLabels",
                new Class<?>[] {
                    java.awt.Graphics2D.class,
                    java.awt.Rectangle.class,
                    int.class,
                    java.awt.FontMetrics.class
                },
                graphics,
                new java.awt.Rectangle(0, 0, 132, 14),
                11,
                graphics.getFontMetrics());

        assertEquals(themeColor("MUTED"), graphics.getColor(),
                "NNUE atlas plane labels use one uniform muted color");
        graphics.dispose();
    }

    /**
     * Verifies signed metric bars reserve a label lane instead of painting the
     * positive/negative fill underneath the value text.
     */
    private static void testHorizontalMetricBarKeepsLabelLaneClear() {
        Theme.setMode(Theme.Mode.DARK);
        TensorViz.refreshPalette();
        String label = "+72 cp";
        BufferedImage image = new BufferedImage(240, 36, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        java.awt.Rectangle rect = new java.awt.Rectangle(5, 7, 220, 20);
        graphics.setFont(Theme.font(11, Font.PLAIN));
        int reservedWidth = graphics.getFontMetrics().stringWidth(label) + 16;
        TensorViz.drawHorizontalBar(graphics, rect, 72.0f, 80.0f, label);
        graphics.dispose();

        int laneX = rect.x + rect.width - reservedWidth + 4;
        Color laneSample = new Color(image.getRGB(laneX, rect.y + rect.height / 2), true);
        assertColor(themeColor("PANEL_SOLID"), laneSample,
                "horizontal NN metric bar keeps value lane neutral");

        Theme.setMode(Theme.Mode.LIGHT);
        TensorViz.refreshPalette();
    }

    /**
     * Verifies board annotation brushes use fixed, theme-independent colours (an
     * arrow keeps its colour in light and dark, as in every other chess UI) and
     * that the four gesture colours are clearly distinct from one another.
     */
    private static void testBoardMarkupBrushesUseFixedDistinctColors() {
        Theme.setMode(Theme.Mode.LIGHT);
        Color green = MarkupBrush.forGesture(0).displayColor();
        Color red = MarkupBrush.forGesture(1).displayColor();
        Color blue = MarkupBrush.forGesture(2).displayColor();
        Color yellow = MarkupBrush.forGesture(3).displayColor();
        assertEquals(new Color(0x81, 0xB6, 0x4C), green,
                "green markup uses chess.com-style best-move colour");
        assertEquals(new Color(0xD9, 0x51, 0x4E), red,
                "red markup uses chess.com-style blunder colour");
        assertEquals(new Color(0x2D, 0x70, 0xB8), blue,
                "blue markup uses chess.com-style analysis colour");
        assertEquals(new Color(0xF0, 0xB1, 0x3A), yellow,
                "yellow markup uses chess.com-style inaccuracy colour");
        assertTrue(MarkupBrush.glyphs().containsAll(List.of("□", "Zz", "=", "∞", "±", "∓", "+=", "=+",
                        "+-", "-+", AnnotationGlyphs.DRAW_RESULT, AnnotationGlyphs.WHITE_CHECKMATED,
                        AnnotationGlyphs.BLACK_CHECKMATED, "↑↑", "↑", "→", "⇆", "⊕", "=∞", "△", "MW",
                        "Bk", "Pin", AnnotationGlyphs.FORK, AnnotationGlyphs.SKEWER,
                        AnnotationGlyphs.DISCOVERED_ATTACK, AnnotationGlyphs.DOUBLE_ATTACK,
                        AnnotationGlyphs.XRAY, AnnotationGlyphs.BATTERY)),
                "draw glyph picker exposes tactical annotation symbols");

        // The colour must not change with the theme.
        Theme.setMode(Theme.Mode.DARK);
        assertEquals(green, MarkupBrush.forGesture(0).displayColor(),
                "green markup colour is theme-independent");
        assertEquals(blue, MarkupBrush.forGesture(2).displayColor(),
                "blue markup colour is theme-independent");

        // The four gesture colours must be visually distinct.
        assertColorDistanceAtLeast(green, red, 16.0, "green and red markups differ");
        assertColorDistanceAtLeast(blue, yellow, 16.0, "blue and yellow markups differ");
        assertColorDistanceAtLeast(green, blue, 16.0, "green and blue markups differ");

        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the Draw rail exposes the expected annotation controls.
     */
    private static void testDrawPanelBuildsAnnotationControls() {
        BoardPanel board = new BoardPanel();
        DrawPanel panel = new DrawPanel(board, new JPanel());
        assertTrue(panel.getPreferredSize().width <= 460,
                "draw panel uses a bounded inspector width");
        assertTrue(componentTreeHasLabelText(panel, "Presets"),
                "draw panel exposes one-click annotation presets");
        assertEquals("Brush", buttonWithText(panel, "Brush").getText(),
                "draw panel collapses brush controls");
        assertEquals("Color", buttonWithText(panel, "Color").getText(),
                "draw panel collapses color controls");
        assertTrue(componentTreeHasLabelText(panel, "Target"),
                "draw panel exposes color target picker");
        assertTrue(componentTreeHasLabelText(panel, "Shape"),
                "draw panel exposes shape picker");
        assertTrue(componentTreeHasLabelText(panel, "Glyph"),
                "draw panel exposes glyph picker");
        assertTrue(componentTreeHasLabelText(panel, "Opacity"),
                "draw panel exposes opacity control");
        assertTrue(componentTreeHasLabelText(panel, "Width"),
                "draw panel exposes arrow width control");
        assertTrue(componentTreeHasLabelText(panel, "Border"),
                "draw panel exposes border thickness control");
        assertTrue(componentTreeHasLabelText(panel, "Rounded corners"),
                "draw panel exposes rectangle corner style control");
        assertTrue(componentTreeHasLabelText(panel, "Details"),
                "draw panel exposes annotation detail visibility control");
        JList<?> annotationList = (JList<?>) field(panel, "annotationList");
        assertTrue(annotationList.getCellRenderer().getClass().getName().contains("AnnotationRenderer"),
                "draw panel uses a themed annotation row renderer");
        assertTrue(annotationList.getFixedCellHeight() >= 64,
                "draw panel annotation rows reserve room for detail text");
        assertTrue(annotationList.getComponentPopupMenu() != null,
                "draw panel annotation list exposes row context actions");
        assertFalse(componentTreeHasLabelText(panel, "Red"),
                "draw panel keeps redundant RGB sliders out of the rail");
        assertFalse(componentTreeHasLabelText(panel, "Hue"),
                "draw panel keeps redundant HSV sliders out of the rail");
        assertTrue(componentTreeHasLabelText(panel, "Hex"),
                "draw panel exposes direct hexadecimal color entry");
        assertTrue(componentTreeHasLabelText(panel, "ARGB"),
                "draw panel exposes direct ARGB color entry");
        JComponent colorPlane = (JComponent) field(panel, "colorPlane");
        assertTrue(colorPlane.getPreferredSize().width <= 430,
                "draw panel color picker stays within inspector width");
        assertFalse(componentTreeHasTooltip(panel, "Current stroke preview."),
                "draw panel does not include the removed stroke preview card");
        assertTrue(componentTreeHasLabelText(panel, "Annotations"),
                "draw panel exposes annotation history");
        assertTrue(componentTreeHasLabelText(panel, "Export"),
                "draw panel exposes grouped export controls");
        assertEquals(MarkupBrush.DEFAULT_GLYPH, board.directAnnotationBrush().glyph(),
                "draw panel starts with the default glyph annotation");
        assertEquals(Integer.valueOf(MarkupBrush.DEFAULT_BORDER_WIDTH),
                Integer.valueOf(board.directAnnotationBrush().displayBorderWidth()),
                "draw panel starts with the default border width");
        assertFalse(board.directAnnotationBrush().displayRoundedRectangle(),
                "draw panel starts with square rectangle corners");
        JComponent glyphPicker = (JComponent) field(panel, "glyphPicker");
        assertFalse(glyphPicker.isEnabled(),
                "draw panel disables exact glyph choices while shape is not glyph");
        JButton blunderPreset = buttonWithText(panel, "Blunder");
        assertEquals(new Color(0x8B, 0x4C, 0xE0),
                buttonWithText(panel, "Brilliant move").getClientProperty("workbench.draw.preset.fill"),
                "draw panel brilliant preset uses the gradient magenta");
        assertEquals(new Color(0x79, 0xB9, 0x4A),
                buttonWithText(panel, "Good move").getClientProperty("workbench.draw.preset.fill"),
                "draw panel good-move preset uses the gradient green");
        assertEquals(new Color(0x2F, 0x78, 0xC4),
                buttonWithText(panel, "Interesting move").getClientProperty("workbench.draw.preset.fill"),
                "draw panel interesting-move preset uses the gradient blue");
        assertEquals("!?", buttonWithText(panel, "Interesting move").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel annotation menu exposes the selected glyph token");
        assertTrue(readSource(Path.of("src/application/gui/workbench/draw/DrawPanel.java"))
                        .contains("AnnotationGlyphs.paintCustom"),
                "draw panel previews custom move presets as vector glyphs");
        assertTrue(readSource(Path.of("src/application/gui/workbench/board/BoardMarkupPainter.java"))
                        .contains("AnnotationGlyphs.paintCustom"),
                "board painter renders custom move markers as vector glyphs");
        assertTrue(readSource(Path.of("src/application/gui/workbench/board/BoardExporter.java"))
                        .contains("appendCustomGlyph"),
                "board exporter preserves custom move vector glyphs");
        assertEquals(new Color(0xF4, 0xC5, 0x42),
                buttonWithText(panel, "Dubious move").getClientProperty("workbench.draw.preset.fill"),
                "draw panel dubious-move preset uses the gradient yellow");
        assertEquals(new Color(0xF3, 0x9A, 0x35),
                buttonWithText(panel, "Mistake").getClientProperty("workbench.draw.preset.fill"),
                "draw panel mistake preset uses the gradient orange");
        assertEquals(new Color(0xC9, 0x4A, 0x3A),
                blunderPreset.getClientProperty("workbench.draw.preset.fill"),
                "draw panel blunder preset exposes the gradient red");
        assertEquals(new Color(0xA9, 0x44, 0x52),
                buttonWithText(panel, "Only move").getClientProperty("workbench.draw.preset.fill"),
                "draw panel only-move preset uses the requested red");
        assertEquals(new Color(0x7C, 0x5A, 0xE6),
                buttonWithText(panel, "Zugzwang").getClientProperty("workbench.draw.preset.fill"),
                "draw panel zugzwang preset uses the requested purple");
        assertEquals(Color.WHITE, blunderPreset.getClientProperty("workbench.draw.preset.border"),
                "draw panel blunder preset exposes its white border");
        assertEquals(BoardMarkupTool.GLYPH, blunderPreset.getClientProperty("workbench.draw.preset.tool"),
                "draw panel blunder preset exposes glyph tool");
        assertTrue(blunderPreset.getToolTipText().contains("width"),
                "draw panel preset tooltip summarizes brush settings");
        blunderPreset.doClick();
        assertEquals(BoardMarkupTool.GLYPH, board.markupTool(),
                "draw panel glyph preset switches to glyph shape");
        assertEquals("??", board.directAnnotationBrush().glyph(),
                "draw panel glyph preset applies the selected glyph");
        assertEquals(Integer.valueOf(255), Integer.valueOf(board.directAnnotationBrush().displayColor().getAlpha()),
                "draw panel preset applies fully opaque fill");
        assertEquals(Color.WHITE, board.directAnnotationBrush().displayBorderColor(),
                "draw panel preset applies a white border");
        assertTrue(glyphPicker.isEnabled(),
                "draw panel enables exact glyph choices for glyph shape");
        assertEquals(new Color(0x6E, 0x77, 0x81),
                buttonWithText(panel, "Equal position").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes equal-position notation in the menu");
        assertEquals(new Color(0x6E, 0x77, 0x81),
                buttonWithText(panel, "Unclear position").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes unclear-position notation in the menu");
        assertEquals(new Color(0x79, 0xB9, 0x4A),
                buttonWithText(panel, "White is slightly better").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes white-slightly-better symbol with good-move green");
        assertEquals(new Color(0xC9, 0x4A, 0x3A),
                buttonWithText(panel, "Black is slightly better").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes black-slightly-better symbol with threat red");
        assertEquals(new Color(0x79, 0xB9, 0x4A),
                buttonWithText(panel, "White is winning").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes white-winning symbol with the requested neutral fill");
        assertEquals(new Color(0xC9, 0x4A, 0x3A),
                buttonWithText(panel, "Black is winning").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes black-winning symbol with threat red");
        assertEquals(new Color(0x6E, 0x77, 0x81),
                buttonWithText(panel, "Draw").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes draw-result symbol with a neutral fill");
        assertEquals(new Color(0xEC, 0xEF, 0xF4),
                buttonWithText(panel, "White checkmated").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes white-checkmated symbol with a light-side fill");
        assertEquals(new Color(0x1F, 0x23, 0x28),
                buttonWithText(panel, "White checkmated").getClientProperty("workbench.draw.preset.border"),
                "draw panel exposes white-checkmated symbol with a dark mark");
        assertEquals(new Color(0x2A, 0x2D, 0x33),
                buttonWithText(panel, "Black checkmated").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes black-checkmated symbol with a dark-side fill");
        assertEquals(AnnotationGlyphs.WHITE_WINNING,
                buttonWithText(panel, "White is winning").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses explicit winning glyph token");
        assertEquals(AnnotationGlyphs.DRAW_RESULT,
                buttonWithText(panel, "Draw").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses explicit draw-result glyph token");
        assertEquals(AnnotationGlyphs.WHITE_CHECKMATED,
                buttonWithText(panel, "White checkmated").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses explicit white-checkmated glyph token");
        assertEquals(AnnotationGlyphs.BLACK_CHECKMATED,
                buttonWithText(panel, "Black checkmated").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses explicit black-checkmated glyph token");
        assertEquals(BoardMarkupTool.GLYPH,
                buttonWithText(panel, "Novelty").getClientProperty("workbench.draw.preset.tool"),
                "draw panel exposes novelty glyph preset");
        assertEquals(new Color(0xFF, 0x71, 0x4B),
                buttonWithText(panel, "Counterplay").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes counterplay with the requested orange fill");
        assertEquals(AnnotationGlyphs.COUNTERPLAY,
                buttonWithText(panel, "Counterplay").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses the custom counterplay glyph token");
        assertEquals(new Color(0x6F, 0x52, 0xCC),
                buttonWithText(panel, "Fork").getClientProperty("workbench.draw.preset.fill"),
                "draw panel exposes fork with the tactical purple fill");
        assertEquals(AnnotationGlyphs.FORK,
                buttonWithText(panel, "Fork").getClientProperty("workbench.draw.preset.glyph"),
                "draw panel uses the custom fork glyph token");
        assertEquals(BoardMarkupTool.GLYPH,
                buttonWithText(panel, "With the idea").getClientProperty("workbench.draw.preset.tool"),
                "draw panel exposes idea glyph preset");
        JButton circlePreset = buttonWithText(panel, "Circle");
        assertEquals(new Color(0x2F, 0x78, 0xC4),
                circlePreset.getClientProperty("workbench.draw.preset.fill"),
                "draw panel circle preset exposes its blue fill");
        assertEquals(BoardMarkupTool.CIRCLE, circlePreset.getClientProperty("workbench.draw.preset.tool"),
                "draw panel circle preset exposes circle tool");
        assertEquals(Integer.valueOf(10), circlePreset.getClientProperty("workbench.draw.preset.lineWidth"),
                "draw panel circle preset exposes line width");
        circlePreset.doClick();
        assertEquals(BoardMarkupTool.CIRCLE, board.markupTool(),
                "draw panel shape preset switches to circle shape");
        assertFalse(glyphPicker.isEnabled(),
                "draw panel disables exact glyph choices after leaving glyph shape");
        JButton roundPreset = buttonWithText(panel, "Round");
        assertEquals(Boolean.TRUE, roundPreset.getClientProperty("workbench.draw.preset.rounded"),
                "draw panel round preset exposes rounded rectangle state");
        AbstractButton roundedToggle = (AbstractButton) field(panel, "roundedRectangleToggle");
        roundedToggle.doClick();
        assertTrue(board.directAnnotationBrush().displayRoundedRectangle(),
                "draw panel rounded-corner toggle updates the active brush");
        roundedToggle.doClick();
        AbstractButton glyphShadowToggle = (AbstractButton) field(panel, "glyphShadowToggle");
        assertTrue(board.isGlyphShadow(),
                "draw panel starts with the glyph drop shadow enabled");
        glyphShadowToggle.doClick();
        assertFalse(board.isGlyphShadow(),
                "draw panel glyph-shadow toggle disables the board glyph drop shadow");
        glyphShadowToggle.doClick();
        assertTrue(board.isGlyphShadow(),
                "draw panel glyph-shadow toggle re-enables the board glyph drop shadow");
        assertTrue(panel.workspaceContext().contains("0 arrows"),
                "draw panel starts with detailed workspace context");
        AbstractButton detailsToggle = (AbstractButton) field(panel, "detailsToggle");
        detailsToggle.doClick();
        assertTrue(panel.workspaceContext().contains("0 annotations"),
                "draw panel compact context shows total annotation count");
        assertFalse(panel.workspaceContext().contains("0 arrows"),
                "draw panel compact context hides per-shape counts");
        assertTrue(annotationList.getFixedCellHeight() <= 40,
                "draw panel compact annotation rows collapse to one readable line");
        detailsToggle.doClick();
        assertTrue(annotationList.getFixedCellHeight() >= 64,
                "draw panel detailed annotation rows restore two-line height");
        JTextField hexField = (JTextField) field(panel, "hexField");
        JSpinner opacitySpinner = (JSpinner) field(panel, "opacitySpinner");
        JSpinner alphaSpinner = (JSpinner) field(panel, "alphaSpinner");
        JSpinner redSpinner = (JSpinner) field(panel, "redSpinner");
        JSpinner greenSpinner = (JSpinner) field(panel, "greenSpinner");
        JSpinner blueSpinner = (JSpinner) field(panel, "blueSpinner");
        assertDrawChannelSpinnerWidth(opacitySpinner, "opacity");
        assertDrawChannelSpinnerWidth(alphaSpinner, "alpha");
        assertDrawChannelSpinnerWidth(redSpinner, "red");
        assertDrawChannelSpinnerWidth(greenSpinner, "green");
        assertDrawChannelSpinnerWidth(blueSpinner, "blue");
        opacitySpinner.setValue(Integer.valueOf(96));
        assertEquals(Integer.valueOf(96), Integer.valueOf(board.directAnnotationBrush().displayColor().getAlpha()),
                "draw panel opacity spinner updates brush alpha");
        hexField.setText("#80445566");
        hexField.postActionEvent();
        assertEquals(new Color(0x44, 0x55, 0x66, 0x80), board.directAnnotationBrush().displayColor(),
                "draw panel hexadecimal entry updates fill color and alpha");
        alphaSpinner.setValue(Integer.valueOf(96));
        redSpinner.setValue(Integer.valueOf(17));
        greenSpinner.setValue(Integer.valueOf(34));
        blueSpinner.setValue(Integer.valueOf(51));
        assertEquals(new Color(17, 34, 51, 96), board.directAnnotationBrush().displayColor(),
                "draw panel ARGB channel spinners update fill color and alpha");
        board.addArrow(Field.toIndex('b', '1'), Field.toIndex('c', '3'), new Color(0xCB, 0x37, 0x37, 180), 18);
        invoke(panel, "refreshAnnotationState", new Class<?>[0]);
        annotationList.clearSelection();
        annotationList.setSelectedIndex(0);
        assertEquals(BoardMarkupTool.ARROW, board.markupTool(),
                "draw panel annotation selection restores the selected shape");
        assertEquals(new Color(0xCB, 0x37, 0x37, 180), board.directAnnotationBrush().displayColor(),
                "draw panel annotation selection uses the selected brush as template");
        assertEquals(Theme.BOARD_LIGHT, board.boardLightColor(),
                "draw panel starts with theme light square color");
        assertEquals(Theme.BOARD_DARK, board.boardDarkColor(),
                "draw panel starts with theme dark square color");
    }

    /**
     * Verifies a Draw color-channel spinner has enough room for 0-255 values
     * plus the stepper buttons.
     *
     * @param spinner spinner to inspect
     * @param label assertion label
     */
    private static void assertDrawChannelSpinnerWidth(JSpinner spinner, String label) {
        assertTrue(spinner.getPreferredSize().width >= 76,
                "draw panel " + label + " channel spinner has readable preferred width");
        assertTrue(spinner.getMinimumSize().width >= 76,
                "draw panel " + label + " channel spinner has readable minimum width");
    }

    /**
     * Verifies Draw mode routes left-drag gestures into persistent board
     * annotations, including circle-only mode.
     */
    private static void testDrawModeLeftDragAddsAnnotation() {
        BoardPanel board = new BoardPanel();
        board.setSize(520, 520);
        board.setPositionInstant(new Position(START_FEN), Move.NO_MOVE);
        board.setDirectAnnotationMode(true);
        board.setMarkupTool(BoardMarkupTool.ARROW);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x21, 0x9E, 0x3C, 212), 12));
        board.doLayout();

        Point e2 = squareCenter(board, (byte) 52);
        Point e4 = squareCenter(board, (byte) 36);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e2, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "left-drag draw mode adds an arrow annotation");

        board.setMarkupTool(BoardMarkupTool.CIRCLE);
        Point d4 = squareCenter(board, (byte) 35);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(2), Integer.valueOf(board.markupCount()),
                "circle draw mode adds a circle annotation");
        assertTrue(board.boardMarkups().get(1).isCircle(), "second draw annotation is a circle");

        board.setMarkupTool(BoardMarkupTool.RECTANGLE);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x21, 0x9E, 0x3C, 212),
                new Color(245, 245, 245, 212), 12, MarkupBrush.DEFAULT_BORDER_WIDTH,
                MarkupBrush.DEFAULT_GLYPH, true));
        Point c5 = squareCenter(board, (byte) 26);
        Point e3 = squareCenter(board, (byte) 44);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, c5, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1_DOWN_MASK, e3, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e3, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(3), Integer.valueOf(board.markupCount()),
                "rectangle draw mode adds a filled rectangle annotation");
        BoardMarkup rectangle = board.boardMarkups().get(2);
        assertTrue(rectangle.isRectangle(), "third draw annotation is a rectangle");
        assertTrue(rectangle.brush().displayRoundedRectangle(),
                "rectangle draw mode stores selected rounded-corner style");

        board.setMarkupTool(BoardMarkupTool.GLYPH);
        // "T" is a free-form glyph with no registered vector form, so it exercises the
        // text-fallback export path (every palette glyph now renders as a vector badge).
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x21, 0x9E, 0x3C, 212), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, "T"));
        Point f6 = squareCenter(board, (byte) 21);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, f6, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, f6, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(4), Integer.valueOf(board.markupCount()),
                "glyph draw mode adds a glyph annotation");
        BoardMarkup glyph = board.boardMarkups().get(3);
        assertTrue(glyph.isGlyph(), "fourth draw annotation is a glyph");
        assertEquals("T", glyph.brush().glyph(), "glyph annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x16, 0x82, 0x26), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BRILLIANT_MOVE));
        Point h6 = squareCenter(board, (byte) 23);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, h6, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, h6, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(5), Integer.valueOf(board.markupCount()),
                "brilliant-move glyph draw mode adds a vector glyph annotation");
        BoardMarkup brilliantGlyph = board.boardMarkups().get(4);
        assertTrue(brilliantGlyph.isGlyph(), "fifth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.BRILLIANT_MOVE, brilliantGlyph.brush().glyph(),
                "brilliant-move annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x22, 0xAC, 0x38), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.EXCELLENT_MOVE));
        Point g5 = squareCenter(board, (byte) 30);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, g5, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, g5, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(6), Integer.valueOf(board.markupCount()),
                "excellent-move glyph draw mode adds a vector glyph annotation");
        BoardMarkup excellentGlyph = board.boardMarkups().get(5);
        assertTrue(excellentGlyph.isGlyph(), "sixth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.EXCELLENT_MOVE, excellentGlyph.brush().glyph(),
                "excellent-move annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xEA, 0x45, 0xD8), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.INTERESTING_MOVE));
        Point h5 = squareCenter(board, (byte) 31);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, h5, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, h5, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(7), Integer.valueOf(board.markupCount()),
                "interesting-move glyph draw mode adds a vector glyph annotation");
        BoardMarkup interestingGlyph = board.boardMarkups().get(6);
        assertTrue(interestingGlyph.isGlyph(), "seventh draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.INTERESTING_MOVE, interestingGlyph.brush().glyph(),
                "interesting-move annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x56, 0xB4, 0xE9), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.DUBIOUS_MOVE));
        Point g4 = squareCenter(board, (byte) 38);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, g4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, g4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(8), Integer.valueOf(board.markupCount()),
                "dubious-move glyph draw mode adds a vector glyph annotation");
        BoardMarkup dubiousGlyph = board.boardMarkups().get(7);
        assertTrue(dubiousGlyph.isGlyph(), "eighth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.DUBIOUS_MOVE, dubiousGlyph.brush().glyph(),
                "dubious-move annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xDF, 0x53, 0x53), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BLUNDER_MOVE));
        Point h4 = squareCenter(board, (byte) 39);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, h4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, h4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(9), Integer.valueOf(board.markupCount()),
                "blunder glyph draw mode adds a vector glyph annotation");
        BoardMarkup blunderGlyph = board.boardMarkups().get(8);
        assertTrue(blunderGlyph.isGlyph(), "ninth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.BLUNDER_MOVE, blunderGlyph.brush().glyph(),
                "blunder annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xA0, 0x40, 0x48), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.ONLY_MOVE));
        Point g3 = squareCenter(board, (byte) 46);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, g3, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, g3, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(10), Integer.valueOf(board.markupCount()),
                "only-move glyph draw mode adds a vector glyph annotation");
        BoardMarkup onlyMoveGlyph = board.boardMarkups().get(9);
        assertTrue(onlyMoveGlyph.isGlyph(), "tenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.ONLY_MOVE, onlyMoveGlyph.brush().glyph(),
                "only-move annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x91, 0x71, 0xF2), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.ZUGZWANG));
        Point f3 = squareCenter(board, (byte) 45);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, f3, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, f3, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(11), Integer.valueOf(board.markupCount()),
                "zugzwang glyph draw mode adds a vector glyph annotation");
        BoardMarkup zugzwangGlyph = board.boardMarkups().get(10);
        assertTrue(zugzwangGlyph.isGlyph(), "eleventh draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.ZUGZWANG, zugzwangGlyph.brush().glyph(),
                "zugzwang annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xBB, 0xBB, 0xBB), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.WHITE_WINNING));
        Point e3Winning = squareCenter(board, (byte) 44);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK,
                e3Winning, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e3Winning, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(12), Integer.valueOf(board.markupCount()),
                "white-winning glyph draw mode adds a vector glyph annotation");
        BoardMarkup whiteWinningGlyph = board.boardMarkups().get(11);
        assertTrue(whiteWinningGlyph.isGlyph(), "twelfth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.WHITE_WINNING, whiteWinningGlyph.brush().glyph(),
                "white-winning annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xFF, 0x78, 0x4F), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.COUNTERPLAY));
        Point d3Counterplay = squareCenter(board, (byte) 43);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK,
                d3Counterplay, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d3Counterplay, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(13), Integer.valueOf(board.markupCount()),
                "counterplay glyph draw mode adds a vector glyph annotation");
        BoardMarkup counterplayGlyph = board.boardMarkups().get(12);
        assertTrue(counterplayGlyph.isGlyph(), "thirteenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.COUNTERPLAY, counterplayGlyph.brush().glyph(),
                "counterplay annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x7C, 0x87, 0x94), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.DRAW_RESULT));
        Point c3Draw = squareCenter(board, (byte) 42);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK,
                c3Draw, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, c3Draw, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(14), Integer.valueOf(board.markupCount()),
                "draw-result glyph draw mode adds a vector glyph annotation");
        BoardMarkup drawGlyph = board.boardMarkups().get(13);
        assertTrue(drawGlyph.isGlyph(), "fourteenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.DRAW_RESULT, drawGlyph.brush().glyph(),
                "draw-result annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xF2, 0xF2, 0xF2),
                new Color(0x24, 0x27, 0x2E), 10, MarkupBrush.DEFAULT_BORDER_WIDTH,
                AnnotationGlyphs.WHITE_CHECKMATED));
        Point b3WhiteCheckmated = squareCenter(board, (byte) 41);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK,
                b3WhiteCheckmated, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, b3WhiteCheckmated, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(15), Integer.valueOf(board.markupCount()),
                "white-checkmated glyph draw mode adds a vector glyph annotation");
        BoardMarkup whiteCheckmatedGlyph = board.boardMarkups().get(14);
        assertTrue(whiteCheckmatedGlyph.isGlyph(), "fifteenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.WHITE_CHECKMATED, whiteCheckmatedGlyph.brush().glyph(),
                "white-checkmated annotation stores selected glyph");
        assertEquals(new Color(0x24, 0x27, 0x2E), whiteCheckmatedGlyph.brush().displayBorderColor(),
                "white-checkmated annotation stores dark mark color");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x33, 0x33, 0x33), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BLACK_CHECKMATED));
        Point a3BlackCheckmated = squareCenter(board, (byte) 40);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK,
                a3BlackCheckmated, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, a3BlackCheckmated, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(16), Integer.valueOf(board.markupCount()),
                "black-checkmated glyph draw mode adds a vector glyph annotation");
        BoardMarkup blackCheckmatedGlyph = board.boardMarkups().get(15);
        assertTrue(blackCheckmatedGlyph.isGlyph(), "sixteenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.BLACK_CHECKMATED, blackCheckmatedGlyph.brush().glyph(),
                "black-checkmated annotation stores selected glyph");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x6F, 0x57, 0xC8), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.FORK));
        Point h3Fork = squareCenter(board, (byte) 47);
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, h3Fork, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, h3Fork, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(17), Integer.valueOf(board.markupCount()),
                "fork glyph draw mode adds a vector glyph annotation");
        BoardMarkup forkGlyph = board.boardMarkups().get(16);
        assertTrue(forkGlyph.isGlyph(), "seventeenth draw annotation is a glyph");
        assertEquals(AnnotationGlyphs.FORK, forkGlyph.brush().glyph(),
                "fork annotation stores selected glyph");

        // Fan four glyphs from DIFFERENT exclusive groups (one move-quality, two
        // non-exclusive tactics, one condition) so they all stack on one square.
        Point fanSquare = squareCenter(board, (byte) 51);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xC2, 0x18, 0x5B), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.INTERESTING_MOVE));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, fanSquare, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, fanSquare, MouseEvent.BUTTON1);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xF4, 0x7B, 0x3F), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.FORK));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, fanSquare, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, fanSquare, MouseEvent.BUTTON1);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xFF, 0x17, 0x44), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.SKEWER));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, fanSquare, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, fanSquare, MouseEvent.BUTTON1);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x2D, 0x70, 0xB8), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.ZUGZWANG));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, fanSquare, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, fanSquare, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(21), Integer.valueOf(board.markupCount()),
                "same-square glyph draw mode keeps multiple non-sibling vector glyph annotations");

        board.setBoardColors(new Color(0xDDEEFF), new Color(0x335577));
        assertEquals(new Color(0xDDEEFF), board.boardLightColor(), "custom light board color applies");
        assertEquals(new Color(0x335577), board.boardDarkColor(), "custom dark board color applies");
        board.setShowSpecialMoveHints(true);
        String svg = BoardExporter.toSvg(board, 512);
        assertTrue(svg.contains("#ddeeff"), "SVG export includes custom light board color");
        assertTrue(svg.contains("#335577"), "SVG export includes custom dark board color");
        assertTrue(svg.contains("#787878"), "SVG export includes special move hint arrow fill");
        int rectangleMarkup = svg.indexOf("fill=\"#219e3c\"");
        int firstPiece = svg.indexOf("<g transform=");
        assertTrue(rectangleMarkup >= 0 && firstPiece >= 0 && rectangleMarkup < firstPiece,
                "SVG export paints rectangle annotations beneath pieces");
        String rectangleElement = svg.substring(svg.lastIndexOf("<rect", rectangleMarkup),
                svg.indexOf("/>", rectangleMarkup) + 2);
        assertTrue(rectangleElement.contains("rx=\"") && rectangleElement.contains("ry=\""),
                "SVG export preserves rounded rectangle corners");
        int glyphText = svg.indexOf(">T</text>");
        int glyphCircle = svg.lastIndexOf("<circle", glyphText);
        int glyphRect = svg.lastIndexOf("<rect", glyphText);
        assertTrue(glyphText >= 0 && glyphCircle > glyphRect,
                "SVG export paints glyph badges as circles");
        String glyphCircleElement = svg.substring(glyphCircle, svg.indexOf("/>", glyphCircle) + 2);
        assertTrue(glyphCircleElement.contains("cx=\"380\"") && glyphCircleElement.contains("cy=\"136\""),
                "SVG export straddles glyph badges over the square's top-right corner (Lichess-style)");
        String glyphTextElement = svg.substring(svg.lastIndexOf("<text", glyphText),
                svg.indexOf("</text>", glyphText) + "</text>".length());
        assertFalse(glyphTextElement.contains("dominant-baseline"),
                "SVG export avoids viewer-dependent central glyph baselines");
        assertTrue(svgAttribute(glyphTextElement, "y") > svgAttribute(glyphCircleElement, "cy"),
                "SVG export places glyph text on a Java2D-style baseline below badge center");
        int brilliantPath = svg.indexOf(AnnotationGlyphs.BRILLIANT_MOVE_SVG_PATH);
        assertTrue(brilliantPath >= 0, "SVG export includes the brilliant-move vector path");
        int brilliantCircle = svg.lastIndexOf("<circle", brilliantPath);
        String brilliantCircleElement = svg.substring(brilliantCircle,
                svg.indexOf("/>", brilliantCircle) + 2);
        assertTrue(brilliantCircleElement.contains("fill=\"#168226\""),
                "SVG export preserves the requested brilliant-move fill");
        assertFalse(svg.contains(">!!</text>"),
                "SVG export avoids text fallback for the brilliant-move marker");
        int excellentPath = svg.indexOf(AnnotationGlyphs.EXCELLENT_MOVE_SVG_PATH);
        assertTrue(excellentPath >= 0, "SVG export includes the excellent-move vector path");
        int excellentCircle = svg.lastIndexOf("<circle", excellentPath);
        String excellentCircleElement = svg.substring(excellentCircle,
                svg.indexOf("/>", excellentCircle) + 2);
        assertTrue(excellentCircleElement.contains("fill=\"#22ac38\""),
                "SVG export preserves the requested excellent-move fill");
        assertFalse(svg.contains(">!</text>"),
                "SVG export avoids text fallback for the excellent-move marker");
        int interestingPath = svg.indexOf(AnnotationGlyphs.INTERESTING_MOVE_SVG_PATH);
        assertTrue(interestingPath >= 0, "SVG export includes the interesting-move vector path");
        int interestingCircle = svg.lastIndexOf("<circle", interestingPath);
        String interestingCircleElement = svg.substring(interestingCircle,
                svg.indexOf("/>", interestingCircle) + 2);
        assertTrue(interestingCircleElement.contains("fill=\"#ea45d8\""),
                "SVG export preserves the requested interesting-move fill");
        assertFalse(svg.contains(">!?</text>"),
                "SVG export avoids text fallback for the interesting-move marker");
        int dubiousPath = svg.indexOf(AnnotationGlyphs.DUBIOUS_MOVE_SVG_PATH);
        assertTrue(dubiousPath >= 0, "SVG export includes the dubious-move vector path");
        int dubiousCircle = svg.lastIndexOf("<circle", dubiousPath);
        String dubiousCircleElement = svg.substring(dubiousCircle,
                svg.indexOf("/>", dubiousCircle) + 2);
        assertTrue(dubiousCircleElement.contains("fill=\"#56b4e9\""),
                "SVG export preserves the requested dubious-move fill");
        assertFalse(svg.contains(">?!</text>"),
                "SVG export avoids text fallback for the dubious-move marker");
        int blunderPath = svg.indexOf(AnnotationGlyphs.BLUNDER_MOVE_SVG_PATH);
        assertTrue(blunderPath >= 0, "SVG export includes the blunder vector path");
        int blunderCircle = svg.lastIndexOf("<circle", blunderPath);
        String blunderCircleElement = svg.substring(blunderCircle,
                svg.indexOf("/>", blunderCircle) + 2);
        assertTrue(blunderCircleElement.contains("fill=\"#df5353\""),
                "SVG export preserves the requested blunder fill");
        assertFalse(svg.contains(">??</text>"),
                "SVG export avoids text fallback for the blunder marker");
        int onlyMovePath = svg.indexOf(AnnotationGlyphs.ONLY_MOVE_SVG_PATH);
        assertTrue(onlyMovePath >= 0, "SVG export includes the only-move rectangle path");
        int onlyMoveCircle = svg.lastIndexOf("<circle", onlyMovePath);
        String onlyMoveCircleElement = svg.substring(onlyMoveCircle,
                svg.indexOf("/>", onlyMoveCircle) + 2);
        assertTrue(onlyMoveCircleElement.contains("fill=\"#a04048\""),
                "SVG export preserves the requested only-move fill");
        String onlyMovePathElement = svg.substring(svg.lastIndexOf("<path", onlyMovePath),
                svg.indexOf("/>", onlyMovePath) + 2);
        assertTrue(onlyMovePathElement.contains("fill=\"none\""),
                "SVG export keeps the only-move rectangle unfilled");
        assertTrue(onlyMovePathElement.contains("stroke=\"#ffffff\""),
                "SVG export strokes the only-move rectangle in white");
        assertTrue(onlyMovePathElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested only-move stroke width");
        assertFalse(svg.contains(">" + AnnotationGlyphs.ONLY_MOVE + "</text>"),
                "SVG export avoids text fallback for the only-move marker");
        int zugzwangFill = svg.indexOf("fill=\"#9171f2\"");
        assertTrue(zugzwangFill >= 0, "SVG export preserves the requested zugzwang fill");
        String zugzwangFillCircle = svg.substring(svg.lastIndexOf("<circle", zugzwangFill),
                svg.indexOf("/>", zugzwangFill) + 2);
        assertFalse(zugzwangFillCircle.contains("stroke="),
                "SVG export keeps the zugzwang badge circle fill-only");
        assertTrue(svg.contains("<circle cx=\"50\" cy=\"50\" r=\"46.5\" fill=\"none\" stroke=\"#ffffff\""),
                "SVG export includes the zugzwang outer ring flush with the badge edge");
        assertTrue(svg.contains("<circle cx=\"50\" cy=\"50\" r=\"7\" fill=\"#ffffff\"/>"),
                "SVG export fills the zugzwang center dot");
        assertFalse(svg.contains(">" + AnnotationGlyphs.ZUGZWANG + "</text>"),
                "SVG export avoids text fallback for the zugzwang marker");
        int whiteWinningPath = svg.indexOf(AnnotationGlyphs.WHITE_WINNING_SVG_PATH);
        assertTrue(whiteWinningPath >= 0, "SVG export includes the white-winning vector path");
        int whiteWinningCircle = svg.lastIndexOf("<circle", whiteWinningPath);
        String whiteWinningCircleElement = svg.substring(whiteWinningCircle,
                svg.indexOf("/>", whiteWinningCircle) + 2);
        assertTrue(whiteWinningCircleElement.contains("fill=\"#bbbbbb\""),
                "SVG export preserves the requested white-winning fill");
        String whiteWinningPathElement = svg.substring(svg.lastIndexOf("<path", whiteWinningPath),
                svg.indexOf("/>", whiteWinningPath) + 2);
        assertTrue(whiteWinningPathElement.contains("fill=\"none\""),
                "SVG export keeps the white-winning marker unfilled");
        assertTrue(whiteWinningPathElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested white-winning stroke width");
        assertFalse(svg.contains(">" + AnnotationGlyphs.WHITE_WINNING + "</text>"),
                "SVG export avoids text fallback for the white-winning marker");
        int counterplayPath = svg.indexOf(AnnotationGlyphs.COUNTERPLAY_FILL_SVG_PATH);
        assertTrue(counterplayPath >= 0, "SVG export includes the counterplay filled vector path");
        int counterplayCircle = svg.lastIndexOf("<circle", counterplayPath);
        String counterplayCircleElement = svg.substring(counterplayCircle,
                svg.indexOf("/>", counterplayCircle) + 2);
        assertTrue(counterplayCircleElement.contains("fill=\"#ff784f\""),
                "SVG export preserves the requested counterplay fill");
        assertTrue(svg.contains("d=\"" + AnnotationGlyphs.COUNTERPLAY_STROKE_SVG_PATH + "\""),
                "SVG export includes the counterplay stroked lane path");
        int counterplayStroke = svg.indexOf(AnnotationGlyphs.COUNTERPLAY_STROKE_SVG_PATH);
        String counterplayStrokeElement = svg.substring(svg.lastIndexOf("<path", counterplayStroke),
                svg.indexOf("/>", counterplayStroke) + 2);
        assertTrue(counterplayStrokeElement.contains("fill=\"none\""),
                "SVG export keeps the counterplay lane path unfilled");
        assertTrue(counterplayStrokeElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested counterplay stroke width");
        assertFalse(svg.contains(">" + AnnotationGlyphs.COUNTERPLAY + "</text>"),
                "SVG export avoids text fallback for the counterplay marker");
        int forkPath = svg.indexOf(AnnotationGlyphs.FORK_FILL_SVG_PATH);
        assertTrue(forkPath >= 0, "SVG export includes the fork filled vector path");
        int forkCircle = svg.lastIndexOf("<circle", forkPath);
        String forkCircleElement = svg.substring(forkCircle, svg.indexOf("/>", forkCircle) + 2);
        assertTrue(forkCircleElement.contains("fill=\"#6f57c8\""),
                "SVG export preserves the requested fork fill");
        assertTrue(svg.contains("d=\"" + AnnotationGlyphs.FORK_STROKE_SVG_PATH + "\""),
                "SVG export includes the fork branch stroke path");
        int forkStroke = svg.indexOf(AnnotationGlyphs.FORK_STROKE_SVG_PATH);
        String forkStrokeElement = svg.substring(svg.lastIndexOf("<path", forkStroke),
                svg.indexOf("/>", forkStroke) + 2);
        assertTrue(forkStrokeElement.contains("fill=\"none\""),
                "SVG export keeps the fork branch path unfilled");
        assertTrue(forkStrokeElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested fork stroke width");
        assertFalse(forkStrokeElement.contains("stroke-linecap=\"round\""),
                "SVG export uses butt caps for the fork stalk, matching the arrow glyphs");
        assertFalse(svg.contains(">" + AnnotationGlyphs.FORK + "</text>"),
                "SVG export avoids text fallback for the fork marker");
        int drawResultPath = svg.indexOf(AnnotationGlyphs.DRAW_RESULT_SVG_PATH);
        assertTrue(drawResultPath >= 0, "SVG export includes the draw-result vector path");
        int drawResultCircle = svg.lastIndexOf("<circle", drawResultPath);
        String drawResultCircleElement = svg.substring(drawResultCircle,
                svg.indexOf("/>", drawResultCircle) + 2);
        assertTrue(drawResultCircleElement.contains("fill=\"#7c8794\""),
                "SVG export preserves the requested draw-result fill");
        String drawResultPathElement = svg.substring(svg.lastIndexOf("<path", drawResultPath),
                svg.indexOf("/>", drawResultPath) + 2);
        assertFalse(drawResultPathElement.contains("fill=\"none\""),
                "SVG export fills the draw-result fraction outline");
        assertFalse(drawResultPathElement.contains("stroke-width"),
                "SVG export draws the draw-result fraction as a filled glyph, not a stroke");
        assertFalse(svg.contains(">" + AnnotationGlyphs.DRAW_RESULT + "</text>"),
                "SVG export avoids text fallback for the draw-result marker");
        int whiteCheckmatedPath = svg.indexOf(AnnotationGlyphs.WHITE_CHECKMATED_SVG_PATH);
        assertTrue(whiteCheckmatedPath >= 0, "SVG export includes the white-checkmated vector path");
        int whiteCheckmatedCircle = svg.lastIndexOf("<circle", whiteCheckmatedPath);
        String whiteCheckmatedCircleElement = svg.substring(whiteCheckmatedCircle,
                svg.indexOf("/>", whiteCheckmatedCircle) + 2);
        assertTrue(whiteCheckmatedCircleElement.contains("fill=\"#f2f2f2\""),
                "SVG export preserves the requested white-checkmated fill");
        String whiteCheckmatedPathElement = svg.substring(svg.lastIndexOf("<path", whiteCheckmatedPath),
                svg.indexOf("/>", whiteCheckmatedPath) + 2);
        assertTrue(whiteCheckmatedPathElement.contains("stroke=\"#24272e\""),
                "SVG export preserves the requested white-checkmated dark mark");
        assertTrue(whiteCheckmatedPathElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested white-checkmated stroke width");
        assertFalse(svg.contains(">" + AnnotationGlyphs.WHITE_CHECKMATED + "</text>"),
                "SVG export avoids text fallback for the white-checkmated marker");
        int blackCheckmatedPath = svg.indexOf(AnnotationGlyphs.BLACK_CHECKMATED_SVG_PATH);
        assertTrue(blackCheckmatedPath >= 0, "SVG export includes the black-checkmated vector path");
        int blackCheckmatedCircle = svg.lastIndexOf("<circle", blackCheckmatedPath);
        String blackCheckmatedCircleElement = svg.substring(blackCheckmatedCircle,
                svg.indexOf("/>", blackCheckmatedCircle) + 2);
        assertTrue(blackCheckmatedCircleElement.contains("fill=\"#333333\""),
                "SVG export preserves the requested black-checkmated fill");
        String blackCheckmatedPathElement = svg.substring(svg.lastIndexOf("<path", blackCheckmatedPath),
                svg.indexOf("/>", blackCheckmatedPath) + 2);
        assertTrue(blackCheckmatedPathElement.contains("stroke=\"#ffffff\""),
                "SVG export preserves the requested black-checkmated white mark");
        assertTrue(blackCheckmatedPathElement.contains("stroke-width=\"7\""),
                "SVG export preserves the requested black-checkmated stroke width");
        assertFalse(svg.contains(">" + AnnotationGlyphs.BLACK_CHECKMATED + "</text>"),
                "SVG export avoids text fallback for the black-checkmated marker");
        double fanA = circleCxForFill(svg, "#c2185b");
        double fanB = circleCxForFill(svg, "#f47b3f");
        double fanC = circleCxForFill(svg, "#ff1744");
        double fanD = circleCxForFill(svg, "#2d70b8");
        assertTrue(fanA < fanB && fanB < fanC && fanC < fanD,
                "SVG export fans same-square glyph badges from left to right");
        assertTrue(fanB - fanA > 8.0 && fanD - fanC > 8.0,
                "SVG export gives same-square glyph badges visible horizontal offsets");
        assertTrue(board.canUndoMarkupEdit(), "draw annotations can be undone");
        board.undoMarkupEdit();
        assertEquals(Integer.valueOf(20), Integer.valueOf(board.markupCount()),
                "undo removes the latest draw annotation");
        assertTrue(board.canRedoMarkupEdit(), "draw annotations can be redone");
        board.redoMarkupEdit();
        assertEquals(Integer.valueOf(21), Integer.valueOf(board.markupCount()),
                "redo restores the latest draw annotation");
        board.removeMarkup(0);
        assertEquals(Integer.valueOf(20), Integer.valueOf(board.markupCount()),
                "delete selected support removes one annotation");
        board.clearUserMarkup();
        assertEquals(Integer.valueOf(0), Integer.valueOf(board.markupCount()),
                "clear all support removes draw annotations");
    }

    /**
     * Verifies mutually exclusive position-evaluation glyphs replace each other
     * while unrelated move-quality glyphs can still stack on the same square.
     */
    private static void testDrawGlyphEvaluationAnnotationsReplaceSiblings() {
        BoardPanel board = new BoardPanel();
        board.setSize(520, 520);
        board.setPositionInstant(new Position(START_FEN), Move.NO_MOVE);
        board.setDirectAnnotationMode(true);
        board.setMarkupTool(BoardMarkupTool.GLYPH);
        board.doLayout();

        Point d4 = squareCenter(board, (byte) 35);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x81, 0xB6, 0x4C), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, "±"));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "first evaluation glyph adds one annotation");
        assertTrue(boardHasGlyph(board, "±"), "white advantage glyph is present");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xDF, 0x53, 0x53), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, "∓"));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "opposite evaluation glyph replaces the earlier evaluation glyph");
        assertFalse(boardHasGlyph(board, "±"), "white advantage glyph was replaced");
        assertTrue(boardHasGlyph(board, "∓"), "black advantage glyph is present");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xEA, 0x45, 0xD8), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.INTERESTING_MOVE));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(2), Integer.valueOf(board.markupCount()),
                "unrelated move-quality glyph can stack with an evaluation glyph");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "interesting-move glyph remains stacked on the square");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xDF, 0x53, 0x53), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, "-+"));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(2), Integer.valueOf(board.markupCount()),
                "winning evaluation glyph replaces only the previous evaluation sibling");
        assertFalse(boardHasGlyph(board, "∓"), "black slight-advantage glyph was replaced");
        assertTrue(boardHasGlyph(board, "-+"), "black-winning glyph is present");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "non-exclusive move-quality glyph is preserved");

        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, d4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, d4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "clicking the same exclusive glyph toggles it off");
        assertFalse(boardHasGlyph(board, "-+"), "black-winning glyph was toggled off");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "stacked non-exclusive glyph remains after exclusive toggle-off");

        // Move-quality glyphs are their own exclusive group: a second one replaces the first.
        Point e4 = squareCenter(board, (byte) 28);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x16, 0x82, 0x26), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BRILLIANT_MOVE));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.BRILLIANT_MOVE), "brilliant move-quality glyph is present");
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xDF, 0x53, 0x53), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BLUNDER_MOVE));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.BLUNDER_MOVE),
                "blunder move-quality glyph replaces the brilliant sibling on the same square");
        assertFalse(boardHasGlyph(board, AnnotationGlyphs.BRILLIANT_MOVE),
                "earlier move-quality glyph was replaced by its exclusive sibling");
    }

    /**
     * Verifies mutually exclusive game-result glyphs replace each other while
     * unrelated notation glyphs can still stack on the same square.
     */
    private static void testDrawGlyphResultAnnotationsReplaceSiblings() {
        BoardPanel board = new BoardPanel();
        board.setSize(520, 520);
        board.setPositionInstant(new Position(START_FEN), Move.NO_MOVE);
        board.setDirectAnnotationMode(true);
        board.setMarkupTool(BoardMarkupTool.GLYPH);
        board.doLayout();

        Point e4 = squareCenter(board, (byte) 36);
        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x7C, 0x87, 0x94), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.DRAW_RESULT));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "first result glyph adds one annotation");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.DRAW_RESULT), "draw-result glyph is present");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xF2, 0xF2, 0xF2),
                new Color(0x24, 0x27, 0x2E), 10, MarkupBrush.DEFAULT_BORDER_WIDTH,
                AnnotationGlyphs.WHITE_CHECKMATED));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "white-checkmated result glyph replaces the draw-result glyph");
        assertFalse(boardHasGlyph(board, AnnotationGlyphs.DRAW_RESULT), "draw-result glyph was replaced");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.WHITE_CHECKMATED),
                "white-checkmated glyph is present");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0xEA, 0x45, 0xD8), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.INTERESTING_MOVE));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(2), Integer.valueOf(board.markupCount()),
                "unrelated move-quality glyph can stack with a result glyph");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "interesting-move glyph remains stacked on the square");

        board.setDirectAnnotationBrush(MarkupBrush.custom(new Color(0x33, 0x33, 0x33), Color.WHITE, 10,
                MarkupBrush.DEFAULT_BORDER_WIDTH, AnnotationGlyphs.BLACK_CHECKMATED));
        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(2), Integer.valueOf(board.markupCount()),
                "black-checkmated result glyph replaces only the previous result sibling");
        assertFalse(boardHasGlyph(board, AnnotationGlyphs.WHITE_CHECKMATED),
                "white-checkmated glyph was replaced");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.BLACK_CHECKMATED),
                "black-checkmated glyph is present");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "non-exclusive move-quality glyph is preserved with a result glyph");

        dispatchBoardMouse(board, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1_DOWN_MASK, e4, MouseEvent.BUTTON1);
        dispatchBoardMouse(board, MouseEvent.MOUSE_RELEASED, 0, e4, MouseEvent.BUTTON1);
        assertEquals(Integer.valueOf(1), Integer.valueOf(board.markupCount()),
                "clicking the same result glyph toggles it off");
        assertFalse(boardHasGlyph(board, AnnotationGlyphs.BLACK_CHECKMATED),
                "black-checkmated glyph was toggled off");
        assertTrue(boardHasGlyph(board, AnnotationGlyphs.INTERESTING_MOVE),
                "stacked non-exclusive glyph remains after result toggle-off");
    }

    /**
     * Returns whether a board currently contains a glyph annotation.
     *
     * @param board source board
     * @param glyph glyph text
     * @return true when present
     */
    private static boolean boardHasGlyph(BoardPanel board, String glyph) {
        for (BoardMarkup markup : board.boardMarkups()) {
            if (markup.isGlyph() && glyph.equals(markup.brush().glyph())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the current center point for a board square.
     *
     * @param board board state
     * @param square square index
     * @return square center
     */
    private static Point squareCenter(BoardPanel board, byte square) {
        Rectangle bounds = board.currentBoardBounds();
        int cell = Math.max(1, bounds.width / 8);
        int col = square % 8;
        int row = square / 8;
        return new Point(bounds.x + col * cell + cell / 2,
                bounds.y + row * cell + cell / 2);
    }

    /**
     * Finds a button by visible text.
     *
     * @param component root component
     * @param text expected text
     * @return matching button
     */
    private static JButton buttonWithText(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return buttonWithText(child, text);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing button text " + text);
    }

    /**
     * Reads one numeric SVG attribute from an element.
     *
     * @param element SVG element text
     * @param name attribute name
     * @return parsed attribute value
     */
    private static double svgAttribute(String element, String name) {
        String marker = name + "=\"";
        int start = element.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("missing SVG attribute " + name + " in " + element);
        }
        int valueStart = start + marker.length();
        int valueEnd = element.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new AssertionError("unterminated SVG attribute " + name + " in " + element);
        }
        return Double.parseDouble(element.substring(valueStart, valueEnd));
    }

    /**
     * Reads the center x of the badge circle with the supplied fill color.
     *
     * @param svg SVG document
     * @param fill fill color in CSS hex form
     * @return circle center x
     */
    private static double circleCxForFill(String svg, String fill) {
        int fillIndex = svg.indexOf("fill=\"" + fill + "\"");
        if (fillIndex < 0) {
            throw new AssertionError("missing SVG circle fill " + fill);
        }
        int circleStart = svg.lastIndexOf("<circle", fillIndex);
        int circleEnd = svg.indexOf("/>", fillIndex);
        if (circleStart < 0 || circleEnd < 0) {
            throw new AssertionError("missing SVG circle for fill " + fill);
        }
        return svgAttribute(svg.substring(circleStart, circleEnd + 2), "cx");
    }

    /**
     * Dispatches one mouse event to the board.
     *
     * @param board board state
     * @param id event id
     * @param modifiers extended modifiers
     * @param point event point
     * @param button mouse button
     */
    private static void dispatchBoardMouse(BoardPanel board, int id, int modifiers, Point point, int button) {
        board.dispatchEvent(new MouseEvent(board, id, System.currentTimeMillis(),
                modifiers, point.x, point.y, 1, false, button));
    }

    /**
     * Verifies the active theme palette has sufficient contrast.
     *
     * @param modeName mode label for assertion messages
     */
    private static void assertCurrentThemeContrast(String modeName) {
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
        assertTrue(moveHighlight.getAlpha() < 160, "move highlight is translucent");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_LIGHT"), 70.0,
                "move highlight distinguishes from light board squares");
        assertColorDistanceAtLeast(moveHighlight, themeColor("BOARD_DARK"), 70.0,
                "move highlight distinguishes from dark board squares");
        assertTrue(themeColor("SELECTED_EDGE").getGreen() > themeColor("SELECTED_EDGE").getBlue(),
                "selected square uses a chessboard.js-style green tint");
        assertTrue(themeColor("LEGAL_TARGET").getAlpha() >= 120,
                "legal move dot stays visible on board squares");
        assertEquals(themeColor("LEGAL_TARGET").getRed(), themeColor("BOARD_ARROW").getRed(),
                "best-move arrow reuses legal-move red channel");
        assertEquals(themeColor("LEGAL_TARGET").getGreen(), themeColor("BOARD_ARROW").getGreen(),
                "best-move arrow reuses legal-move green channel");
        assertEquals(themeColor("LEGAL_TARGET").getBlue(), themeColor("BOARD_ARROW").getBlue(),
                "best-move arrow reuses legal-move blue channel");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_LIGHT"), 95.0,
                "best-move arrow distinguishes from light board squares");
        assertColorDistanceAtLeast(themeColor("BOARD_ARROW"), themeColor("BOARD_DARK"), 95.0,
                "best-move arrow distinguishes from dark board squares in " + modeName + " mode");
    }

    /**
     * Verifies a live theme refresh keeps the semantic foreground hierarchy for
     * muted labels, section labels, and status labels in both modes.
     */
    private static void testThemeRefreshPreservesLabelRoles() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "LIGHT"));

        JLabel muted = (JLabel) invokeStatic(type("Ui"), "label", new Class<?>[] { String.class }, "muted");
        JLabel section = (JLabel) invokeStatic(type("Theme"), "section", new Class<?>[] { String.class }, "section");
        JLabel warning = new JLabel("warning");
        invokeStatic(type("Theme"), "foreground",
                new Class<?>[] { javax.swing.JComponent.class, type("Theme$ForegroundRole") },
                warning, enumValue(type("Theme$ForegroundRole"), "WARNING"));
        JPanel panel = new JPanel();
        panel.add(muted);
        panel.add(section);
        panel.add(warning);

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "DARK"));
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertEquals(themeColor("MUTED"), muted.getForeground(), "muted label stays muted in dark");
        assertEquals(themeColor("TEXT"), section.getForeground(), "section label stays text in dark");
        assertEquals(themeColor("STATUS_WARNING_TEXT"), warning.getForeground(), "warning label stays warning in dark");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType }, enumValue(modeType, "LIGHT"));
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertEquals(themeColor("MUTED"), muted.getForeground(), "muted label stays muted in light");
        assertEquals(themeColor("TEXT"), section.getForeground(), "section label stays text in light");
        assertEquals(themeColor("STATUS_WARNING_TEXT"), warning.getForeground(),
                "warning label stays warning in light");
    }

    /**
     * Verifies fixed Swing line borders are rebuilt with the active theme.
     */
    private static void testThemeRefreshUpdatesLineBorders() {
        Theme.setMode(Theme.Mode.LIGHT);
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE)));

        Theme.setMode(Theme.Mode.DARK);
        Theme.refreshComponentTree(panel);
        assertEquals(themeColor("LINE"), firstBorderColor(panel.getBorder()),
                "compound line border follows dark theme");

        Theme.setMode(Theme.Mode.LIGHT);
        Theme.refreshComponentTree(panel);
        assertEquals(themeColor("LINE"), firstBorderColor(panel.getBorder()),
                "compound line border follows light theme");
    }

    /**
     * Verifies palette refresh restores workbench-specific control delegates.
     */
    private static void testThemeRefreshRestoresCustomControlUis() {
        Theme.setMode(Theme.Mode.LIGHT);
        JPanel panel = new JPanel();
        JTabbedPane tabs = Ui.tabbedPane();
        tabs.addTab("A", new JPanel());
        JComboBox<String> combo = new JComboBox<>(new String[] { "one", "two" });
        Ui.styleCombo(combo);
        JSpinner spinner = new JSpinner();
        Ui.styleSpinner(spinner);
        JScrollPane scroll = Ui.scroll(new JPanel());
        panel.add(tabs);
        panel.add(combo);
        panel.add(spinner);
        panel.add(scroll);

        Theme.setMode(Theme.Mode.DARK);
        javax.swing.SwingUtilities.updateComponentTreeUI(panel);
        Theme.refreshComponentTree(panel);

        assertTrue(tabs.getUI().getClass().getName().contains("FlatTabbedPaneUI"),
                "tabbed pane custom UI restored");
        assertTrue(combo.getUI().getClass().getName().contains("StyledComboBoxUI"),
                "combo custom UI restored");
        assertTrue(spinner.getUI().getClass().getName().contains("StyledSpinnerUI"),
                "spinner custom UI restored");
        assertTrue(scroll.getVerticalScrollBar().getUI().getClass().getName().contains("StyledScrollBarUI"),
                "scrollbar custom UI restored");
        Theme.setMode(Theme.Mode.LIGHT);
    }

    /**
     * Verifies the installed Swing defaults use the workbench palette rather
     * than default look-and-feel colors.
     */
    private static void testThemeInstallSetsTooltipColors() {
        invokeStatic(type("Theme"), "install", new Class<?>[0]);
        assertEquals(themeColor("TOOLTIP_BG"), UIManager.getColor("ToolTip.background"), "tooltip background themed");
        assertEquals(themeColor("TOOLTIP_TEXT"), UIManager.getColor("ToolTip.foreground"), "tooltip foreground themed");
        assertEquals(themeColor("TEXT_SELECTION"), UIManager.getColor("TextField.selectionBackground"),
                "text-field selection background themed");
        assertEquals(themeColor("SELECTION_SOLID"), UIManager.getColor("List.selectionBackground"),
                "list selection background themed");
        assertEquals(themeColor("PANEL_SOLID"), UIManager.getColor("ScrollBar.background"),
                "scrollbar background themed");
        assertEquals(themeColor("PANEL_SOLID"), UIManager.getColor("ScrollBar.track"),
                "scrollbar track themed");
        assertEquals(themeColor("SCROLLBAR_THUMB"), UIManager.getColor("ScrollBar.thumb"),
                "scrollbar thumb themed");
    }

    /**
     * Verifies the workbench keeps interface and code fonts separated.
     */
    private static void testThemeUsesDeliberateFontStacks() {
        // The base type scale is asserted at the default (DENSE) density, where
        // the density multiplier is 1.0; reset it so this stays true regardless
        // of any density a prior in-JVM step may have set.
        Theme.setDensity(Theme.Density.DENSE);
        Theme.install();
        Font uiFont = Theme.font(13, Font.PLAIN);
        Font monoFont = Theme.mono(13);
        assertEquals(Integer.valueOf(13), Integer.valueOf(uiFont.getSize()), "UI font size");
        assertEquals(Integer.valueOf(13), Integer.valueOf(monoFont.getSize()), "mono font size");
        assertFalse("Serif".equals(uiFont.getFamily()), "UI font avoids serif fallback");
        assertFalse(uiFont.getFamily().equals(monoFont.getFamily()), "UI and code fonts are distinct");
        assertEquals(uiFont.getFamily(), UIManager.getFont("TextField.font").getFamily(),
                "text fields use UI font stack");
        assertEquals(uiFont.getFamily(), UIManager.getFont("FileChooser.font").getFamily(),
                "file chooser uses UI font stack");
        assertEquals(monoFont.getFamily(), UIManager.getFont("TextArea.font").getFamily(),
                "text areas use code font stack");

        JTextField field = new JTextField("path");
        Theme.field(field);
        JTextArea area = new JTextArea("fen");
        Theme.area(area);
        assertEquals(uiFont.getFamily(), field.getFont().getFamily(), "styled text field uses UI font");
        assertEquals(monoFont.getFamily(), area.getFont().getFamily(), "styled text area uses code font");
    }

    /**
     * Verifies file chooser icons are custom vector glyphs that repaint from
     * the active light or dark palette.
     */
    private static void testFileChooserIconsUseThemePalette() {
        Theme.setMode(Theme.Mode.LIGHT);
        Theme.install();
        JFileChooser lightChooser = FileDialogs.createFileChooser("Open", new File("."),
                new FileNameExtensionFilter("TOML files", "toml"));
        Icon lightFolder = UIManager.getIcon("FileView.directoryIcon");
        Icon lightUp = UIManager.getIcon("FileChooser.upFolderIcon");
        assertTrue(lightChooser.getPreferredSize().width >= 760, "file chooser opens at a usable width");
        assertTrue(lightChooser.getPreferredSize().height >= 520, "file chooser opens at a usable height");
        assertTrue(lightChooser.getMinimumSize().width >= 620, "file chooser minimum width prevents collapse");
        assertTrue(lightChooser.getMinimumSize().height >= 420, "file chooser minimum height prevents collapse");
        assertEquals(Integer.valueOf(18), Integer.valueOf(lightFolder.getIconWidth()),
                "file chooser folder icon width");
        assertEquals(Integer.valueOf(20), Integer.valueOf(lightUp.getIconWidth()),
                "file chooser toolbar icon width");
        BufferedImage lightIcon = paintIcon(lightFolder);
        assertTrue(maxAlpha(lightIcon) > 180, "light file chooser icon paints opaque strokes");
        assertTrue(lightChooser.getIcon(new File(".")) != null, "chooser exposes themed directory icon");
        assertEquals("Type", UIManager.getString("FileChooser.filesOfTypeLabelText"),
                "file chooser type label is concise");
        assertTrue(assertChooserListsUseFont(lightChooser, Theme.font(13, Font.PLAIN).getFamily()) > 0,
                "file chooser exposes styled file rows");
        String filterText = lightChooser.getFileFilter().toString();
        assertTrue(filterText.contains("TOML files (*.toml)"), "file chooser filter text is readable");
        assertFalse(filterText.contains("@"), "file chooser filter text hides Swing object id");

        Theme.setMode(Theme.Mode.DARK);
        Theme.install();
        JFileChooser darkChooser = FileDialogs.createFileChooser("Open", new File("."), null);
        Icon darkFolder = UIManager.getIcon("FileView.directoryIcon");
        BufferedImage darkIcon = paintIcon(darkFolder);
        assertTrue(maxAlpha(darkIcon) > 180, "dark file chooser icon paints opaque strokes");
        assertTrue(darkChooser.getIcon(new File(".")) != null, "dark chooser exposes themed directory icon");
        assertColorDistanceAtLeast(averagePaintColor(lightIcon), averagePaintColor(darkIcon), 16.0,
                "file chooser icons respond to theme changes");
        Theme.setMode(Theme.Mode.LIGHT);
        Theme.install();
    }

    /**
     * Verifies file chooser list rows use the UI font stack.
     *
     * @param component root component
     * @param family expected font family
     * @return number of chooser lists inspected
     */
    private static int assertChooserListsUseFont(Component component, String family) {
        int matches = 0;
        if (component instanceof JList<?> list) {
            assertEquals(family, list.getFont().getFamily(), "file chooser list font");
            matches++;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches += assertChooserListsUseFont(child, family);
            }
        }
        return matches;
    }

    /**
     * Verifies toast bubbles use a bottom-right desktop notification position.
     */
    private static void testToastUsesBottomRightPlacement() {
        int margin = (Integer) staticField(type("Toast"), "RIGHT_MARGIN");
        int x = (Integer) invokeStatic(type("Toast"), "toastX",
                new Class<?>[] { int.class, int.class }, 800, 220);
        assertEquals(Integer.valueOf(800 - 220 - margin), Integer.valueOf(x),
                "toast sits against the right edge");
        assertEquals(Integer.valueOf(0), invokeStatic(type("Toast"), "toastX",
                new Class<?>[] { int.class, int.class }, 180, 220),
                "oversized toast stays onscreen");
    }

    /**
     * Verifies toast fade alpha applies to child text as well as chrome.
     */
    private static void testToastFadeAppliesToTextAndChromeTogether() {
        Object warning = enumValue(type("Toast$Kind"), "WARNING");
        JComponent toast = (JComponent) construct(type("Toast$ToastPanel"),
                new Class<?>[] { JFrame.class, type("Toast$Kind"), String.class },
                null, warning, "Warning text should fade with the bubble");
        setField(toast, "alpha", 0.25f);
        Dimension preferred = toast.getPreferredSize();
        int width = Math.max(320, preferred.width);
        int height = Math.max(56, preferred.height);
        toast.setSize(width, height);
        BufferedImage image = paint(toast, width, height);
        assertTrue(maxAlpha(image) <= 96, "toast text and chrome share fade alpha");
    }

    /**
     * Verifies collapsible information sections hide and restore their content.
     */
    private static void testCollapsibleInfoSectionTogglesContent() {
        JLabel content = new JLabel("details");
        JComponent section = (JComponent) invokeStatic(type("Ui"), "collapsible",
                new Class<?>[] { String.class, javax.swing.JComponent.class, boolean.class },
                "Info", content, true);
        JButton toggle = firstButton(section);
        assertEquals("Info", toggle.getText(), "collapsible header uses clean title text");
        assertFalse(toggle.getText().startsWith("-") || toggle.getText().startsWith("+"),
                "collapsible header does not expose raw +/- glyphs");
        assertTrue(content.isVisible(), "collapsible content initially visible");
        toggle.doClick();
        assertFalse(content.isVisible(), "collapsible content hidden");
        Timer timer = (Timer) field(section, "expansionTimer");
        assertFalse(timer.isRunning(), "hidden collapsible does not keep an animation timer");
        toggle.doClick();
        assertTrue(content.isVisible(), "collapsible content restored");
    }

    /**
     * Verifies tall disclosure content is capped and handed to a shared styled
     * scroll pane instead of resizing its parent surface.
     */
    private static void testBoundedCollapsibleSectionScrollsInternally() {
        JPanel content = new JPanel(new java.awt.GridLayout(32, 1, 0, 3));
        content.setOpaque(false);
        for (int i = 0; i < 32; i++) {
            content.add(new JLabel("flag " + i));
        }
        JComponent section = Ui.collapsible("Flags", content, true, 120);
        JScrollPane scroll = firstDescendant(section, JScrollPane.class);

        assertTrue(scroll != null, "bounded collapsible creates one internal scroll pane");
        assertEquals(Integer.valueOf(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED),
                Integer.valueOf(scroll.getVerticalScrollBarPolicy()),
                "bounded collapsible keeps vertical scrolling available");
        assertEquals(Integer.valueOf(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),
                Integer.valueOf(scroll.getHorizontalScrollBarPolicy()),
                "bounded collapsible does not introduce horizontal scrolling");
        assertTrue(scroll.getVerticalScrollBar().getUI().getClass().getName().contains("StyledScrollBarUI"),
                "bounded collapsible uses shared scrollbar UI");
        assertTrue(scroll.getPreferredSize().height <= 120,
                "bounded collapsible caps expanded scroll height");
        assertEquals(Integer.valueOf(section.getPreferredSize().height),
                Integer.valueOf(section.getMaximumSize().height),
                "bounded collapsible reports a stable maximum height");
    }

    /**
     * Verifies nested scroll panes do not show transient scroll bars while a
     * collapsed section is still animating open.
     */
    private static void testCollapsibleSectionDefersNestedScrollbarsDuringAnimation() {
        JTextArea text = new JTextArea("line 1\nline 2\nline 3\nline 4\nline 5\nline 6");
        JScrollPane scroll = Ui.scroll(text);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JComponent section = (JComponent) invokeStatic(type("Ui"), "collapsible",
                new Class<?>[] { String.class, javax.swing.JComponent.class, boolean.class },
                "Solution", scroll, false);

        invoke(section, "setExpanded", new Class<?>[] { boolean.class, boolean.class }, true, true);

        assertEquals(Integer.valueOf(JScrollPane.VERTICAL_SCROLLBAR_NEVER),
                Integer.valueOf(scroll.getVerticalScrollBarPolicy()),
                "nested vertical scrollbar is hidden during expansion");
        assertEquals(Integer.valueOf(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),
                Integer.valueOf(scroll.getHorizontalScrollBarPolicy()),
                "nested horizontal scrollbar is hidden during expansion");

        Timer timer = (Timer) field(section, "expansionTimer");
        assertTrue(timer.isRunning(), "collapsible expansion animation is active");
        setField(section, "expansionAnimationStartedAt", Long.valueOf(System.currentTimeMillis() - 1000L));
        invoke(section, "tickExpansionAnimation", new Class<?>[0]);

        assertFalse(timer.isRunning(), "collapsible expansion animation finishes");
        assertEquals(Integer.valueOf(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED),
                Integer.valueOf(scroll.getVerticalScrollBarPolicy()),
                "nested vertical scrollbar returns after expansion");
        assertEquals(Integer.valueOf(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                Integer.valueOf(scroll.getHorizontalScrollBarPolicy()),
                "nested horizontal scrollbar returns after expansion");
    }

    /**
     * Verifies Solve mode exposes compact inspector sections instead of a stack of
     * elevated cards in the right rail.
     */
    private static void testPuzzleHeaderControlsAvoidClippingAtNarrowWidth() {
        String source = readSource(Path.of("src/application/gui/workbench/game/PuzzlePanel.java"));
        assertTrue(source.contains("Ui.emptyState(\"No puzzle loaded\""),
                "Solve mode has a designed empty state");
        assertTrue(source.contains("flatSection(\"Puzzle\""),
                "Solve mode exposes puzzle source and feedback in one compact section");
        assertTrue(source.contains("flatSection(\"Controls\""),
                "Solve mode keeps puzzle controls visible in a flat section");
        assertTrue(source.contains("flatSection(\"Solution\""),
                "Solve mode wraps solution handling in a flat section");
        assertFalse(source.contains("Ui.card(\"Puzzle Source\""),
                "Solve mode no longer uses a separate puzzle source card");
        assertFalse(source.contains("Ui.card(\"Progress / Feedback\""),
                "Solve mode no longer uses a separate feedback card");
    }

    /**
     * Verifies lazy editor wrappers do not construct heavy panels until the
     * wrapper is materialized.
     */
    private static void testLazyPanelDefersConstruction() {
        AtomicInteger created = new AtomicInteger();
        LazyPanel panel = new LazyPanel("Network", () -> {
            created.incrementAndGet();
            return new JPanel();
        });
        assertFalse(panel.isLoaded(), "lazy panel starts as placeholder");
        assertEquals(0, created.get(), "lazy panel factory is deferred");
        invoke(panel, "materialize", new Class<?>[0]);
        assertTrue(panel.isLoaded(), "lazy panel materializes on demand");
        assertEquals(1, created.get(), "lazy panel factory runs once");
    }

    /**
     * Verifies command-selector buttons reserve their bold selected width so
     * neighboring controls do not shift while clicking through command tabs.
     */
    private static void testCommandTabsReserveSelectedTextWidth() {
        JToggleButton tab = new JToggleButton("Generate FENs");
        Theme.commandTab(tab);
        Dimension plain = tab.getPreferredSize();
        tab.setSelected(true);
        Dimension selected = tab.getPreferredSize();
        assertEquals(plain, selected, "command tab preferred size is stable when selected");
    }

    /**
     * Verifies styled tab panes keep one scrollable row instead of wrapping
     * dense section tabs into stacked rows on small screens.
     */
    private static void testTabbedPaneUsesScrollableSingleRowTabs() {
        JTabbedPane tabs = Ui.tabbedPane();
        assertEquals(Integer.valueOf(JTabbedPane.SCROLL_TAB_LAYOUT),
                Integer.valueOf(tabs.getTabLayoutPolicy()),
                "tabbed pane uses scrollable single-row layout");
    }

    /**
     * Verifies styled tab panes switch immediately without keeping outgoing
     * content snapshots that can ghost over the next tab.
     */
    private static void testTabbedPaneSwitchesWithoutSnapshotOverlay() {
        JTabbedPane tabs = (JTabbedPane) invokeStatic(type("Ui"), "tabbedPane",
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
     * Verifies the flat tab UI tolerates mouse rollover during empty-pane
     * disposal.
     */
    private static void testTabbedPaneRolloverIgnoresEmptyPanes() {
        JTabbedPane tabs = Ui.tabbedPane();
        FlatTabbedPaneUI ui = (FlatTabbedPaneUI) tabs.getUI();
        assertEquals(Integer.valueOf(-1), Integer.valueOf(ui.tabForCoordinate(tabs, 1, 1)),
                "empty tabbed pane has no rollover tab");
    }

    /**
     * Finds the first descendant of the requested component type.
     *
     * @param root root component
     * @param type requested component type
     * @param <T> component type
     * @return first matching component, or null
     */
    private static <T extends Component> T firstDescendant(Component root, Class<T> type) {
        if (root == null || type == null) {
            return null;
        }
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = firstDescendant(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /**
     * Runs layout recursively after the root component receives a test size.
     *
     * @param component root component
     */
    private static void layoutTree(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    /**
     * Verifies visible descendants stay within their immediate parent bounds.
     *
     * @param container parent container
     * @param label assertion label
     */
    private static void assertVisibleChildrenInside(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            int overflowX = child.getX() + child.getWidth() - container.getWidth();
            int overflowY = child.getY() + child.getHeight() - container.getHeight();
            if (child.getX() < 0 || child.getY() < 0 || overflowX > 1 || overflowY > 1) {
                throw new AssertionError(label + " clips " + child.getClass().getSimpleName()
                        + " bounds=" + child.getBounds() + " parent="
                        + container.getWidth() + "x" + container.getHeight());
            }
            if (child instanceof Container childContainer) {
                assertVisibleChildrenInside(childContainer, label);
            }
        }
    }

    /**
     * Paints an icon into a transparent image.
     *
     * @param icon icon to paint
     * @return painted image
     */
    private static BufferedImage paintIcon(Icon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(new JLabel(), g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Returns the average color of non-transparent icon pixels.
     *
     * @param image source image
     * @return average paint color
     */
    private static Color averagePaintColor(BufferedImage image) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long count = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 32) {
                    red += pixel.getRed();
                    green += pixel.getGreen();
                    blue += pixel.getBlue();
                    count++;
                }
            }
        }
        if (count == 0L) {
            return new Color(0, 0, 0, 0);
        }
        return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
    }

}
