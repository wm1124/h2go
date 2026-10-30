package h2go;

import java.util.ArrayList;

/**
 * A k-d tree with k = 2: each level splits the places by x (east-west) or y (north-south) in turn, so a search
 * can skip subtrees that are too far away. One tree holds the fountains, one the walkway points.
 */
public class KdTree {

    /** One place: a fountain or walkway point id. */
    private static class Node {
        int id;
        double x;    // meters east
        double y;    // meters north
        Node left;
        Node right;

        Node(int id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    private Node root;
    private int size;

    private Node best;
    private double bestDistance;

    public void add(int id, double lat, double lng) {
        Node newNode = new Node(id, Distance.x(lng), Distance.y(lat));
        root = add(root, newNode, 0);
        size++;
    }

    private Node add(Node node, Node newNode, int depth) {
        if (node == null) {
            return newNode;
        }
        if (isLeft(newNode.x, newNode.y, node, depth)) {
            node.left = add(node.left, newNode, depth + 1);
        } else {
            node.right = add(node.right, newNode, depth + 1);
        }
        return node;
    }

    /** x on even levels, y on odd levels. */
    private boolean isLeft(double x, double y, Node node, int depth) {
        if (depth % 2 == 0) {
            return x < node.x;
        }
        return y < node.y;
    }

    /** How far (x, y) is from the node's split line. */
    private double distanceToSplit(double x, double y, Node node, int depth) {
        if (depth % 2 == 0) {
            return Math.abs(x - node.x);
        }
        return Math.abs(y - node.y);
    }

    public int size() {
        return size;
    }

    /** The nearest place's id, or -1 when the tree is empty. */
    public int nearest(double lat, double lng) {
        best = null;
        bestDistance = Double.MAX_VALUE;
        nearest(root, Distance.x(lng), Distance.y(lat), 0);
        if (best == null) {
            return -1;
        }
        return best.id;
    }

    private void nearest(Node node, double x, double y, int depth) {
        if (node == null) {
            return;
        }
        double d = Math.sqrt((node.x - x) * (node.x - x) + (node.y - y) * (node.y - y));
        if (d < bestDistance) {
            bestDistance = d;
            best = node;
        }
        // our side first; the other side only if the split line is closer than the best so far
        Node sameSide;
        Node otherSide;
        if (isLeft(x, y, node, depth)) {
            sameSide = node.left;
            otherSide = node.right;
        } else {
            sameSide = node.right;
            otherSide = node.left;
        }
        nearest(sameSide, x, y, depth + 1);
        if (distanceToSplit(x, y, node, depth) < bestDistance) {
            nearest(otherSide, x, y, depth + 1);
        }
    }

    /** The ids of all places within that many meters. */
    public ArrayList<Integer> within(double lat, double lng, double meters) {
        ArrayList<Integer> found = new ArrayList<>();
        within(root, Distance.x(lng), Distance.y(lat), meters, 0, found);
        return found;
    }

    private void within(Node node, double x, double y, double meters, int depth, ArrayList<Integer> found) {
        if (node == null) {
            return;
        }
        double d = Math.sqrt((node.x - x) * (node.x - x) + (node.y - y) * (node.y - y));
        if (d <= meters) {
            found.add(node.id);
        }
        Node sameSide;
        Node otherSide;
        if (isLeft(x, y, node, depth)) {
            sameSide = node.left;
            otherSide = node.right;
        } else {
            sameSide = node.right;
            otherSide = node.left;
        }
        within(sameSide, x, y, meters, depth + 1, found);
        if (distanceToSplit(x, y, node, depth) <= meters) {
            within(otherSide, x, y, meters, depth + 1, found);
        }
    }
}
