package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Modeless dialog that shows the raw tensor slice behind a clicked region.
 *
 * <p>One shared instance is reused across the three architecture views so the
 * user gets a single secondary window rather than a window per arch. The dialog
 * is non-modal so the user can keep clicking around the visualizer while it is
 * open. Calling {@link #inspect} again reuses the same window.</p>
 */
final class WorkbenchInspectorDialog extends JDialog {

    /**
     * Maximum number of rows shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_ROWS = 64;

    /**
     * Maximum number of columns shown when rendering a matrix slice.
     */
    private static final int MAX_MATRIX_COLS = 16;

    /**
     * Maximum number of entries shown when rendering a flat slice.
     */
    private static final int MAX_FLAT_ENTRIES = 4096;

    /**
     * Serialization identifier for Swing dialog compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Shared instance, lazily created.
     */
    private static WorkbenchInspectorDialog INSTANCE;

    /**
     * Returns the shared dialog instance, creating it on first use.
     *
     * @param near component used to find the owner window
     * @return shared dialog
     */
    static WorkbenchInspectorDialog shared(Component near) {
        if (INSTANCE == null) {
            Window owner = near == null ? null : SwingUtilities.getWindowAncestor(near);
            INSTANCE = new WorkbenchInspectorDialog(owner);
        }
        return INSTANCE;
    }

    /**
     * Title label shown at the top of the dialog body.
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
     * Creates the dialog. Initial visibility is hidden; call {@link #inspect}.
     *
     * @param owner owner window
     */
    private WorkbenchInspectorDialog(Window owner) {
        super(owner);
        setTitle("Tensor inspector");
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(440, 360));
        setSize(580, 480);

        titleLabel.setFont(WorkbenchTheme.font(13, Font.BOLD));
        titleLabel.setForeground(WorkbenchTheme.TEXT);
        subtitleLabel.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        subtitleLabel.setForeground(WorkbenchTheme.MUTED);
        statsLabel.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        statsLabel.setForeground(WorkbenchTheme.MUTED);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setOpaque(true);
        header.setBackground(WorkbenchTheme.PANEL_SOLID);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitleLabel);
        header.add(Box.createVerticalStrut(6));
        header.add(statsLabel);

        body.setEditable(false);
        body.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        body.setBackground(WorkbenchTheme.BG);
        body.setForeground(WorkbenchTheme.TEXT);
        body.setMargin(new Insets(8, 12, 8, 12));
        body.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(WorkbenchTheme.BG);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        getContentPane().setBackground(WorkbenchTheme.BG);
    }

    /**
     * Shows the dialog with the contents of one region's tensor slice.
     *
     * @param region clicked region (must have a non-null data key)
     * @param snapshot snapshot to read from
     */
    void inspect(WorkbenchHitRegions.Region region, WorkbenchActivationSnapshot snapshot) {
        if (region == null || region.dataKey == null) {
            return;
        }
        titleLabel.setText(region.title);
        subtitleLabel.setText(region.description == null ? "" : region.description);
        float[] data = snapshot == null ? null : snapshot.data(region.dataKey);
        if (data == null) {
            statsLabel.setText("(no data for key " + region.dataKey + ")");
            body.setText("");
            showDialog();
            return;
        }
        int off = Math.max(0, Math.min(region.dataOffset, data.length));
        int len = region.dataLength <= 0 ? data.length - off : Math.min(region.dataLength, data.length - off);
        if (len <= 0) {
            statsLabel.setText("(empty slice for key " + region.dataKey + ")");
            body.setText("");
            showDialog();
            return;
        }
        renderStats(data, off, len, region.shapeText);
        body.setText(formatValues(data, off, len, region.dataStride));
        body.setCaretPosition(0);
        showDialog();
    }

    private void showDialog() {
        if (!isVisible()) {
            setLocationRelativeTo(getOwner());
        }
        setVisible(true);
        toFront();
    }

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

    private static String formatValues(float[] data, int off, int len, int stride) {
        if (stride > 0 && len % stride == 0 && stride > 1) {
            return formatMatrix(data, off, len / stride, stride);
        }
        return formatFlat(data, off, len);
    }

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

    private static String formatFlat(float[] data, int off, int len) {
        int show = Math.min(len, MAX_FLAT_ENTRIES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; ++i) {
            sb.append(String.format("[%6d]   %+10.5f%n", i, data[off + i]));
        }
        if (len > show) {
            sb.append("   ... ").append(len - show).append(" more values\n");
        }
        return sb.toString();
    }
}
