package org.ebookdroid.core;

import java.util.ArrayList;
import java.util.List;

public class NavigationHistoryTree {

    public enum NavigationType {
        LINK, OUTLINE, SEARCH, GOTO, BOOKMARK
    }

    public static class Node {
        public final int page;
        public final NavigationType type;
        public final String detail;
        public final long timestamp;
        public Node parent;
        public final List<Node> children = new ArrayList<Node>();

        Node(final int page, final NavigationType type, final String detail) {
            this.page = page;
            this.type = type;
            this.detail = detail;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Node root;
    private Node current;

    public NavigationHistoryTree(final int startPage) {
        root = new Node(startPage, null, null);
        current = root;
    }

    public void recordJump(final int targetPage, final NavigationType type, final String detail) {
        final Node node = new Node(targetPage, type, detail);
        node.parent = current;
        current.children.add(node);
        current = node;
    }

    public boolean goBack() {
        if (current.parent != null) {
            current = current.parent;
            return true;
        }
        return false;
    }

    public void goToNode(final Node node) {
        current = node;
    }

    public Node getRoot() {
        return root;
    }

    public Node getCurrent() {
        return current;
    }

    public boolean isEmpty() {
        return root.children.isEmpty();
    }

    public void flatten(final Node node, final int depth, final List<FlatEntry> out) {
        out.add(new FlatEntry(node, depth));
        for (final Node child : node.children) {
            flatten(child, depth + 1, out);
        }
    }

    public List<FlatEntry> flatten() {
        final List<FlatEntry> result = new ArrayList<FlatEntry>();
        flatten(root, 0, result);
        return result;
    }

    public static class FlatEntry {
        public final Node node;
        public final int depth;

        FlatEntry(final Node node, final int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
