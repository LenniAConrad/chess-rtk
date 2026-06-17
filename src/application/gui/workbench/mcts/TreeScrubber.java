package application.gui.workbench.mcts;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Growth-history scrubber for the search-tree panel.
 */
final class TreeScrubber {

    /**
     * Frame interval for scrubber playback, in milliseconds.
     */
    private static final int PLAY_INTERVAL_MS = 110;

    /**
     * Session that owns the recorded frames.
     */
    private final transient MctsSession session;

    /**
     * Renders a recorded tree frame.
     */
    private final transient Consumer<MctsSearch.TreeSnapshot> frameRenderer;

    /**
     * Renders the live session snapshot.
     */
    private final transient Consumer<MctsSession.Snapshot> liveRenderer;

    /**
     * Faint node-count-over-time sparkline.
     */
    private final TreeGrowthSparkline sparkline;

    /**
     * Growth-scrubber timeline.
     */
    private final JSlider slider = new JSlider(0, 0, 0);

    /**
     * Step-back-one-frame button.
     */
    private final JButton previousButton =
            Ui.iconButton("«", "Step one recorded frame back", event -> step(-1));

    /**
     * Step-forward-one-frame button.
     */
    private final JButton nextButton =
            Ui.iconButton("»", "Step one recorded frame forward", event -> step(1));

    /**
     * Play/pause button that animates through recorded frames.
     */
    private final JButton playButton = Ui.button("Play", false, event -> togglePlay());

    /**
     * Return-to-live button.
     */
    private final JButton liveButton = Ui.button("Live", false, event -> goLive());

    /**
     * Frame counter / visits / nodes readout.
     */
    private final JLabel label = new JLabel("no recording yet");

    /**
     * True while showing a recorded frame instead of the live tree.
     */
    private boolean scrubbing;

    /**
     * Index of the recorded frame shown while scrubbing.
     */
    private int index;

    /**
     * Guards slider events triggered by programmatic updates.
     */
    private boolean adjusting;

    /**
     * Last recorded frame rendered while scrubbing.
     */
    private transient MctsSearch.TreeSnapshot lastRenderedFrame;

    /**
     * Timer driving scrubber playback, or null when not playing.
     */
    private transient javax.swing.Timer playTimer;

    TreeScrubber(MctsSession session, Consumer<MctsSearch.TreeSnapshot> frameRenderer,
            Consumer<MctsSession.Snapshot> liveRenderer) {
        this.session = session;
        this.frameRenderer = frameRenderer;
        this.liveRenderer = liveRenderer;
        this.sparkline = new TreeGrowthSparkline(session);
        configureControls();
    }

    /**
     * Builds the bottom growth-scrubber bar.
     *
     * @return scrubber component
     */
    JComponent buildBar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM, Theme.SPACE_XS, Theme.SPACE_SM)));
        JPanel nav = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_XS, 0));
        nav.add(Ui.label("Growth"));
        nav.add(previousButton);
        nav.add(nextButton);
        nav.add(playButton);
        nav.add(liveButton);
        bar.add(nav, BorderLayout.WEST);
        JPanel timeline = Ui.transparentPanel(new BorderLayout(0, 0));
        timeline.add(sparkline, BorderLayout.NORTH);
        timeline.add(slider, BorderLayout.CENTER);
        bar.add(timeline, BorderLayout.CENTER);
        bar.add(label, BorderLayout.EAST);
        return bar;
    }

    /**
     * Updates the slider, buttons, and readout from the recorded history.
     */
    void updateControls() {
        int frames = session.historySize();
        boolean has = frames > 0;
        if (!has) {
            scrubbing = false;
        }
        slider.setEnabled(has);
        previousButton.setEnabled(has);
        nextButton.setEnabled(has);
        liveButton.setEnabled(has && scrubbing);
        adjusting = true;
        slider.setMinimum(0);
        slider.setMaximum(Math.max(0, frames - 1));
        if (scrubbing) {
            index = Math.min(index, Math.max(0, frames - 1));
        } else {
            index = Math.max(0, frames - 1);
        }
        slider.setValue(index);
        adjusting = false;
        if (scrubbing && session.historyFrame(index) != lastRenderedFrame) {
            renderCurrentFrame();
        }
        updateLabel();
        sparkline.repaint();
    }

    /**
     * Returns whether the panel is showing a recorded frame.
     *
     * @return true while scrubbing
     */
    boolean isScrubbing() {
        return scrubbing;
    }

    /**
     * Steps the scrubber by a number of frames.
     *
     * @param delta frame delta
     */
    void step(int delta) {
        int frames = session.historySize();
        if (frames == 0) {
            return;
        }
        stopPlay();
        scrubbing = true;
        index = Math.max(0, Math.min(frames - 1, index + delta));
        liveButton.setEnabled(true);
        showFrame();
    }

    /**
     * Jumps back to following the live, growing tree.
     */
    void goLive() {
        stopPlay();
        scrubbing = false;
        MctsSession.Snapshot snapshot = session.snapshot();
        updateControls();
        liveRenderer.accept(snapshot);
    }

    /**
     * Toggles playback through recorded frames.
     */
    void togglePlay() {
        if (playTimer != null) {
            stopPlay();
        } else {
            startPlay();
        }
    }

    /**
     * Stops playback if running.
     */
    void stopPlay() {
        if (playTimer != null) {
            playTimer.stop();
            playTimer = null;
        }
        playButton.setText("Play");
    }

    /**
     * Renders the current recorded frame.
     */
    void renderCurrentFrame() {
        MctsSearch.TreeSnapshot tree = session.historyFrame(index);
        lastRenderedFrame = tree;
        frameRenderer.accept(tree);
    }

    private void configureControls() {
        Ui.styleSlider(slider);
        slider.setToolTipText("Scrub through the recorded growth of the tree");
        slider.setPreferredSize(new Dimension(260, Theme.CONTROL_HEIGHT));
        slider.addChangeListener(event -> onSlider());
        liveButton.setToolTipText("Jump back to the live, growing tree");
        label.setFont(Theme.mono(12));
        label.setForeground(Theme.MUTED);
        playButton.setToolTipText("Play back the recorded growth frame by frame");
    }

    private void updateLabel() {
        int frames = session.historySize();
        if (frames == 0) {
            label.setText("no recording yet");
            return;
        }
        if (!scrubbing) {
            label.setText(String.format("Live · %,d frames", frames));
            return;
        }
        MctsSearch.TreeSnapshot frame = session.historyFrame(index);
        label.setText(String.format("Frame %,d / %,d · %,d visits · %,d nodes",
                index + 1, frames, frame == null ? 0 : frame.playouts(),
                frame == null ? 0 : frame.nodes().size()));
    }

    private void onSlider() {
        if (adjusting) {
            return;
        }
        stopPlay();
        scrubbing = true;
        index = slider.getValue();
        liveButton.setEnabled(true);
        updateLabel();
        renderCurrentFrame();
    }

    private void startPlay() {
        if (session.historySize() == 0) {
            return;
        }
        scrubbing = true;
        if (index >= session.historySize() - 1) {
            index = 0;
        }
        liveButton.setEnabled(true);
        playButton.setText("Pause");
        playTimer = new javax.swing.Timer(PLAY_INTERVAL_MS, event -> advancePlay());
        playTimer.start();
        showFrame();
    }

    private void advancePlay() {
        int frames = session.historySize();
        if (frames == 0) {
            stopPlay();
            return;
        }
        if (index >= frames - 1) {
            index = frames - 1;
            showFrame();
            stopPlay();
            return;
        }
        index++;
        showFrame();
    }

    private void showFrame() {
        adjusting = true;
        slider.setValue(index);
        adjusting = false;
        updateLabel();
        renderCurrentFrame();
    }
}
