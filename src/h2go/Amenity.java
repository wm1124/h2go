package h2go;

import java.util.ArrayList;

/**
 * Amenities as bit flags: one int holds all of a fountain's amenities (1 + 8 + 32 = 41).
 * A fountain has everything you need when (fountain & needs) == needs.
 */
public class Amenity {

    public static final int COLD_WATER = 1;
    public static final int HOT_WATER = 2;
    public static final int SENSOR_TAP = 4;
    public static final int TALL_BOTTLE = 8;
    public static final int FILTERED = 32;       // 16 is no longer used

    public static final int[] ALL = {COLD_WATER, HOT_WATER, SENSOR_TAP, TALL_BOTTLE, FILTERED};
    public static final String[] NAMES = {"Cold water", "Hot water", "Sensor tap", "Tall bottle clearance", "Filtered water"};

    /** True when the fountain has every amenity in "needs". */
    public static boolean hasAll(int fountain, int needs) {
        return (fountain & needs) == needs;
    }

    /** The bits that are on, for example 41 gives 1, 8 and 32. */
    public static ArrayList<Integer> bitsIn(int amenities) {
        ArrayList<Integer> bits = new ArrayList<>();
        for (int bit : ALL) {
            if ((amenities & bit) != 0) {
                bits.add(bit);
            }
        }
        return bits;
    }

    /** True for 1, 2, 4, 8 or 32. */
    public static boolean isAmenity(int bit) {
        for (int a : ALL) {
            if (a == bit) {
                return true;
            }
        }
        return false;
    }

    /** For example "Cold water, Sensor tap". */
    public static String names(int amenities) {
        String text = "";
        for (int i = 0; i < ALL.length; i++) {
            if ((amenities & ALL[i]) != 0) {
                if (!text.isEmpty()) {
                    text += ", ";
                }
                text += NAMES[i];
            }
        }
        return text.isEmpty() ? "None listed" : text;
    }
}
