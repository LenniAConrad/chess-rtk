package application.gui.workbench.mcts;

import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Hosts the MCTS root table and full search graph behind one local view switcher.
 *
 * <p>The Engine Lab owns one Search mode; this panel keeps the table workflow as
 * the default first-class view and exposes the graph as an alternate view of the
 * same shared {@link MctsSession}. Both child panels are still built by the
 * window-level factories so their session listeners, board-FEN updates, and
 * disposal remain centralized.</p>
 */
public final class MctsWorkspacePanel extends SurfacePanel {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Root-move table view index.
     */
    public static final int VIEW_TABLE = 0;

    /**
     * Search-tree graph view index.
     */
    public static final int VIEW_GRAPH = 1;

    /**
     * Root-move table card key.
     */
    private static final String CARD_TABLE = "table";

    /**
     * Search-tree graph card key.
     */
    private static final String CARD_GRAPH = "graph";

    /**
     * Local view switcher.
     */
    private final SegmentedSwitcher viewSwitcher = Ui.segmentedControl("Table", "Graph");

    /**
     * Card layout for the view bodies.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Card host for the view bodies.
     */
    private final JPanel body = new JPanel(cards);

    /**
     * Table body factory.
     */
    private final transient Supplier<? extends JComponent> tableBuilder;

    /**
     * Graph body factory.
     */
    private final transient Supplier<? extends JComponent> graphBuilder;

    /**
     * True after the table card is materialized.
     */
    private boolean tableBuilt;

    /**
     * True after the graph card is materialized.
     */
    private boolean graphBuilt;

    /**
     * Creates the search workspace.
     *
     * @param tableBuilder factory for the root-move table panel
     * @param graphBuilder factory for the search-tree graph panel
     */
    public MctsWorkspacePanel(Supplier<? extends JComponent> tableBuilder,
            Supplier<? extends JComponent> graphBuilder) {
        super(new BorderLayout(0, 0), Theme.Surface.BACKDROP);
        this.tableBuilder = tableBuilder;
        this.graphBuilder = graphBuilder;

        viewSwitcher.setToolTipText("Switch between the root-move table and the live search graph");
        viewSwitcher.getAccessibleContext().setAccessibleName("MCTS search view");
        viewSwitcher.addActionListener(event -> showView(viewSwitcher.getSelectedIndex()));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        Ui.styleToolbarBand(header,
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));
        header.add(Ui.labeledControl("View", viewSwitcher));

        body.setOpaque(true);
        body.setBackground(Theme.BG);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        showView(VIEW_TABLE);
    }

    /**
     * Returns the active Search view.
     *
     * @return view index
     */
    public int viewMode() {
        return viewSwitcher.getSelectedIndex();
    }

    /**
     * Selects the active Search view.
     *
     * @param viewMode view index
     */
    public void setViewMode(int viewMode) {
        if (viewMode != VIEW_TABLE && viewMode != VIEW_GRAPH) {
            return;
        }
        if (viewSwitcher.getSelectedIndex() == viewMode) {
            showView(viewMode);
        } else {
            viewSwitcher.setSelectedIndex(viewMode);
        }
    }

    /**
     * Returns whether the graph body has been built.
     *
     * @return true after the graph card is materialized
     */
    public boolean graphBuilt() {
        return graphBuilt;
    }

    /**
     * Builds the target view if needed and shows it.
     *
     * @param viewMode view index
     */
    private void showView(int viewMode) {
        if (viewMode == VIEW_GRAPH) {
            ensureGraphBuilt();
            cards.show(body, CARD_GRAPH);
        } else {
            ensureTableBuilt();
            cards.show(body, CARD_TABLE);
        }
        body.revalidate();
        body.repaint();
    }

    /**
     * Materializes the table card once.
     */
    private void ensureTableBuilt() {
        if (tableBuilt) {
            return;
        }
        body.add(requireBuilt(tableBuilder, "MCTS table"), CARD_TABLE);
        tableBuilt = true;
    }

    /**
     * Materializes the graph card once.
     */
    private void ensureGraphBuilt() {
        if (graphBuilt) {
            return;
        }
        body.add(requireBuilt(graphBuilder, "MCTS graph"), CARD_GRAPH);
        graphBuilt = true;
    }

    /**
     * Runs a child-view builder and validates its result.
     *
     * @param builder child builder
     * @param label diagnostic label
     * @return built component
     */
    private static JComponent requireBuilt(Supplier<? extends JComponent> builder, String label) {
        if (builder == null) {
            throw new IllegalArgumentException(label + " builder is missing");
        }
        JComponent component = builder.get();
        if (component == null) {
            throw new IllegalStateException(label + " builder returned null");
        }
        return component;
    }
}
