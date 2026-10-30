package h2go;

/** A water refill station (fountain) on campus. */
public class Station {

    // from h2go_stations.stations
    private int id;
    private String name;
    private String building;
    private int floor;
    private double lat;
    private double lng;
    private int amenities;   // bit flags
    private int pointId;     // the walkway point where routes end

    // worked out from the reports (Confidence.java)
    private String status = "UNKNOWN";
    private int confidence = 0;        // percent
    private String queue = "UNKNOWN";  // NONE, SHORT, LONG or UNKNOWN
    private double stars = 0;          // average rating
    private int starCount = 0;
    private int recentReports = 0;     // reports in the last 3 days

    public Station(int id, String name, String building, int floor, double lat, double lng, int amenities, int pointId) {
        this.id = id;
        this.name = name;
        this.building = building;
        this.floor = floor;
        this.lat = lat;
        this.lng = lng;
        this.amenities = amenities;
        this.pointId = pointId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBuilding() {
        return building;
    }

    public int getFloor() {
        return floor;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getAmenities() {
        return amenities;
    }

    public int getPointId() {
        return pointId;
    }

    public String getStatus() {
        return status;
    }

    public int getConfidence() {
        return confidence;
    }

    public String getQueue() {
        return queue;
    }

    public double getStars() {
        return stars;
    }

    public int getStarCount() {
        return starCount;
    }

    public void setReportResults(String status, int confidence, String queue, double stars, int starCount, int recentReports) {
        this.status = status;
        this.confidence = confidence;
        this.queue = queue;
        this.stars = stars;
        this.starCount = starCount;
        this.recentReports = recentReports;
    }

    public String toJson(boolean favorite) {
        return "{" + jsonFields(favorite) + "}";
    }

    /** The JSON fields without the { }, so Api.java can add the reports. */
    public String jsonFields(boolean favorite) {
        return "\"id\": " + id
                + ", \"name\": " + Json.text(name)
                + ", \"building\": " + Json.text(building)
                + ", \"floor\": " + floor
                + ", \"lat\": " + lat
                + ", \"lng\": " + lng
                + ", \"amenities\": " + amenities
                + ", \"amenityNames\": " + Json.text(Amenity.names(amenities))
                + ", \"status\": " + Json.text(status)
                + ", \"statusLabel\": " + Json.text(Labels.status(status))
                + ", \"statusGroup\": " + Json.text(Labels.group(status))
                + ", \"confidence\": " + confidence
                + ", \"queue\": " + Json.text(queue)
                + ", \"queueLabel\": " + Json.text(Labels.queue(queue))
                + ", \"stars\": " + Math.round(stars * 10) / 10.0
                + ", \"starCount\": " + starCount
                + ", \"ratingText\": " + Json.text(Labels.rating(stars, starCount))
                + ", \"recentReports\": " + recentReports
                + ", \"recentReportsText\": " + Json.text(Labels.count(recentReports, "report", "reports"))
                + ", \"favorite\": " + favorite;
    }
}
