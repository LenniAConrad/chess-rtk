package application.gui.layout.tab;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import application.gui.ui.RoundedPanel;

/**
 * AnnotateTabPanel class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class AnnotateTabPanel {

	/**
	 * Result record.
	 *
	 * Provides record behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public record Result(RoundedPanel panel,
		JLabel moveLabel,
		JTextArea commentArea,
		JButton saveButton,
		JButton clearCommentButton,
		JButton clearNagButton) {}

	/**
	 * build method.
	 *
	 * @param ctx parameter.
	 * @param onCommentFocusLost parameter.
	 * @param saveAction parameter.
	 * @param clearCommentAction parameter.
	 * @param clearNagAction parameter.
	 * @return return value.
	 */
	public static Result build(AnnotateTabContext ctx,
		Runnable onCommentFocusLost,
		ActionListener saveAction,
		ActionListener clearCommentAction,
		ActionListener clearNagAction) {
		RoundedPanel card = new RoundedPanel(0);
		ctx.registerFlatCard(card);
		card.setLayout(new BorderLayout(12, 12));
		card.setBorder(new EmptyBorder(12, 12, 12, 12));

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JLabel moveLabel = ctx.createMutedLabel("No move selected");
		body.add(moveLabel);
		body.add(Box.createVerticalStrut(8));

		JPanel nagRow = new JPanel(new GridLayout(1, 0, 6, 6));
		nagRow.setOpaque(false);
		ctx.addNagButton(nagRow, "!", 1);
		ctx.addNagButton(nagRow, "?", 2);
		ctx.addNagButton(nagRow, "!!", 3);
		ctx.addNagButton(nagRow, "??", 4);
		ctx.addNagButton(nagRow, "!?", 5);
		ctx.addNagButton(nagRow, "?!", 6);
		body.add(nagRow);
		body.add(Box.createVerticalStrut(8));

		JTextArea commentArea = new JTextArea(8, 26);
		commentArea.setLineWrap(true);
		commentArea.setWrapStyleWord(true);
		commentArea.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				onCommentFocusLost.run();
			}
		});
		ctx.registerTextArea(commentArea);

		JScrollPane scroll = new JScrollPane(commentArea);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		ctx.registerScrollPane(scroll);
		body.add(scroll);
		body.add(Box.createVerticalStrut(8));

		JPanel actionRow = new JPanel(new GridLayout(1, 0, 8, 8));
		actionRow.setOpaque(false);
		JButton saveButton = ctx.createThemedButton("Apply", saveAction);
		JButton clearCommentButton = ctx.createThemedButton("Clear Comment", clearCommentAction);
		JButton clearNagButton = ctx.createThemedButton("Clear NAG", clearNagAction);
		actionRow.add(saveButton);
		actionRow.add(clearCommentButton);
		actionRow.add(clearNagButton);
		ctx.registerButton(saveButton);
		ctx.registerButton(clearCommentButton);
		ctx.registerButton(clearNagButton);
		body.add(actionRow);

		card.setContent(body);
		return new Result(card, moveLabel, commentArea, saveButton, clearCommentButton, clearNagButton);
	}
}
