package application.gui.workbench.mcts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure tidy-tree layout for an MCTS search tree.
 *
 * <p>Takes the flat {@link MctsSearch.NodeInfo} list from a tree snapshot and
 * produces positioned nodes plus edges. When {@code merge} is on, distinct tree
 * paths that reach the same position (identical zobrist signature) collapse to a
 * single node, exactly as the search shares transposition statistics. The
 * spanning tree is discovered by breadth-first search so the visible structure
 * is always acyclic; every additional parent of a merged node becomes a dashed
 * "transposition" edge, and the most-visited child chain from the root is marked
 * as the principal variation.</p>
 *
 * <p>When {@code collapseLeaves} is on, childless siblings under a parent are
 * batched into a single "blob" node so a shallow-but-wide frontier stays compact
 * while genuinely branching nodes fan out. The blob keeps its members for the
 * navigation inspector.</p>
 */
public final class TreeLayout {

    /**
     * Margin around the laid-out graph, in unscaled pixels.
     */
    public static final int MARGIN = 28;

    /**
     * Field separator used to key directed edges.
     */
    private static final char EDGE_SEP = '\u0001';

    /**
     * Suffix marking a synthetic batched-leaf blob key.
     */
    private static final String BLOB_SUFFIX = "\u0002leaves";

    /**
     * Minimum childless siblings under a parent before they batch into a blob.
     */
    private static final int BLOB_THRESHOLD = 2;

    /**
     * Prevents instantiation.
     */
    private TreeLayout() {
    }

    /**
     * A positioned tree node.
     *
     * @param key merge key (signature when merging, node id otherwise)
     * @param info representative node info (max visits in the group / blob)
     * @param x left coordinate in unscaled pixels
     * @param y top coordinate in unscaled pixels
     * @param w node width
     * @param h node height
     * @param layer depth layer (breadth-first distance from the root)
     * @param root true for the search root
     * @param selected true when this node is the inspected selection
     * @param onPrincipalVariation true when on the most-visited child chain
     * @param blob true when this node batches several childless leaves
     * @param members for a blob, the batched leaves (most-visited first); empty otherwise
     */
    public record Node(String key, MctsSearch.NodeInfo info, int x, int y, int w, int h,
            int layer, boolean root, boolean selected, boolean onPrincipalVariation,
            boolean blob, List<MctsSearch.NodeInfo> members) {

        /**
         * Returns the node center x.
         *
         * @return center x
         */
        public int centerX() {
            return x + w / 2;
        }

        /**
         * Returns the node center y.
         *
         * @return center y
         */
        public int centerY() {
            return y + h / 2;
        }
    }

    /**
     * A directed edge between two laid-out nodes.
     *
     * @param fromKey parent key
     * @param toKey child key
     * @param san move in algebraic notation
     * @param transposition true for a secondary (merged) parent edge
     */
    public record Edge(String fromKey, String toKey, String san, boolean transposition) {
    }

    /**
     * The full laid-out model.
     *
     * @param nodes positioned nodes
     * @param edges directed edges
     * @param width total width including margins
     * @param height total height including margins
     * @param rootKey root node key
     * @param uniquePositions distinct positions shown (including batched leaves)
     * @param transpositionEdges count of dashed transposition edges
     * @param omittedNodes nodes omitted by the upstream snapshot cap/filtering
     */
    public record Model(List<Node> nodes, List<Edge> edges, int width, int height,
            String rootKey, int uniquePositions, int transpositionEdges, int omittedNodes) {

        /**
         * Creates a model with no omitted-node accounting.
         *
         * @param nodes positioned nodes
         * @param edges directed edges
         * @param width total width including margins
         * @param height total height including margins
         * @param rootKey root node key
         * @param uniquePositions distinct positions shown
         * @param transpositionEdges count of dashed transposition edges
         */
        public Model(List<Node> nodes, List<Edge> edges, int width, int height,
                String rootKey, int uniquePositions, int transpositionEdges) {
            this(nodes, edges, width, height, rootKey, uniquePositions, transpositionEdges, 0);
        }

        /**
         * Normalizes snapshot accounting.
         *
         * @param nodes node collection or node budget
         * @param edges layout edges
         * @param width width in pixels
         * @param height height in pixels
         * @param rootKey root node key
         * @param uniquePositions number of unique positions in the layout
         * @param transpositionEdges number of transposition edges
         * @param omittedNodes number of nodes omitted from the layout
         */
        public Model {
            omittedNodes = Math.max(0, omittedNodes);
        }

        /**
         * Returns whether the model has any node.
         *
         * @return true when empty
         */
        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }

    /**
     * Lays out a tree snapshot.
     *
     * @param infos flat node list from {@link MctsSearch.TreeSnapshot#nodes()}
     * @param merge collapse transpositions by signature
     * @param collapseLeaves batch childless siblings into blob nodes
     * @param selectedId id of the inspected node, or null
     * @param nodeW node width
     * @param nodeH node height
     * @param hGap horizontal gap between sibling columns
     * @param vGap vertical gap between layers
     * @return positioned model
     */
    public static Model layout(List<MctsSearch.NodeInfo> infos, boolean merge, boolean collapseLeaves,
            String selectedId, int nodeW, int nodeH, int hGap, int vGap) {
        return layout(infos, merge, collapseLeaves, selectedId, nodeW, nodeH, hGap, vGap, 0);
    }

    /**
     * Lays out a tree snapshot and carries aggregate omitted-node accounting
     * through the view/export model.
     *
     * @param infos flat node list from {@link MctsSearch.TreeSnapshot#nodes()}
     * @param merge collapse transpositions by signature
     * @param collapseLeaves batch childless siblings into blob nodes
     * @param selectedId id of the inspected node, or null
     * @param nodeW node width
     * @param nodeH node height
     * @param hGap horizontal gap between sibling columns
     * @param vGap vertical gap between layers
     * @param omittedNodes nodes omitted by the upstream snapshot cap/filtering
     * @return positioned model
     */
    public static Model layout(List<MctsSearch.NodeInfo> infos, boolean merge, boolean collapseLeaves,
            String selectedId, int nodeW, int nodeH, int hGap, int vGap, int omittedNodes) {
        if (infos == null || infos.isEmpty()) {
            return new Model(List.of(), List.of(), 2 * MARGIN, 2 * MARGIN, null, 0, 0, omittedNodes);
        }
        Map<String, MctsSearch.NodeInfo> idToNode = new HashMap<>();
        for (MctsSearch.NodeInfo n : infos) {
            idToNode.put(n.id(), n);
        }

        Map<String, MctsSearch.NodeInfo> reps = new LinkedHashMap<>();
        for (MctsSearch.NodeInfo n : infos) {
            String key = keyOf(n, merge);
            MctsSearch.NodeInfo cur = reps.get(key);
            if (cur == null || n.visits() > cur.visits()
                    || (n.visits() == cur.visits() && n.depth() < cur.depth())) {
                reps.put(key, n);
            }
        }

        String rootKey = null;
        for (MctsSearch.NodeInfo n : infos) {
            if (n.parentId() == null || n.parentId().isEmpty()) {
                rootKey = keyOf(n, merge);
                break;
            }
        }
        if (rootKey == null) {
            rootKey = reps.keySet().iterator().next();
        }

        Map<String, LinkedHashSet<String>> childMap = new HashMap<>();
        Map<String, String> edgeSan = new HashMap<>();
        for (MctsSearch.NodeInfo n : infos) {
            String parentKey = parentKeyOf(n, idToNode, merge);
            if (parentKey == null) {
                continue;
            }
            String childKey = keyOf(n, merge);
            if (parentKey.equals(childKey)) {
                continue;
            }
            childMap.computeIfAbsent(parentKey, ignored -> new LinkedHashSet<>()).add(childKey);
            edgeSan.putIfAbsent(parentKey + EDGE_SEP + childKey, n.san());
        }

        Map<String, Integer> layer = new HashMap<>();
        Map<String, List<String>> treeChildren = new HashMap<>();
        List<String[]> transEdges = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(rootKey);
        layer.put(rootKey, 0);
        queue.add(rootKey);
        while (!queue.isEmpty()) {
            String parent = queue.poll();
            List<String> kids = new ArrayList<>(childMap.getOrDefault(parent, new LinkedHashSet<>()));
            kids.sort((a, b) -> Integer.compare(visitsOf(reps, b), visitsOf(reps, a)));
            List<String> primaryKids = new ArrayList<>();
            for (String child : kids) {
                if (visited.add(child)) {
                    layer.put(child, layer.get(parent) + 1);
                    primaryKids.add(child);
                    queue.add(child);
                } else {
                    transEdges.add(new String[] { parent, child });
                }
            }
            treeChildren.put(parent, primaryKids);
        }

        Map<String, List<MctsSearch.NodeInfo>> blobMembers = new LinkedHashMap<>();
        Map<String, String> leafToBlob = new HashMap<>();
        if (collapseLeaves) {
            for (String parent : new ArrayList<>(treeChildren.keySet())) {
                List<String> kids = treeChildren.get(parent);
                if (kids == null || kids.isEmpty()) {
                    // Skip leaves already removed by a sibling's blob collapse.
                    continue;
                }
                List<String> leaves = new ArrayList<>();
                List<String> branching = new ArrayList<>();
                for (String kid : kids) {
                    if (treeChildren.getOrDefault(kid, List.of()).isEmpty()) {
                        leaves.add(kid);
                    } else {
                        branching.add(kid);
                    }
                }
                if (leaves.size() < BLOB_THRESHOLD) {
                    continue;
                }
                String blobKey = parent + BLOB_SUFFIX;
                List<MctsSearch.NodeInfo> members = new ArrayList<>();
                for (String leaf : leaves) {
                    members.add(reps.get(leaf));
                    visited.remove(leaf);
                    treeChildren.remove(leaf);
                    leafToBlob.put(leaf, blobKey);
                }
                members.sort((a, b) -> Integer.compare(b.visits(), a.visits()));
                branching.add(blobKey);
                treeChildren.put(parent, branching);
                treeChildren.put(blobKey, List.of());
                layer.put(blobKey, layer.get(parent) + 1);
                visited.add(blobKey);
                blobMembers.put(blobKey, members);
            }
        }

        Map<String, Integer> xPos = new HashMap<>();
        assignX(rootKey, treeChildren, nodeW, hGap, xPos, new int[] { 0 }, new HashSet<>());

        Set<String> principalVariation = new HashSet<>();
        for (String cursor = rootKey; cursor != null && principalVariation.add(cursor);) {
            cursor = bestBranchingChild(treeChildren.get(cursor), blobMembers, reps);
        }

        String selectedKey = selectedId != null && idToNode.containsKey(selectedId)
                ? keyOf(idToNode.get(selectedId), merge)
                : null;
        if (selectedKey != null && leafToBlob.containsKey(selectedKey)) {
            selectedKey = leafToBlob.get(selectedKey);
        }

        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : treeChildren.entrySet()) {
            String parent = entry.getKey();
            for (String child : entry.getValue()) {
                String san = blobMembers.containsKey(child) ? "" : edgeSan.get(parent + EDGE_SEP + child);
                edges.add(new Edge(parent, child, san, false));
            }
        }
        int transpositionEdges = 0;
        Set<String> seenTrans = new HashSet<>();
        for (String[] te : transEdges) {
            if (leafToBlob.containsKey(te[0])) {
                continue;
            }
            String to = leafToBlob.getOrDefault(te[1], te[1]);
            if (!visited.contains(te[0]) || !visited.contains(to) || te[0].equals(to)) {
                continue;
            }
            if (seenTrans.add(te[0] + EDGE_SEP + to)) {
                edges.add(new Edge(te[0], to, edgeSan.get(te[0] + EDGE_SEP + te[1]), true));
                transpositionEdges++;
            }
        }

        int rowStride = nodeH + vGap;
        int maxX = 0;
        int maxLayer = 0;
        List<Node> nodes = new ArrayList<>();
        for (String key : visited) {
            int x = xPos.getOrDefault(key, 0) + MARGIN;
            int nodeLayer = layer.getOrDefault(key, 0);
            int y = nodeLayer * rowStride + MARGIN;
            maxX = Math.max(maxX, x + nodeW);
            maxLayer = Math.max(maxLayer, nodeLayer);
            List<MctsSearch.NodeInfo> members = blobMembers.get(key);
            if (members != null) {
                nodes.add(new Node(key, members.get(0), x, y, nodeW, nodeH, nodeLayer,
                        false, key.equals(selectedKey), false, true, members));
            } else {
                nodes.add(new Node(key, reps.get(key), x, y, nodeW, nodeH, nodeLayer,
                        key.equals(rootKey), key.equals(selectedKey), principalVariation.contains(key),
                        false, List.of()));
            }
        }
        int width = maxX + MARGIN;
        int height = maxLayer * rowStride + nodeH + 2 * MARGIN;
        return new Model(nodes, edges, width, height, rootKey, reps.size(), transpositionEdges, omittedNodes);
    }

    /**
     * Returns the children of a node as info rows, for the navigation inspector.
     *
     * <p>This works on the raw snapshot (independent of leaf batching) so the
     * inspector can list and navigate to every child even when the canvas shows
     * them collapsed into a blob.</p>
     *
     * @param infos flat node list
     * @param key node key (signature or id, matching {@code merge})
     * @param merge whether keys are signatures
     * @return distinct children, most-visited first
     */
    public static List<MctsSearch.NodeInfo> childrenOf(List<MctsSearch.NodeInfo> infos,
            String key, boolean merge) {
        if (infos == null || key == null) {
            return List.of();
        }
        Map<String, MctsSearch.NodeInfo> idToNode = new HashMap<>();
        for (MctsSearch.NodeInfo n : infos) {
            idToNode.put(n.id(), n);
        }
        Map<String, MctsSearch.NodeInfo> kids = new LinkedHashMap<>();
        for (MctsSearch.NodeInfo n : infos) {
            if (key.equals(parentKeyOf(n, idToNode, merge))) {
                String ck = keyOf(n, merge);
                MctsSearch.NodeInfo cur = kids.get(ck);
                if (cur == null || n.visits() > cur.visits()) {
                    kids.put(ck, n);
                }
            }
        }
        List<MctsSearch.NodeInfo> out = new ArrayList<>(kids.values());
        out.sort((a, b) -> Integer.compare(b.visits(), a.visits()));
        return out;
    }

    /**
     * Returns the merge key for a node.
     *
     * @param n node info
     * @param merge whether to key by signature
     * @return merge key
     */
    private static String keyOf(MctsSearch.NodeInfo n, boolean merge) {
        return merge ? Long.toString(n.signature()) : n.id();
    }

    /**
     * Returns the merge key of a node's parent, or null for the root.
     *
     * @param n node info
     * @param idToNode id lookup
     * @param merge whether to key by signature
     * @return parent key or null
     */
    private static String parentKeyOf(MctsSearch.NodeInfo n,
            Map<String, MctsSearch.NodeInfo> idToNode, boolean merge) {
        if (n.parentId() == null || n.parentId().isEmpty()) {
            return null;
        }
        MctsSearch.NodeInfo parent = idToNode.get(n.parentId());
        return parent == null ? null : keyOf(parent, merge);
    }

    /**
     * Returns the most-visited non-blob child for principal-variation tracing.
     *
     * @param kids spanning-tree children, or null
     * @param blobMembers blob membership
     * @param reps representatives
     * @return best child key, or null
     */
    private static String bestBranchingChild(List<String> kids,
            Map<String, List<MctsSearch.NodeInfo>> blobMembers, Map<String, MctsSearch.NodeInfo> reps) {
        if (kids == null) {
            return null;
        }
        String best = null;
        int bestVisits = -1;
        for (String kid : kids) {
            if (blobMembers.containsKey(kid)) {
                continue;
            }
            int v = visitsOf(reps, kid);
            if (v > bestVisits) {
                bestVisits = v;
                best = kid;
            }
        }
        return best;
    }

    /**
     * Returns the representative visit count for a key.
     *
     * @param reps representatives
     * @param key node key
     * @return visit count
     */
    private static int visitsOf(Map<String, MctsSearch.NodeInfo> reps, String key) {
        MctsSearch.NodeInfo info = reps.get(key);
        return info == null ? 0 : info.visits();
    }

    /**
     * Assigns x-positions in post-order: leaves take sequential columns, parents
     * center over their children.
     *
     * @param key current node key
     * @param treeChildren spanning-tree children
     * @param nodeW node width
     * @param hGap horizontal gap
     * @param xPos accumulating x positions
     * @param nextX mutable leaf cursor
     * @param stack ancestor stack used while walking the tree
     * @return assigned x for this node
     */
    private static int assignX(String key, Map<String, List<String>> treeChildren,
            int nodeW, int hGap, Map<String, Integer> xPos, int[] nextX, Set<String> stack) {
        Integer placed = xPos.get(key);
        if (placed != null) {
            // Defensive: a node reached twice (or a transposition-induced cycle)
            // is laid out once; reuse its column instead of recursing forever.
            return placed;
        }
        List<String> kids = treeChildren.getOrDefault(key, List.of());
        if (kids.isEmpty() || !stack.add(key)) {
            int x = nextX[0];
            nextX[0] += nodeW + hGap;
            xPos.put(key, x);
            return x;
        }
        int first = -1;
        int last = -1;
        for (String child : kids) {
            int cx = assignX(child, treeChildren, nodeW, hGap, xPos, nextX, stack);
            if (first < 0) {
                first = cx;
            }
            last = cx;
        }
        stack.remove(key);
        int x = (first + last) / 2;
        xPos.put(key, x);
        return x;
    }
}
