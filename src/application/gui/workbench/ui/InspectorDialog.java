package application.gui.workbench.ui;

import application.gui.workbench.network.ActivationSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * In-workbench overlay that shows the raw tensor slice behind a clicked
 * region. Replaces the previous standalone {@code JDialog}: opening the
 * inspector no longer spawns a separate OS window, keeping the user inside
 * the workbench frame in line with modern editor UX.
 *
 * <p>The shared instance is mounted via {@link ModalOverlay#forComponent}
 * onto the workbench's modal layer; calling {@link #inspect} again reuses
 * the same panel and re-centres it.</p>
 */
public final class InspectorDialog extends JPanel {

    /**
     * Maximum number of rows shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_ROWS = 64;

    /**
     * Maximum number of columns shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_COLS = 16;

    /**
     * Preferred overlay width.
     */
    private static final int PREFERRED_WIDTH = 620;

    /**
     * Preferred overlay height.
     */
    private static final int PREFERRED_HEIGHT = 520;

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Shared instance, lazily created.
     */
    private static InspectorDialog instance;

    /**
     * Returns the shared inspector panel, creating it on first use.
     *
     * @param near component used to resolve the workbench overlay
     * @return shared inspector
     */
    public static InspectorDialog shared(Component near) {
        if (instance == null) {
            instance = new InspectorDialog();
        }
        instance.attach(near);
        return instance;
    }

    /**
     * Title label shown at the top of the body.
     */
    private final JLabel titleLabel = new JLabel();

    /**
     * Description text shown under the title.
     */
    private final JLabel subtitleLabel = new JLabel();

    /**
     * One-line stats summary (shape, min, max, mean, rms).
     */
    private final JLabel statsLabel = new JLabel();

    /**
     * Monospaced text body holding the formatted matrix values.
     */
    private final JTextArea body = new JTextArea();

    /**
     * Close button anchored in the header.
     */
    private final JButtonClose closeButton = new JButtonClose();

    /**
     * Most recently resolved overlay, used for show/hide on the EDT.
     */
    private transient ModalOverlay overlay;

    /**
     * Creates the inspector panel.
     */
    private InspectorDialog() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);

        titleLabel.setFont(Theme.font(13, Font.BOLD));
        titleLabel.setForeground(Theme.TEXT);
        subtitleLabel.setFont(Theme.font(11, Font.PLAIN));
        subtitleLabel.setForeground(Theme.MUTED);
        statsLabel.setFont(Theme.font(11, Font.PLAIN));
        statsLabel.setForeground(Theme.MUTED);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setOpaque(true);
        header.setBackground(Theme.PANEL_SOLID);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitleLabel);
        header.add(Box.createVerticalStrut(6));
        header.add(statsLabel);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(true);
        headerRow.setBackground(Theme.PANEL_SOLID);
        headerRow.add(header, BorderLayout.CENTER);
        headerRow.add(closeButton, BorderLayout.EAST);
        closeButton.addActionListener(event -> hideInspector());

        body.setEditable(false);
        body.setFont(Theme.mono(11));
        body.setBackground(Theme.BG);
        body.setForeground(Theme.TEXT);
        body.setMargin(new Insets(8, 12, 8, 12));
        body.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG);

        add(headerRow, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Resolves the overlay backing this inspector against the given anchor.
     *
     * @param near anchor component
     */
    private void attach(Component near) {
        overlay = ModalOverlay.forComponent(near);
    }

    /**
     * Hides the inspector overlay if it is currently shown.
     */
    private void hideInspector() {
        if (overlay != null) {
            overlay.hide();
        }
    }

    /**
     * Shows the inspector with the contents of one region's tensor slice.
     *
     * @param region clicked region (must have a non-null data key)
     * @param snapshot snapshot to read from
     */
    public void inspect(HitRegions.Region region, ActivationSnapshot snapshot) {
        if (region == null || !region.hasData()) {
            return;
        }
        titleLabel.setText(region.title);
        subtitleLabel.setText(region.description == null ? "" : region.description);
        float[] data = region.inlineData != null ? region.inlineData
                : (snapshot == null ? null : snapshot.data(region.dataKey));
        if (data == null) {
            statsLabel.setText("(no data for key " + region.dataKey + ")");
            body.setText("");
            present();
            return;
        }
        int off = Math.max(0, Math.min(region.dataOffset, data.length));
        int len = region.dataLength <= 0 ? data.length - off : Math.min(region.dataLength, data.length - off);
        if (len <= 0) {
            statsLabel.setText("(empty slice for key " + region.dataKey + ")");
            body.setText("");
            present();
            return;
        }
        renderStats(data, off, len, region.shapeText);
        body.setText(formatValues(data, off, len, region.dataStride));
        body.setCaretPosition(0);
        present();
    }

    /**
     * Mounts the inspector on the workbench overlay.
     */
    private void present() {
        if (overlay != null) {
            overlay.show(this, PREFERRED_WIDTH, PREFERRED_HEIGHT);
        }
    }

    /**
     * Renders aggregate statistics for the selected data slice.
     *
     * @param data source data
     * @param off start offset
     * @param len element count
     * @param shapeText display shape text
     */
    private void renderStats(float[] data, int off, int len, String shapeText) {
        double sum = 0;
        double sumSq = 0;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < len; ++i) {
            float v = data[off + i];
            sum += v;
            sumSq += (double) v * v;
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        float mean = (float) (sum / len);
        float rms = (float) Math.sqrt(sumSq / len);
        String shape = shapeText == null || shapeText.isBlank() ? Integer.toString(len) : shapeText;
        statsLabel.setText(String.format(
                "shape %s   ·   n=%d   ·   min %+.4f   ·   max %+.4f   ·   mean %+.4f   ·   rms %.4f",
                shape, len, min, max, mean, rms));
    }

    /**
     * Formats selected values as either a matrix or a flat list.
     *
     * @param data source data
     * @param off start offset
     * @param len element count
     * @param stride row stride, or zero for flat output
     * @return formatted values
     */
    private static String formatValues(float[] data, int off, int len, int stride) {
        if (stride > 0 && len % stride == 0 && stride > 1) {
    return formatMatrix(data, off, len / stride, stride);
        }
        return InspectorText.formatFlat(data, off, len);
    }

    /**
     * Formats a matrix slice with row and column labels.
     *
     * @param data source data
     * @param off start offset
     * @param rows row count
     * @param cols column count
     * @return formatted matrix
     */
    private static String formatMatrix(float[] data, int off, int rows, int cols) {
        int showRows = Math.min(rows, MAX_MATRIX_ROWS);
        int showCols = Math.min(cols, MAX_MATRIX_COLS);
        StringBuilder sb = new StringBuilder();
        sb.append("        ");
        for (int c = 0; c < showCols; ++c) {
            sb.append(String.format("%8d", c));
        }
        if (cols > showCols) {
            sb.append("   ...");
        }
        sb.append('\n');
        for (int r = 0; r < showRows; ++r) {
            sb.append(String.format("%6d  ", r));
            for (int c = 0; c < showCols; ++c) {
                sb.append(String.format(" %+7.3f", data[off + r * cols + c]));
            }
            if (cols > showCols) {
                sb.append("   ...");
            }
            sb.append('\n');
        }
        if (rows > showRows) {
            sb.append("   ...\n");
        }
        return sb.toString();
    }

    /**
     * Lightweight close affordance used in the inspector header.
     */
    private static final class JButtonClose extends javax.swing.JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the close button.
         */
        JButtonClose() {
            super("Close");
            Theme.button(this, false);
            setMargin(new Insets(4, 10, 4, 10));
        }
    }
}
