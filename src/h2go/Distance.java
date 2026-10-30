package h2go;

/**
 * Meters between two places. The campus is small enough to treat as flat: a degree of latitude is
 * 110,574 m, a degree of longitude 111,320 m x cos(latitude), then Pythagoras.
 */
public class Distance {

    public static final double METERS_PER_DEGREE_LAT = 110574;
    public static final double METERS_PER_DEGREE_LNG = 111320 * Math.cos(Math.toRadians(13.94));

    /** Meters east (x). */
    public static double x(double lng) {
        return lng * METERS_PER_DEGREE_LNG;
    }

    /** Meters north (y). */
    public static double y(double lat) {
        return lat * METERS_PER_DEGREE_LAT;
    }

    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double dx = x(lng2) - x(lng1);
        double dy = y(lat2) - y(lat1);
        return Math.sqrt(dx * dx + dy * dy);
    }
}
