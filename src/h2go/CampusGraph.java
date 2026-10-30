package h2go;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.PriorityQueue;

/**
 * The walkways as a weighted, undirected graph: walkway points are the vertices, walkways the edges,
 * weighted by their length in meters. Dijkstra's algorithm, with a priority queue, finds the shortest walks.
 */
public class CampusGraph {

    /** A walkway from one point to a neighbor point. */
    private static class Edge {
        int to;
        double meters;

        Edge(int to, double meters) {
            this.to = to;
            this.meters = meters;
        }
    }

    /** An entry in Dijkstra's priority queue. */
    private static class Step implements Comparable<Step> {
        int point;
        double meters;

        Step(int point, double meters) {
            this.point = point;
            this.meters = meters;
        }

        /** The smaller distance comes out of the priority queue first. */
        public int compareTo(Step other) {
            return Double.compare(meters, other.meters);
        }
    }

    /** Dijkstra's result. */
    public static class ShortestPaths {
        private PathPoint[] points;
        private double[] meters;    // meters[id]: shortest walk from the start to point id
        private int[] previous;     // previous[id]: the point before id on that walk

        public double metersTo(int id) {
            return meters[id];
        }

        /** The walkway points from the start to this point. */
        public ArrayList<PathPoint> pathTo(int id) {
            ArrayList<PathPoint> path = new ArrayList<>();
            for (int p = id; p != -1; p = previous[p]) {
                path.add(points[p]);
            }
            Collections.reverse(path);   // it was followed from the end
            return path;
        }
    }

    private PathPoint[] points;                  // points[id]
    private ArrayList<ArrayList<Edge>> edges;    // edges.get(id): the walkways from point id
    private int walkwayCount;

    public CampusGraph(ArrayList<PathPoint> pointList) {
        int maxId = 0;
        for (PathPoint p : pointList) {
            maxId = Math.max(maxId, p.getId());
        }
        points = new PathPoint[maxId + 1];
        edges = new ArrayList<>();
        for (int i = 0; i <= maxId; i++) {
            edges.add(new ArrayList<>());
        }
        for (PathPoint p : pointList) {
            points[p.getId()] = p;
        }
    }

    /** Adds a walkway both ways, weighted by its length. */
    public void addWalkway(int a, int b) {
        double meters = Distance.meters(points[a].getLat(), points[a].getLng(), points[b].getLat(), points[b].getLng());
        edges.get(a).add(new Edge(b, meters));
        edges.get(b).add(new Edge(a, meters));
        walkwayCount++;
    }

    public PathPoint getPoint(int id) {
        return points[id];
    }

    public int getWalkwayCount() {
        return walkwayCount;
    }

    /** Dijkstra's algorithm from the start point. */
    public ShortestPaths dijkstra(int start) {
        double[] meters = new double[points.length];
        int[] previous = new int[points.length];
        Arrays.fill(meters, Double.MAX_VALUE);   // "infinity": not reached yet
        Arrays.fill(previous, -1);
        meters[start] = 0;

        PriorityQueue<Step> queue = new PriorityQueue<>();
        queue.add(new Step(start, 0));
        while (!queue.isEmpty()) {
            Step step = queue.poll();               // the closest unfinished point
            if (step.meters > meters[step.point]) {
                continue;                           // an old entry: a shorter way was found
            }
            for (Edge e : edges.get(step.point)) {
                double newDistance = meters[step.point] + e.meters;
                if (newDistance < meters[e.to]) {
                    meters[e.to] = newDistance;
                    previous[e.to] = step.point;
                    queue.add(new Step(e.to, newDistance));
                }
            }
        }

        ShortestPaths result = new ShortestPaths();
        result.points = points;
        result.meters = meters;
        result.previous = previous;
        return result;
    }
}
