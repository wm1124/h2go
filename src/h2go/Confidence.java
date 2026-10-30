package h2go;

import java.util.ArrayList;

/**
 * A fountain's status and confidence, from its reports of the last 3 days (problems marked fixed don't count).
 * A report weighs 1 when under a day old, 0.5 at 1 to 2 days, 0.25 at 2 to 3 days. The status is the one
 * with the most weight, and
 *     confidence = 100 x consistency (that status's share of the weight) x frequency (reports / 5, at most 1)
 */
public class Confidence {

    public static final String[] STATUSES = {"WORKING", "NOT_COLD", "LOW_PRESSURE", "DIRTY", "LEAKING", "BROKEN"};
    public static final int MINUTES_PER_DAY = 24 * 60;

    /** How much a report counts, by its age. */
    public static double weight(int minutesAgo) {
        if (minutesAgo < MINUTES_PER_DAY) {
            return 1.0;
        } else if (minutesAgo < 2 * MINUTES_PER_DAY) {
            return 0.5;
        } else if (minutesAgo < 3 * MINUTES_PER_DAY) {
            return 0.25;
        }
        return 0;
    }

    /** Works out the station's status, confidence, line and stars. */
    public static void update(Station station, ArrayList<Report> reports) {
        double[] weightOf = new double[STATUSES.length];
        double totalWeight = 0;
        int recentReports = 0;
        String queue = "UNKNOWN";
        int queueAge = Integer.MAX_VALUE;
        int starTotal = 0;
        int starCount = 0;

        for (Report r : reports) {
            if (r.getStationId() != station.getId()) {
                continue;
            }
            if (r.getStars() > 0) {   // ratings count however old
                starTotal += r.getStars();
                starCount++;
            }
            double w = weight(r.getMinutesAgo());
            if (r.isFixed() || w == 0) {
                continue;   // fixed or too old
            }
            for (int i = 0; i < STATUSES.length; i++) {
                if (STATUSES[i].equals(r.getStatus())) {
                    weightOf[i] += w;
                }
            }
            totalWeight += w;
            recentReports++;
            // the line: from the newest report of the last hour that has one
            if (!r.getQueue().equals("UNKNOWN") && r.getMinutesAgo() <= 60 && r.getMinutesAgo() < queueAge) {
                queue = r.getQueue();
                queueAge = r.getMinutesAgo();
            }
        }

        String status = "UNKNOWN";
        int confidence = 0;
        if (totalWeight > 0) {
            int best = 0;
            for (int i = 1; i < STATUSES.length; i++) {
                if (weightOf[i] > weightOf[best]) {
                    best = i;
                }
            }
            status = STATUSES[best];
            double consistency = weightOf[best] / totalWeight;
            double frequency = Math.min(1.0, recentReports / 5.0);
            confidence = (int) Math.round(100 * consistency * frequency);
        }
        double stars = 0;
        if (starCount > 0) {
            stars = (double) starTotal / starCount;
        }
        station.setReportResults(status, confidence, queue, stars, starCount, recentReports);
    }
}
