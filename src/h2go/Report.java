package h2go;

/** A user's report about a fountain: is it working, how long is the line, and their rating. */
public class Report {

    private int id;
    private int stationId;
    private String userName;
    private String status;       // WORKING, NOT_COLD, LOW_PRESSURE, DIRTY, LEAKING or BROKEN
    private String queue;        // NONE, SHORT, LONG or UNKNOWN
    private int stars;           // 1 to 5, or 0 for no rating
    private String comment;
    private int minutesAgo;
    private boolean fixed;       // true once an admin marked the problem fixed

    public Report(int id, int stationId, String userName, String status, String queue, int stars, String comment,
                  int minutesAgo, boolean fixed) {
        this.id = id;
        this.stationId = stationId;
        this.userName = userName;
        this.status = status;
        this.queue = queue;
        this.stars = stars;
        this.comment = comment;
        this.minutesAgo = minutesAgo;
        this.fixed = fixed;
    }

    public int getStationId() {
        return stationId;
    }

    public String getStatus() {
        return status;
    }

    public String getQueue() {
        return queue;
    }

    public int getStars() {
        return stars;
    }

    public int getMinutesAgo() {
        return minutesAgo;
    }

    public boolean isFixed() {
        return fixed;
    }

    /** True when this report says something is wrong with the fountain. */
    public boolean isProblem() {
        return !status.equals("WORKING");
    }

    /** canRemove: the person looking is an admin. */
    public String toJson(boolean canRemove) {
        return "{\"id\": " + id
                + ", \"user\": " + Json.text(userName)
                + ", \"status\": " + Json.text(status)
                + ", \"statusLabel\": " + Json.text(Labels.status(status))
                + ", \"statusGroup\": " + Json.text(Labels.group(status))
                + ", \"queue\": " + Json.text(queue)
                + ", \"stars\": " + stars
                + ", \"starsText\": " + Json.text(Labels.stars(stars))
                + ", \"comment\": " + Json.text(comment)
                + ", \"minutesAgo\": " + minutesAgo
                + ", \"ago\": " + Json.text(Labels.ago(minutesAgo))
                + ", \"fixed\": " + fixed
                + ", \"canRemove\": " + canRemove + "}";
    }
}
