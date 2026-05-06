package application.gui.workbench;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Lightweight non-blocking notification overlay rendered on the workbench
 * frame's layered pane. Replaces modal dialogs for non-critical messages.
 */
final class WorkbenchToast {

    /**
     * Default duration (ms) a toast is visible before fading.
     */
    private static final int VISIBLE_MS = 3200;

    /**
     * Fade-out duration (ms).
     */
    private static final int FADE_MS = 220;

    /**
     * Frame-tick interval for the fade animation (ms).
     */
    private static final int TICK_MS = 16;

    /**
     * Margin from the frame's bottom edge.
     */
    private static final int BOTTOM_MARGIN = 36;

    /**
     * Vertical gap between stacked toasts.
     */
    private static final int STACK_GAP = 6;

    /**
     * Live toasts per frame (newest last). Guarded by EDT.
     */
    private static final Map<JFrame, List<ToastPanel>> ACTIVE = new IdentityHashMap<>();

    /**
     * Component listeners installed per frame so the toasts re-flow on resize.
     */
    private static final Map<JFrame, ComponentListener> RESIZE_LISTENERS = new IdentityHashMap<>();

    /**
     * Toast severity controlling color.
     */
    enum Kind {
        /** Informational message. */
        INFO,
        /** Successful operation. */
        SUCCESS,
        /** Warning that does not require user action. */
        WARNING,
        /** Error surfaced non-blockingly. */
        ERROR
    }

    private WorkbenchToast() {
        // utility
    }

    /**
     * Shows a toast notification on the supplied frame.
     *
     * @param frame parent frame
     * @param kind severity
     * @param message message
     */
    static void show(JFrame frame, Kind kind, String message) {
        if (frame == null || message == null || message.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> showOnEdt(frame, kind, message));
    }

    /**
     * Implementation that runs on the EDT.
     */
    private static void showOnEdt(JFrame frame, Kind kind, String message) {
        ToastPanel toast = new ToastPanel(frame, kind, message);
        JLayeredPane layered = frame.getLayeredPane();
        layered.add(toast, JLayeredPane.POPUP_LAYER);
        ACTIVE.computeIfAbsent(frame, f -> new ArrayList<>()).add(toast);
        installResizeListener(frame);
        relayout(frame);
        toast.startLifecycle();
    }

    /**
     * Installs a one-shot resize listener for a frame so toasts re-flow on
     * window size changes.
     *
     * @param frame parent frame
     */
    private static void installResizeListener(JFrame frame) {
        if (RESIZE_LISTENERS.containsKey(frame)) {
            return;
        }
        ComponentListener listener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                relayout(frame);
            }
        };
        frame.addComponentListener(listener);
        RESIZE_LISTENERS.put(frame, listener);
    }

    /**
     * Re-positions every active toast on a frame, stacking newest at the
     * bottom and older ones above it.
     *
     * @param frame parent frame
     */
    private static void relayout(JFrame frame) {
        List<ToastPanel> toasts = ACTIVE.get(frame);
        if (toasts == null || toasts.isEmpty()) {
            return;
        }
        JLayeredPane layered = frame.getLayeredPane();
        int bottom = layered.getHeight() - BOTTOM_MARGIN;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            ToastPanel toast = toasts.get(i);
            Dimension preferred = toast.getPreferredSize();
            int x = (layered.getWidth() - preferred.width) / 2;
            int y = bottom - preferred.height;
            toast.setBounds(x, y, preferred.width, preferred.height);
            bottom = y - STACK_GAP;
        }
        layered.repaint();
    }

    /**
     * Removes a finished toast and re-flows the rest. Cleans up frame-level
     * resize listener once no toasts remain.
     *
     * @param frame parent frame
     * @param toast finished toast
     */
    private static void remove(JFrame frame, ToastPanel toast) {
        List<ToastPanel> toasts = ACTIVE.get(frame);
        if (toasts != null) {
            toasts.remove(toast);
            if (toasts.isEmpty()) {
                ACTIVE.remove(frame);
                ComponentListener listener = RESIZE_LISTENERS.remove(frame);
                if (listener != null) {
                    frame.removeComponentListener(listener);
                }
            }
        }
        Container parent = toast.getParent();
        if (parent != null) {
            parent.remove(toast);
            parent.repaint();
        }
        relayout(frame);
    }

    /**
     * Toast panel rendering and lifecycle.
     */
    private static final class ToastPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final JFrame owner;
        private final Kind kind;
        private final long shownAt = System.currentTimeMillis();
        private float alpha = 0f;
        private Timer timer;

        ToastPanel(JFrame owner, Kind kind, String message) {
            super(new BorderLayout());
            this.owner = owner;
            this.kind = kind;
            setOpaque(false);
            JPanel inner = new JPanel(new GridBagLayout());
            inner.setOpaque(false);
            JLabel label = new JLabel(message);
            label.setFont(WorkbenchTheme.font(12, Font.PLAIN));
            label.setForeground(textColor(kind));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new java.awt.Insets(10, 18, 10, 18);
            inner.add(label, c);
            add(inner, BorderLayout.CENTER);
            setBorder(WorkbenchTheme.pad(0, 0, 0, 0));
            setSize(getPreferredSize());
        }

        void startLifecycle() {
            timer = new Timer(TICK_MS, event -> tick());
            timer.setCoalesce(true);
            timer.start();
        }

        private void tick() {
            long elapsed = System.currentTimeMillis() - shownAt;
            if (elapsed < FADE_MS) {
                alpha = Math.min(1f, elapsed / (float) FADE_MS);
            } else if (elapsed < VISIBLE_MS) {
                alpha = 1f;
            } else if (elapsed < VISIBLE_MS + FADE_MS) {
                alpha = Math.max(0f, 1f - (elapsed - VISIBLE_MS) / (float) FADE_MS);
            } else {
                timer.stop();
                WorkbenchToast.remove(owner, this);
                return;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
                g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.BOARD_SHADOW, 64));
                g.fillRoundRect(2, 6, getWidth() - 4, getHeight() - 8, 14, 14);
                g.setColor(backgroundColor(kind));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 6, 12, 12);
                g.setColor(borderColor(kind));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 6, 12, 12);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static Color backgroundColor(Kind kind) {
        return switch (kind) {
            case SUCCESS -> WorkbenchTheme.STATUS_SUCCESS_BG;
            case WARNING -> WorkbenchTheme.STATUS_WARNING_BG;
            case ERROR -> WorkbenchTheme.STATUS_ERROR_BG;
            case INFO -> WorkbenchTheme.STATUS_INFO_BG;
        };
    }

    private static Color borderColor(Kind kind) {
        return switch (kind) {
            case SUCCESS -> WorkbenchTheme.STATUS_SUCCESS_BORDER;
            case WARNING -> WorkbenchTheme.STATUS_WARNING_BORDER;
            case ERROR -> WorkbenchTheme.STATUS_ERROR_BORDER;
            case INFO -> WorkbenchTheme.STATUS_INFO_BORDER;
        };
    }

    private static Color textColor(Kind kind) {
        return switch (kind) {
            case SUCCESS -> WorkbenchTheme.STATUS_SUCCESS_TEXT;
            case WARNING -> WorkbenchTheme.STATUS_WARNING_TEXT;
            case ERROR -> WorkbenchTheme.STATUS_ERROR_TEXT;
            case INFO -> WorkbenchTheme.STATUS_INFO_TEXT;
        };
    }
}
