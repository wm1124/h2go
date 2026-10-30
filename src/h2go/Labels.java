package h2go;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The words and groups the page shows for H2-Go's codes and numbers, for example NOT_COLD: "Not cold", "problem". */
public class Labels {

    // a full and an empty star
    private static final String FULL_STAR = "\u2605";
    private static final String EMPTY_STAR = "\u2606";

    public static String status(String status) {
        switch (status) {
            case "WORKING":
                return "Working";
            case "NOT_COLD":
                return "Not cold";
            case "LOW_PRESSURE":
                return "Low pressure";
            case "DIRTY":
                return "Dirty";
            case "LEAKING":
                return "Leaking";
            case "BROKEN":
                return "Broken";
            default:
                return "No recent reports";
        }
    }

    /** working, problem, broken or unknown: style.css colors each group. */
    public static String group(String status) {
        switch (status) {
            case "WORKING":
                return "working";
            case "BROKEN":
                return "broken";
            case "UNKNOWN":
                return "unknown";
            default:
                return "problem";
        }
    }

    public static String queue(String queue) {
        switch (queue) {
            case "NONE":
                return "No line";
            case "SHORT":
                return "Short line";
            case "LONG":
                return "Long line";
            default:
                return "Line not reported";
        }
    }

    /** "12 min ago", "6 h ago", "2 days ago" */
    public static String ago(int minutes) {
        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (minutes < Confidence.MINUTES_PER_DAY) {
            return (minutes / 60) + " h ago";
        }
        int days = minutes / Confidence.MINUTES_PER_DAY;
        return days == 1 ? "1 day ago" : days + " days ago";
    }

    /** 4 gives four full stars and one empty one; 0 (no rating) gives "". */
    public static String stars(int stars) {
        if (stars <= 0) {
            return "";
        }
        return FULL_STAR.repeat(stars) + EMPTY_STAR.repeat(5 - stars);
    }

    /** "1 report", "3 reports" */
    public static String count(int number, String one, String many) {
        return number + " " + (number == 1 ? one : many);
    }

    /** The average rating with its count, or "No ratings yet". */
    public static String rating(double stars, int ratings) {
        if (ratings == 0) {
            return "No ratings yet";
        }
        return oneDecimal(stars) + " " + FULL_STAR + " (" + count(ratings, "rating", "ratings") + ")";
    }

    /** 4.56 gives "4.6", 4.0 gives "4". */
    public static String oneDecimal(double number) {
        double rounded = Math.round(number * 10) / 10.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }

    /** "13.94094, 121.14679" */
    public static String position(double lat, double lng) {
        return fiveDecimals(lat) + ", " + fiveDecimals(lng);
    }

    private static String fiveDecimals(double number) {
        return new BigDecimal(number).setScale(5, RoundingMode.HALF_UP).toPlainString();
    }

    /** Minutes rounded to a tenth, then up to a whole minute. */
    public static int walkMinutes(double minutes) {
        return (int) Math.ceil(Math.round(minutes * 10) / 10.0);
    }
}
