package application.gui.history.status;

import application.gui.history.ui.HistoryUiFactory;
import application.gui.history.window.HistoryTabDependencies;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.MatteBorder;

 /**
  * Builds the status bar used by the history window.
  *
  * @since 2026
  * @author Lennart A. Conrad
  */
public final class HistoryStatusBar {

    /**
     * HistoryStatusBar method.
     */
    private HistoryStatusBar() {
    }

    /**
     * Result class.
     *
     * Provides class behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public static final class Result {
        /**
         * component field.
         */
        public final JPanel component;
        /**
         * statusLeft field.
         */
        public final JLabel statusLeft;
        /**
         * statusMiddle field.
         */
        public final JLabel statusMiddle;
        /**
         * statusRight field.
         */
        public final JLabel statusRight;
        /**
         * statusEngine field.
         */
        public final JLabel statusEngine;
        /**
         * statusProblem field.
         */
        public final JLabel statusProblem;
        /**
         * statusPanel field.
         */
        public final JLabel statusPanel;
        /**
         * statusMode field.
         */
        public final JLabel statusMode;
        /**
         * statusEol field.
         */
        public final JLabel statusEol;
        /**
         * statusEncoding field.
         */
        public final JLabel statusEncoding;
        /**
         * statusBranch field.
         */
        public final JLabel statusBranch;
        /**
         * statusTheme field.
         */
        public final JLabel statusTheme;
        /**
         * statusTabPicker field.
         */
        public final JComboBox<String> statusTabPicker;

        /**
         * Result method.
         *
         * @param component parameter.
         * @param statusLeft parameter.
         * @param statusMiddle parameter.
         * @param statusRight parameter.
         * @param statusEngine parameter.
         * @param statusProblem parameter.
         * @param statusPanel parameter.
         * @param statusMode parameter.
         * @param statusEol parameter.
         * @param statusEncoding parameter.
         * @param statusBranch parameter.
         * @param statusTheme parameter.
         * @param statusTabPicker parameter.
         */
        private Result(JPanel component, JLabel statusLeft, JLabel statusMiddle, JLabel statusRight,
                       JLabel statusEngine, JLabel statusProblem, JLabel statusPanel, JLabel statusMode,
                       JLabel statusEol, JLabel statusEncoding, JLabel statusBranch, JLabel statusTheme,
                       JComboBox<String> statusTabPicker) {
            this.component = component;
            this.statusLeft = statusLeft;
            this.statusMiddle = statusMiddle;
            this.statusRight = statusRight;
            this.statusEngine = statusEngine;
            this.statusProblem = statusProblem;
            this.statusPanel = statusPanel;
            this.statusMode = statusMode;
            this.statusEol = statusEol;
            this.statusEncoding = statusEncoding;
            this.statusBranch = statusBranch;
            this.statusTheme = statusTheme;
            this.statusTabPicker = statusTabPicker;
        }
    }

    /**
     * build method.
     *
     * @param deps parameter.
     * @param uiFactory parameter.
     * @return return value.
     */
    public static Result build(HistoryTabDependencies deps, HistoryUiFactory uiFactory) {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(true);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, deps.theme().border()));
        JLabel statusLeftLabel = uiFactory.mutedLabel("Ready");
        statusLeftLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel statusMiddleLabel = uiFactory.mutedLabel("—");
        statusMiddleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel statusRightLabel = uiFactory.mutedLabel("");
        statusRightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel statusEngineLabel = uiFactory.mutedLabel("Engine: Off");
        statusEngineLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusEngineLabel.setToolTipText("Toggle engine (Ctrl+S)");
        statusEngineLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                deps.toggleEnginePower();
            }
        });
        JLabel statusThemeLabel = uiFactory.mutedLabel(deps.isLightMode() ? "Theme: Light" : "Theme: Dark");
        statusThemeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusThemeLabel.setToolTipText("Toggle theme");
        statusThemeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                deps.toggleTheme();
            }
        });
        JLabel statusProblemLabel = uiFactory.mutedLabel("Problems: 0");
        statusProblemLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusProblemLabel.setToolTipText("Open Problems (Ctrl+Shift+M)");
        statusProblemLabel.setVisible(false);
        statusProblemLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                deps.setPanelVisible(true, true);
                deps.openPanelTab("Problems");
            }
        });
        JLabel statusPanelLabel = uiFactory.mutedLabel(deps.panelVisible() ? "Panel: On" : "Panel: Off");
        statusPanelLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusPanelLabel.setToolTipText("Toggle panel (Ctrl+J)");
        statusPanelLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                deps.togglePanel();
            }
        });
        JLabel statusModeLabel = uiFactory.mutedLabel("Mode: Chess");
        statusModeLabel.setToolTipText("Current mode");
        JLabel statusEolLabel = uiFactory.mutedLabel("LF");
        statusEolLabel.setToolTipText("Line endings");
        JLabel statusEncodingLabel = uiFactory.mutedLabel("UTF-8");
        statusEncodingLabel.setToolTipText("Encoding");
        JLabel statusBranchLabel = uiFactory.mutedLabel("Branch: —");
        statusBranchLabel.setToolTipText("Workspace branch");

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(statusLeftLabel, BorderLayout.WEST);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(statusMiddleLabel, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        JComboBox<String> statusTabPicker = new JComboBox<>();
        statusTabPicker.setFocusable(false);
        statusTabPicker.addActionListener(e -> {
            if (deps.statusTabUpdating()) {
                return;
            }
            int index = statusTabPicker.getSelectedIndex();
            if (index >= 0) {
                deps.setStatusTabUpdating(true);
                try {
                    deps.selectRightTab(index);
                } finally {
                    deps.setStatusTabUpdating(false);
                }
            }
        });
        deps.registerCombo(statusTabPicker);
        Dimension pickerSize = deps.scaleDimension(new Dimension(150, 24));
        statusTabPicker.setPreferredSize(pickerSize);
        statusTabPicker.setMaximumSize(pickerSize);
        right.add(statusTabPicker);
        right.add(Box.createHorizontalStrut(12));
        right.add(statusEngineLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusRightLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusProblemLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusPanelLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusModeLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusEolLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusEncodingLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusBranchLabel);
        right.add(Box.createHorizontalStrut(10));
        right.add(statusThemeLabel);

        bar.add(left, BorderLayout.WEST);
        bar.add(middle, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        bar.setPreferredSize(deps.scaleDimension(new Dimension(10, 28)));

        return new Result(bar, statusLeftLabel, statusMiddleLabel, statusRightLabel, statusEngineLabel,
                statusProblemLabel, statusPanelLabel, statusModeLabel, statusEolLabel, statusEncodingLabel,
                statusBranchLabel, statusThemeLabel, statusTabPicker);
    }
}
