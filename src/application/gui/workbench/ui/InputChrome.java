package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

/**
 * Shared hover, focus, and disabled-state chrome for input-like controls.
 */
final class InputChrome {

    /**
     * Client-property key marking controls with input hover/focus chrome.
     */
    private static final String CHROME_LISTENER_PROPERTY =
            InputChrome.class.getName() + ".inputChromeListener";

    /**
     * Client-property key for compact input-border mode.
     */
    private static final String COMPACT_PROPERTY =
            InputChrome.class.getName() + ".inputCompact";

    /**
     * Client-property key storing transient input hover state.
     */
    private static final String HOVER_PROPERTY =
            InputChrome.class.getName() + ".inputHover";

    /**
     * Client-property key storing text-control enabled backgrounds.
     */
    private static final String ENABLED_BACKGROUND_PROPERTY =
            InputChrome.class.getName() + ".inputEnabledBackground";

    /**
     * Client-property key marking text controls with enabled-state refreshes.
     */
    private static final String ENABLED_LISTENER_PROPERTY =
            InputChrome.class.getName() + ".inputEnabledListener";

    /**
     * Prevents instantiation.
     */
    private InputChrome() {
        // utility
    }

    /**
     * Builds an input border for the current interaction state.
     *
     * @param focused whether the control has focus
     * @param hovered whether the pointer is over the control
     * @param compact true for compact toolbar controls
     * @return input border
     */
    static Border border(boolean focused, boolean hovered, boolean compact) {
        Color lineColor = focused ? Theme.INPUT_FOCUS : hovered ? Theme.ACCENT_HOVER : Theme.INPUT_BORDER;
        Border line = BorderFactory.createLineBorder(lineColor);
        Border inner = compact ? Theme.pad(4, 8, 4, 8) : Theme.pad(7, 9, 7, 9);
        return BorderFactory.createCompoundBorder(line, inner);
    }

    /**
     * Builds a compact input border for toolbar controls.
     *
     * @param focused whether the control has focus
     * @param hovered whether the pointer is over the control
     * @return compact input border
     */
    static Border compactBorder(boolean focused, boolean hovered) {
        return border(focused, hovered, true);
    }

    /**
     * Installs modern hover/focus border chrome on one input-like control.
     *
     * @param component target component
     * @param compact true for compact toolbar controls
     */
    static void install(JComponent component, boolean compact) {
        component.putClientProperty(COMPACT_PROPERTY, Boolean.valueOf(compact));
        updateBorder(component);
        if (Boolean.TRUE.equals(component.getClientProperty(CHROME_LISTENER_PROPERTY))) {
            return;
        }
        component.putClientProperty(CHROME_LISTENER_PROPERTY, Boolean.TRUE);
        component.addFocusListener(new FocusAdapter() {
            /**
             * Applies focused chrome.
             *
             * @param event focus event
             */
            @Override
            public void focusGained(FocusEvent event) {
                updateBorder(component);
            }

            /**
             * Restores resting or hover chrome.
             *
             * @param event focus event
             */
            @Override
            public void focusLost(FocusEvent event) {
                updateBorder(component);
            }
        });
        component.addMouseListener(new MouseAdapter() {
            /**
             * Applies hover chrome.
             *
             * @param event mouse event
             */
            @Override
            public void mouseEntered(MouseEvent event) {
                component.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
                updateBorder(component);
            }

            /**
             * Restores resting chrome.
             *
             * @param event mouse event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                component.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
                updateBorder(component);
            }
        });
    }

    /**
     * Installs enabled-state background changes on a text component.
     *
     * @param component text component
     * @param enabledBackground enabled background color
     */
    static void installEnabledBackground(JTextComponent component, Color enabledBackground) {
        component.putClientProperty(ENABLED_BACKGROUND_PROPERTY, enabledBackground);
        component.setBackground(component.isEnabled() ? enabledBackground : Theme.INPUT_DISABLED);
        if (Boolean.TRUE.equals(component.getClientProperty(ENABLED_LISTENER_PROPERTY))) {
            return;
        }
        component.putClientProperty(ENABLED_LISTENER_PROPERTY, Boolean.TRUE);
        component.addPropertyChangeListener("enabled", event -> {
            Object value = component.getClientProperty(ENABLED_BACKGROUND_PROPERTY);
            Color background = value instanceof Color color ? color : Theme.INPUT;
            component.setBackground(component.isEnabled() ? background : Theme.INPUT_DISABLED);
        });
    }

    /**
     * Refreshes one input control's border from its current state.
     *
     * @param component input-like component
     */
    private static void updateBorder(JComponent component) {
        boolean compact = Boolean.TRUE.equals(component.getClientProperty(COMPACT_PROPERTY));
        boolean hovered = Boolean.TRUE.equals(component.getClientProperty(HOVER_PROPERTY));
        component.setBorder(border(component.isFocusOwner(), hovered, compact));
    }
}
