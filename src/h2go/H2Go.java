package h2go;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** The fountains, the walkway graph and the two k-d trees, read again from MySQL for every request. */
public class H2Go {

    private Database db;
    private ArrayList<Station> stations;
    private ArrayList<PathPoint> pathPoints;
    private CampusGraph graph;
    private KdTree stationTree;   // fountains near you
    private KdTree pointTree;     // the walkway point nearest to you

    public H2Go(Database db) {
        this.db = db;
    }

    public void reload() throws SQLException {
        stations = db.loadStations();
        pathPoints = db.loadPathPoints();

        graph = new CampusGraph(pathPoints);
        for (int[] path : db.loadPaths()) {
            graph.addWalkway(path[0], path[1]);
        }
        // a random order keeps the k-d trees balanced
        stationTree = new KdTree();
        pointTree = new KdTree();
        ArrayList<Station> shuffledStations = new ArrayList<>(stations);
        Collections.shuffle(shuffledStations, new Random(1));
        for (Station s : shuffledStations) {
            stationTree.add(s.getId(), s.getLat(), s.getLng());
        }
        ArrayList<PathPoint> shuffledPoints = new ArrayList<>(pathPoints);
        Collections.shuffle(shuffledPoints, new Random(1));
        for (PathPoint p : shuffledPoints) {
            pointTree.add(p.getId(), p.getLat(), p.getLng());
        }
    }

    public Database getDatabase() {
        return db;
    }

    public ArrayList<Station> getStations() {
        return stations;
    }

    public ArrayList<PathPoint> getPathPoints() {
        return pathPoints;
    }

    public CampusGraph getGraph() {
        return graph;
    }

    public KdTree getStationTree() {
        return stationTree;
    }

    /** The fountain with this id, or null. */
    public Station getStation(int id) {
        for (Station s : stations) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    /** Reads the reports and works out every fountain's status. Returns the reports. */
    public ArrayList<Report> updateStatuses() throws SQLException {
        ArrayList<Report> reports = db.loadReports();
        for (Station s : stations) {
            Confidence.update(s, reports);
        }
        return reports;
    }

    public PathPoint nearestPathPoint(double lat, double lng) {
        return graph.getPoint(pointTree.nearest(lat, lng));
    }

    /** Meters from (lat, lng) to the start point, along the walkways, then to the fountain. MAX_VALUE if unreachable. */
    public double walkingMeters(double lat, double lng, PathPoint start, CampusGraph.ShortestPaths walks, Station s) {
        PathPoint end = graph.getPoint(s.getPointId());
        if (walks.metersTo(end.getId()) == Double.MAX_VALUE) {
            return Double.MAX_VALUE;
        }
        return Distance.meters(lat, lng, start.getLat(), start.getLng())
                + walks.metersTo(end.getId())
                + Distance.meters(end.getLat(), end.getLng(), s.getLat(), s.getLng());
    }
}
