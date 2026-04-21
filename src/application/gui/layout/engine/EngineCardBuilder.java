package application.gui.layout.engine;

import application.gui.window.GuiWindowHistory;
import application.gui.history.ui.HistoryUiFactory;
import application.gui.model.PvEntry;
import application.gui.ui.RoundedPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Responsible for assembling the engine card that hosts engine controls, settings, and PV outputs.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class EngineCardBuilder {

    /**
     * EngineCardBuilder method.
     */
    private EngineCardBuilder() {
        // utility class
    }

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param actions parameter.
     * @return return value.
     */
    public static Result build(Context ctx, Actions actions) {
        RoundedPanel card = ctx.uiFactory().createFlatCard("Engine", false);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel engineStatusLabel = ctx.mutedLabel("Idle");
        engineStatusLabel.setOpaque(true);
        engineStatusLabel.setBorder(new javax.swing.border.EmptyBorder(4, 8, 4, 8));
        engineStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        body.add(engineStatusLabel);
        body.add(Box.createVerticalStrut(6));

        JPanel statsRow1 = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
        statsRow1.setOpaque(false);
        JLabel engineDepthValue = new JLabel("—");
        ctx.registerStrongLabel(engineDepthValue);
        JLabel engineNodesValue = new JLabel("—");
        ctx.registerStrongLabel(engineNodesValue);
        statsRow1.add(ctx.labeledValue("Depth", engineDepthValue));
        statsRow1.add(ctx.labeledValue("Nodes", engineNodesValue));

        JPanel statsRow2 = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
        statsRow2.setOpaque(false);
        JLabel engineTimeValue = new JLabel("—");
        ctx.registerStrongLabel(engineTimeValue);
        JLabel engineNpsValue = new JLabel("—");
        ctx.registerStrongLabel(engineNpsValue);
        statsRow2.add(ctx.labeledValue("Time", engineTimeValue));
        statsRow2.add(ctx.labeledValue("NPS", engineNpsValue));

        body.add(statsRow1);
        body.add(Box.createVerticalStrut(6));
        body.add(statsRow2);
        body.add(Box.createVerticalStrut(8));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.setOpaque(false);
        JButton engineStartButton = ctx.uiFactory().themedButton("Start", e -> actions.startEngine());
        JButton engineStopButton = ctx.uiFactory().themedButton("Stop", e -> actions.stopEngine());
        JButton engineBestButton = ctx.uiFactory().themedButton("Play Best", e -> actions.playEngineBest());
        engineStartButton.setToolTipText("Start/stop engine (Ctrl+S)");
        engineStopButton.setToolTipText("Start/stop engine (Ctrl+S)");
        engineBestButton.setToolTipText("Play best move (Space)");
        engineStopButton.setEnabled(false);
        engineBestButton.setEnabled(false);
        actionRow.add(engineStartButton);
        actionRow.add(engineStopButton);
        actionRow.add(engineBestButton);
        body.add(actionRow);
        body.add(Box.createVerticalStrut(8));

        JPanel versusRow = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
        versusRow.setOpaque(false);
        JButton engineVsNewButton = ctx.uiFactory().themedButton("Play Vs Engine", e -> actions.startVsEngineNewGame());
        JButton engineVsContinueButton = ctx.uiFactory().themedButton("Continue Here", e -> actions.continueVsEngineFromCurrent());
        JButton engineVsStopButton = ctx.uiFactory().themedButton("Stop Vs", e -> actions.stopVsEngineGame());
        versusRow.add(engineVsNewButton);
        versusRow.add(engineVsContinueButton);
        versusRow.add(engineVsStopButton);
        body.add(versusRow);
        body.add(Box.createVerticalStrut(6));

        JCheckBox vsEnginePlayWhiteToggle = ctx.themedCheckbox("Play as White", ctx.vsEngineHumanWhite(), null);
        vsEnginePlayWhiteToggle.addActionListener(e -> actions.onVsEngineSideChange(vsEnginePlayWhiteToggle.isSelected()));
        JPanel versusSideRow = ctx.settingsRow("Vs Engine Side", vsEnginePlayWhiteToggle);
        versusSideRow.putClientProperty("settingLabel", "vs engine side");
        body.add(versusSideRow);
        body.add(Box.createVerticalStrut(8));

        JTextField engineSearchField = new JTextField();
        ctx.registerTextField(engineSearchField);
        JPanel searchRow = ctx.labeledField("Search settings", engineSearchField);
        body.add(searchRow);
        body.add(Box.createVerticalStrut(6));

        JPanel settingsList = new JPanel();
        settingsList.setOpaque(false);
        settingsList.setLayout(new BoxLayout(settingsList, BoxLayout.Y_AXIS));
        List<JComponent> settingRows = new ArrayList<>();

        JPanel protoControl = new JPanel(new BorderLayout(6, 0));
        protoControl.setOpaque(false);
        JTextField engineProtocolField = new JTextField(ctx.defaultProtocolPath());
        ctx.registerTextField(engineProtocolField);
        protoControl.add(engineProtocolField, BorderLayout.CENTER);
        protoControl.add(ctx.uiFactory().themedButton("Browse", e -> actions.chooseProtocolFile()), BorderLayout.EAST);
        JPanel protoRow = ctx.settingsRow("Protocol", protoControl);
        protoRow.putClientProperty("settingLabel", "protocol");
        settingsList.add(protoRow);
        settingRows.add(protoRow);

        JTextField engineNodesField = new JTextField(String.valueOf(ctx.defaultEngineNodes()));
        JTextField engineTimeField = new JTextField(String.valueOf(ctx.defaultEngineDuration()));
        ctx.registerTextField(engineNodesField);
        ctx.registerTextField(engineTimeField);

        JComboBox<Integer> engineMultiPvBox = new JComboBox<>(new Integer[] { 1, 2, 3 });
        engineMultiPvBox.setSelectedItem(1);
        ctx.registerComboBox(engineMultiPvBox);

        JComboBox<String> pvWrapModeBox = new JComboBox<>(new String[] {
                "Auto Word Wrap",
                "Auto Char Wrap",
                "Token Wrap (Pixel)",
                "Fixed Tokens/Line",
                "Fixed Chars/Line",
                "Off (Single Line)"
        });
        pvWrapModeBox.setSelectedIndex(0);
        pvWrapModeBox.addActionListener(e -> actions.refreshPvListCellHeight(null));
        ctx.registerComboBox(pvWrapModeBox);

        JPanel nodesRow = ctx.settingsRow("Nodes", engineNodesField);
        nodesRow.putClientProperty("settingLabel", "nodes");
        settingsList.add(nodesRow);
        settingRows.add(nodesRow);

        JPanel timeRow = ctx.settingsRow("Time ms", engineTimeField);
        timeRow.putClientProperty("settingLabel", "time");
        settingsList.add(timeRow);
        settingRows.add(timeRow);

        JPanel pvRow = ctx.settingsRow("MultiPV", engineMultiPvBox);
        pvRow.putClientProperty("settingLabel", "multipv");
        settingsList.add(pvRow);
        settingRows.add(pvRow);

        JPanel pvWrapRow = ctx.settingsRow("PV wrap", pvWrapModeBox);
        pvWrapRow.putClientProperty("settingLabel", "pv wrap");
        pvWrapRow.putClientProperty("settingPinned", Boolean.TRUE);
        settingsList.add(pvWrapRow);
        settingRows.add(pvWrapRow);

        JCheckBox engineEndlessToggle = ctx.themedCheckbox("Endless", false, null);
        JPanel endlessRow = ctx.settingsRow("Endless", engineEndlessToggle);
        endlessRow.putClientProperty("settingLabel", "endless");
        settingsList.add(endlessRow);
        settingRows.add(endlessRow);

        JButton engineManageButton = ctx.uiFactory().themedButton("Manage Engine", e -> actions.openEngineManager());
        JPanel manageRow = ctx.settingsRow("Manage", engineManageButton);
        manageRow.putClientProperty("settingLabel", "manage engine");
        settingsList.add(manageRow);
        settingRows.add(manageRow);

        body.add(settingsList);
        body.add(Box.createVerticalStrut(8));

        engineSearchField.getDocument().addDocumentListener(new DocumentListener() {
                        /**
             * Handles insert update.
             * @param e e value
             */
@Override
            public void insertUpdate(DocumentEvent e) {
                filterSettings(); }
                        /**
             * Handles remove update.
             * @param e e value
             */
@Override
            public void removeUpdate(DocumentEvent e) {
                filterSettings(); }
                        /**
             * Handles changed update.
             * @param e e value
             */
@Override
            public void changedUpdate(DocumentEvent e) {
                filterSettings(); }

                        /**
             * Handles filter settings.
             */
private void filterSettings() {
                String query = engineSearchField.getText();
                String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
                for (JComponent row : settingRows) {
                    boolean pinned = Boolean.TRUE.equals(row.getClientProperty("settingPinned"));
                    Object key = row.getClientProperty("settingLabel");
                    String label = key instanceof String ? (String) key : "";
                    boolean show = pinned || needle.isEmpty() || label.contains(needle);
                    row.setVisible(show);
                }
                settingsList.revalidate();
                settingsList.repaint();
            }
        });

        JPanel bestHeader = new JPanel(new BorderLayout(8, 8));
        bestHeader.setOpaque(false);
        JLabel bestLabel = ctx.mutedLabel("Best moves");
        bestHeader.add(bestLabel, BorderLayout.WEST);
        JPanel pvControls = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
        pvControls.setOpaque(false);
        JButton engineCopyPvButton = ctx.uiFactory().themedButton("Copy PV", e -> actions.copyEnginePv());
        engineCopyPvButton.setEnabled(false);
        JCheckBox engineLockPvToggle = ctx.themedCheckbox("Lock PV", false, e -> actions.toggleEngineLockPv());
        pvControls.add(engineCopyPvButton);
        pvControls.add(engineLockPvToggle);
        bestHeader.add(pvControls, BorderLayout.EAST);
        body.add(bestHeader);

        JTextArea engineBestArea = new JTextArea(4, 26);
        engineBestArea.setEditable(false);
        engineBestArea.setLineWrap(true);
        engineBestArea.setWrapStyleWord(true);
        ctx.registerTextArea(engineBestArea);

        DefaultListModel<PvEntry> pvListModel = new DefaultListModel<>();
        JList<PvEntry> pvList = new JList<>(pvListModel) {
                        /**
             * Returns the scrollable tracks viewport width.
             * @return computed value
             */
@Override
            public boolean getScrollableTracksViewportWidth() {
                return true; }
                        /**
             * Returns the preferred size.
             * @return computed value
             */
@Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() instanceof javax.swing.JViewport viewport && viewport.getWidth() > 0) {
                    d.width = viewport.getWidth(); }
                return d; }
        };
        pvList.setFixedCellHeight(-1);
        pvList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        pvList.setCellRenderer(new application.gui.render.PvCellRenderer(ctx.owner()));
        pvList.addComponentListener(new java.awt.event.ComponentAdapter() {
                        /**
             * Handles component resized.
             * @param e e value
             */
@Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                actions.refreshPvListCellHeight(null);
                pvList.revalidate();
                pvList.repaint(); }
        });
        pvList.addMouseMotionListener(new MouseAdapter() {
                        /**
             * Handles mouse moved.
             * @param e e value
             */
@Override
            public void mouseMoved(MouseEvent e) {
                int idx = pvList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    Rectangle bounds = pvList.getCellBounds(idx, idx);
                    if (bounds != null && bounds.contains(e.getPoint())) {
                        PvEntry entry = pvListModel.getElementAt(idx);
                        if (entry != null) {
                            int hoverPly = actions.pvPlyAtPoint(entry, e.getPoint(), bounds);
                            int totalPlies = actions.pvTotalPlies(entry);
                            int previewPly = Math.max(1, Math.min(totalPlies, hoverPly));
                            Object prevIdxObj = pvList.getClientProperty("pvHoverMoveIndex");
                            Object prevPlyObj = pvList.getClientProperty("pvHoverPly");
                            int prevIdx = prevIdxObj instanceof Integer ? (Integer) prevIdxObj : -1;
                            int prevPly = prevPlyObj instanceof Integer ? (Integer) prevPlyObj : -1;
                            boolean changed = prevIdx != idx || prevPly != hoverPly;
                            if (changed) {
                                pvList.putClientProperty("pvHoverMoveIndex", idx);
                                pvList.putClientProperty("pvHoverPly", hoverPly);
                                actions.startPvHoverAnimation();
                                actions.previewEnginePv(entry.pv(), previewPly);
                                actions.showEnginePvMiniBoardPreview(entry.pv(), previewPly, e.getLocationOnScreen());
                            }
                            return; } } }
                actions.stopPvHoverAnimation();
                actions.hideEnginePvMiniBoardPreview(); }
        });
        pvList.addMouseListener(new MouseAdapter() {
                        /**
             * Handles mouse exited.
             * @param e e value
             */
@Override
            public void mouseExited(MouseEvent e) {
                actions.stopPvHoverAnimation();
                actions.hideEnginePvMiniBoardPreview(); }
                        /**
             * Handles mouse clicked.
             * @param e e value
             */
@Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int idx = pvList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Rectangle bounds = pvList.getCellBounds(idx, idx);
                        if (bounds != null && bounds.contains(e.getPoint())) {
                            PvEntry entry = pvListModel.getElementAt(idx);
                            if (actions.isPvExpandToggleHit(pvList, entry, bounds, e.getPoint())) {
                                actions.togglePvExpanded(entry);
                                actions.refreshPvListCellHeight(null);
                                pvList.revalidate();
                                pvList.repaint();
                                return; } } } }
                if (e.getClickCount() == 2) {
                    int idx = pvList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        PvEntry entry = pvListModel.getElementAt(idx);
                        if (entry != null) {
                            Rectangle bounds = pvList.getCellBounds(idx, idx);
                            int ply = actions.pvPlyAtPoint(entry, e.getPoint(), bounds);
                            actions.playEnginePv(entry.pv(), ply);
                        }
                    }
                }
                actions.hideEnginePvMiniBoardPreview(); }
        });
        ctx.registerList(pvList);

        JScrollPane pvListScroll = new JScrollPane(pvList);
        pvListScroll.setBorder(BorderFactory.createEmptyBorder());
        Dimension pvPaneSize = ctx.scaleDimension(new Dimension(320, 170));
        pvListScroll.setPreferredSize(pvPaneSize);
        pvListScroll.setMinimumSize(ctx.scaleDimension(new Dimension(280, 140)));
        pvListScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.round(420 * ctx.uiScale())));
        ctx.registerScrollPane(pvListScroll);
        body.add(pvListScroll);
        body.add(Box.createVerticalStrut(8));

        JTextArea engineOutputArea = new JTextArea(8, 26);
        engineOutputArea.setEditable(false);
        engineOutputArea.setLineWrap(true);
        engineOutputArea.setWrapStyleWord(true);
        ctx.registerTextArea(engineOutputArea);
        JScrollPane outputScroll = new JScrollPane(engineOutputArea);
        outputScroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(outputScroll);
        body.add(outputScroll);

        card.setContent(body);
        return new Result(card,
                engineStatusLabel,
                engineDepthValue,
                engineNodesValue,
                engineTimeValue,
                engineNpsValue,
                engineStartButton,
                engineStopButton,
                engineBestButton,
                engineVsNewButton,
                engineVsContinueButton,
                engineVsStopButton,
                vsEnginePlayWhiteToggle,
                engineSearchField,
                engineProtocolField,
                engineNodesField,
                engineTimeField,
                engineMultiPvBox,
                pvWrapModeBox,
                engineEndlessToggle,
                engineManageButton,
                engineCopyPvButton,
                engineLockPvToggle,
                engineBestArea,
                pvListModel,
                pvList,
                pvListScroll,
                engineOutputArea);
    }

    /**
     * Context interface.
     *
     * Provides interface behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public interface Context {
        /**
         * uiFactory method.
         *
         * @return return value.
         */
        HistoryUiFactory uiFactory();
        /**
         * uiScale method.
         *
         * @return return value.
         */
        float uiScale();
        /**
         * scaleDimension method.
         *
         * @param base parameter.
         * @return return value.
         */
        Dimension scaleDimension(Dimension base);
        /**
         * owner method.
         *
         * @return return value.
         */
        GuiWindowHistory owner();
        /**
         * mutedLabel method.
         *
         * @param text parameter.
         * @return return value.
         */
        JLabel mutedLabel(String text);
        /**
         * labeledValue method.
         *
         * @param label parameter.
         * @param value parameter.
         * @return return value.
         */
        JPanel labeledValue(String label, JLabel value);
        /**
         * labeledField method.
         *
         * @param label parameter.
         * @param field parameter.
         * @return return value.
         */
        JPanel labeledField(String label, JTextField field);
        /**
         * settingsRow method.
         *
         * @param label parameter.
         * @param control parameter.
         * @return return value.
         */
        JPanel settingsRow(String label, JComponent control);
        /**
         * themedCheckbox method.
         *
         * @param text parameter.
         * @param selected parameter.
         * @param action parameter.
         * @return return value.
         */
        JCheckBox themedCheckbox(String text, boolean selected, java.awt.event.ActionListener action);
        /**
         * registerStrongLabel method.
         *
         * @param label parameter.
         */
        void registerStrongLabel(JLabel label);
        /**
         * registerTextField method.
         *
         * @param field parameter.
         */
        void registerTextField(JTextField field);
        /**
         * registerTextArea method.
         *
         * @param area parameter.
         */
        void registerTextArea(JTextArea area);
        /**
         * registerList method.
         *
         * @param list parameter.
         */
        void registerList(JList<?> list);
        /**
         * registerScrollPane method.
         *
         * @param scroll parameter.
         */
        void registerScrollPane(JScrollPane scroll);
        /**
         * registerComboBox method.
         *
         * @param combo parameter.
         */
        void registerComboBox(JComboBox<?> combo);
        /**
         * defaultProtocolPath method.
         *
         * @return return value.
         */
        String defaultProtocolPath();
        /**
         * defaultEngineNodes method.
         *
         * @return return value.
         */
        long defaultEngineNodes();
        /**
         * defaultEngineDuration method.
         *
         * @return return value.
         */
        long defaultEngineDuration();
        /**
         * vsEngineHumanWhite method.
         *
         * @return return value.
         */
        boolean vsEngineHumanWhite();
    }

    /**
     * Actions interface.
     *
     * Provides interface behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public interface Actions {
        /**
         * startEngine method.
         */
        void startEngine();
        /**
         * stopEngine method.
         */
        void stopEngine();
        /**
         * playEngineBest method.
         */
        void playEngineBest();
        /**
         * startVsEngineNewGame method.
         */
        void startVsEngineNewGame();
        /**
         * continueVsEngineFromCurrent method.
         */
        void continueVsEngineFromCurrent();
        /**
         * stopVsEngineGame method.
         */
        void stopVsEngineGame();
        /**
         * refreshBoard method.
         */
        void refreshBoard();
        /**
         * requestVsEngineReplyIfNeeded method.
         */
        void requestVsEngineReplyIfNeeded();
        /**
         * refreshStatusBar method.
         */
        void refreshStatusBar();
        /**
         * chooseProtocolFile method.
         */
        void chooseProtocolFile();
        /**
         * copyEnginePv method.
         */
        void copyEnginePv();
        /**
         * toggleEngineLockPv method.
         */
        void toggleEngineLockPv();
        /**
         * openEngineManager method.
         */
        void openEngineManager();
        /**
         * refreshPvListCellHeight method.
         *
         * @param entries parameter.
         */
        void refreshPvListCellHeight(java.util.List<PvEntry> entries);
        /**
         * startPvHoverAnimation method.
         */
        void startPvHoverAnimation();
        /**
         * stopPvHoverAnimation method.
         */
        void stopPvHoverAnimation();
        /**
         * previewEnginePv method.
         *
         * @param pv parameter.
         * @param ply parameter.
         */
        void previewEnginePv(int pv, int ply);
        /**
         * showEnginePvMiniBoardPreview method.
         *
         * @param pv parameter.
         * @param ply parameter.
         * @param location parameter.
         */
        void showEnginePvMiniBoardPreview(int pv, int ply, Point location);
        /**
         * hideEnginePvMiniBoardPreview method.
         */
        void hideEnginePvMiniBoardPreview();
        /**
         * playEnginePv method.
         *
         * @param pv parameter.
         * @param ply parameter.
         */
        void playEnginePv(int pv, int ply);
        /**
         * togglePvExpanded method.
         *
         * @param entry parameter.
         */
        void togglePvExpanded(PvEntry entry);
        /**
         * isPvExpandToggleHit method.
         *
         * @param list parameter.
         * @param entry parameter.
         * @param bounds parameter.
         * @param point parameter.
         * @return return value.
         */
        boolean isPvExpandToggleHit(JList<? extends PvEntry> list, PvEntry entry, Rectangle bounds, Point point);
        /**
         * pvPlyAtPoint method.
         *
         * @param entry parameter.
         * @param listPoint parameter.
         * @param cellBounds parameter.
         * @return return value.
         */
        int pvPlyAtPoint(PvEntry entry, Point listPoint, Rectangle cellBounds);
        /**
         * pvTotalPlies method.
         *
         * @param entry parameter.
         * @return return value.
         */
        int pvTotalPlies(PvEntry entry);
        /**
         * onVsEngineSideChange method.
         *
         * @param playWhite parameter.
         */
        void onVsEngineSideChange(boolean playWhite);
    }

    /**
     * Result record.
     *
     * Provides record behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public record Result(
        /**
         * Stores the panel.
         */
        RoundedPanel panel,
        /**
         * Stores the engine status label.
         */
        JLabel engineStatusLabel,
        /**
         * Stores the engine depth value.
         */
        JLabel engineDepthValue,
        /**
         * Stores the engine nodes value.
         */
        JLabel engineNodesValue,
        /**
         * Stores the engine time value.
         */
        JLabel engineTimeValue,
        /**
         * Stores the engine nps value.
         */
        JLabel engineNpsValue,
        /**
         * Stores the engine start button.
         */
        JButton engineStartButton,
        /**
         * Stores the engine stop button.
         */
        JButton engineStopButton,
        /**
         * Stores the engine best button.
         */
        JButton engineBestButton,
        /**
         * Stores the engine vs new button.
         */
        JButton engineVsNewButton,
        /**
         * Stores the engine vs continue button.
         */
        JButton engineVsContinueButton,
        /**
         * Stores the engine vs stop button.
         */
        JButton engineVsStopButton,
        /**
         * Stores the vs engine play white toggle.
         */
        JCheckBox vsEnginePlayWhiteToggle,
        /**
         * Stores the engine search field.
         */
        JTextField engineSearchField,
        /**
         * Stores the engine protocol field.
         */
        JTextField engineProtocolField,
        /**
         * Stores the engine nodes field.
         */
        JTextField engineNodesField,
        /**
         * Stores the engine time field.
         */
        JTextField engineTimeField,
        /**
         * Stores the engine multi pv box.
         */
        JComboBox<Integer> engineMultiPvBox,
        /**
         * Stores the pv wrap mode box.
         */
        JComboBox<String> pvWrapModeBox,
        /**
         * Stores the engine endless toggle.
         */
        JCheckBox engineEndlessToggle,
        /**
         * Stores the engine manage button.
         */
        JButton engineManageButton,
        /**
         * Stores the engine copy pv button.
         */
        JButton engineCopyPvButton,
        /**
         * Stores the engine lock pv toggle.
         */
        JCheckBox engineLockPvToggle,
        /**
         * Stores the engine best area.
         */
        JTextArea engineBestArea,
        /**
         * Stores the pv list model.
         */
        DefaultListModel<PvEntry> pvListModel,
        /**
         * Stores the pv list.
         */
        JList<PvEntry> pvList,
        /**
         * Stores the pv list scroll.
         */
        JScrollPane pvListScroll,
        /**
         * Stores the engine output area.
         */
        JTextArea engineOutputArea
    ) {}
}
