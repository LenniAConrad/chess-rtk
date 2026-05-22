/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import application.gui.workbench.network.ActivationSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/**
 * Always-visible right-side details pane for the network workbench.
 *
 * <p>Replaces the previous modeless inspector dialog. Each architecture view
 * pushes its current selection here via {@link #inspect}; an empty selection
 * surfaces a brief "click an element" hint instead. Header shows the bold
 * title, a one-line subtitle, and the current value (sign-colored). Below
 * sits a short educational description, a one-line stats summary, the raw
 * tensor slice (monospace, scrollable), and a copy-to-clipboard button so
 * researchers can pull values out into other tools.</p>
 */
public final class InspectorPanel extends JPanel {

    /**
     * Maximum rows shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_ROWS = 64;

    /**
     * Maximum columns shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_COLS = 16;

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Header title (bold).
     */
    private final JLabel titleLabel = new JLabel("Inspector");

    /**
     * One-line subtitle under the title (muted color).
     */
    private final JLabel subtitleLabel = new JLabel(" ");

    /**
     * Big sign-colored value text (eval / activation / probability).
     */
    private final JLabel valueLabel = new JLabel(" ");

    /**
     * One-line stats summary (mean / min / max / rms).
     */
    private final JLabel statsLabel = new JLabel(" ");

    /**
     * Short educational description.
     */
    private final JTextArea descriptionArea = new JTextArea();

    /**
     * Raw tensor slice (monospace).
     */
    private final JTextArea dataArea = new JTextArea();

    /**
     * Scroll container for raw tensor data; hidden for empty selections so the
     * inspector does not present a large blank viewport.
     */
    private final JScrollPane dataScroll;

    /**
     * Copy raw data + summary to clipboard.
     */
    private final JButton copyButton = Ui.button("Copy", false, null);

    /**
     * Clear the current selection (returns to the empty hint).
     */
    private final JButton clearButton = Ui.button("Clear", false, null);

    /**
     * Cached clipboard payload assembled during {@link #inspect}.
     */
    private String clipboardPayload = "";

    /**
     * Creates the inspector panel.
     */
    public InspectorPanel() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_MD)));
        setPreferredSize(new Dimension(340, 600));

        titleLabel.setFont(Theme.font(13, Font.BOLD));
        titleLabel.setForeground(Theme.TEXT);
        subtitleLabel.setFont(Theme.font(11, Font.PLAIN));
        subtitleLabel.setForeground(Theme.MUTED);
        valueLabel.setFont(Theme.font(16, Font.BOLD));
        valueLabel.setForeground(Theme.TEXT);
        statsLabel.setFont(Theme.mono(11));
        statsLabel.setForeground(Theme.MUTED);

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setForeground(Theme.TEXT);
        descriptionArea.setFont(Theme.font(11, Font.ITALIC));
        descriptionArea.setBorder(Theme.pad(Theme.SPACE_XS, 0));

        dataArea.setEditable(false);
        dataArea.setLineWrap(false);
        dataArea.setOpaque(true);
        dataArea.setBackground(Theme.BG);
        dataArea.setForeground(Theme.TEXT);
        dataArea.setFont(Theme.mono(11));
        dataArea.setMargin(new Insets(Theme.SPACE_SM, Theme.SPACE_SM,
                Theme.SPACE_SM, Theme.SPACE_SM));

        dataScroll = new JScrollPane(dataArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dataScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        dataScroll.getViewport().setBackground(Theme.BG);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(Theme.SPACE_XS));
        header.add(subtitleLabel);
        header.add(Box.createVerticalStrut(Theme.SPACE_SM));
        header.add(valueLabel);
        header.add(Box.createVerticalStrut(Theme.SPACE_XS));
        header.add(statsLabel);
        header.add(Box.createVerticalStrut(Theme.SPACE_SM));
        header.add(descriptionArea);
        header.add(Box.createVerticalStrut(Theme.SPACE_SM));

        copyButton.setEnabled(false);
        clearButton.setEnabled(false);
        copyButton.addActionListener(event -> copyToClipboard());
        clearButton.addActionListener(event -> clear());

        JPanel buttonRow = new JPanel();
        buttonRow.setOpaque(false);
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(copyButton);
        buttonRow.add(Box.createHorizontalStrut(Theme.SPACE_SM));
        buttonRow.add(clearButton);
        buttonRow.add(Box.createHorizontalGlue());

        add(header, BorderLayout.NORTH);
        add(dataScroll, BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);

        clear();
    }

    /**
     * Resets the inspector to the empty-selection state.
     */
    public void clear() {
        titleLabel.setText("Inspector");
        subtitleLabel.setText("Select an item.");
        valueLabel.setText(" ");
        statsLabel.setText(" ");
        descriptionArea.setText("");
        dataArea.setText("");
        dataScroll.setVisible(false);
        clipboardPayload = "";
        copyButton.setEnabled(false);
        clearButton.setEnabled(false);
        revalidate();
        repaint();
    }

    /**
     * Populates the inspector from a hit region plus the current snapshot.
     *
     * @param region clicked region (may be null to clear)
     * @param snapshot snapshot to read raw data from (may be null)
     */
    public void inspect(HitRegions.Region region, ActivationSnapshot snapshot) {
        if (region == null) {
            clear();
            return;
        }
        titleLabel.setText(region.title.isEmpty() ? "Inspector" : region.title);
        subtitleLabel.setText(region.description == null || region.description.isEmpty()
                ? " " : region.description);
        valueLabel.setText(region.value == null || region.value.isEmpty() ? " " : region.value);
        descriptionArea.setText(educationalText(region));
        descriptionArea.setCaretPosition(0);
        dataScroll.setVisible(false);

        if (region.dataKey != null && snapshot != null) {
            float[] data = region.inlineData != null ? region.inlineData : snapshot.data(region.dataKey);
            if (data == null || data.length == 0) {
                statsLabel.setText("(no data for key " + region.dataKey + ")");
                dataArea.setText("");
                clipboardPayload = composeClipboard(region, "", "(no data)");
            } else {
                int off = Math.max(0, Math.min(region.dataOffset, data.length));
                int len = region.dataLength <= 0
                        ? data.length - off
                        : Math.min(region.dataLength, data.length - off);
                if (len <= 0) {
                    statsLabel.setText("(empty slice)");
                    dataArea.setText("");
                    clipboardPayload = composeClipboard(region, "", "(empty slice)");
                } else {
                    String stats = computeStats(data, off, len, region.shapeText);
                    String body = formatValues(data, off, len, region.dataStride);
                    statsLabel.setText(stats);
                    dataArea.setText(body);
                    dataArea.setCaretPosition(0);
                    dataScroll.setVisible(true);
                    clipboardPayload = composeClipboard(region, stats, body);
                }
            }
            copyButton.setEnabled(!clipboardPayload.isBlank());
        } else if (region.inlineData != null) {
            float[] data = region.inlineData;
            int len = data.length;
            if (len <= 0) {
                statsLabel.setText("(empty slice)");
                dataArea.setText("");
                clipboardPayload = composeClipboard(region, "", "(empty slice)");
            } else {
                String stats = computeStats(data, 0, len, region.shapeText);
                String body = formatValues(data, 0, len, region.dataStride);
                statsLabel.setText(stats);
                dataArea.setText(body);
                dataArea.setCaretPosition(0);
                dataScroll.setVisible(true);
                clipboardPayload = composeClipboard(region, stats, body);
            }
            copyButton.setEnabled(!clipboardPayload.isBlank());
        } else {
            statsLabel.setText(" ");
            dataArea.setText("");
            clipboardPayload = composeClipboard(region, "", "");
            copyButton.setEnabled(false);
        }
        clearButton.setEnabled(true);
        revalidate();
        repaint();
    }

    /**
     * Returns a short educational sentence based on the region's title prefix.
     *
     * @param region clicked region
     * @return short description suitable for educational context
     */
    private static String educationalText(HitRegions.Region region) {
        String title = region.title.toLowerCase();
        if (title.startsWith("feature ")) {
            return "Half-KP feature combines own-king square with a piece location. Each active feature adds its weight row to the L1 accumulator; the signed contribution to the eval is shown above.";
        }
        if (title.startsWith("accumulator slot")) {
            return "One slot of the NNUE L1 accumulator (pre-ReLU). All active features contribute additively into this slot; clipping then keeps positives only.";
        }
        if (title.startsWith("clipped slot")) {
            return "Post-ReLU activation of an L1 slot. Negative values are clipped to zero before feeding into the L2/L3 head.";
        }
        if (title.startsWith("centipawn")) {
            return "Final NNUE eval after the affine output layer plus a fixed scaling step. Positive favors the side to move.";
        }
        if (title.startsWith("head ")) {
            return "One self-attention head's softmax distribution over the 64 board tokens. Peak attention concentrates this head onto specific square pairs.";
        }
        if (title.startsWith("block ")) {
            return "One transformer encoder block: multi-head attention + FFN with residual streams. The two values are mean attention magnitude and FFN energy.";
        }
        if (title.startsWith("residual block")) {
            return "One LC0 ResNet residual block (conv + relu + conv + relu + skip). Channel-wise activations are reshaped onto the 8x8 board.";
        }
        if (title.startsWith("input plane")) {
            return "One of LC0's 112 input planes. Piece planes carry binary masks; auxiliary planes carry castling, en-passant, halfmove count, and constant bits.";
        }
        if (title.startsWith("top policy") || title.startsWith("top move")) {
            return "Compressed LC0 policy index decoded to a legal move. Probability is computed with softmax over the legal moves only.";
        }
        if (title.startsWith("value head") || title.startsWith("centipawn output")) {
            return "Network's win/draw/loss prediction for the side to move. Sum is normalised to 1 by softmax.";
        }
        if (title.startsWith("current position")) {
            return "Position fed into the network. NNUE encodes it as Half-KP features; LC0 networks encode it as 112 input planes.";
        }
        return "";
    }

    /**
     * Computes a one-line statistics summary.
     *
     * @param data flat values
     * @param off offset
     * @param len length
     * @param shapeText optional shape label
     * @return formatted stats line
     */
    private static String computeStats(float[] data, int off, int len, String shapeText) {
        double sum = 0;
        double sumSq = 0;
        double sumAbs = 0;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < len; ++i) {
            float v = data[off + i];
            sum += v;
            sumAbs += Math.abs(v);
            sumSq += (double) v * v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        float mean = (float) (sum / len);
        float absMean = (float) (sumAbs / len);
        float rms = (float) Math.sqrt(sumSq / len);
        String shape = (shapeText == null || shapeText.isBlank()) ? Integer.toString(len) : shapeText;
        return String.format(
                "shape %s · n=%d · mean %+.4f · |mean| %.4f · rms %.4f · min %+.4f · max %+.4f",
                shape, len, mean, absMean, rms, min, max);
    }

    /**
     * Formats a float slice as either a flat list or a matrix.
     *
     * @param data flat values
     * @param off offset
     * @param len length
     * @param stride row width when &gt; 1 (matrix mode)
     * @return formatted multi-line text
     */
    private static String formatValues(float[] data, int off, int len, int stride) {
        if (stride > 1 && len % stride == 0) {
            int rows = len / stride;
            int showRows = Math.min(rows, MAX_MATRIX_ROWS);
            int showCols = Math.min(stride, MAX_MATRIX_COLS);
            StringBuilder sb = new StringBuilder();
            sb.append("       ");
            for (int c = 0; c < showCols; ++c) {
                sb.append(String.format("%8d", c));
            }
            if (stride > showCols) {
                sb.append("   ...");
            }
            sb.append('\n');
            for (int r = 0; r < showRows; ++r) {
                sb.append(String.format("%5d  ", r));
                for (int c = 0; c < showCols; ++c) {
                    sb.append(String.format(" %+7.3f", data[off + r * stride + c]));
                }
                if (stride > showCols) {
                    sb.append("   ...");
                }
                sb.append('\n');
            }
            if (rows > showRows) {
                sb.append("   ... ").append(rows - showRows).append(" more rows\n");
            }
            return sb.toString();
        }
        return InspectorText.formatFlat(data, off, len);
    }

    /**
     * Builds the clipboard payload for the current selection.
     *
     * @param region selected region
     * @param stats stats line
     * @param body raw values body
     * @return composite payload
     */
    private static String composeClipboard(HitRegions.Region region, String stats, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(region.title).append('\n');
        if (region.value != null && !region.value.isEmpty()) {
            sb.append("Value: ").append(region.value).append('\n');
        }
        if (region.description != null && !region.description.isEmpty()) {
            sb.append("Description: ").append(region.description).append('\n');
        }
        if (region.dataKey != null || region.inlineData != null) {
            sb.append("Source key: ")
                    .append(region.dataKey == null ? "(computed)" : region.dataKey);
            if (region.shapeText != null && !region.shapeText.isEmpty()) {
                sb.append(" (").append(region.shapeText).append(')');
            }
            sb.append('\n');
        }
        if (stats != null && !stats.isEmpty()) {
            sb.append("Stats: ").append(stats).append('\n');
        }
        if (body != null && !body.isEmpty()) {
            sb.append('\n').append(body);
        }
        return sb.toString();
    }

    /**
     * Sends the current clipboard payload to the system clipboard.
     */
    private void copyToClipboard() {
        if (clipboardPayload.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(clipboardPayload), null);
    }
}
