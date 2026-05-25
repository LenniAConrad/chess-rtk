package testing;

import application.cli.PathOps;
import static testing.WorkbenchTestSupport.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;

import application.gui.workbench.layout.LazyPanel;
import application.gui.workbench.layout.FlatTabbedPaneUI;
import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.dataset.DatasetChart;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SettingsChipRow;
import application.gui.workbench.ui.TagCloud;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.window.LayoutMenu;
import application.gui.workbench.window.SettingsMenu;

import chess.core.Move;
import chess.struct.Game;
import chess.uci.Output;

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
        testOptionFilterTokenMatching();
        testTextAreaScrollPaintsOpaque();
        testScrollPaneUsesSolidCorners();
        testViewportFillWrapperTracksAvailableHeight();
        testCenteredViewportAvoidsHorizontalScrolling();
        testDataSurfacesUseSolidBackgrounds();
        testTagCloudGroupsAndWrapsTags();
        testCustomPaintedSurfacesClearBackground();
        testBooleanTableRendererIsStyled();
        testComponentTreeStylingCoversPlainControls();
        testPlainCheckboxUsesWorkbenchGlyph();
        testProgressBarUsesWorkbenchChrome();
        testDisabledComboUsesThemeBackground();
        testEnabledComboFillsArrowGutter();
        testSpinnerEditorUsesReadableInputColors();
        testSpinnerFillsArrowGutter();
        testGameLineImportInputKeepsMultilineHeight();
        testSettingsToggleRowsAreReadable();
        testSettingsSliderRefreshKeepsReadableColors();
        testSettingsMenuExposesThemeModes();
        testSettingsTextStaysReadableInBothModes();
        testLayoutMenuExposesUsefulWorkbenchControls();
        testToggleSwitchAnimatesStateChanges();
        testStatusBadgeAnimatesStateChanges();
        testStatusBadgeCanReserveStableTextWidth();
        testSegmentedSwitcherAnimatesSelection();
        testSplitPaneSashAnimatesHover();
        testChartsRevealNewData();
        testCommandFormOptionalTogglesFillLeadColumn();
        testCommandFormFlagsWrapAcrossColumns();
        testThemeColorContrast();
        testThemeUsesVscodeModernColorTokens();
        testNetworkPaletteUsesSemanticFocusColor();
        testNetworkArchitectureBlocksKeepReadableNeutralFill();
        testNnueAtlasPlaneLabelsUseUniformColor();
        testHorizontalMetricBarKeepsLabelLaneClear();
        testBoardMarkupBrushesFollowThemeMode();
        testThemeRefreshPreservesLabelRoles();
        testThemeRefreshUpdatesLineBorders();
        testThemeRefreshRestoresCustomControlUis();
        testThemeInstallSetsTooltipColors();
        testThemeUsesDeliberateFontStacks();
        testFileChooserIconsUseThemePalette();
        testToastUsesBottomRightPlacement();
        testToastFadeAppliesToTextAndChromeTogether();
        testCollapsibleInfoSectionTogglesContent();
        testCollapsibleSectionDefersNestedScrollbarsDuringAnimation();
        testPuzzleHeaderControlsAvoidClippingAtNarrowWidth();
        testLazyPanelDefersConstruction();
        testCommandTabsReserveSelectedTextWidth();
        testTabbedPaneUsesScrollableSingleRowTabs();
        testTabbedPaneSwitchesWithoutSnapshotOverlay();
        testTabbedPaneRolloverIgnoresEmptyPanes();
        testSplitAreaUsesIndependentEditorGroups();
        testSplitAreaCreatesVerticalSplitsForTopBottomActions();
        testSplitAreaTabSelectionDoesNotRebuildDivider();
        testSplitAreaTabSelectionReusesTabComponents();
        testSplitAreaThemeRefreshUpdatesHiddenTabs();
        testSplitAreaSupportsCornerEditorGroups();
        testSplitAreaDocksDraggedTabsBackIntoGroup();
        testSplitAreaExposesFlexibleTabActions();
        testSplitAreaDuplicatesFactoryBackedTabs();
        testSplitAreaDuplicatesFactoryBackedToolTabs();
        testDetachedAnalysisWorkspaceKeepsLocalHistory();
        testEditorShellUsesVscodeStyleSplitChrome();
        testEditorShellShowsRookWatermarkWhenEmpty();
        testEditorShellRefreshesHiddenPanelTheme();
        testButtonHoverTransitionStarts();
        testWorkbenchTimingDefaultsAreSnappy();
        testWorkbenchOperationalDefaultsAreSnappy();
        testResetButtonUsesResetIcon();
        testButtonDisabledIconIsMuted();
        testIconOnlyButtonKeepsIconAfterThemeRefresh();
        testBoardNavigationButtonsUseTransportIcons();
        testBoardNavigationButtonsExposeShortcutTooltips();
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
        Object action = construct(type("CommandPalette$PaletteAction"),
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
        root.add(field);
        root.add(button);
        root.add(pane);

        invokeStatic(type("Ui"), "styleComponentTree", new Class<?>[] { Component.class }, root);

        assertEquals(Integer.valueOf(255), Integer.valueOf(field.getBackground().getAlpha()),
                "recursive text field solid alpha");
        assertFalse(button.isContentAreaFilled(), "recursive button content area hidden");
        assertTrue(list.isOpaque(), "recursive list opaque");
        assertEquals(list.getBackground(), pane.getViewport().getBackground(), "recursive scroll viewport background");
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
     * Verifies the top-level settings menu exposes light and dark modes.
     */
    private static void testSettingsMenuExposesThemeModes() {
        Theme.Mode[] activeMode = { Theme.Mode.LIGHT };
        boolean[] displaySettingsOpened = { false };
        boolean[] engineSettingsOpened = { false };
        boolean[] soundSettingsOpened = { false };
        boolean[] soundEnabled = { true };
        SettingsMenu menu = new SettingsMenu(new SettingsMenu.Controller() {
            @Override
            public Theme.Mode themeMode() {
                return activeMode[0];
            }

            @Override
            public void setThemeMode(Theme.Mode mode) {
                activeMode[0] = mode;
            }

            @Override
            public boolean soundEnabled() {
                return soundEnabled[0];
            }

            @Override
            public void setSoundEnabled(boolean enabled) {
                soundEnabled[0] = enabled;
            }

            @Override
            public void showDisplaySettings() {
                displaySettingsOpened[0] = true;
            }

            @Override
            public void showEngineSettings() {
                engineSettingsOpened[0] = true;
            }

            @Override
            public void showSoundSettings() {
                soundSettingsOpened[0] = true;
            }

            @Override
            public void showCommandPalette() {
                // not needed for this regression
            }

            @Override
            public void openLogsDirectory() {
                // not needed for this regression
            }
        });

        JMenuBar bar = menu.component();
        assertEquals(Integer.valueOf(1), Integer.valueOf(bar.getMenuCount()), "settings menu count");
        JMenu settings = bar.getMenu(0);
        assertEquals("Settings", settings.getText(), "settings menu label");
        JPopupMenu popup = settings.getPopupMenu();
        JPanel settingsPanel = namedComponent(popup, JPanel.class, "settingsPanel");
        assertTrue(settingsPanel.getPreferredSize().width >= 360, "settings popup is a larger grouped panel");
        ChipGroup appearance = namedComponent(popup, ChipGroup.class, "settings.appearance");
        ChipGroup sound = namedComponent(popup, ChipGroup.class, "settings.sound");
        assertEquals(Integer.valueOf(0), Integer.valueOf(appearance.getSelectedIndex()),
                "light mode starts selected");
        assertEquals(Integer.valueOf(0), Integer.valueOf(sound.getSelectedIndex()),
                "sound starts enabled");

        clickChip(appearance, 1);
        assertEquals(Theme.Mode.DARK, activeMode[0], "dark menu item applies dark mode");
        Theme.setMode(Theme.Mode.DARK);
        menu.syncMode();
        menu.refreshTheme();
        assertEquals(Integer.valueOf(1), Integer.valueOf(appearance.getSelectedIndex()),
                "dark chip reflects controller state");
        Theme.setMode(Theme.Mode.LIGHT);

        clickChip(sound, 1);
        assertTrue(!soundEnabled[0], "sound menu toggles effects off");
        soundEnabled[0] = true;
        menu.syncMode();
        assertEquals(Integer.valueOf(0), Integer.valueOf(sound.getSelectedIndex()),
                "sound chip reflects controller state");
        button(popup, "Sound").doClick();
        button(popup, "Board").doClick();
        button(popup, "Engine").doClick();
        assertTrue(soundSettingsOpened[0], "settings menu opens sound settings");
        assertTrue(displaySettingsOpened[0], "settings menu opens board settings");
        assertTrue(engineSettingsOpened[0], "settings menu opens engine settings");
    }

    /**
     * Verifies settings text remains readable in both palettes.
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
            @Override
            public Theme.Mode themeMode() {
                return Theme.mode();
            }

            @Override
            public void setThemeMode(Theme.Mode mode) {
                Theme.setMode(mode);
            }

            @Override
            public boolean soundEnabled() {
                return true;
            }

            @Override
            public void setSoundEnabled(boolean enabled) {
                // no-op test controller
            }

            @Override
            public void showDisplaySettings() {
                // no-op test controller
            }

            @Override
            public void showEngineSettings() {
                // no-op test controller
            }

            @Override
            public void showSoundSettings() {
                // no-op test controller
            }

            @Override
            public void showCommandPalette() {
                // no-op test controller
            }

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
        int[] restoreTabs = { 0 };
        int[] closeOthers = { 0 };
        LayoutMenu menu = new LayoutMenu(new LayoutMenu.Controller() {
            @Override
            public boolean statusBarVisible() {
                return statusVisible[0];
            }

            @Override
            public void setStatusBarVisible(boolean visible) {
                statusVisible[0] = visible;
            }

            @Override
            public void splitRight() {
                splitRight[0]++;
            }

            @Override
            public void splitDown() {
                splitDown[0]++;
            }

            @Override
            public void splitLeft() {
                splitLeft[0]++;
            }

            @Override
            public void splitUp() {
                splitUp[0]++;
            }

            @Override
            public void reopenAllTabs() {
                restoreTabs[0]++;
            }

            @Override
            public void closeOtherTabs() {
                closeOthers[0]++;
            }

            @Override
            public int openTabCount() {
                return 7;
            }

            @Override
            public int visibleGroupCount() {
                return 2;
            }
        });

        JComponent component = menu.component();
        assertEquals(Integer.valueOf(4), Integer.valueOf(component.getComponentCount()),
                "layout toolbar exposes four chrome buttons");
        assertEquals("Customize Layout", ((JButton) component.getComponent(0)).getToolTipText(),
                "layout toolbar starts with customize button");
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
        assertThemedCheckIcon(statusItem, "status-bar row");
        assertWorkbenchMenuChrome(statusItem, "status-bar row");
        statusItem.doClick();
        assertFalse(statusVisible[0], "status-bar row toggles controller state");

        popupItem(popup, "Split Left").doClick();
        popupItem(popup, "Split Up").doClick();
        popupItem(popup, "Close Other Tabs").doClick();
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitLeft[0]),
                "layout popup split-left item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(splitUp[0]),
                "layout popup split-up item works");
        assertEquals(Integer.valueOf(1), Integer.valueOf(closeOthers[0]),
                "layout popup close-others item works");
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
     * Finds a named component inside a component tree.
     *
     * @param <T> component type
     * @param root root component
     * @param type component type
     * @param name component name
     * @return matching component
     */
    private static <T extends Component> T namedComponent(Component root, Class<T> type, String name) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return namedComponent(child, type, name);
                } catch (AssertionError ex) {
                    // continue searching sibling branches
                }
            }
        }
        throw new AssertionError("missing component " + name);
    }

    /**
     * Finds a button by text inside a component tree.
     *
     * @param root root component
     * @param text button text
     * @return matching button
     */
    private static JButton button(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return button(child, text);
                } catch (AssertionError ex) {
                    // continue searching sibling branches
                }
            }
        }
        throw new AssertionError("missing button " + text);
    }

    /**
     * Simulates a mouse press on a two-choice chip group.
     *
     * @param chips chip group
     * @param index chip index
     */
    private static void clickChip(ChipGroup chips, int index) {
        Dimension size = chips.getPreferredSize();
        chips.setSize(size);
        int x = index == 0 ? 6 : size.width - 6;
        MouseEvent event = new MouseEvent(chips, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, size.height / 2, 1, false, MouseEvent.BUTTON1);
        chips.dispatchEvent(event);
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
     * Verifies command-builder optional toggles fill the shared lead column so
     * short flags such as --quiet align with longer flags such as --no-header.
     */
    private static void testCommandFormOptionalTogglesFillLeadColumn() {
        JComponent toggle = (JComponent) construct(type("ToggleBox"),
                new Class<?>[] { String.class, boolean.class }, "--quiet", true);
        JComponent holder = (JComponent) invokeStatic(type("CommandForm"), "fixedLead",
                new Class<?>[] { javax.swing.JComponent.class }, toggle);
        holder.setSize(holder.getPreferredSize());
        holder.doLayout();

        int leadWidth = (Integer) staticField(type("CommandForm"), "LEAD_WIDTH");
        assertEquals(Integer.valueOf(leadWidth), Integer.valueOf(toggle.getWidth()),
                "optional toggle fills command lead column");
    }

    /**
     * Verifies command-builder flags wrap horizontally into additional columns
     * when the optional section has enough width.
     */
    private static void testCommandFormFlagsWrapAcrossColumns() {
        JComponent grid = (JComponent) construct(type("CommandForm$FlagGridPanel"), new Class<?>[0]);
        for (int i = 0; i < 6; i++) {
            JPanel row = new JPanel();
            row.setPreferredSize(new Dimension(520, 36));
            grid.add(row);
        }

        grid.setSize(1600, 200);
        grid.doLayout();
        Component first = grid.getComponent(0);
        Component second = grid.getComponent(1);
        Component fourth = grid.getComponent(3);
        assertEquals(Integer.valueOf(first.getY()), Integer.valueOf(second.getY()),
                "wide flags share a row");
        assertTrue(second.getX() > first.getX(), "second flag sits to the right of the first");
        assertTrue(fourth.getY() > first.getY(), "wide flags wrap after fitting columns");

        grid.setSize(540, 260);
        grid.doLayout();
        assertEquals(Integer.valueOf(first.getX()), Integer.valueOf(second.getX()),
                "narrow flags use one column");
        assertTrue(second.getY() > first.getY(), "narrow flags stack vertically");
    }

    /**
     * Verifies core theme foreground/background pairs meet practical contrast
     * thresholds for extended workbench use.
     */
    private static void testThemeColorContrast() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
        assertCurrentThemeContrast("light");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        assertCurrentThemeContrast("dark");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies the workbench uses VS Code Modern neutral and control tokens
     * consistently across light and dark modes.
     */
    private static void testThemeUsesVscodeModernColorTokens() {
        Theme.setMode(Theme.Mode.LIGHT);
        assertColor(new Color(0xF8F8F8), themeColor("BG"), "light panel background");
        assertColor(Color.WHITE, themeColor("PANEL_SOLID"), "light VS Code editor background");
        assertColor(Color.WHITE, themeColor("ELEVATED_SOLID"), "light VS Code dropdown background");
        assertColor(new Color(0xE5E5E5), themeColor("LINE"), "light panel border");
        assertColor(new Color(0xCECECE), themeColor("INPUT_BORDER"), "light input border");
        assertColor(new Color(0xFFFFFF), themeColor("TAB_HOVER"), "light VS Code tab hover");
        assertColor(new Color(0xF8F8F8), themeColor("TAB_IDLE"), "light inactive tab");
        assertColor(new Color(0x3B3B3B), themeColor("TEXT"), "light foreground");
        assertColor(new Color(0x616161), themeColor("MUTED"), "light muted foreground");
        assertColor(new Color(0x005FB8), themeColor("ACCENT"), "light VS Code focus accent");
        assertColor(new Color(0xBED6ED), themeColor("TOGGLE_ON_BG"), "light active option fill");

        Theme.setMode(Theme.Mode.DARK);
        assertColor(new Color(0x181818), themeColor("BG"), "dark VS Code panel background");
        assertColor(new Color(0x1F1F1F), themeColor("PANEL_SOLID"), "dark VS Code editor background");
        assertColor(new Color(0x313131), themeColor("ELEVATED_SOLID"), "dark dropdown background");
        assertColor(new Color(0x2B2B2B), themeColor("LINE"), "dark panel border");
        assertColor(new Color(0x3C3C3C), themeColor("INPUT_BORDER"), "dark input border");
        assertColor(new Color(0x1F1F1F), themeColor("TAB_HOVER"), "dark VS Code tab hover");
        assertColor(new Color(0x181818), themeColor("TAB_IDLE"), "dark VS Code inactive tab");
        assertColor(new Color(0xCCCCCC), themeColor("TEXT"), "dark foreground");
        assertColor(new Color(0x9D9D9D), themeColor("MUTED"), "dark muted foreground");
        assertColor(new Color(0x0078D4), themeColor("ACCENT"), "dark VS Code focus accent");
        assertColor(new Color(36, 137, 219, 130), themeColor("TOGGLE_ON_BG"),
                "dark active option fill");
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
     * Verifies board annotation brushes resolve colors lazily from the active
     * theme instead of keeping stale colors from the mode in which they were
     * created.
     */
    private static void testBoardMarkupBrushesFollowThemeMode() {
        Theme.setMode(Theme.Mode.LIGHT);
        MarkupBrush blue = new MarkupBrush("blue", Theme.ACCENT, 10);
        Color lightBlue = blue.themedColor();

        Theme.setMode(Theme.Mode.DARK);
        assertEquals(themeColor("ACCENT"), blue.themedColor(), "blue markup follows dark accent");
        assertColorDistanceAtLeast(lightBlue, blue.themedColor(), 16.0,
                "blue markup changes when the theme accent changes");

        Theme.setMode(Theme.Mode.LIGHT);
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
    }

    /**
     * Verifies the workbench keeps interface and code fonts separated.
     */
    private static void testThemeUsesDeliberateFontStacks() {
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
     * Verifies the puzzle header control strip keeps every visible control inside
     * its parent bounds on a narrow editor pane.
     */
    private static void testPuzzleHeaderControlsAvoidClippingAtNarrowWidth() {
        JComponent panel = (JComponent) construct(type("PuzzlePanel"),
                new Class<?>[] { boolean.class }, Boolean.FALSE);
        JComponent header = (JComponent) panel.getComponent(0);
        header.setSize(360, 96);
        Dimension preferred = header.getPreferredSize();
        header.setSize(360, preferred.height);
        layoutTree(header);

        assertTrue(preferred.height > 38, "puzzle header is allowed to grow beyond one cramped row");
        assertVisibleChildrenInside(header, "puzzle header");
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
     * Verifies the workbench split shell keeps separate editor-group tab lists,
     * so moving a tab into the other pane does not duplicate every tab in both
     * strips.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaUsesIndependentEditorGroups() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        assertFalse(primary.contains(2), "dragged tab moved out of primary group");
        assertTrue(secondary.contains(2), "dragged tab moved into secondary group");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "secondary group becomes active after right split");

        invoke(area, "setSecondary", new Class<?>[] { int.class }, 1);
        assertFalse(primary.contains(1), "center drop removes tab from source group");
        assertTrue(secondary.contains(1), "center drop adds tab to target group");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, 1),
                "moved tab is visible in target group");

        invoke(area, "setPrimary", new Class<?>[] { int.class }, 1);
        assertTrue(primary.contains(1), "tab can move back to primary group");
        assertFalse(secondary.contains(1), "tab is not duplicated across editor groups");
    }

    /**
     * Verifies top and bottom split actions create vertical editor groups
     * rather than being routed through the horizontal left/right split.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaCreatesVerticalSplitsForTopBottomActions() {
        Object downArea = splitFixture();
        invoke(downArea, "splitSelectedTabDown", new Class<?>[0]);
        JSplitPane downSplit = (JSplitPane) field(downArea, "splitPane");
        assertEquals(Integer.valueOf(JSplitPane.VERTICAL_SPLIT), Integer.valueOf(downSplit.getOrientation()),
                "split-down creates a top-bottom split pane");
        assertEquals(Integer.valueOf(2), invoke(downArea, "visibleGroupCount", new Class<?>[0]),
                "split-down leaves two visible editor groups");
        List<Integer> tertiary = (List<Integer>) field(downArea, "tertiaryTabs");
        assertTrue(tertiary.contains(1), "split-down places the selected tab below");

        Object upArea = splitFixture();
        invoke(upArea, "splitSelectedTabUp", new Class<?>[0]);
        JSplitPane upSplit = (JSplitPane) field(upArea, "splitPane");
        assertEquals(Integer.valueOf(JSplitPane.VERTICAL_SPLIT), Integer.valueOf(upSplit.getOrientation()),
                "split-up creates a top-bottom split pane");
        List<Integer> primary = (List<Integer>) field(upArea, "primaryTabs");
        assertTrue(primary.contains(1), "split-up places the selected tab above");
    }

    /**
     * Creates a selected three-tab editor area for split command tests.
     *
     * @return installed editor split area
     */
    private static Object splitFixture() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 3; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "select", new Class<?>[] { int.class }, 1);
        return area;
    }

    /**
     * Verifies ordinary tab selection inside a split editor group does not
     * recreate the split pane and momentarily reset divider geometry.
     */
    private static void testSplitAreaTabSelectionDoesNotRebuildDivider() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);
        JSplitPane split = (JSplitPane) field(area, "splitPane");
        split.setDividerLocation(320);

        invoke(area, "setPrimary", new Class<?>[] { int.class }, 1);

        assertTrue(split == field(area, "splitPane"), "tab selection reuses the existing split pane");
        assertEquals(Integer.valueOf(320), Integer.valueOf(split.getDividerLocation()),
                "tab selection keeps divider location stable");
        assertEquals(Integer.valueOf(1), invoke(area, "selectedIndex", new Class<?>[0]),
                "selected primary tab becomes active");
    }

    /**
     * Verifies ordinary tab selection updates existing tab components in place
     * instead of recreating the whole strip for every click.
     */
    private static void testSplitAreaTabSelectionReusesTabComponents() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 24; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        JPanel primaryStrip = (JPanel) field(area, "primaryStrip");
        Component firstTab = primaryStrip.getComponent(0);
        Component secondTab = primaryStrip.getComponent(1);

        invoke(area, "select", new Class<?>[] { int.class }, 1);

        assertTrue(firstTab == primaryStrip.getComponent(0), "first tab component is reused");
        assertTrue(secondTab == primaryStrip.getComponent(1), "selected tab component is reused");
        assertEquals(Integer.valueOf(1), invoke(area, "selectedIndex", new Class<?>[0]),
                "tab selection still changes the active panel");
    }

    /**
     * Verifies editor-shell theme refresh reaches stored tab panels that are
     * not currently attached to the visible Swing hierarchy.
     */
    private static void testSplitAreaThemeRefreshUpdatesHiddenTabs() {
        Theme.setMode(Theme.Mode.LIGHT);
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        JPanel visible = new JPanel();
        JPanel hidden = new JPanel();
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Visible", visible);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Hidden", hidden);
        invoke(area, "install", new Class<?>[0]);
        assertTrue(hidden.getParent() == null, "second tab panel starts detached from the visible host");

        Theme.setMode(Theme.Mode.DARK);
        invoke(area, "refreshTheme", new Class<?>[0]);
        assertEquals(themeColor("PANEL_SOLID"), visible.getBackground(), "visible tab follows dark panel");
        assertEquals(themeColor("PANEL_SOLID"), hidden.getBackground(), "hidden tab follows dark panel");
        assertEquals(themeColor("BG"), ((JComponent) area).getBackground(), "editor shell restores dark chrome");

        Theme.setMode(Theme.Mode.LIGHT);
        invoke(area, "refreshTheme", new Class<?>[0]);
        assertEquals(themeColor("PANEL_SOLID"), hidden.getBackground(), "hidden tab follows light panel");
    }

    /**
     * Verifies corner tab drops can create VS Code-style quadrant editor groups
     * instead of being limited to left/right or top/bottom splits.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaSupportsCornerEditorGroups() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        setField(area, "dragZone", staticField(areaType, "DROP_BOTTOM_RIGHT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 1);
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        List<Integer> quaternary = (List<Integer>) field(area, "quaternaryTabs");
        assertTrue(secondary.contains(2), "existing right group stays in the top-right quadrant");
        assertTrue(quaternary.contains(1), "bottom-right corner creates a bottom-right group");
        assertEquals(Integer.valueOf(1), invoke(area, "selectedIndex", new Class<?>[0]),
                "bottom-right corner drop activates the moved tab");

        setField(area, "dragZone", staticField(areaType, "DROP_TOP_LEFT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 3);
        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> tertiary = (List<Integer>) field(area, "tertiaryTabs");
        assertTrue(primary.contains(3), "top-left corner isolates the dragged tab");
        assertTrue(tertiary.contains(0), "previous top-left tabs move into the bottom-left group");
        assertFalse(primary.contains(0), "top-left corner split does not duplicate displaced tabs");

        invoke(area, "closeTab", new Class<?>[] { int.class }, 3);
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "closing active top-left group falls back to the repaired primary group");
        invoke(area, "select", new Class<?>[] { int.class }, 3);
        primary = (List<Integer>) field(area, "primaryTabs");
        assertTrue(primary.contains(3), "closed tab reopens in the active visible group");

        invoke(area, "collapseSplit", new Class<?>[0]);
        primary = (List<Integer>) field(area, "primaryTabs");
        secondary = (List<Integer>) field(area, "secondaryTabs");
        tertiary = (List<Integer>) field(area, "tertiaryTabs");
        quaternary = (List<Integer>) field(area, "quaternaryTabs");
        assertTrue(primary.contains(0), "collapse preserves bottom-left tabs");
        assertTrue(primary.contains(1), "collapse preserves bottom-right tabs");
        assertTrue(primary.contains(2), "collapse preserves top-right tabs");
        assertTrue(primary.contains(3), "collapse preserves reopened top-left tabs");
        assertTrue(secondary.isEmpty(), "collapse clears top-right group");
        assertTrue(tertiary.isEmpty(), "collapse clears bottom-left group");
        assertTrue(quaternary.isEmpty(), "collapse clears bottom-right group");
    }

    /**
     * Verifies a tab dragged onto another editor group's tab strip docks back
     * into that group instead of creating another split zone.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaDocksDraggedTabsBackIntoGroup() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        invoke(area, "dockDraggedTab", new Class<?>[] { int.class, int.class, int.class },
                2, staticField(areaType, "PANE_PRIMARY"), 1);
        List<Integer> primary = (List<Integer>) field(area, "primaryTabs");
        List<Integer> secondary = (List<Integer>) field(area, "secondaryTabs");
        assertEquals(Integer.valueOf(1), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "docking the only split tab back collapses to one editor group");
        assertEquals(Integer.valueOf(2), primary.get(1),
                "docked tab uses the requested tab-strip insertion point");
        assertTrue(secondary.isEmpty(), "source editor group is emptied after docking back");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "docked tab becomes the active tab in the target group");
    }

    /**
     * Verifies tab management does not depend on pointer dragging alone.
     */
    private static void testSplitAreaExposesFlexibleTabActions() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 3; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "select", new Class<?>[] { int.class }, 1);
        invoke(area, "splitSelectedTabRight", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "shortcut split creates a second editor group");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, 1),
                "split tab remains visible");

        JComponent primaryStrip = (JComponent) field(area, "primaryStrip");
        Component firstTab = primaryStrip.getComponent(0);
        JPopupMenu menu = ((JComponent) firstTab).getComponentPopupMenu();
        assertTrue(menu != null, "tab exposes a context action menu");
        assertEquals("Split Right", ((JMenuItem) menu.getComponent(0)).getText(),
                "tab menu starts with split actions");
        assertEquals("Close Others", ((JMenuItem) menu.getComponent(5)).getText(),
                "tab menu exposes close-others action");

        invoke(area, "closeOtherTabs", new Class<?>[0]);
        assertEquals(Integer.valueOf(1), invoke(area, "openTabCount", new Class<?>[0]),
                "close-others keeps only the active tab");
        invoke(area, "reopenAllTabs", new Class<?>[0]);
        assertEquals(Integer.valueOf(3), invoke(area, "openTabCount", new Class<?>[0]),
                "restore-all reopens hidden tabs");
        invoke(area, "closeSelectedTab", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(area, "openTabCount", new Class<?>[0]),
                "close active tab hides one tab");
    }

    /**
     * Verifies factory-backed editor tabs can be opened repeatedly, producing
     * distinct Swing components that can be shown in separate editor groups.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaDuplicatesFactoryBackedTabs() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        java.util.function.Supplier<JComponent> supplier = JPanel::new;
        invoke(area, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Analyze", new JPanel(), supplier);
        invoke(area, "install", new Class<?>[0]);
        JPanel primaryStrip = (JPanel) field(area, "primaryStrip");
        assertTrue(primaryStrip.getComponent(1) instanceof JToggleButton,
                "factory-backed tabs expose a visible new-tab affordance");
        assertEquals("New or restore tab", ((JToggleButton) primaryStrip.getComponent(1)).getToolTipText(),
                "new-tab affordance explains both creation and restore");

        int firstCopy = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);
        int secondCopy = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);

        assertEquals(Integer.valueOf(3), invoke(area, "openTabCount", new Class<?>[0]),
                "duplicate analysis tabs remain open");
        assertEquals(Integer.valueOf(3), invoke(area, "count", new Class<?>[0]),
                "duplicate analysis tabs are registered");
        List<String> names = (List<String>) field(area, "names");
        assertEquals("Analyze 2", names.get(firstCopy), "first duplicate gets a numbered label");
        assertEquals("Analyze 3", names.get(secondCopy), "second duplicate gets a numbered label");
        List<JComponent> panels = (List<JComponent>) field(area, "panels");
        assertFalse(panels.get(0) == panels.get(firstCopy), "duplicate tab has a fresh component");

        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, secondCopy, false);
        assertEquals(Integer.valueOf(2), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "duplicate tab can split beside the original");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, secondCopy),
                "duplicate tab is visible after splitting");

        Object splitArea = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(splitArea, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Analyze", new JPanel(), supplier);
        invoke(splitArea, "install", new Class<?>[0]);
        invoke(splitArea, "splitSelectedTabRight", new Class<?>[0]);
        assertEquals(Integer.valueOf(2), invoke(splitArea, "openTabCount", new Class<?>[0]),
                "VS Code split command duplicates a factory-backed tab");
        assertEquals(Integer.valueOf(2), invoke(splitArea, "visibleGroupCount", new Class<?>[0]),
                "split command opens the duplicate beside the original");
        List<String> splitNames = (List<String>) field(splitArea, "names");
        assertEquals("Analyze 2", splitNames.get(1), "split duplicate gets a numbered label");
        invoke(splitArea, "splitSelectedTabDown", new Class<?>[0]);
        assertEquals(Integer.valueOf(3), invoke(splitArea, "openTabCount", new Class<?>[0]),
                "split command can duplicate again from an existing editor group");
        assertEquals(Integer.valueOf(3), invoke(splitArea, "visibleGroupCount", new Class<?>[0]),
                "split command adds an adjacent group without collapsing existing groups");
        List<Integer> secondaryTabs = (List<Integer>) field(splitArea, "secondaryTabs");
        List<Integer> quaternaryTabs = (List<Integer>) field(splitArea, "quaternaryTabs");
        assertTrue(secondaryTabs.contains(1), "existing right group keeps its active duplicate");
        assertTrue(quaternaryTabs.contains(2), "down split from right group targets bottom-right");
    }

    /**
     * Verifies tool-style tabs such as Network, Datasets, and Publish can be
     * opened repeatedly from factory-backed registrations.
     */
    @SuppressWarnings("unchecked")
    private static void testSplitAreaDuplicatesFactoryBackedToolTabs() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        AtomicInteger networkCreated = new AtomicInteger();
        Supplier<JComponent> networkFactory = () -> new LazyPanel("Network", () -> {
            networkCreated.incrementAndGet();
            return new JPanel();
        });
        Supplier<JComponent> dataFactory = () -> new LazyPanel("Datasets", JPanel::new);
        Supplier<JComponent> publishFactory = () -> new LazyPanel("Publish", JPanel::new);
        invoke(area, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Network", new LazyPanel("Network", JPanel::new), networkFactory);
        invoke(area, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Datasets", new LazyPanel("Datasets", JPanel::new), dataFactory);
        invoke(area, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Publish", new LazyPanel("Publish", JPanel::new), publishFactory);
        invoke(area, "install", new Class<?>[0]);

        int networkSecond = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);
        int networkThird = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);
        int dataSecond = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 1);
        int publishSecond = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 2);

        assertEquals(Integer.valueOf(7), invoke(area, "openTabCount", new Class<?>[0]),
                "multiple tool tab duplicates remain open");
        List<String> names = (List<String>) field(area, "names");
        assertEquals("Network 2", names.get(networkSecond), "first Network duplicate is numbered");
        assertEquals("Network 3", names.get(networkThird), "second Network duplicate is numbered");
        assertEquals("Datasets 2", names.get(dataSecond), "Datasets duplicate is numbered");
        assertEquals("Publish 2", names.get(publishSecond), "Publish duplicate is numbered");
        List<JComponent> panels = (List<JComponent>) field(area, "panels");
        assertFalse(panels.get(0) == panels.get(networkSecond), "Network duplicate has a distinct wrapper");
        assertEquals(0, networkCreated.get(), "duplicate tool tabs stay lazy until shown");
        invoke(panels.get(networkSecond), "materialize", new Class<?>[0]);
        assertEquals(1, networkCreated.get(), "materializing one Network duplicate creates one panel");
    }

    /**
     * Verifies duplicate Analyze workspaces keep their own move history and
     * keyboard/navigation controls instead of being one-way throwaway boards.
     */
    @SuppressWarnings("unchecked")
    private static void testDetachedAnalysisWorkspaceKeepsLocalHistory() {
        Class<?> builderType = type("AnalysisWorkspacePanel$CommandBuilder");
        Object builder = Proxy.newProxyInstance(builderType.getClassLoader(),
                new Class<?>[] { builderType }, (proxy, method, args) -> List.of("engine", "analyze"));
        Consumer<List<String>> runner = args -> {
            // no-op command runner
        };
        Consumer<String> copier = text -> {
            // no-op clipboard bridge
        };
        Object workspace = construct(type("AnalysisWorkspacePanel"),
                new Class<?>[] { String.class, boolean.class, builderType, Consumer.class, Consumer.class },
                START_FEN, Boolean.TRUE, builder, runner, copier);

        invoke(workspace, "playMove", new Class<?>[] { short.class }, Move.parse("e2e4"));
        String afterE4 = (String) invoke(workspace, "currentFen", new Class<?>[0]);
        assertFalse(START_FEN.equals(afterE4), "detached analysis move changes local board");

        assertTrue((Boolean) invoke(workspace, "navigatePosition", new Class<?>[] { int.class }, -1),
                "detached analysis can navigate backward");
        assertEquals(START_FEN, invoke(workspace, "currentFen", new Class<?>[0]),
                "detached analysis back returns to local start");
        assertTrue((Boolean) invoke(workspace, "navigatePosition", new Class<?>[] { int.class }, 1),
                "detached analysis can navigate forward");
        assertEquals(afterE4, invoke(workspace, "currentFen", new Class<?>[0]),
                "detached analysis forward restores local move");

        invoke(workspace, "jumpPositionToStart", new Class<?>[0]);
        assertEquals(START_FEN, invoke(workspace, "currentFen", new Class<?>[0]),
                "detached analysis start shortcut uses local history");
        invoke(workspace, "jumpPositionToEnd", new Class<?>[0]);
        assertEquals(afterE4, invoke(workspace, "currentFen", new Class<?>[0]),
                "detached analysis end shortcut uses local history");
    }

    /**
     * Verifies editor-group split chrome follows VS Code's compact icon/sash
     * model rather than a text button and a visible divider grip.
     */
    private static void testEditorShellUsesVscodeStyleSplitChrome() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "One", new JPanel());
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Two", new JPanel());
        invoke(area, "install", new Class<?>[0]);
        JToggleButton splitButton = (JToggleButton) field(area, "splitButton");
        assertEquals("", splitButton.getText(), "split action uses icon-only chrome");
        assertEquals(new Dimension(28, 28), splitButton.getPreferredSize(),
                "split action keeps a compact VS Code-style hit target");

        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        invokeStatic(type("layout.SplitPaneStyler"), "style", new Class<?>[] { JSplitPane.class }, pane);
        assertEquals(Integer.valueOf(4), Integer.valueOf(pane.getDividerSize()),
                "split sash uses VS Code's four-pixel interaction strip");
        assertTrue(pane.isContinuousLayout(), "split sash resizes continuously");
        assertFalse(pane.isOneTouchExpandable(), "split sash has no Swing one-touch affordance");
    }

    /**
     * Verifies the editor shell can close to an empty VS Code-style host and
     * paints a subtle rook silhouette watermark instead of a blank pane.
     */
    @SuppressWarnings("unchecked")
    private static void testEditorShellShowsRookWatermarkWhenEmpty() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Only", new JPanel());
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "closeTab", new Class<?>[] { int.class }, 0);

        assertTrue(((List<Integer>) field(area, "open")).isEmpty(), "last tab can close");
        assertEquals(Integer.valueOf(-1), invoke(area, "selectedIndex", new Class<?>[0]),
                "empty editor has no selected tab");
        JComponent host = (JComponent) field(area, "primaryHost");
        assertEquals(Integer.valueOf(0), Integer.valueOf(host.getComponentCount()),
                "empty editor host contains no panel");
        assertEmbeddedRookWatermarkSilhouette();

        host.setSize(360, 300);
        BufferedImage image = paint(host, 360, 300);
        Color background = themeColor("PANEL_SOLID");
        int markedPixels = 0;
        for (int y = 70; y < 230; y++) {
            for (int x = 110; x < 250; x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (colorDistance(pixel, background) > 2.0) {
                    markedPixels++;
                }
            }
        }
        assertTrue(markedPixels > 1500, "empty editor paints filled rook watermark silhouette");
    }

    /**
     * Verifies the empty-editor watermark is sourced from the embedded rook SVG
     * silhouette rather than a simplified hand-built shape.
     */
    private static void assertEmbeddedRookWatermarkSilhouette() {
        try {
            Class<?> hostType = Class.forName("application.gui.workbench.layout.EmptyEditorHost");
            java.awt.Shape silhouette = (java.awt.Shape) staticField(hostType, "ROOK_WATERMARK_SILHOUETTE");
            java.awt.geom.Rectangle2D bounds = silhouette.getBounds2D();
            assertTrue(bounds.getWidth() > 110.0 && bounds.getHeight() > 130.0,
                    "empty editor rook watermark uses embedded rook SVG silhouette");
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("missing empty editor host class", ex);
        }
    }

    /**
     * Verifies panels constructed before a theme switch are refreshed when they
     * become visible later.
     */
    private static void testEditorShellRefreshesHiddenPanelTheme() {
        Class<?> modeType = type("Theme$Mode");
        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
        JTextArea hidden = new JTextArea("late panel");
        invokeStatic(type("Theme"), "area", new Class<?>[] { JTextArea.class }, hidden);
        Color lightBackground = hidden.getBackground();

        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "First", new JPanel());
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Hidden", hidden);
        invoke(area, "install", new Class<?>[0]);

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "DARK"));
        invoke(area, "select", new Class<?>[] { int.class }, 1);
        assertFalse(lightBackground.equals(hidden.getBackground()),
                "late-visible panel does not keep stale light background");
        assertEquals(themeColor("TEXT_AREA"), hidden.getBackground(),
                "late-visible panel refreshes to active theme");

        invokeStatic(type("Theme"), "setMode", new Class<?>[] { modeType },
                enumValue(modeType, "LIGHT"));
    }

    /**
     * Verifies styled buttons ease into hover state instead of switching instantly.
     */
    private static void testButtonHoverTransitionStarts() {
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
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
        Object board = construct(type("BoardPanel"), new Class<?>[0]);
        assertTrue((Integer) staticField(type("Ui"), "BUTTON_TRANSITION_MS") <= 80,
                "button transitions are short");
        assertTrue((Integer) staticField(type("BoardPanel"), "MOVE_ANIMATION_MS") <= 120,
                "board move animation is short");
        assertTrue((Integer) field(board, "snapbackAnimationMs") <= 100,
                "snapback animation is short");
        assertTrue((Integer) field(board, "snapAnimationMs") <= 70,
                "snap animation is short");
        assertTrue((Integer) field(board, "flipAnimationMs") <= 160,
                "flip animation is short");
        assertTrue((Integer) staticField(type("EvalBar"), "ANIMATION_DURATION_MS") <= 260,
                "eval bar transition is smooth but still responsive");
        assertTrue((Integer) staticField(type("StatusBadge"), "TRANSITION_MS") <= 140,
                "status badge transitions are short");
        assertTrue((Integer) staticField(type("SegmentedSwitcher"), "SELECTION_ANIMATION_MS") <= 160,
                "segmented switcher selection animation is short");
        assertTrue((Integer) staticField(type("SplitPaneStyler"), "SASH_TRANSITION_MS") <= 140,
                "split sash transition is short");
        assertTrue((Integer) staticField(type("CollapsibleSection"), "COLLAPSE_ANIMATION_MS") <= 160,
                "collapsible section transition is short");
        assertTrue((Integer) staticField(type("MiniChart"), "REVEAL_MS") <= 200,
                "mini chart reveal is short");
        assertTrue((Integer) staticField(DatasetChart.class, "BAR_REVEAL_MS") <= 220,
                "dataset chart reveal is short");
        assertTrue((Integer) staticField(type("ProgressBarChrome"), "PROGRESS_ANIMATION_MS") <= 190,
                "progress fill transition is short");
        assertTrue((Integer) staticField(type("Window"), "EVAL_DEBOUNCE_MS") <= 100,
                "eval refresh debounce is short");
    }

    /**
     * Verifies operational defaults stay responsive and avoid unnecessary
     * first-run CPU work.
     */
    private static void testWorkbenchOperationalDefaultsAreSnappy() {
        Class<?> defaults = type("Defaults");
        assertEquals("1s", staticField(defaults, "ANALYSIS_DURATION"),
                "interactive analysis default is short");
        assertEquals(Integer.valueOf(2), staticField(defaults, "ANALYSIS_MULTIPV"),
                "interactive MultiPV default is compact");
        assertTrue((Integer) staticField(defaults, "MCTS_VISITS") <= 300,
                "MCTS default visit budget is lightweight");
        assertEquals(Boolean.FALSE, staticField(defaults, "NETWORK_MCTS_FOLLOW_LEAF"),
                "Network MCTS does not re-infer every leaf by default");
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

    /**
     * Verifies the Reset button keeps the reset glyph.
     */
    private static void testResetButtonUsesResetIcon() {
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
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
        JButton button = (JButton) invokeStatic(type("Ui"), "button",
                new Class<?>[] { String.class, boolean.class, ActionListener.class },
                "Stop", false, (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "button icon present");
        assertTrue(button.getDisabledIcon() != null, "disabled button icon present");
        assertFalse(button.getIcon() == button.getDisabledIcon(), "disabled icon distinct");
    }

    /**
     * Verifies icon-only buttons keep their resolved glyph after theme refreshes.
     */
    private static void testIconOnlyButtonKeepsIconAfterThemeRefresh() {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                "Back", (ActionListener) event -> {
                    // no-op test listener
                });
        assertTrue(button.getIcon() != null, "icon-only button starts with icon");
        JPanel panel = new JPanel();
        panel.add(button);
        invokeStatic(type("Theme"), "refreshComponentTree", new Class<?>[] { Component.class }, panel);
        assertTrue(button.getIcon() != null, "icon-only button keeps icon after refresh");
        assertEquals("PREVIOUS", String.valueOf(field(button.getIcon(), "kind")), "icon-only button keeps kind");
    }

    /**
     * Verifies board-navigation labels resolve to distinct transport glyphs.
     */
    private static void testBoardNavigationButtonsUseTransportIcons() {
        assertButtonIconKind("Start", "FIRST");
        assertButtonIconKind("Back", "PREVIOUS");
        assertButtonIconKind("Forward", "NEXT");
        assertButtonIconKind("End", "LAST");
    }

    /**
     * Verifies board transport buttons expose their keyboard shortcuts.
     */
    private static void testBoardNavigationButtonsExposeShortcutTooltips() {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                "Start", (ActionListener) event -> {
                    // no-op test listener
                });
        invokeStatic(type("window.WindowBoardLayer"), "setTransportShortcut",
                new Class<?>[] { JButton.class, String.class }, button, "Home / Alt+Up");
        assertEquals("Start (Home / Alt+Up)", button.getToolTipText(),
                "board transport tooltip includes shortcut");
    }

    /**
     * Verifies one button label resolves to the expected icon kind.
     *
     * @param label button label
     * @param kind expected icon kind
     */
    private static void assertButtonIconKind(String label, String kind) {
        JButton button = (JButton) invokeStatic(type("Ui"), "iconButton",
                new Class<?>[] { String.class, ActionListener.class },
                label, (ActionListener) event -> {
                    // no-op test listener
                });
        assertEquals(kind, String.valueOf(field(button.getIcon(), "kind")), label + " icon kind");
    }
}
