package testing;

import static testing.WorkbenchTestSupport.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

import application.gui.workbench.dataset.DatasetChart;
import application.gui.workbench.layout.LazyPanel;
import application.gui.workbench.ui.Theme;
import chess.core.Move;

/**
 * Editor-shell, split-area, timing, and icon regression checks.
 */
final class WorkbenchUiEditorRegression {

    /**
     * Prevents instantiation.
     */
    private WorkbenchUiEditorRegression() {
        // utility
    }

    /**
     * Runs this focused regression group.
     */
    static void run() {
        testSplitAreaUsesIndependentEditorGroups();
        testSplitAreaCreatesVerticalSplitsForTopBottomActions();
        testSplitAreaTabSelectionDoesNotRebuildDivider();
        testSplitAreaTabSelectionReusesTabComponents();
        testSplitAreaThemeRefreshUpdatesHiddenTabs();
        testSplitAreaSupportsCornerEditorGroups();
        testSplitAreaDocksDraggedTabsBackIntoGroup();
        testSplitAreaDropOverlayStaysAboveContent();
        testSplitAreaShowsOverflowButtonWhenTabsDoNotFit();
        testSplitAreaExposesFlexibleTabActions();
        testEditorSplitActionsExposeClearStates();
        testSplitGroupsKeepLocalLayoutControls();
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
        testUtilityButtonsUseExpectedIcons();
        testIconOnlyButtonKeepsIconAfterThemeRefresh();
        testBoardNavigationButtonsUseTransportIcons();
        testBoardNavigationButtonsExposeShortcutTooltips();
    }

    /**
     * Verifies split panes keep independent tab groups while dragging tabs.
     */
    private static void testSplitAreaUsesIndependentEditorGroups() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        for (int i = 0; i < 4; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);

        List<Integer> primary = integerList(field(area, "primaryTabs"));
        List<Integer> secondary = integerList(field(area, "secondaryTabs"));
        assertFalse(primary.contains(2), "dragged tab moved out of primary group");
        assertTrue(secondary.contains(2), "dragged tab moved into secondary group");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "secondary group becomes active after right split");

        invoke(area, "setSecondary", new Class<?>[] { int.class }, 1);
        primary = integerList(field(area, "primaryTabs"));
        secondary = integerList(field(area, "secondaryTabs"));
        assertFalse(primary.contains(1), "center drop removes tab from source group");
        assertTrue(secondary.contains(1), "center drop adds tab to target group");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, 1),
                "moved tab is visible in target group");

        invoke(area, "setPrimary", new Class<?>[] { int.class }, 1);
        primary = integerList(field(area, "primaryTabs"));
        secondary = integerList(field(area, "secondaryTabs"));
        assertTrue(primary.contains(1), "tab can move back to primary group");
        assertFalse(secondary.contains(1), "tab is not duplicated across editor groups");
    }

    /**
     * Verifies top and bottom split actions create vertical editor groups
     * rather than being routed through the horizontal left/right split.
     */
    private static void testSplitAreaCreatesVerticalSplitsForTopBottomActions() {
        Object downArea = splitFixture();
        invoke(downArea, "splitSelectedTabDown", new Class<?>[0]);
        JSplitPane downSplit = (JSplitPane) field(downArea, "splitPane");
        assertEquals(Integer.valueOf(JSplitPane.VERTICAL_SPLIT), Integer.valueOf(downSplit.getOrientation()),
                "split-down creates a top-bottom split pane");
        assertEquals(Integer.valueOf(2), invoke(downArea, "visibleGroupCount", new Class<?>[0]),
                "split-down leaves two visible editor groups");
        List<Integer> tertiary = integerList(field(downArea, "tertiaryTabs"));
        assertTrue(tertiary.contains(1), "split-down places the selected tab below");

        Object upArea = splitFixture();
        invoke(upArea, "splitSelectedTabUp", new Class<?>[0]);
        JSplitPane upSplit = (JSplitPane) field(upArea, "splitPane");
        assertEquals(Integer.valueOf(JSplitPane.VERTICAL_SPLIT), Integer.valueOf(upSplit.getOrientation()),
                "split-up creates a top-bottom split pane");
        List<Integer> primary = integerList(field(upArea, "primaryTabs"));
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
        List<Integer> secondary = integerList(field(area, "secondaryTabs"));
        List<Integer> quaternary = integerList(field(area, "quaternaryTabs"));
        assertTrue(secondary.contains(2), "existing right group stays in the top-right quadrant");
        assertTrue(quaternary.contains(1), "bottom-right corner creates a bottom-right group");
        assertEquals(Integer.valueOf(1), invoke(area, "selectedIndex", new Class<?>[0]),
                "bottom-right corner drop activates the moved tab");

        setField(area, "dragZone", staticField(areaType, "DROP_TOP_LEFT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 3);
        List<Integer> primary = integerList(field(area, "primaryTabs"));
        List<Integer> tertiary = integerList(field(area, "tertiaryTabs"));
        assertTrue(primary.contains(3), "top-left corner isolates the dragged tab");
        assertTrue(tertiary.contains(0), "previous top-left tabs move into the bottom-left group");
        assertFalse(primary.contains(0), "top-left corner split does not duplicate displaced tabs");

        invoke(area, "closeTab", new Class<?>[] { int.class }, 3);
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "closing active top-left group falls back to the repaired primary group");
        invoke(area, "select", new Class<?>[] { int.class }, 3);
        primary = integerList(field(area, "primaryTabs"));
        assertTrue(primary.contains(3), "closed tab reopens in the active visible group");

        invoke(area, "collapseSplit", new Class<?>[0]);
        primary = integerList(field(area, "primaryTabs"));
        secondary = integerList(field(area, "secondaryTabs"));
        tertiary = integerList(field(area, "tertiaryTabs"));
        quaternary = integerList(field(area, "quaternaryTabs"));
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
        List<Integer> primary = integerList(field(area, "primaryTabs"));
        List<Integer> secondary = integerList(field(area, "secondaryTabs"));
        assertEquals(Integer.valueOf(1), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "docking the only split tab back collapses to one editor group");
        assertEquals(Integer.valueOf(2), primary.get(1),
                "docked tab uses the requested tab-strip insertion point");
        assertTrue(secondary.isEmpty(), "source editor group is emptied after docking back");
        assertEquals(Integer.valueOf(2), invoke(area, "selectedIndex", new Class<?>[0]),
                "docked tab becomes the active tab in the target group");
    }

    /**
     * Verifies the split drag-and-drop preview renders on a dedicated top layer
     * above the editor content, so it can never be painted under tabs, split
     * panes, or hosted panels that repaint independently mid-drag.
     */
    private static void testSplitAreaDropOverlayStaysAboveContent() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 2; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);

        JLayeredPane layered = (JLayeredPane) field(area, "layeredCentre");
        JComponent centre = (JComponent) field(area, "centre");
        JComponent overlay = (JComponent) field(area, "dropOverlay");

        // Structural z-order invariant: the overlay lives above the content on a
        // layered host that re-composites overlapping layers on partial repaints.
        assertTrue(layered.getLayer((Component) overlay) > layered.getLayer((Component) centre),
                "drop overlay sits on a higher layer than the editor content");
        assertFalse(layered.isOptimizedDrawingEnabled(),
                "layered host re-composites overlapping layers on child repaints");
        assertFalse(overlay.isOpaque(), "drop overlay is transparent so content shows through");
        assertFalse(overlay.contains(8, 8), "drop overlay passes pointer input through to content");

        // Painting invariant: a right-edge drop preview fills the right half and
        // leaves the left half untouched.
        centre.setBounds(0, 0, 400, 300);
        overlay.setBounds(0, 0, 400, 300);
        setField(area, "dragZone", staticField(areaType, "DROP_RIGHT"));
        BufferedImage image = paint(overlay, 400, 300);
        assertTrue(alphaSum(image, 240, 40, 120, 200) > 0,
                "drop overlay paints the preview in the targeted right half");
        assertEquals(Integer.valueOf(0), Integer.valueOf(alphaSum(image, 20, 40, 120, 200)),
                "drop overlay leaves the untargeted left half clear");
    }

    /**
     * Verifies each editor group exposes a tab-overflow button that appears
     * only when its tab strip cannot fit all open tabs, keeping overflowed tabs
     * reachable.
     */
    private static void testSplitAreaShowsOverflowButtonWhenTabsDoNotFit() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 9; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);

        JToggleButton[] overflow = (JToggleButton[]) field(area, "overflowButtons");
        assertTrue(overflow[0] != null, "primary editor group has an overflow button");
        assertEquals("workbench.editor.tabs.overflow", overflow[0].getName(),
                "overflow button carries a stable name");
        assertFalse(overflow[0].isVisible(), "overflow button is hidden before the strip is measured");

        JPanel strip = (JPanel) field(area, "primaryStrip");
        // A strip far narrower than its nine tabs must surface the overflow button.
        strip.setSize(40, Theme.CONTROL_HEIGHT);
        invoke(area, "updateOverflowButton", new Class<?>[] { int.class }, 0);
        assertTrue(overflow[0].isVisible(), "overflow button shows when tabs do not fit the strip");

        // A strip wide enough for every tab must hide it again.
        strip.setSize(4000, Theme.CONTROL_HEIGHT);
        invoke(area, "updateOverflowButton", new Class<?>[] { int.class }, 0);
        assertFalse(overflow[0].isVisible(), "overflow button hides when every tab fits");
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
        assertEquals("Split Tab Right", ((JMenuItem) menu.getComponent(0)).getText(),
                "tab menu starts with split actions");
        assertEquals("Close Other Tabs", ((JMenuItem) menu.getComponent(5)).getText(),
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
     * Verifies icon-only editor layout controls expose clear names, stable
     * command ids, and disabled no-op states.
     */
    private static void testEditorSplitActionsExposeClearStates() {
        Object single = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(single, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Only", new JPanel());
        invoke(single, "install", new Class<?>[0]);
        JToggleButton splitButton = (JToggleButton) field(single, "splitButton");
        assertFalse(splitButton.isEnabled(), "single non-duplicable tab cannot split");
        assertEquals("workbench.editor.split.right", splitButton.getActionCommand(),
                "split button exposes stable command id");
        assertTrue(splitButton.getToolTipText().contains("duplicate-capable"),
                "disabled split tooltip explains recovery");

        Object area = splitFixture();
        splitButton = (JToggleButton) field(area, "splitButton");
        assertTrue(splitButton.isEnabled(), "multi-tab editor can split selected tab");
        assertEquals("Split active tab to the right", splitButton.getToolTipText(),
                "split button names its actual action");

        JPanel primaryStrip = (JPanel) field(single, "primaryStrip");
        assertEquals(Integer.valueOf(1), Integer.valueOf(primaryStrip.getComponentCount()),
                "single closed/restorable fixture starts with one tab only");
        invoke(single, "closeSelectedTab", new Class<?>[0]);
        primaryStrip = (JPanel) field(single, "primaryStrip");
        JToggleButton reopen = (JToggleButton) primaryStrip.getComponent(0);
        assertEquals("workbench.editor.tabs.newOrRestore", reopen.getActionCommand(),
                "restore button exposes stable command id");
        assertTrue(reopen.getToolTipText().contains("restore a closed tab"),
                "restore button tooltip explains its action");
    }

    /**
     * Verifies every visible split group owns its local layout controls instead
     * of sharing a single Swing component that can only appear in one header.
     */
    private static void testSplitGroupsKeepLocalLayoutControls() {
        Class<?> areaType = type("layout.EditorSplitArea");
        Object area = construct(areaType, new Class<?>[0]);
        for (int i = 0; i < 5; i++) {
            invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                    "Tab " + i, new JPanel());
        }
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, 2, false);
        setField(area, "dragZone", staticField(areaType, "DROP_BOTTOM_RIGHT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 1);
        setField(area, "dragZone", staticField(areaType, "DROP_TOP_LEFT"));
        invoke(area, "finishTabDrag", new Class<?>[] { int.class }, 3);
        assertEquals(Integer.valueOf(4), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "fixture exposes all four editor groups");

        JPanel[] headers = (JPanel[]) field(area, "paneHeaders");
        JToggleButton[] splitButtons = (JToggleButton[]) field(area, "splitButtons");
        for (int pane = 0; pane < headers.length; pane++) {
            assertTrue(containsDescendant(headers[pane], splitButtons[pane]),
                    "split group " + pane + " owns its split button");
            assertEquals("workbench.editor.split.right", splitButtons[pane].getActionCommand(),
                    "split group " + pane + " split button has stable command id");
            assertTrue(splitButtons[pane].isEnabled(), "split group " + pane + " split button is usable");
        }

        invoke(area, "closeTab", new Class<?>[] { int.class }, 4);
        JPanel[] strips = {
            (JPanel) field(area, "primaryStrip"),
            (JPanel) field(area, "secondaryStrip"),
            (JPanel) field(area, "tertiaryStrip"),
            (JPanel) field(area, "quaternaryStrip")
        };
        for (int pane = 0; pane < strips.length; pane++) {
            assertTrue(containsButtonWithCommand(strips[pane], "workbench.editor.tabs.newOrRestore"),
                    "split group " + pane + " keeps its restore/new-tab control");
        }
    }

    /**
     * Returns whether a component subtree contains a target component.
     *
     * @param parent subtree root
     * @param target target component
     * @return true when the target is found
     */
    private static boolean containsDescendant(Container parent, Component target) {
        for (Component child : parent.getComponents()) {
            if (child == target) {
                return true;
            }
            if (child instanceof Container nested && containsDescendant(nested, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a component subtree contains a toggle button command.
     *
     * @param parent subtree root
     * @param command action command to find
     * @return true when a matching button is found
     */
    private static boolean containsButtonWithCommand(Container parent, String command) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JToggleButton button && command.equals(button.getActionCommand())) {
                return true;
            }
            if (child instanceof Container nested && containsButtonWithCommand(nested, command)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies factory-backed editor tabs can be opened repeatedly, producing
     * distinct Swing components that can be shown in separate editor groups.
     */
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
        assertEquals("New duplicate tab or restore a closed tab",
                ((JToggleButton) primaryStrip.getComponent(1)).getToolTipText(),
                "new-tab affordance explains both creation and restore");

        int firstCopy = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);
        int secondCopy = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);

        assertEquals(Integer.valueOf(3), invoke(area, "openTabCount", new Class<?>[0]),
                "duplicate analysis tabs remain open");
        assertEquals(Integer.valueOf(3), invoke(area, "count", new Class<?>[0]),
                "duplicate analysis tabs are registered");
        List<String> names = stringList(field(area, "names"));
        assertEquals("Analyze 2", names.get(firstCopy), "first duplicate gets a numbered label");
        assertEquals("Analyze 3", names.get(secondCopy), "second duplicate gets a numbered label");
        List<JComponent> panels = typedList(field(area, "panels"), JComponent.class);
        assertFalse(panels.get(0) == panels.get(firstCopy), "duplicate tab has a fresh component");

        invoke(area, "closeTab", new Class<?>[] { int.class }, firstCopy);
        int replacementCopy = (Integer) invoke(area, "duplicate", new Class<?>[] { int.class }, 0);
        names = stringList(field(area, "names"));
        assertEquals("Analyze 2", names.get(replacementCopy), "closed duplicate suffix is reused");

        invoke(area, "splitWithDragged", new Class<?>[] { int.class, boolean.class }, secondCopy, false);
        assertEquals(Integer.valueOf(2), invoke(area, "visibleGroupCount", new Class<?>[0]),
                "duplicate tab can split beside the original");
        assertTrue((Boolean) invoke(area, "isVisibleInPane", new Class<?>[] { int.class }, secondCopy),
                "duplicate tab is visible after splitting");

        Object restartArea = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(restartArea, "addPanel",
                new Class<?>[] { String.class, javax.swing.JComponent.class, java.util.function.Supplier.class },
                "Analyze", new JPanel(), supplier);
        invoke(restartArea, "install", new Class<?>[0]);
        invoke(restartArea, "closeTab", new Class<?>[] { int.class }, 0);
        int restarted = (Integer) invoke(restartArea, "duplicate", new Class<?>[] { int.class }, 0);
        List<String> restartNames = stringList(field(restartArea, "names"));
        assertEquals("Analyze", restartNames.get(restarted), "new Analyze tab after closing all tabs is unnumbered");
        assertEquals(Integer.valueOf(1), invoke(restartArea, "openTabCount", new Class<?>[0]),
                "restarted analysis workspace opens as a single tab");

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
        List<String> splitNames = stringList(field(splitArea, "names"));
        assertEquals("Analyze 2", splitNames.get(1), "split duplicate gets a numbered label");
        invoke(splitArea, "splitSelectedTabDown", new Class<?>[0]);
        assertEquals(Integer.valueOf(3), invoke(splitArea, "openTabCount", new Class<?>[0]),
                "split command can duplicate again from an existing editor group");
        assertEquals(Integer.valueOf(3), invoke(splitArea, "visibleGroupCount", new Class<?>[0]),
                "split command adds an adjacent group without collapsing existing groups");
        List<Integer> secondaryTabs = integerList(field(splitArea, "secondaryTabs"));
        List<Integer> quaternaryTabs = integerList(field(splitArea, "quaternaryTabs"));
        assertTrue(secondaryTabs.contains(1), "existing right group keeps its active duplicate");
        assertTrue(quaternaryTabs.contains(2), "down split from right group targets bottom-right");
    }

    /**
     * Verifies tool-style tabs such as Network, Datasets, and Publish can be
     * opened repeatedly from factory-backed registrations.
     */
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
        List<String> names = stringList(field(area, "names"));
        assertEquals("Network 2", names.get(networkSecond), "first Network duplicate is numbered");
        assertEquals("Network 3", names.get(networkThird), "second Network duplicate is numbered");
        assertEquals("Datasets 2", names.get(dataSecond), "Datasets duplicate is numbered");
        assertEquals("Publish 2", names.get(publishSecond), "Publish duplicate is numbered");
        List<JComponent> panels = typedList(field(area, "panels"), JComponent.class);
        assertFalse(panels.get(0) == panels.get(networkSecond), "Network duplicate has a distinct wrapper");
        assertEquals(0, networkCreated.get(), "duplicate tool tabs stay lazy until shown");
        invoke(panels.get(networkSecond), "materialize", new Class<?>[0]);
        assertEquals(1, networkCreated.get(), "materializing one Network duplicate creates one panel");
    }

    /**
     * Verifies duplicate Analyze workspaces keep their own move history and
     * keyboard/navigation controls instead of being one-way throwaway boards.
     */
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
    private static void testEditorShellShowsRookWatermarkWhenEmpty() {
        Object area = construct(type("layout.EditorSplitArea"), new Class<?>[0]);
        invoke(area, "addPanel", new Class<?>[] { String.class, javax.swing.JComponent.class },
                "Only", new JPanel());
        invoke(area, "install", new Class<?>[0]);
        invoke(area, "closeTab", new Class<?>[] { int.class }, 0);

        assertTrue(integerList(field(area, "open")).isEmpty(), "last tab can close");
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
        Object animation = field(board, "animationState");
        assertTrue((Integer) invoke(animation, "snapbackAnimationMs", new Class<?>[0]) <= 100,
                "snapback animation is short");
        assertTrue((Integer) invoke(animation, "snapAnimationMs", new Class<?>[0]) <= 70,
                "snap animation is short");
        assertTrue((Integer) invoke(animation, "flipAnimationMs", new Class<?>[0]) <= 160,
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
     * Verifies utility action labels resolve to compact control glyphs.
     */
    private static void testUtilityButtonsUseExpectedIcons() {
        assertButtonIconKind("Hide", "CLOSE");
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
