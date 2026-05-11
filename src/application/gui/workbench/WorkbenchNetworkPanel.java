package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Workbench network-visualizer host panel.
 *
 * <p>Owns the architecture switcher (NNUE / lc0-CNN / lc0-BT4) and the
 * abstract-vs-detailed view-mode toggle. Runs real network inference on a
 * SwingWorker with a short debounce so rapid FEN changes coalesce into one
 * forward pass per architecture. Falls back to synthetic activations when a
 * model file is missing or fails to load.</p>
 */
final class WorkbenchNetworkPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Architecture identifier for NNUE.
     */
    private static final String ARCH_NNUE = "NNUE";

    /**
     * Architecture identifier for LC0 CNN.
     */
    private static final String ARCH_CNN = "LC0 CNN";

    /**
     * Architecture identifier for LC0 BT4.
     */
    private static final String ARCH_BT4 = "LC0 BT4";

    /**
     * Debounce delay before kicking off inference on a new FEN.
     */
    private static final int DEBOUNCE_MS = 220;

    /**
     * NNUE view.
     */
    private final WorkbenchNnueView nnueView = new WorkbenchNnueView();

    /**
     * CNN view.
     */
    private final WorkbenchCnnView cnnView = new WorkbenchCnnView();

    /**
     * BT4 view.
     */
    private final WorkbenchBt4View bt4View = new WorkbenchBt4View();

    /**
     * NNUE snapshot.
     */
    private final WorkbenchActivationSnapshot nnueSnap = new WorkbenchActivationSnapshot();

    /**
     * CNN snapshot.
     */
    private final WorkbenchActivationSnapshot cnnSnap = new WorkbenchActivationSnapshot();

    /**
     * BT4 snapshot.
     */
    private final WorkbenchActivationSnapshot bt4Snap = new WorkbenchActivationSnapshot();

    /**
     * Architecture switcher.
     */
    private final JComboBox<String> archCombo = new JComboBox<>(new String[] { ARCH_NNUE, ARCH_CNN, ARCH_BT4 });

    /**
     * Position picker. "Use main board" forwards live FENs; any other selection
     * pins the view to a canned position so the user can explore.
     */
    private final JComboBox<String> positionCombo = new JComboBox<>(WorkbenchPositions.labels());

    /**
     * View-mode toggle.
     */
    private final WorkbenchToggleBox detailedToggle = new WorkbenchToggleBox("Detailed view");

    /**
     * Status badge in the toolbar.
     */
    private final JLabel statusBadge = new JLabel("loading models...");

    /**
     * Card layout container that swaps views.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Card-hosting panel.
     */
    private final JPanel cardPanel = new JPanel(cards);

    /**
     * Real activation provider. Loads networks lazily.
     */
    private final WorkbenchRealActivations provider = new WorkbenchRealActivations();

    /**
     * Most recent FEN (canned override if any, otherwise the main-board FEN).
     */
    private String currentFen = "";

    /**
     * Most recent main-board FEN. Saved separately so the picker can switch
     * back to live updates without losing the live value.
     */
    private String mainBoardFen = "";

    /**
     * Canned-position FEN; null when the picker is set to "use main board".
     */
    private String overrideFen;

    /**
     * Debounce timer; fires inference after the user stops navigating.
     */
    private final Timer debounceTimer;

    /**
     * Worker for the currently in-flight inference, or null when idle.
     */
    private SwingWorker<Void, Void> inferenceWorker;

    /**
     * Pending FEN that the next debounce tick should infer.
     */
    private String pendingFen;

    /**
     * Creates the network panel.
     */
    WorkbenchNetworkPanel() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
        setBorder(WorkbenchTheme.pad(8, 8, 8, 8));
        add(buildToolbar(), BorderLayout.NORTH);
        cardPanel.setOpaque(true);
        cardPanel.setBackground(WorkbenchTheme.BG);
        cardPanel.add(nnueView, ARCH_NNUE);
        cardPanel.add(cnnView, ARCH_CNN);
        cardPanel.add(bt4View, ARCH_BT4);
        add(cardPanel, BorderLayout.CENTER);
        archCombo.addActionListener(event -> showSelected());
        positionCombo.addActionListener(event -> onPositionPicked());
        detailedToggle.addActionListener(event -> propagateDetailed());
        debounceTimer = new Timer(DEBOUNCE_MS, event -> startInference());
        debounceTimer.setRepeats(false);
        showSelected();
    }

    /**
     * Sets the current FEN. Inference is scheduled on the debounce timer.
     * When the position picker is on a canned override, the new value is
     * stored as the latest main-board FEN but does not trigger inference.
     *
     * @param fen current position FEN
     */
    void setFen(String fen) {
        String value = fen == null ? "" : fen;
        mainBoardFen = value;
        if (overrideFen != null) {
            return;
        }
        scheduleInference(value);
    }

    /**
     * Schedules a debounced inference run on the given FEN.
     *
     * @param fen FEN to infer
     */
    private void scheduleInference(String fen) {
        if (fen.equals(currentFen)) {
            return;
        }
        currentFen = fen;
        pendingFen = fen;
        debounceTimer.restart();
    }

    /**
     * Reacts to a position picker change.
     */
    private void onPositionPicked() {
        String label = (String) positionCombo.getSelectedItem();
        String fen = WorkbenchPositions.fenFor(label);
        overrideFen = fen;
        scheduleInference(fen == null ? mainBoardFen : fen);
    }

    /**
     * Builds the toolbar row (architecture switcher + view toggle + badge).
     *
     * @return toolbar
     */
    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBackground(WorkbenchTheme.BG);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel archLabel = new JLabel("Architecture");
        archLabel.setForeground(WorkbenchTheme.MUTED);
        archLabel.setFont(WorkbenchTheme.font(12, Font.BOLD));
        WorkbenchUi.styleCombo(archCombo);
        archCombo.setPreferredSize(new Dimension(130, 28));
        JLabel positionLabel = new JLabel("Position");
        positionLabel.setForeground(WorkbenchTheme.MUTED);
        positionLabel.setFont(WorkbenchTheme.font(12, Font.BOLD));
        WorkbenchUi.styleCombo(positionCombo);
        positionCombo.setPreferredSize(new Dimension(190, 28));
        positionCombo.setToolTipText("Pin a canned position to explore, or follow the main board.");
        left.add(archLabel);
        left.add(archCombo);
        left.add(positionLabel);
        left.add(positionCombo);
        left.add(detailedToggle);
        bar.add(left, BorderLayout.WEST);
        statusBadge.setForeground(WorkbenchTheme.MUTED);
        statusBadge.setFont(WorkbenchTheme.font(11, Font.ITALIC));
        bar.add(statusBadge, BorderLayout.EAST);
        return bar;
    }

    /**
     * Shows the architecture selected by the combo box.
     */
    private void showSelected() {
        String key = (String) archCombo.getSelectedItem();
        if (key == null) {
            key = ARCH_NNUE;
        }
        cards.show(cardPanel, key);
        propagateDetailed();
        refreshStatusBadge();
    }

    /**
     * Refreshes the per-architecture status badge.
     */
    private void refreshStatusBadge() {
        String key = (String) archCombo.getSelectedItem();
        if (key == null) {
            statusBadge.setText("");
            return;
        }
        String statusKey = switch (key) {
            case ARCH_CNN -> "cnn";
            case ARCH_BT4 -> "bt4";
            default -> "nnue";
        };
        statusBadge.setText(key + ": " + provider.statusFor(statusKey));
    }

    /**
     * Propagates the toggle state to every view.
     */
    private void propagateDetailed() {
        boolean d = detailedToggle.isSelected();
        nnueView.setDetailed(d);
        cnnView.setDetailed(d);
        bt4View.setDetailed(d);
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    /**
     * Spawns a SwingWorker that fills the snapshots for the pending FEN and
     * pushes them back to the views on completion.
     */
    private void startInference() {
        final String fen = pendingFen;
        pendingFen = null;
        if (fen == null) {
            return;
        }
        if (inferenceWorker != null && !inferenceWorker.isDone()) {
            // A worker is in flight; let it finish, then re-fire if a newer
            // FEN arrived in the meantime.
            pendingFen = fen;
            return;
        }
        statusBadge.setText("running inference...");
        inferenceWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                provider.fillNnue(fen, nnueSnap);
                provider.fillCnn(fen, cnnSnap);
                provider.fillBt4(fen, bt4Snap);
                return null;
            }

            @Override
            protected void done() {
                nnueView.setSnapshot(nnueSnap);
                cnnView.setSnapshot(cnnSnap);
                bt4View.setSnapshot(bt4Snap);
                nnueView.setFen(fen);
                cnnView.setFen(fen);
                bt4View.setFen(fen);
                nnueView.setVersionLabel(provider.nnueVersionLabel());
                refreshStatusBadge();
                inferenceWorker = null;
                if (pendingFen != null) {
                    SwingUtilities.invokeLater(WorkbenchNetworkPanel.this::startInference);
                }
            }
        };
        inferenceWorker.execute();
    }
}
