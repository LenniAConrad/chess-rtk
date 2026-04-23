package application.gui.history.input;

import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;


/**
 * Shared keyboard and drag/drop wiring for history navigation.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryInputSupport {

	/**
	 * HistoryInputSupport method.
	 */
	private HistoryInputSupport() {
	}

	/**
	 * NavigationActions interface.
	 *
	 * Provides interface behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public interface NavigationActions {
		/**
		 * isTextFocus method.
		 *
		 * @return return value.
		 */
		boolean isTextFocus();

		/**
		 * navigatePrev method.
		 */
		void navigatePrev();

		/**
		 * navigateNext method.
		 */
		void navigateNext();

		/**
		 * navigateStart method.
		 */
		void navigateStart();

		/**
		 * navigateEnd method.
		 */
		void navigateEnd();

		/**
		 * navigateUp method.
		 */
		void navigateUp();

		/**
		 * navigateDown method.
		 */
		void navigateDown();

		/**
		 * playEngineBest method.
		 */
		void playEngineBest();

		/**
		 * undoMove method.
		 */
		void undoMove();

		/**
		 * flipBoard method.
		 */
		void flipBoard();

		/**
		 * clearAnnotations method.
		 */
		void clearAnnotations();

		/**
		 * toggleLegalHints method.
		 */
		void toggleLegalHints();

		/**
		 * toggleCoords method.
		 */
		void toggleCoords();

		/**
		 * openBoardEditor method.
		 */
		void openBoardEditor();

		/**
		 * toggleEngineAnalysis method.
		 */
		void toggleEngineAnalysis();

		/**
		 * openCommandPalette method.
		 */
		void openCommandPalette();

		/**
		 * copyFen method.
		 */
		void copyFen();

		/**
		 * copyBoardImage method.
		 */
		void copyBoardImage();

		/**
		 * saveBoardImage method.
		 */
		void saveBoardImage();

		/**
		 * toggleSidebar method.
		 */
		void toggleSidebar();

		/**
		 * togglePanel method.
		 */
		void togglePanel();

		/**
		 * showProblems method.
		 */
		void showProblems();

		/**
		 * showOutput method.
		 */
		void showOutput();

		/**
		 * toggleFocusMode method.
		 */
		void toggleFocusMode();

		/**
		 * focusExit method.
		 */
		void focusExit();
	}

	/**
	 * DropHandler interface.
	 *
	 * Provides interface behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public interface DropHandler {
		/**
		 * NavigationOutcome enum.
		 *
		 * Provides enum behavior for the GUI module.
		 *
		 * @since 2026
		 * @author Lennart A. Conrad
		 */
		enum NavigationOutcome {
			/**
			 * FILES enum constant.
			 */
			FILES,
			/**
			 * TEXT enum constant.
			 */
			TEXT,
			/**
			 * NONE enum constant.
			 */
			NONE
		}

		/**
		 * canHandleFiles method.
		 *
		 * @param files parameter.
		 * @return return value.
		 */
		boolean canHandleFiles(List<File> files);

		/**
		 * handleFiles method.
		 *
		 * @param files parameter.
		 * @return return value.
		 */
		boolean handleFiles(List<File> files);

		/**
		 * canHandleText method.
		 *
		 * @param text parameter.
		 * @return return value.
		 */
		boolean canHandleText(String text);

		/**
		 * handleText method.
		 *
		 * @param text parameter.
		 * @return return value.
		 */
		boolean handleText(String text);
	}

	/**
	 * installNavigationBindings method.
	 *
	 * @param target parameter.
	 * @param actions parameter.
	 */
	public static void installNavigationBindings(JComponent target, NavigationActions actions) {
		InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = target.getActionMap();
		bindNav(im, am, "nav-prev", KeyEvent.VK_LEFT, 0, actions::navigatePrev, actions);
		bindNav(im, am, "nav-next", KeyEvent.VK_RIGHT, 0, actions::navigateNext, actions);
		bindNav(im, am, "nav-start", KeyEvent.VK_HOME, 0, actions::navigateStart, actions);
		bindNav(im, am, "nav-end", KeyEvent.VK_END, 0, actions::navigateEnd, actions);
		bindNav(im, am, "nav-up", KeyEvent.VK_UP, 0, actions::navigateUp, actions);
		bindNav(im, am, "nav-down", KeyEvent.VK_DOWN, 0, actions::navigateDown, actions);
		bindNav(im, am, "nav-best", KeyEvent.VK_SPACE, 0, actions::playEngineBest, actions);
		bindNav(im, am, "nav-undo", KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, actions::undoMove, actions);
		bindNav(im, am, "nav-flip", KeyEvent.VK_F, 0, actions::flipBoard, actions);
		bindNav(im, am, "nav-clear-annotations", KeyEvent.VK_X, 0, actions::clearAnnotations, actions);
		bindNav(im, am, "nav-legal", KeyEvent.VK_L, 0, actions::toggleLegalHints, actions);
		bindNav(im, am, "nav-coords", KeyEvent.VK_C, 0, actions::toggleCoords, actions);
		bindNav(im, am, "nav-editor", KeyEvent.VK_E, 0, actions::openBoardEditor, actions);
		bindNav(im, am, "nav-engine", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, actions::toggleEngineAnalysis,
				actions);
		bindNavAlways(im, am, "nav-palette", KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK,
				actions::openCommandPalette);
		bindNavAlways(im, am, "nav-palette-shift", KeyEvent.VK_P,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::openCommandPalette);
		bindNav(im, am, "nav-copy-fen", KeyEvent.VK_C,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::copyFen, actions);
		bindNav(im, am, "nav-copy-image", KeyEvent.VK_I,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::copyBoardImage, actions);
		bindNav(im, am, "nav-save-image", KeyEvent.VK_S,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::saveBoardImage, actions);
		bindNav(im, am, "nav-sidebar", KeyEvent.VK_B,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::toggleSidebar, actions);
		bindNav(im, am, "nav-sidebar-simple", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
				actions::toggleSidebar, actions);
		bindNavAlways(im, am, "nav-panel", KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK, actions::togglePanel);
		bindNavAlways(im, am, "nav-problems", KeyEvent.VK_M,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::showProblems);
		bindNavAlways(im, am, "nav-output", KeyEvent.VK_U,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, actions::showOutput);
		bindNav(im, am, "nav-focus", KeyEvent.VK_F11, 0, actions::toggleFocusMode, actions);
		bindNav(im, am, "nav-focus-exit", KeyEvent.VK_ESCAPE, 0, actions::focusExit, actions);
	}

	/**
	 * installGlobalNavigationDispatcher method.
	 *
	 * @param root parameter.
	 * @param actions parameter.
	 * @return return value.
	 */
	public static KeyEventDispatcher installGlobalNavigationDispatcher(JComponent root, NavigationActions actions) {
		KeyEventDispatcher dispatcher = event -> {
			if (event == null || event.getID() != KeyEvent.KEY_PRESSED) {
				return false;
			}
			if (root == null || !root.isShowing()) {
				return false;
			}
			if (actions.isTextFocus()) {
				return false;
			}
			switch (event.getKeyCode()) {
			case KeyEvent.VK_LEFT:
				actions.navigatePrev();
				return true;
			case KeyEvent.VK_RIGHT:
				actions.navigateNext();
				return true;
			case KeyEvent.VK_HOME:
				actions.navigateStart();
				return true;
			case KeyEvent.VK_END:
				actions.navigateEnd();
				return true;
			case KeyEvent.VK_UP:
				actions.navigateUp();
				return true;
			case KeyEvent.VK_DOWN:
				actions.navigateDown();
				return true;
			default:
				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
		return dispatcher;
	}

	/**
	 * uninstallGlobalNavigationDispatcher method.
	 *
	 * @param dispatcher parameter.
	 */
	public static void uninstallGlobalNavigationDispatcher(KeyEventDispatcher dispatcher) {
		if (dispatcher == null) {
			return;
		}
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
	}

	/**
	 * installDragAndDrop method.
	 *
	 * @param target parameter.
	 * @param handler parameter.
	 */
	public static void installDragAndDrop(JComponent target, DropHandler handler) {
		TransferHandler transferHandler = new TransferHandler() {
						/**
			 * Handles can import.
			 * @param support support value
			 * @return computed value
			 */
@Override
			public boolean canImport(javax.swing.TransferHandler.TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
						|| support.isDataFlavorSupported(DataFlavor.stringFlavor);
			}

						/**
			 * Handles import data.
			 * @param support support value
			 * @return computed value
			 */
@Override
			public boolean importData(javax.swing.TransferHandler.TransferSupport support) {
				if (!canImport(support)) {
					return false;
				}
				try {
					if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						@SuppressWarnings("unchecked")
						List<File> files = (List<File>) support.getTransferable()
								.getTransferData(DataFlavor.javaFileListFlavor);
						return handler != null && handler.handleFiles(files);
					}
					if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
						String text = (String) support.getTransferable()
								.getTransferData(DataFlavor.stringFlavor);
						return handler != null && handler.handleText(text);
					}
				} catch (Exception ex) {
					return false;
				}
				return false;
			}
		};
		target.setTransferHandler(transferHandler);
	}

	/**
	 * bindNav method.
	 *
	 * @param im parameter.
	 * @param am parameter.
	 * @param id parameter.
	 * @param keyCode parameter.
	 * @param modifiers parameter.
	 * @param action parameter.
	 * @param actions parameter.
	 */
	private static void bindNav(InputMap im, ActionMap am, String id, int keyCode, int modifiers, Runnable action,
			NavigationActions actions) {
		im.put(KeyStroke.getKeyStroke(keyCode, modifiers), id);
		am.put(id, new AbstractAction() {

						/**
			 * Handles action performed.
			 * @param e e value
			 */
@Override
			/**
			 * actionPerformed method.
			 *
			 * @param e parameter.
			 */
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (actions.isTextFocus()) {
					return;
				}
				action.run();
			}
		});
	}

	/**
	 * bindNavAlways method.
	 *
	 * @param im parameter.
	 * @param am parameter.
	 * @param id parameter.
	 * @param keyCode parameter.
	 * @param modifiers parameter.
	 * @param action parameter.
	 */
	private static void bindNavAlways(InputMap im, ActionMap am, String id, int keyCode, int modifiers,
			Runnable action) {
		im.put(KeyStroke.getKeyStroke(keyCode, modifiers), id);
		am.put(id, new AbstractAction() {

						/**
			 * Handles action performed.
			 * @param e e value
			 */
@Override
			/**
			 * actionPerformed method.
			 *
			 * @param e parameter.
			 */
			public void actionPerformed(java.awt.event.ActionEvent e) {
				action.run();
			}
		});
	}
}
