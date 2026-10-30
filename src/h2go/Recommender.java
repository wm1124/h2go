package h2go;

import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * Smart recommendations: the k-d tree finds the fountains within 1 km, filters drop broken ones (when hidden),
 * ones missing a need (bitwise AND) and short ones for a large bottle, Dijkstra gives the walk, and a priority
 * queue returns the best scores first:
 *     score = 40 x walk + 30 x reliability + 20 x line + 10 x stars   (each part 0 to 1)
 */
public class Recommender {

    public static final double SEARCH_RADIUS = 1000;          // meters
    public static final double METERS_PER_MINUTE = 78;        // 1.3 meters per second

    /** One fountain you could go to, with its walk and score. */
    public static class Choice implements Comparable<Choice> {
        Station station;
        double meters;
        double minutes;
        double score;

        /** Higher score first. */
        public int compareTo(Choice other) {
            return Double.compare(other.score, score);
        }

        public String toJson() {
            return "{\"id\": " + station.getId()
                    + ", \"name\": " + Json.text(station.getName())
                    + ", \"status\": " + Json.text(station.getStatus())
                    + ", \"statusLabel\": " + Json.text(Labels.status(station.getStatus()))
                    + ", \"statusGroup\": " + Json.text(Labels.group(station.getStatus()))
                    + ", \"queueLabel\": " + Json.text(Labels.queue(station.getQueue()))
                    + ", \"meters\": " + Math.round(meters)
                    + ", \"minutes\": " + Math.round(minutes * 10) / 10.0
                    + ", \"walkMinutes\": " + Labels.walkMinutes(minutes)
                    + ", \"score\": " + Math.round(score * 10) / 10.0 + "}";
        }
    }

    /** The best fountains for someone at (lat, lng), best first. */
    public static ArrayList<Choice> recommend(H2Go app, double lat, double lng, int needs, String bottle,
                                              boolean hideBroken, int howMany) {
        PathPoint start = app.nearestPathPoint(lat, lng);
        CampusGraph.ShortestPaths walks = app.getGraph().dijkstra(start.getId());

        PriorityQueue<Choice> queue = new PriorityQueue<>();
        for (int id : app.getStationTree().within(lat, lng, SEARCH_RADIUS)) {
            Station s = app.getStation(id);
            if (hideBroken && s.getStatus().equals("BROKEN")) {
                continue;
            }
            if (!Amenity.hasAll(s.getAmenities(), needs)) {
                continue;
            }
            if (bottle.equals("LARGE") && !Amenity.hasAll(s.getAmenities(), Amenity.TALL_BOTTLE)) {
                continue;
            }
            Choice c = new Choice();
            c.station = s;
            c.meters = app.walkingMeters(lat, lng, start, walks, s);
            if (c.meters == Double.MAX_VALUE) {
                continue;   // no walkway goes there
            }
            c.minutes = c.meters / METERS_PER_MINUTE;
            c.score = score(s, c.minutes);
            queue.add(c);
        }

        ArrayList<Choice> best = new ArrayList<>();
        while (!queue.isEmpty() && best.size() < howMany) {
            best.add(queue.poll());
        }
        return best;
    }

    /** The score out of 100. A 10-minute walk scores 0. */
    public static double score(Station s, double minutes) {
        double walk = Math.max(0, 1 - minutes / 10);

        double reliability;
        if (s.getStatus().equals("WORKING")) {
            reliability = s.getConfidence() / 100.0;
        } else if (s.getStatus().equals("UNKNOWN")) {
            reliability = 0.3;   // no recent reports
        } else if (s.getStatus().equals("BROKEN")) {
            reliability = 0;
        } else {
            reliability = 0.1;   // has a problem
        }

        double line;
        if (s.getQueue().equals("NONE")) {
            line = 1;
        } else if (s.getQueue().equals("SHORT")) {
            line = 0.6;
        } else if (s.getQueue().equals("LONG")) {
            line = 0.1;
        } else {
            line = 0.5;   // not reported
        }

        double stars = 0.5;   // no ratings yet
        if (s.getStarCount() > 0) {
            stars = s.getStars() / 5;
        }

        return 40 * walk + 30 * reliability + 20 * line + 10 * stars;
    }
}
