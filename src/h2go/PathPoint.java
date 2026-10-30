package h2go;

/** A point on a campus walkway: a gate, a building entrance or a corner of a path. */
public class PathPoint {

    private int id;
    private String name;
    private double lat;
    private double lng;

    public PathPoint(int id, String name, double lat, double lng) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    /** label: what the map shows on the point, for example "2. Main Gate". */
    public String toJson() {
        return "{\"id\": " + id + ", \"name\": " + Json.text(name) + ", \"lat\": " + lat + ", \"lng\": " + lng
                + ", \"label\": " + Json.text(id + ". " + name) + "}";
    }
}
