package application.gui.window;
import application.Config;
import application.cli.Format;
import application.cli.command.CommandSupport;
import application.gui.GuiTheme;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import application.gui.model.CommandSpec;
import application.gui.model.EngineCacheEntry;
import application.gui.model.EngineUpdate;
import application.gui.model.HistoryEntry;
import application.gui.model.MovePair;
import application.gui.model.PaletteCommand;
import application.gui.model.PgnMainline;
import application.gui.model.PvEntry;
import application.gui.model.RecentCommand;
import application.gui.model.ReportEntry;
import application.gui.model.ReportUpdate;
import application.gui.model.TabLabel;
import application.gui.history.AblationSupport;
import application.gui.history.FigurineSanFormatter;
import application.gui.history.PvTextFormatter;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
import application.gui.ui.FocusBorder;
import application.gui.ui.ThemedScrollBarUI;
import application.gui.ui.ThemedSliderUI;
import application.gui.ui.ThemedSplitPaneUI;
import application.gui.ui.ThemedTabbedPaneUI;
import application.gui.ui.ThemedComboBoxUI;
import application.gui.ui.ThemedToggleIcon;
import application.gui.util.TransferableImage;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
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
import javax.swing.JTable;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.TransferHandler;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import javax.swing.border.Border;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.eval.Evaluator;
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
 * GuiWindowHistoryTheme class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
abstract class GuiWindowHistoryTheme extends GuiWindowHistoryCore {

            /**
             * updateCommandForm method.
             */
            protected void updateCommandForm() {
                if (commandSelect == null || commandFieldsPanel == null || commandBindings == null
                        || useFenToggle == null || commandFenPanel == null) {
                    return;
                }
                CommandSpec spec = commandSpecs.get((String) commandSelect.getSelectedItem());
                if (spec == null) {
                    commandFieldsPanel.removeAll();
                    commandBindings.clear();
                    commandFieldsPanel.revalidate();
                    commandFieldsPanel.repaint();
                    return;
                }
                if (!spec.supportsFen()) {
                    useFenToggle.setSelected(false);
                    useFenToggle.setEnabled(false);
                    commandFenPanel.setVisible(false);
                } else {
                    useFenToggle.setEnabled(true);
                    commandFenPanel.setVisible(true);
                }
                syncCommandFenField();
                CommandFieldSpec[] fieldSpecs = spec.fields().toArray(CommandFieldSpec[]::new);
                commandFormBuilder.render(commandFieldsPanel, commandBindings, fieldSpecs, field -> themedCheckbox(field.label(), false, null));
            }

			/**
			 * buildCommandField method.
			 * @param field parameter.
			 * @return return value.
			 */
			protected CommandFieldBinding buildCommandField(CommandFieldSpec field) {
				if (field.type() == CommandFieldType.FLAG) {
					JCheckBox box = themedCheckbox(field.label(), false, null);
					JPanel flagRow = new JPanel(new BorderLayout());
					flagRow.setOpaque(false);
					flagRow.add(box, BorderLayout.WEST);
					return new CommandFieldBinding(field, flagRow, box, null);
				}
				JTextField input = new JTextField();
				textFields.add(input);
				int columns = switch (field.type()) {
					case NUMBER -> 6;
					case PATH -> 18;
					case TEXT -> 12;
					case FLAG -> 8;
				};
				input.setColumns(columns);
				if (!field.placeholder().isEmpty()) {
					input.setToolTipText(field.placeholder());
				}
				Dimension pref = input.getPreferredSize();
				input.setMaximumSize(new Dimension(pref.width, pref.height));

				JPanel row = new JPanel();
				row.setOpaque(false);
				row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
				row.add(mutedLabel(field.label()));
				row.add(Box.createHorizontalStrut(8));
				row.add(input);
				row.add(Box.createHorizontalGlue());

				JPanel container = new JPanel();
				container.setOpaque(false);
				container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
				container.add(row);
				if (!field.placeholder().isEmpty()) {
					JLabel hint = mutedLabel(field.placeholder());
					container.add(hint);
				}
				return new CommandFieldBinding(field, container, null, input);
			}

			/**
			 * runCommandFromForm method.
			 */
			protected void runCommandFromForm() {
				String selected = (String) commandSelect.getSelectedItem();
				if (selected == null) {
					return;
				}
				CommandSpec spec = commandSpecs.get(selected);
				if (spec == null) {
					return;
				}
				List<String> args = new ArrayList<>();
				for (CommandFieldBinding binding : commandBindings) {
					CommandFieldSpec field = binding.spec();
					if (field.type() == CommandFieldType.FLAG) {
						if (binding.checkBox() != null && binding.checkBox().isSelected()) {
							args.add(field.flag());
						}
						continue;
					}
					if (binding.input() == null) {
						continue;
					}
					String value = binding.input().getText().trim();
					if (!value.isEmpty()) {
						args.add(field.flag());
						args.add(value);
					}
				}
				if (spec.supportsFen()) {
					if (useFenToggle.isSelected()) {
						args.add("--fen");
						args.add(position.toString());
					} else if (commandFenField != null) {
						String fenText = commandFenField.getText().trim();
						if (!fenText.isEmpty()) {
							args.add("--fen");
							args.add(fenText);
						} else {
							JOptionPane.showMessageDialog(frame, "Enter a FEN or enable \"Use current FEN\".",
									"Command", JOptionPane.WARNING_MESSAGE);
							return;
						}
					}
				}
				String extra = commandExtraArgsField.getText().trim();
				if (!extra.isEmpty()) {
					args.addAll(splitArgs(extra));
				}
				rememberRecentCommand(selected, args);
				runCliCommand(selected, args);
			}

			/**
			 * runHelp method.
			 */
			protected void runHelp() {
				String selected = (String) commandSelect.getSelectedItem();
				if (selected == null) {
					return;
				}
				runCliCommand("help", List.of(selected));
			}

			/**
			 * rememberRecentCommand method.
			 * @param command parameter.
			 * @param args parameter.
			 */
			protected void rememberRecentCommand(String command, List<String> args) {
				if (recentCommandModel == null || command == null) {
					return;
				}
				String label = command;
				if (args != null && !args.isEmpty()) {
					label = command + " " + String.join(" ", args);
				}
				for (int i = 0; i < recentCommandModel.getSize(); i++) {
					RecentCommand existing = recentCommandModel.getElementAt(i);
					if (existing != null && existing.label().equals(label)) {
						recentCommandModel.remove(i);
						break;
					}
				}
				recentCommandModel.add(0, new RecentCommand(label, command, args == null ? List.of() : List.copyOf(args)));
				while (recentCommandModel.getSize() > 8) {
					recentCommandModel.remove(recentCommandModel.getSize() - 1);
				}
			}

			/**
			 * runRecentCommand method.
			 * @param index parameter.
			 */
			protected void runRecentCommand(int index) {
				if (recentCommandModel == null || index < 0 || index >= recentCommandModel.getSize()) {
					return;
				}
				RecentCommand entry = recentCommandModel.getElementAt(index);
				if (entry == null) {
					return;
				}
				runCliCommand(entry.command(), entry.args());
			}

			/**
			 * themedCheckbox method.
			 * @param text parameter.
			 * @param selected parameter.
			 * @param action parameter.
			 * @return return value.
			 */
			protected JCheckBox themedCheckbox(String text, boolean selected, java.awt.event.ActionListener action) {
				JCheckBox box = new JCheckBox(text, selected);
				checks.add(box);
				box.setOpaque(false);
				if (action != null) {
					box.addActionListener(action);
				}
				return box;
			}

			/**
			 * toggleFenMode method.
			 */
			protected void toggleFenMode() {
				if (useFenToggle == null || commandFenField == null) {
					return;
				}
				if (useFenToggle.isSelected()) {
					commandFenCustom = commandFenField.getText().trim();
				}
				syncCommandFenField();
				if (!useFenToggle.isSelected() && !commandFenCustom.isBlank()) {
					commandFenField.setText(commandFenCustom);
				}
			}

			/**
			 * syncCommandFenField method.
			 */
			protected void syncCommandFenField() {
				if (commandFenField == null || useFenToggle == null) {
					return;
				}
				if (useFenToggle.isSelected()) {
					commandFenField.setText(position.toString());
					commandFenField.setEnabled(false);
				} else {
					commandFenField.setEnabled(true);
				}
			}

			/**
			 * scaleInt method.
			 * @param value parameter.
			 * @return return value.
			 */
			protected int scaleInt(int value) {
				return Math.max(1, Math.round(value * uiScale));
			}

			/**
			 * scaleDimension method.
			 * @param base parameter.
			 * @return return value.
			 */
			protected Dimension scaleDimension(Dimension base) {
				if (base == null) {
					return null;
				}
				return new Dimension(scaleInt(base.width), scaleInt(base.height));
			}

			/**
			 * applyScaledSizing method.
			 */
			protected void applyScaledSizing() {
				if (frame != null && baseFrameMin != null) {
					frame.setMinimumSize(scaleDimension(baseFrameMin));
				}
				if (boardPanel != null && baseBoardMin != null) {
					boardPanel.setMinimumSize(scaleDimension(baseBoardMin));
				}
				if (boardWrap != null && baseBoardWrapMin != null) {
					boardWrap.setMinimumSize(scaleDimension(baseBoardWrapMin));
				}
				if (leftPane != null && baseLeftMin != null) {
					leftPane.setMinimumSize(scaleDimension(baseLeftMin));
				}
				if (rightSidebarScroll != null) {
					if (baseSidebarMin != null) {
						rightSidebarScroll.setMinimumSize(scaleDimension(baseSidebarMin));
					}
					if (baseSidebarPref != null) {
						rightSidebarScroll.setPreferredSize(scaleDimension(baseSidebarPref));
					}
				}
				if (historyScroll != null && baseHistoryScrollPref != null) {
					historyScroll.setPreferredSize(scaleDimension(baseHistoryScrollPref));
				}
				if (underboardMoveScroll != null && baseUnderboardScrollPref != null) {
					underboardMoveScroll.setPreferredSize(scaleDimension(baseUnderboardScrollPref));
				}
				if (recentCommandScroll != null && baseRecentScrollPref != null) {
					recentCommandScroll.setPreferredSize(scaleDimension(baseRecentScrollPref));
				}
			}

			/**
			 * applyTheme method.
			 */
			protected void applyTheme() {
				GuiTheme base = lightMode ? GuiTheme.light() : GuiTheme.dark();
				this.theme = highContrast ? applyHighContrast(base) : base;
				root.setColors(theme.editor(), theme.editor());
				if (frame != null) {
					frame.getContentPane().setBackground(theme.editor());
				}
				if (mainSplit != null) {
					mainSplit.setBackground(theme.editor());
				}
				Border borderLine = new MatteBorder(0, 0, 0, 0, theme.border());
				Border emptyBorder = BorderFactory.createEmptyBorder();
				UIManager.put("TabbedPane.borderHightlightColor", theme.border());
				UIManager.put("TabbedPane.borderHighlightColor", theme.border());
				UIManager.put("TabbedPane.highlight", theme.sidebar());
				UIManager.put("TabbedPane.shadow", theme.border());
				UIManager.put("TabbedPane.darkShadow", theme.border());
				UIManager.put("TabbedPane.light", theme.border());
				UIManager.put("TabbedPane.selectHighlight", theme.border());
				UIManager.put("TabbedPane.focus", theme.border());
				UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
				UIManager.put("TabbedPane.contentAreaColor", theme.sidebar());
				UIManager.put("Panel.background", theme.editor());
				UIManager.put("ScrollPane.background", theme.editor());
				UIManager.put("ScrollPane.border", emptyBorder);
				UIManager.put("ScrollPane.viewportBorder", emptyBorder);
				UIManager.put("Viewport.border", emptyBorder);
				UIManager.put("SplitPane.border", emptyBorder);
				UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
				UIManager.put("TabbedPane.background", theme.sidebar());
				UIManager.put("Button.background", theme.surfaceAlt());
				UIManager.put("Label.background", theme.editor());
				UIManager.put("Table.background", theme.sidebar());
				UIManager.put("Table.scrollPaneBorder", emptyBorder);
				UIManager.put("TableHeader.background", theme.sidebar());
				UIManager.put("TableHeader.foreground", theme.textStrong());
				UIManager.put("TableHeader.cellBorder", new MatteBorder(0, 0, 1, 0, theme.border()));
				UIManager.put("Table.focusCellHighlightBorder", emptyBorder);
				UIManager.put("List.background", theme.sidebar());
				UIManager.put("List.foreground", theme.text());
				UIManager.put("Separator.foreground", theme.border());
				UIManager.put("Separator.background", theme.border());
				UIManager.put("ToolTip.background", theme.surfaceAlt());
				UIManager.put("ToolTip.foreground", theme.text());
				UIManager.put("ToolTip.border", BorderFactory.createLineBorder(theme.border()));
				Font tooltipFont = scaleFont(theme.bodyFont());
				if (tooltipFont != null) {
					tooltipFont = tooltipFont.deriveFont(Math.max(10f, tooltipFont.getSize2D() * 0.9f));
				}
				UIManager.put("ToolTip.font", tooltipFont);
				UIManager.put("TextField.selectionBackground", theme.selection());
				UIManager.put("TextArea.selectionBackground", theme.selection());
				UIManager.put("ComboBox.background", theme.surfaceAlt());
				UIManager.put("ComboBox.foreground", theme.text());
				UIManager.put("ComboBox.selectionBackground", theme.selection());
				UIManager.put("ComboBox.selectionForeground", theme.textStrong());
				UIManager.put("ComboBox.buttonBackground", theme.surfaceAlt());
				UIManager.put("ComboBox.buttonShadow", theme.border());
				UIManager.put("ComboBox.buttonDarkShadow", theme.border());
				UIManager.put("ComboBox.buttonHighlight", theme.surfaceAlt());
				UIManager.put("ComboBox.border", BorderFactory.createLineBorder(theme.border()));
				UIManager.put("control", theme.editor());
				UIManager.put("controlShadow", theme.border());
				UIManager.put("controlDarkShadow", theme.border());
				UIManager.put("controlHighlight", theme.editor());
				UIManager.put("controlLtHighlight", theme.editor());

				java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>();
				attrs.put(java.awt.font.TextAttribute.TRACKING, 0.08f);
				Font titleFont = scaleFont(theme.titleFont()).deriveFont(attrs);
				Font bodyFont = scaleFont(theme.bodyFont());
				Font monoFont = scaleFont(theme.monoFont());
				float padScale = uiScale * (compactMode ? 0.85f : 1.0f);
				int rootPad = Math.max(8, Math.round(12 * padScale));
				root.setBorder(new EmptyBorder(rootPad, rootPad, rootPad, rootPad));
				applyScaledSizing();
				int buttonPadY = Math.max(5, Math.round(8 * padScale));
				int buttonPadX = Math.max(8, Math.round(16 * padScale));
				int fieldPadY = Math.max(3, Math.round(6 * padScale));
				int fieldPadX = Math.max(5, Math.round(8 * padScale));
				int areaPad = Math.max(5, Math.round(8 * padScale));
				int checkPad = Math.max(2, Math.round(3 * padScale));

				for (RoundedPanel card : cards) {
					card.setTheme(theme.surface(), null);
					card.setShadow(new Color(0, 0, 0, 0), 0);
				}
				for (RoundedPanel card : flatCards) {
					card.setTheme(theme.sidebar(), null);
					card.setShadow(new Color(0, 0, 0, 0), 0);
				}
				if (boardCard != null) {
					boardCard.setTheme(theme.editor(), null);
					boardCard.setShadow(new Color(0, 0, 0, 0), 0);
				}

				for (JButton button : buttons) {
					button.setFont(bodyFont);
					button.setBackground(theme.surfaceAlt());
					button.setForeground(theme.text());
					button.setBorder(focusBorder(buttonPadY, buttonPadX, buttonPadY, buttonPadX));
					button.setOpaque(false);
					button.setContentAreaFilled(false);
					button.setBorderPainted(false);
					button.setFocusPainted(true);
				}
				for (JButton button : iconButtons) {
					Font iconFont = bodyFont.deriveFont(Font.BOLD, Math.max(12f, bodyFont.getSize2D() * 1.02f));
					int iconPad = Math.max(5, Math.round(7 * padScale));
					button.setFont(iconFont);
					button.setBackground(theme.surfaceAlt());
					button.setForeground(theme.text());
					button.setBorder(focusBorder(iconPad, iconPad, iconPad, iconPad));
					button.setOpaque(true);
					button.setContentAreaFilled(true);
					button.setBorderPainted(false);
					button.setFocusPainted(false);
					int size = Math.round(38 * padScale);
					button.setPreferredSize(new Dimension(size, size));
				}
				for (JButton button : activityButtons) {
					Font iconFont = bodyFont.deriveFont(Font.BOLD, Math.max(18f, bodyFont.getSize2D() * 1.45f));
					int pad = 0;
					button.setFont(iconFont);
					button.setBackground(theme.activityBar());
					button.setForeground(theme.textMuted());
					button.setBorder(new EmptyBorder(pad, pad, pad, pad));
					button.setOpaque(true);
					button.setBorderPainted(false);
					button.setFocusPainted(false);
					int size = Math.round(44 * padScale);
					Dimension dim = new Dimension(size, size);
					button.setPreferredSize(dim);
					button.setMaximumSize(dim);
					button.setMinimumSize(dim);
				}

				for (JCheckBox check : checks) {
					check.setFont(bodyFont);
					check.setForeground(theme.text());
					check.setIcon(new ThemedToggleIcon(theme, false));
					check.setSelectedIcon(new ThemedToggleIcon(theme, true));
					check.setIconTextGap(10);
					check.setBorder(focusBorder(checkPad, checkPad, checkPad, checkPad));
					check.setFocusPainted(true);
				}

				for (JLabel label : strongLabels) {
					label.setFont(titleFont);
					label.setForeground(theme.textStrong());
				}

				for (JLabel label : mutedLabels) {
					label.setFont(bodyFont);
					label.setForeground(theme.textMuted());
				}

				for (JTextField field : textFields) {
					field.setFont(bodyFont);
					field.setBackground(theme.surfaceAlt());
					field.setForeground(theme.text());
					field.setCaretColor(theme.textStrong());
					field.setBorder(focusBorder(fieldPadY, fieldPadX, fieldPadY, fieldPadX));
				}

				for (JTextArea area : textAreas) {
					if (area == commandOutput || area == engineBestArea || area == pgnEditorArea || area == pgnViewerArea
							|| area == panelOutputArea || area == panelTerminalArea || area == panelDebugArea) {
						Font mono = area == engineBestArea ? figurineDisplayFont(monoFont) : monoFont;
						area.setFont(mono);
					} else {
						area.setFont(bodyFont);
					}
					Color areaBg = (area == commandOutput || area == panelOutputArea
							|| area == panelTerminalArea || area == panelDebugArea) ? theme.sidebar()
							: theme.surfaceAlt();
					area.setBackground(areaBg);
					area.setForeground(theme.text());
					area.setCaretColor(theme.textStrong());
					area.setBorder(new EmptyBorder(areaPad, areaPad, areaPad, areaPad));
				}

				for (JList<?> list : lists) {
					list.setFont(bodyFont);
					list.setBackground(theme.sidebar());
					list.setForeground(theme.text());
					list.setSelectionBackground(theme.selection());
					list.setSelectionForeground(theme.textStrong());
					installListHover(list);
					if (list.getCellRenderer() instanceof javax.swing.DefaultListCellRenderer) {
						list.setCellRenderer(new application.gui.render.ThemedListCellRenderer((GuiWindowHistory) this));
					}
				}
				for (JTable table : tables) {
					table.setFont(bodyFont);
					table.setBackground(theme.sidebar());
					table.setForeground(theme.text());
					table.setSelectionBackground(theme.selection());
					table.setSelectionForeground(theme.textStrong());
					table.setGridColor(theme.border());
					table.setRowHeight(Math.max(18, Math.round(22 * uiScale)));
					table.setShowGrid(false);
					table.setBorder(BorderFactory.createEmptyBorder());
					installTableHover(table);
					table.setDefaultRenderer(Object.class, new application.gui.render.ThemedTableCellRenderer((GuiWindowHistory) this));
					if (table.getTableHeader() != null) {
						JTableHeader header = table.getTableHeader();
						header.setFont(bodyFont);
						header.setBackground(theme.sidebar());
						header.setForeground(theme.textStrong());
						header.setOpaque(true);
						header.setBorder(new MatteBorder(0, 0, 1, 0, theme.border()));
					}
				}
				if (historyList != null) {
					historyList.setFixedCellHeight(Math.round(22 * uiScale));
				}
				if (underboardMoveList != null) {
					underboardMoveList.setFixedCellHeight(Math.round(24 * uiScale));
				}
				if (reportList != null) {
					reportList.setFixedCellHeight(Math.round(22 * uiScale));
				}

				for (JComboBox<?> combo : combos) {
					combo.setFont(bodyFont);
					combo.setUI(new ThemedComboBoxUI(theme));
					combo.setBackground(theme.surfaceAlt());
					combo.setForeground(theme.text());
					combo.setBorder(BorderFactory.createLineBorder(theme.border()));
					combo.setOpaque(true);
				}

				for (JScrollPane scroll : scrolls) {
					scroll.getViewport().setBackground(theme.sidebar());
					scroll.getViewport().setOpaque(true);
					scroll.setBackground(theme.sidebar());
					scroll.setOpaque(false);
					scroll.setBorder(BorderFactory.createEmptyBorder());
					JScrollBar vBar = scroll.getVerticalScrollBar();
					JScrollBar hBar = scroll.getHorizontalScrollBar();
					int thickness = Math.max(8, Math.round(10 * padScale));
					if (vBar != null) {
						vBar.setBackground(theme.surfaceAlt());
						vBar.setOpaque(false);
						vBar.setUI(new ThemedScrollBarUI(theme, thickness));
						vBar.setPreferredSize(new Dimension(thickness, Integer.MAX_VALUE));
						vBar.setUnitIncrement(16);
					}
					if (hBar != null) {
						hBar.setBackground(theme.surfaceAlt());
						hBar.setOpaque(false);
						hBar.setUI(new ThemedScrollBarUI(theme, thickness));
						hBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, thickness));
						hBar.setUnitIncrement(16);
					}
				}

				for (JSlider slider : sliders) {
					slider.setOpaque(false);
					slider.setForeground(theme.accent());
					slider.setBackground(new Color(0, 0, 0, 0));
					slider.setFocusable(false);
					slider.setUI(new ThemedSliderUI(slider, theme));
				}

				for (JTabbedPane tab : tabs) {
					tab.setFont(bodyFont);
					boolean isEditor = Boolean.TRUE.equals(tab.getClientProperty("editorTabs"));
					tab.setBackground(isEditor ? theme.editor() : theme.sidebar());
					tab.setForeground(theme.text());
					tab.setOpaque(true);
					tab.setFocusable(false);
					tab.setBorder(BorderFactory.createEmptyBorder());
					tab.setUI(new ThemedTabbedPaneUI(theme));
				}

				for (JComponent line : separators) {
					line.setBackground(theme.border());
				}
				if (activityBar != null) {
					activityBar.setBackground(theme.activityBar());
					activityBar.setOpaque(true);
					activityBar.setBorder(new MatteBorder(0, 0, 0, 1, theme.border()));
				}
				if (sidebarHeader != null) {
					sidebarHeader.setBackground(theme.sidebar());
					sidebarHeader.setOpaque(true);
					Border outer = new MatteBorder(0, 0, 1, 0, theme.border());
					Border inner = new EmptyBorder(Math.max(4, Math.round(6 * padScale)),
							Math.max(8, Math.round(12 * padScale)),
							Math.max(4, Math.round(6 * padScale)),
							Math.max(8, Math.round(12 * padScale)));
					sidebarHeader.setBorder(BorderFactory.createCompoundBorder(outer, inner));
				}
				if (statusBar != null) {
					statusBar.setBackground(theme.statusBar());
					statusBar.setOpaque(true);
					statusBar.setBorder(new MatteBorder(1, 0, 0, 0, theme.border()));
				}
				retintLightBorders(root);
				updateTabLabelStyles();
				if (analysisSplit != null) {
					analysisSplit.setDividerSize(Math.max(4, Math.round(6 * padScale)));
					themeSplitPane(analysisSplit);
				}
				if (mainSplit != null && sidebarVisible && !focusMode) {
					mainSplit.setDividerSize(Math.max(6, Math.round(8 * padScale)));
					themeSplitPane(mainSplit);
				}

				if (editorDialog != null) {
					editorDialog.applyDialogTheme();
				}
				if (boardEditorPane != null) {
					boardEditorPane.applyPaneTheme();
				}
				if (pgnDialog != null) {
					pgnDialog.applyDialogTheme();
				}
				if (engineManagerDialog != null) {
					engineManagerDialog.applyDialogTheme();
				}
				if (commandPaletteDialog != null) {
					commandPaletteDialog.applyDialogTheme();
				}
				if (engineStatusLabel != null) {
					setEngineStatus(engineStatusLabel.getText());
				}

				frame.repaint();
			}

			/**
			 * scheduleUiScaleApply method.
			 */
			protected void scheduleUiScaleApply() {
				if (frame == null || uiScaleApplyPending) {
					return;
				}
				uiScaleApplyPending = true;
				SwingUtilities.invokeLater(() -> {
					uiScaleApplyPending = false;
					applyTheme();
					frame.revalidate();
				});
			}

			/**
			 * scaleFont method.
			 * @param base parameter.
			 * @return return value.
			 */
			public Font scaleFont(Font base) {
				if (base == null) {
					return null;
				}
				return base.deriveFont(base.getSize2D() * uiScale);
			}

			/**
			 * getTheme method.
			 * @return return value.
			 */
			public GuiTheme getTheme() {
				return theme;
			}

			/**
			 * getUiScale method.
			 * @return return value.
			 */
			public float getUiScale() {
				return uiScale;
			}

			/**
			 * getAnimationMillis method.
			 * @return return value.
			 */
			public int getAnimationMillis() {
				return Math.max(0, animationMillis);
			}

			/**
			 * getPvWrapTokens method.
			 * @return return value.
			 */
			public int getPvWrapTokens() {
				return 10;
			}

			/**
			 * getPvWrapMode method.
			 * @return return value.
			 */
			public String getPvWrapMode() {
				if (pvWrapModeBox == null || pvWrapModeBox.getSelectedItem() == null) {
					return "Auto Word Wrap";
				}
				return pvWrapModeBox.getSelectedItem().toString();
			}

			/**
			 * isPvAutoWrapMode method.
			 * @return return value.
			 */
			public boolean isPvAutoWrapMode() {
				String mode = getPvWrapMode();
				return mode.startsWith("Auto Word") || mode.startsWith("Auto Char");
			}

			/**
			 * isPvWordWrapMode method.
			 * @return return value.
			 */
			public boolean isPvWordWrapMode() {
				return getPvWrapMode().startsWith("Auto Word");
			}

			/**
			 * formatPvTextForMode method.
			 * @param moves parameter.
			 * @param wrapWidthPx parameter.
			 * @param fm parameter.
			 * @return return value.
			 */
			public String formatPvTextForMode(String moves, int wrapWidthPx, java.awt.FontMetrics fm) {
				return PvTextFormatter.formatForMode(moves, getPvWrapMode(), getPvWrapTokens(), wrapWidthPx, fm);
			}

			/**
			 * normalizePvText method.
			 * @param moves parameter.
			 * @return return value.
			 */
			public String normalizePvText(String moves) {
				return PvTextFormatter.normalize(moves);
			}

			/**
			 * computePvMovesWidth method.
			 * @param list parameter.
			 * @param entry parameter.
			 * @return return value.
			 */
			public int computePvMovesWidth(JList<? extends PvEntry> list, PvEntry entry) {
				if (list == null) {
					return 80;
				}
				Font base = scaleFont(theme.bodyFont());
				Font chipFont = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.85f));
				java.awt.FontMetrics chipFm = list.getFontMetrics(chipFont);
				String eval = (entry == null || entry.eval() == null || entry.eval().isBlank()) ? "—" : entry.eval();
				String depth = "d " + (entry == null || entry.depth() == null ? "" : entry.depth());
				int chipWidth = chipFm.stringWidth(eval) + chipFm.stringWidth(depth) + 10;
				chipWidth += 6; // chip row gap
				int indexSize = Math.round(26 * getUiScale());
				int leftPad = 6;
				int rightPad = 6;
				int hgap = 8;
				int width = list.getWidth() - leftPad - rightPad - indexSize - chipWidth - (hgap * 2);
				return Math.max(80, width);
			}

			/**
			 * collapsePvText method.
			 * @param moves parameter.
			 * @param maxWidthPx parameter.
			 * @param fm parameter.
			 * @return return value.
			 */
			public String collapsePvText(String moves, int maxWidthPx, java.awt.FontMetrics fm) {
				return PvTextFormatter.collapse(moves, maxWidthPx, fm);
			}

			/**
			 * canExpandPvEntry method.
			 * @param entry parameter.
			 * @param maxWidthPx parameter.
			 * @param fm parameter.
			 * @return return value.
			 */
			public boolean canExpandPvEntry(PvEntry entry, int maxWidthPx, java.awt.FontMetrics fm) {
				if (entry == null) {
					return false;
				}
				return PvTextFormatter.canExpand(entry.moves(), maxWidthPx, fm);
			}

			/**
			 * isPvExpanded method.
			 * @param entry parameter.
			 * @return return value.
			 */
			public boolean isPvExpanded(PvEntry entry) {
				return entry != null && expandedPvRows.contains(entry.pv());
			}

			/**
			 * pvTotalPlies method.
			 * @param entry parameter.
			 * @return return value.
			 */
			public int pvTotalPlies(PvEntry entry) {
				if (entry == null) {
					return 1;
				}
				return PvTextFormatter.totalPlies(entry.moves());
			}

			/**
			 * togglePvExpanded method.
			 * @param entry parameter.
			 */
			protected void togglePvExpanded(PvEntry entry) {
				if (entry == null) {
					return;
				}
				if (expandedPvRows.contains(entry.pv())) {
					expandedPvRows.remove(entry.pv());
				} else {
					expandedPvRows.add(entry.pv());
				}
			}

			/**
			 * clearExpandedPvRows method.
			 */
			protected void clearExpandedPvRows() {
				expandedPvRows.clear();
			}

			/**
			 * retainExpandedPvRows method.
			 * @param entries parameter.
			 */
			protected void retainExpandedPvRows(List<PvEntry> entries) {
				if (entries == null || entries.isEmpty()) {
					expandedPvRows.clear();
					return;
				}
				Set<Integer> available = new HashSet<>();
				for (PvEntry entry : entries) {
					if (entry != null) {
						available.add(entry.pv());
					}
				}
				expandedPvRows.retainAll(available);
			}

			/**
			 * isPvExpandToggleHit method.
			 * @param list parameter.
			 * @param entry parameter.
			 * @param cellBounds parameter.
			 * @param point parameter.
			 * @return return value.
			 */
			public boolean isPvExpandToggleHit(JList<? extends PvEntry> list, PvEntry entry, Rectangle cellBounds, Point point) {
				if (list == null || entry == null || cellBounds == null || point == null) {
					return false;
				}
				int toggleLeft = cellBounds.x + 6;
				int toggleRight = toggleLeft + Math.round(16 * getUiScale());
				if (point.x < toggleLeft || point.x > toggleRight || point.y < cellBounds.y
						|| point.y > (cellBounds.y + cellBounds.height)) {
					return false;
				}
				Font pvFont = figurineDisplayFont(scaleFont(theme.bodyFont()));
				java.awt.FontMetrics fm = list.getFontMetrics(pvFont);
				return canExpandPvEntry(entry, computePvMovesWidth(list, entry), fm);
			}

			/**
			 * isLightMode method.
			 * @return return value.
			 */
			public boolean isLightMode() {
				return lightMode;
			}

			/**
			 * isFigurineSanEnabled method.
			 * @return return value.
			 */
			public boolean isFigurineSanEnabled() {
				return figurineSan;
			}

			/**
			 * figurineDisplayFont method.
			 * @param base parameter.
			 * @return return value.
			 */
			public Font figurineDisplayFont(Font base) {
				return FigurineSanFormatter.displayFont(base, figurineSan);
			}

			/**
			 * displaySan method.
			 * @param san parameter.
			 * @return return value.
			 */
			public String displaySan(String san) {
				return FigurineSanFormatter.displaySan(san, figurineSan);
			}

			/**
			 * displaySanLine method.
			 * @param text parameter.
			 * @return return value.
			 */
			public String displaySanLine(String text) {
				return FigurineSanFormatter.displaySanLine(text, figurineSan);
			}

			/**
			 * getMoveHistory method.
			 * @return return value.
			 */
			public List<String> getMoveHistory() {
				return moveHistory;
			}

			/**
			 * applyHighContrast method.
			 * @param base parameter.
			 * @return return value.
			 */
			protected GuiTheme applyHighContrast(GuiTheme base) {
				Color bgTop = lightMode ? Color.WHITE : new Color(18, 18, 18);
				Color bgBottom = lightMode ? Color.WHITE : new Color(18, 18, 18);
				Color surface = lightMode ? Color.WHITE : new Color(28, 28, 28);
				Color surfaceAlt = lightMode ? new Color(245, 245, 245) : new Color(38, 38, 38);
				Color border = lightMode ? new Color(20, 20, 20) : new Color(220, 220, 220);
				Color shadow = new Color(0, 0, 0, lightMode ? 24 : 90);
				Color text = lightMode ? new Color(10, 10, 10) : new Color(245, 245, 245);
				Color textMuted = lightMode ? new Color(40, 40, 40) : new Color(220, 220, 220);
				Color textStrong = text;
				Color accent = lightMode ? new Color(0, 92, 204) : new Color(102, 176, 255);
				Color accentText = Color.WHITE;
				Color editor = bgTop;
				Color sidebar = surface;
				Color activity = surfaceAlt;
				Color status = surface;
				Color selection = lightMode ? new Color(200, 220, 255) : new Color(60, 80, 120);
				Color hover = lightMode ? new Color(230, 230, 230) : new Color(45, 45, 45);
				return new GuiTheme(bgTop, bgBottom, surface, surfaceAlt, border, shadow, text, textMuted, textStrong,
						accent, accentText, editor, sidebar, activity, status, selection, hover, base.titleFont(),
						base.bodyFont(), base.monoFont());
			}

			/**
			 * focusBorder method.
			 * @param top parameter.
			 * @param left parameter.
			 * @param bottom parameter.
			 * @param right parameter.
			 * @return return value.
			 */
			protected Border focusBorder(int top, int left, int bottom, int right) {
				return new FocusBorder(new EmptyBorder(top, left, bottom, right), theme.accent());
			}

			/**
			 * retintLightBorders method.
			 * @param component parameter.
			 */
			protected void retintLightBorders(Component component) {
				if (component == null) {
					return;
				}
				if (component instanceof JComponent comp) {
					Border border = comp.getBorder();
					Border retinted = retintBorder(border);
					if (retinted != border) {
						comp.setBorder(retinted);
					}
				}
				if (component instanceof Container container) {
					for (Component child : container.getComponents()) {
						retintLightBorders(child);
					}
				}
			}

			/**
			 * retintBorder method.
			 * @param border parameter.
			 * @return return value.
			 */
			private Border retintBorder(Border border) {
				if (border == null || border instanceof FocusBorder) {
					return border;
				}
				if (border instanceof CompoundBorder compound) {
					Border outer = retintBorder(compound.getOutsideBorder());
					Border inner = retintBorder(compound.getInsideBorder());
					if (outer != compound.getOutsideBorder() || inner != compound.getInsideBorder()) {
						return new CompoundBorder(outer, inner);
					}
					return border;
				}
				if (border instanceof TitledBorder titled) {
					Border inner = retintBorder(titled.getBorder());
					if (inner != titled.getBorder()) {
						return new TitledBorder(inner, titled.getTitle(), titled.getTitleJustification(),
								titled.getTitlePosition(), titled.getTitleFont(), titled.getTitleColor());
					}
					return border;
				}
				if (border instanceof MatteBorder matte) {
					Insets insets = matte.getBorderInsets(null);
					return new MatteBorder(insets.top, insets.left, insets.bottom, insets.right, theme.border());
				}
				if (border instanceof LineBorder line) {
					if (isNearWhite(line.getLineColor())) {
						return new LineBorder(theme.border(), line.getThickness(), line.getRoundedCorners());
					}
					return border;
				}
				if (border instanceof EtchedBorder || border instanceof BevelBorder) {
					return new MatteBorder(1, 1, 1, 1, theme.border());
				}
				return border;
			}

			/**
			 * isNearWhite method.
			 * @param color parameter.
			 * @return return value.
			 */
			private boolean isNearWhite(Color color) {
				if (color == null) {
					return false;
				}
				int r = color.getRed();
				int g = color.getGreen();
				int b = color.getBlue();
				return r >= 200 && g >= 200 && b >= 200;
			}

			/**
			 * blend method.
			 * @param base parameter.
			 * @param overlay parameter.
			 * @param amount parameter.
			 * @return return value.
			 */
			public Color blend(Color base, Color overlay, float amount) {
				float t = Math.max(0f, Math.min(1f, amount));
				int r = Math.round(base.getRed() + (overlay.getRed() - base.getRed()) * t);
				int g = Math.round(base.getGreen() + (overlay.getGreen() - base.getGreen()) * t);
				int b = Math.round(base.getBlue() + (overlay.getBlue() - base.getBlue()) * t);
				return new Color(r, g, b);
			}

			/**
			 * loadGuiState method.
			 * @param defaultLightMode parameter.
			 * @param defaultWhiteDown parameter.
			 * @return return value.
			 */
			protected GuiState loadGuiState(boolean defaultLightMode, boolean defaultWhiteDown) {
				GuiState state = GuiState.defaults(defaultLightMode, defaultWhiteDown);
				Path path = guiStatePath();
				if (!Files.exists(path)) {
					return state;
				}
				Properties props = new Properties();
				try (java.io.InputStream in = Files.newInputStream(path)) {
					props.load(in);
				} catch (IOException ignored) {
					return state;
				}
				state.lightMode = readBool(props, "lightMode", state.lightMode);
				state.whiteDown = readBool(props, "whiteDown", state.whiteDown);
				state.showLegal = readBool(props, "showLegal", state.showLegal);
				state.showMoves = readBool(props, "showMoves", state.showMoves);
				state.showTags = readBool(props, "showTags", state.showTags);
				state.showBestMove = readBool(props, "showBestMove", state.showBestMove);
				state.showCoords = readBool(props, "showCoords", state.showCoords);
				state.hoverLegal = readBool(props, "hoverLegal", state.hoverLegal);
				state.hoverHighlight = readBool(props, "hoverHighlight", state.hoverHighlight);
				state.hoverOnlyLegal = readBool(props, "hoverOnlyLegal", state.hoverOnlyLegal);
				state.highContrast = readBool(props, "highContrast", state.highContrast);
				state.compactMode = readBool(props, "compactMode", state.compactMode);
				state.figurineSan = readBool(props, "figurineSan", state.figurineSan);
				if (!state.hoverLegal) {
					state.hoverOnlyLegal = false;
				}
				state.boardHueDegrees = readInt(props, "boardHue", state.boardHueDegrees);
				state.boardBrightness = readInt(props, "boardBrightness", state.boardBrightness);
				state.boardSaturation = readInt(props, "boardSaturation", state.boardSaturation);
				state.animationMillis = readInt(props, "animationMillis", state.animationMillis);
				state.boardBrightness = Math.max(40, Math.min(200, state.boardBrightness));
				state.boardSaturation = Math.max(40, Math.min(200, state.boardSaturation));
				state.animationMillis = Math.max(0, Math.min(1000, state.animationMillis));
				state.splitDivider = readInt(props, "splitDivider", state.splitDivider);
				state.analysisDivider = readInt(props, "analysisDivider", state.analysisDivider);
				state.sidebarVisible = readBool(props, "sidebarVisible", state.sidebarVisible);
				state.windowWidth = readInt(props, "windowWidth", state.windowWidth);
				state.windowHeight = readInt(props, "windowHeight", state.windowHeight);
				state.rightTabIndex = readInt(props, "rightTab", state.rightTabIndex);
				state.uiScale = 1.0f;
				return state;
			}

			/**
			 * captureGuiState method.
			 */
			protected void captureGuiState() {
				if (guiState == null || frame == null) {
					return;
				}
				guiState.lightMode = lightMode;
				guiState.whiteDown = whiteDown;
				guiState.boardHueDegrees = boardHueDegrees;
				guiState.boardBrightness = boardBrightness;
				guiState.boardSaturation = boardSaturation;
				guiState.animationMillis = animationMillis;
				guiState.showCoords = showCoords;
				guiState.hoverLegal = hoverLegal;
				guiState.hoverHighlight = hoverHighlight;
				guiState.hoverOnlyLegal = hoverOnlyLegal;
				guiState.highContrast = highContrast;
				guiState.compactMode = compactMode;
				guiState.figurineSan = figurineSan;
				if (legalToggle != null) {
					guiState.showLegal = legalToggle.isSelected();
				}
				if (movesToggle != null) {
					guiState.showMoves = movesToggle.isSelected();
				}
				if (tagsToggle != null) {
					guiState.showTags = tagsToggle.isSelected();
				}
				if (bestMoveToggle != null) {
					guiState.showBestMove = bestMoveToggle.isSelected();
				}
				if (mainSplit != null) {
					guiState.splitDivider = mainSplit.getDividerLocation();
				}
				if (analysisSplit != null) {
					guiState.analysisDivider = analysisSplit.getDividerLocation();
				}
				guiState.sidebarVisible = sidebarVisible;
				if (rightTabs != null) {
					guiState.rightTabIndex = rightTabs.getSelectedIndex();
				}
				Dimension size = frame.getSize();
				guiState.windowWidth = size.width;
				guiState.windowHeight = size.height;
			}

			/**
			 * saveGuiState method.
			 */
			protected void saveGuiState() {
				if (guiState == null) {
					return;
				}
				Path path = guiStatePath();
				try {
					Files.createDirectories(path.getParent());
				} catch (IOException ignored) {
					return;
				}
				Properties props = new Properties();
				props.setProperty("lightMode", String.valueOf(guiState.lightMode));
				props.setProperty("whiteDown", String.valueOf(guiState.whiteDown));
				props.setProperty("showLegal", String.valueOf(guiState.showLegal));
				props.setProperty("showMoves", String.valueOf(guiState.showMoves));
				props.setProperty("showTags", String.valueOf(guiState.showTags));
				props.setProperty("showBestMove", String.valueOf(guiState.showBestMove));
				props.setProperty("showCoords", String.valueOf(guiState.showCoords));
				props.setProperty("hoverLegal", String.valueOf(guiState.hoverLegal));
				props.setProperty("hoverHighlight", String.valueOf(guiState.hoverHighlight));
				props.setProperty("hoverOnlyLegal", String.valueOf(guiState.hoverOnlyLegal));
				props.setProperty("highContrast", String.valueOf(guiState.highContrast));
				props.setProperty("compactMode", String.valueOf(guiState.compactMode));
				props.setProperty("figurineSan", String.valueOf(guiState.figurineSan));
				props.setProperty("boardHue", String.valueOf(guiState.boardHueDegrees));
				props.setProperty("boardBrightness", String.valueOf(guiState.boardBrightness));
				props.setProperty("boardSaturation", String.valueOf(guiState.boardSaturation));
				props.setProperty("animationMillis", String.valueOf(guiState.animationMillis));
				props.setProperty("splitDivider", String.valueOf(guiState.splitDivider));
				props.setProperty("analysisDivider", String.valueOf(guiState.analysisDivider));
				props.setProperty("sidebarVisible", String.valueOf(guiState.sidebarVisible));
				props.setProperty("windowWidth", String.valueOf(guiState.windowWidth));
				props.setProperty("windowHeight", String.valueOf(guiState.windowHeight));
				props.setProperty("rightTab", String.valueOf(guiState.rightTabIndex));
				try (java.io.OutputStream out = Files.newOutputStream(path)) {
					props.store(out, "ChessRTK GUI state");
				} catch (IOException ignored) {
					// best effort
				}
			}

			/**
			 * guiStatePath method.
			 * @return return value.
			 */
			protected Path guiStatePath() {
				return Paths.get(System.getProperty("user.home"), ".crtk", "gui.properties");
			}

			/**
			 * readBool method.
			 * @param props parameter.
			 * @param key parameter.
			 * @param defaultValue parameter.
			 * @return return value.
			 */
			protected boolean readBool(Properties props, String key, boolean defaultValue) {
				String value = props.getProperty(key);
				return value == null ? defaultValue : Boolean.parseBoolean(value);
			}

			/**
			 * readInt method.
			 * @param props parameter.
			 * @param key parameter.
			 * @param defaultValue parameter.
			 * @return return value.
			 */
			protected int readInt(Properties props, String key, int defaultValue) {
				String value = props.getProperty(key);
				if (value == null) {
					return defaultValue;
				}
				try {
					return Integer.parseInt(value.trim());
				} catch (NumberFormatException ex) {
					return defaultValue;
				}
			}

			/**
			 * readFloat method.
			 * @param props parameter.
			 * @param key parameter.
			 * @param defaultValue parameter.
			 * @return return value.
			 */
			protected float readFloat(Properties props, String key, float defaultValue) {
				String value = props.getProperty(key);
				if (value == null) {
					return defaultValue;
				}
				try {
					return Float.parseFloat(value.trim());
				} catch (NumberFormatException ex) {
					return defaultValue;
				}
			}


}
