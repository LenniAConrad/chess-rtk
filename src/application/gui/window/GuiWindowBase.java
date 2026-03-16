package application.gui.window;

import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import application.gui.model.CommandSpec;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import application.gui.ui.ThemedScrollBarUI;
import application.gui.ui.ThemedSliderUI;
import application.gui.ui.ThemedTabbedPaneUI;
import application.gui.ui.ThemedToggleIcon;
import application.gui.util.TransferableImage;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.AlphaComposite;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTabbedPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.TransferHandler;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import chess.images.assets.Pictures;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * Shared GUI constants for the window stack.
 *
 * Centralizes fen defaults, promotion encoding helpers, and board-color/piece scaling constants so each derived window implementation stays consistent with the shared theme.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
class GuiWindowBase {

	/**
	 * Default starting FEN used when none is provided.
	 */
	protected static final String DEFAULT_FEN = Game.STANDARD_START_FEN;
	/**
	 * Promotion encodings matching PawnMove handlers.
	 */
	protected static final byte PROMOTION_QUEEN = 4;
	/**
	 * PROMOTION_ROOK field.
	 */
	protected static final byte PROMOTION_ROOK = 3;
	/**
	 * PROMOTION_BISHOP field.
	 */
	protected static final byte PROMOTION_BISHOP = 2;
	/**
	 * PROMOTION_KNIGHT field.
	 */
	protected static final byte PROMOTION_KNIGHT = 1;
	/**
	 * PROMOTION_NONE field.
	 */
	protected static final byte PROMOTION_NONE = 0;
	/**
	 * Base board colors that mimic lichess’s palette.
	 */
	protected static final Color LICHESS_LIGHT = new Color(238, 223, 199);
	/**
	 * Constructor.
	 */
	protected static final Color LICHESS_DARK = new Color(181, 140, 94);
	/**
	 * Uniform piece scaling used when drawing piece images.
	 */
	protected static final float PIECE_SCALE = 0.90f;

}
