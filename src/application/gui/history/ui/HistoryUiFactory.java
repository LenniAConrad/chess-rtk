package application.gui.history.ui;

import application.gui.GuiTheme;
import application.gui.ui.RoundedPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Builds re-usable UI pieces that are shared between the history window and its dialogs.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryUiFactory {

    /**
     * themeSupplier field.
     */
    private final Supplier<GuiTheme> themeSupplier;
    /**
     * cards field.
     */
    private final List<RoundedPanel> cards;
    /**
     * flatCards field.
     */
    private final List<RoundedPanel> flatCards;
    /**
     * buttons field.
     */
    private final List<JButton> buttons;
    /**
     * iconButtons field.
     */
    private final List<JButton> iconButtons;
    /**
     * strongLabels field.
     */
    private final List<JLabel> strongLabels;
    /**
     * mutedLabels field.
     */
    private final List<JLabel> mutedLabels;
    /**
     * separators field.
     */
    private final List<JComponent> separators;

    /**
     * HistoryUiFactory method.
     *
     * @param themeSupplier parameter.
     * @param cards parameter.
     * @param flatCards parameter.
     * @param buttons parameter.
     * @param iconButtons parameter.
     * @param strongLabels parameter.
     * @param mutedLabels parameter.
     * @param separators parameter.
     */
    public HistoryUiFactory(
            Supplier<GuiTheme> themeSupplier,
            List<RoundedPanel> cards,
            List<RoundedPanel> flatCards,
            List<JButton> buttons,
            List<JButton> iconButtons,
            List<JLabel> strongLabels,
            List<JLabel> mutedLabels,
            List<JComponent> separators) {
        this.themeSupplier = Objects.requireNonNull(themeSupplier, "themeSupplier");
        this.cards = Objects.requireNonNull(cards, "cards");
        this.flatCards = Objects.requireNonNull(flatCards, "flatCards");
        this.buttons = Objects.requireNonNull(buttons, "buttons");
        this.iconButtons = Objects.requireNonNull(iconButtons, "iconButtons");
        this.strongLabels = Objects.requireNonNull(strongLabels, "strongLabels");
        this.mutedLabels = Objects.requireNonNull(mutedLabels, "mutedLabels");
        this.separators = Objects.requireNonNull(separators, "separators");
    }

    /**
     * theme method.
     *
     * @return return value.
     */
    private GuiTheme theme() {
        return Objects.requireNonNull(themeSupplier.get(), "theme");
    }

    /**
     * createCard method.
     *
     * @param title parameter.
     * @return return value.
     */
    public RoundedPanel createCard(String title) {
        return createCard(title, true);
    }

    /**
     * createCard method.
     *
     * @param title parameter.
     * @param showTitle parameter.
     * @return return value.
     */
    public RoundedPanel createCard(String title, boolean showTitle) {
        RoundedPanel card = new RoundedPanel(0);
        cards.add(card);
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));
        if (showTitle && title != null && !title.isBlank()) {
            card.add(titleLabel(title), BorderLayout.NORTH);
        }
        return card;
    }

    /**
     * createFlatCard method.
     *
     * @param title parameter.
     * @return return value.
     */
    public RoundedPanel createFlatCard(String title) {
        return createFlatCard(title, true);
    }

    /**
     * createFlatCard method.
     *
     * @param title parameter.
     * @param showTitle parameter.
     * @return return value.
     */
    public RoundedPanel createFlatCard(String title, boolean showTitle) {
        RoundedPanel card = new RoundedPanel(0);
        flatCards.add(card);
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        if (showTitle && title != null && !title.isBlank()) {
            card.add(titleLabel(title), BorderLayout.NORTH);
        }
        return card;
    }

    /**
     * titleLabel method.
     *
     * @param text parameter.
     * @return return value.
     */
    public JLabel titleLabel(String text) {
        JLabel label = new JLabel(formatTitle(text));
        strongLabels.add(label);
        return label;
    }

    /**
     * formatTitle method.
     *
     * @param text parameter.
     * @return return value.
     */
    public String formatTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /**
     * mutedLabel method.
     *
     * @param text parameter.
     * @return return value.
     */
    public JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        mutedLabels.add(label);
        return label;
    }

    /**
     * labeledField method.
     *
     * @param labelText parameter.
     * @param field parameter.
     * @return return value.
     */
    public JPanel labeledField(String labelText, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setOpaque(false);
        panel.add(mutedLabel(labelText), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    /**
     * labeledValue method.
     *
     * @param labelText parameter.
     * @param value parameter.
     * @return return value.
     */
    public JPanel labeledValue(String labelText, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setOpaque(false);
        panel.add(mutedLabel(labelText), BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    /**
     * labeledCombo method.
     *
     * @param labelText parameter.
     * @param combo parameter.
     * @return return value.
     */
    public JPanel labeledCombo(String labelText, JComboBox<?> combo) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setOpaque(false);
        panel.add(mutedLabel(labelText), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);
        return panel;
    }

    /**
     * settingsRow method.
     *
     * @param labelText parameter.
     * @param control parameter.
     * @return return value.
     */
    public JPanel settingsRow(String labelText, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel label = mutedLabel(labelText);
        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        Border divider = new MatteBorder(0, 0, 1, 0, theme().border());
        Border padding = new EmptyBorder(6, 0, 6, 0);
        row.setBorder(BorderFactory.createCompoundBorder(divider, padding));
        return row;
    }

    /**
     * themedButton method.
     *
     * @param text parameter.
     * @param action parameter.
     * @return return value.
     */
    public JButton themedButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        buttons.add(btn);
        btn.setOpaque(true);
        btn.addActionListener(action);
        return btn;
    }

    /**
     * iconButton method.
     *
     * @param text parameter.
     * @param action parameter.
     * @return return value.
     */
    public JButton iconButton(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        iconButtons.add(btn);
        btn.setOpaque(true);
        btn.setFocusable(false);
        btn.addActionListener(action);
        return btn;
    }

    /**
     * separatorLine method.
     *
     * @return return value.
     */
    public JComponent separatorLine() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separators.add(line);
        line.setBorder(new EmptyBorder(6, 0, 6, 0));
        return line;
    }

    /**
     * sectionHeader method.
     *
     * @param text parameter.
     * @return return value.
     */
    public JLabel sectionHeader(String text) {
        JLabel label = mutedLabel(formatTitle(text));
        label.setBorder(new EmptyBorder(4, 2, 6, 2));
        return label;
    }
}
