package h2go;

import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * The maintenance priority queue: fountains with problem reports from the last 7 days that are not fixed,
 *     priority = 10 x severity of the worst problem + 3 x reports + hours waiting (up to 72) / 6
 */
public class Maintenance {

    public static final int DAYS_COUNTED = 7;

    /** One fountain that needs fixing. */
    public static class Issue implements Comparable<Issue> {
        Station station;
        String worstProblem = "";
        int reports;
        int hoursWaiting;
        int priority;

        /** Higher priority first. */
        public int compareTo(Issue other) {
            return Integer.compare(other.priority, priority);
        }

        /** canFix: the person looking is an admin. */
        public String toJson(boolean canFix) {
            return "{\"stationId\": " + station.getId()
                    + ", \"station\": " + Json.text(station.getName())
                    + ", \"building\": " + Json.text(station.getBuilding())
                    + ", \"problem\": " + Json.text(worstProblem)
                    + ", \"problemLabel\": " + Json.text(Labels.status(worstProblem))
                    + ", \"problemGroup\": " + Json.text(Labels.group(worstProblem))
                    + ", \"reports\": " + reports
                    + ", \"hoursWaiting\": " + hoursWaiting
                    + ", \"priority\": " + priority
                    + ", \"canFix\": " + canFix + "}";
        }
    }

    /** From 0 (not a problem) to 10 (broken). */
    public static int severity(String status) {
        switch (status) {
            case "BROKEN":
                return 10;
            case "LEAKING":
                return 8;
            case "LOW_PRESSURE":
                return 6;
            case "DIRTY":
                return 5;
            case "NOT_COLD":
                return 3;
            default:
                return 0;
        }
    }

    public static ArrayList<Issue> issues(ArrayList<Station> stations, ArrayList<Report> reports) {
        PriorityQueue<Issue> queue = new PriorityQueue<>();
        for (Station s : stations) {
            Issue issue = new Issue();
            issue.station = s;
            int oldestMinutes = 0;
            for (Report r : reports) {
                boolean open = r.getStationId() == s.getId() && r.isProblem() && !r.isFixed()
                        && r.getMinutesAgo() < DAYS_COUNTED * Confidence.MINUTES_PER_DAY;
                if (open) {
                    issue.reports++;
                    if (severity(r.getStatus()) > severity(issue.worstProblem)) {
                        issue.worstProblem = r.getStatus();
                    }
                    oldestMinutes = Math.max(oldestMinutes, r.getMinutesAgo());
                }
            }
            if (issue.reports > 0) {
                issue.hoursWaiting = oldestMinutes / 60;
                issue.priority = 10 * severity(issue.worstProblem) + 3 * issue.reports
                        + Math.min(issue.hoursWaiting, 72) / 6;
                queue.add(issue);
            }
        }

        ArrayList<Issue> list = new ArrayList<>();
        while (!queue.isEmpty()) {
            list.add(queue.poll());
        }
        return list;
    }
}
