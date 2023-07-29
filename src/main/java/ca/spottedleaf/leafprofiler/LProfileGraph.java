package ca.spottedleaf.leafprofiler;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class LProfileGraph {

    public static final int ROOT_NODE = 0;
    // Array idx is the graph node id, where the int->int mapping is a mapping of profile timer id to graph node id
    private Int2IntOpenHashMap[] nodes;
    private int nodeCount;

    public LProfileGraph() {
        final Int2IntOpenHashMap[] nodes = new Int2IntOpenHashMap[16];
        nodes[ROOT_NODE] = new Int2IntOpenHashMap();

        this.nodes = nodes;
        this.nodeCount = 1;
    }

    public static record GraphNode(GraphNode parent, int nodeId, int timerId) {}

    public List<GraphNode> getDFS() {
        final List<GraphNode> ret = new ArrayList<>();
        final ArrayDeque<GraphNode> queue = new ArrayDeque<>();

        queue.addFirst(new GraphNode(null, ROOT_NODE, -1));
        final Int2IntOpenHashMap[] nodes = this.nodes;

        GraphNode graphNode;
        while ((graphNode = queue.pollFirst()) != null) {
            ret.add(graphNode);

            final int parent = graphNode.nodeId;

            final Int2IntOpenHashMap children = nodes[parent];

            for (final Iterator<Int2IntMap.Entry> iterator = children.int2IntEntrySet().fastIterator(); iterator.hasNext();) {
                final Int2IntMap.Entry entry = iterator.next();
                queue.addFirst(new GraphNode(graphNode, entry.getIntValue(), entry.getIntKey()));
            }
        }

        return ret;
    }

    private int createNode(final int parent, final int timerId) {
        Int2IntOpenHashMap[] nodes = this.nodes;

        final Int2IntOpenHashMap node = nodes[parent];

        final int newNode = this.nodeCount;
        final int prev = node.putIfAbsent(timerId, newNode);

        if (prev != 0) {
            // already exists
            return prev;
        }

        // insert new node
        ++this.nodeCount;

        if (newNode >= nodes.length) {
            this.nodes = (nodes = Arrays.copyOf(nodes, nodes.length * 2));
        }

        nodes[newNode] = new Int2IntOpenHashMap();

        return newNode;
    }

    public int getNode(final int parent, final int timerId) {
        // note: requires parent node to exist
        final Int2IntOpenHashMap[] nodes = this.nodes;

        if (parent >= nodes.length) {
            return -1;
        }

        final int mapping = nodes[parent].get(timerId);

        if (mapping != 0) {
            return mapping;
        }

        return -1;
    }

    public int getOrCreateNode(final int parent, final int timerId) {
        // note: requires parent node to exist
        final Int2IntOpenHashMap[] nodes = this.nodes;

        final int mapping = nodes[parent].get(timerId);

        if (mapping != 0) {
            return mapping;
        }

        return this.createNode(parent, timerId);
    }
}
