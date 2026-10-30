package h2go;

/** Writes JSON by hand, for example {"id": 7, "name": "Sentrum Fountain"}. */
public class Json {

    /** A text value in quotes, escaped. */
    public static String text(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c < ' ') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** {"error": "..."} */
    public static String error(String message) {
        return "{\"error\": " + text(message) + "}";
    }
}
