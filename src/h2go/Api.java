package h2go;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;

/**
 * The addresses the page calls (/api/...). Each answers with JSON that is ready to show.
 *
 *   POST /api/login        username, password                  sign in
 *   POST /api/signup       fullName, username, password        make an account and sign in
 *   POST /api/logout                                           sign out
 *   GET  /api/me                                               who is signed in
 *   GET  /api/stations                                         every fountain, and the map area
 *   GET  /api/station      id                                  one fountain and its latest reports
 *   GET  /api/recommend    lat, lng, need, bottle, hideBroken  the best fountains for you
 *   GET  /api/route        lat, lng, id                        the walking route to a fountain
 *   GET  /api/paths                                            the walkway graph and where You start
 *   POST /api/report       id, status, queue, stars, comment   report a fountain
 *   POST /api/refill       id                                  "I refilled here"
 *   POST /api/favorite     id                                  add or remove a favorite
 *   GET  /api/favorites    lat, lng                            your favorites, nearest first, and the map area
 *   POST /api/preferences  bottle, need                        save your preferences
 *   GET  /api/profile                                          your account
 *   GET  /api/maintenance                                      problems, most urgent first
 *   POST /api/fix          id                                  mark a fountain fixed (admins)
 *   POST /api/deleteReport id                                  remove a report (admins)
 *
 * Everything except login, signup and me needs the sign-in cookie.
 */
public class Api {

    private static final String[] QUEUES = {"NONE", "SHORT", "LONG", "UNKNOWN"};
    private static final String[] BOTTLES = {"SMALL", "MEDIUM", "LARGE"};
    private static final double OUTSIDE_CAMPUS_METERS = 300;
    private static final int MAX_WRONG_PASSWORDS = 3;
    private static final int LOCK_HOURS = 24;
    private static final double MIN_BOX = 0.0006;   // degrees, about 60 m

    private H2Go app;
    private Database db;

    public Api(H2Go app) {
        this.app = app;
        this.db = app.getDatabase();
    }

    /** Answers one request to /api/... */
    public void handle(HttpExchange exchange, String path) throws IOException, SQLException {
        app.reload();   // the data may have changed in phpMyAdmin
        HashMap<String, String> params = readParams(exchange);
        User user = signedInUser(exchange);
        boolean isPost = exchange.getRequestMethod().equals("POST");
        String json;
        try {
            if (path.equals("/api/login") || path.equals("/api/signup") || path.equals("/api/logout")) {
                if (!isPost) {
                    WebServer.send(exchange, 405, "application/json", Json.error("Send " + path + " with POST."));
                    return;
                }
                if (path.equals("/api/login")) {
                    json = login(exchange, params);
                } else if (path.equals("/api/signup")) {
                    json = signup(exchange, params);
                } else {
                    json = logout(exchange);
                }
            } else if (path.equals("/api/me")) {
                json = me(user);
            } else if (user == null) {
                WebServer.send(exchange, 401, "application/json", Json.error("Please sign in first."));
                return;
            } else if (path.equals("/api/stations")) {
                json = stations(user);
            } else if (path.equals("/api/station")) {
                json = station(params, user);
            } else if (path.equals("/api/recommend")) {
                json = recommend(params);
            } else if (path.equals("/api/route")) {
                json = route(params);
            } else if (path.equals("/api/paths")) {
                json = paths();
            } else if (path.equals("/api/report") && isPost) {
                json = report(params, user);
            } else if (path.equals("/api/refill") && isPost) {
                json = refill(params, user);
            } else if (path.equals("/api/favorite") && isPost) {
                json = favorite(params, user);
            } else if (path.equals("/api/favorites")) {
                json = favorites(params, user);
            } else if (path.equals("/api/preferences") && isPost) {
                json = preferences(params, user);
            } else if (path.equals("/api/profile")) {
                json = profile(user);
            } else if (path.equals("/api/maintenance")) {
                json = maintenance(user);
            } else if (path.equals("/api/fix") && isPost) {
                if (!user.isAdmin()) {
                    WebServer.send(exchange, 403, "application/json", Json.error("Only admins can mark a fountain fixed."));
                    return;
                }
                json = fix(params, user);
            } else if (path.equals("/api/deleteReport") && isPost) {
                if (!user.isAdmin()) {
                    WebServer.send(exchange, 403, "application/json", Json.error("Only admins can remove comments."));
                    return;
                }
                json = deleteReport(params);
            } else {
                WebServer.send(exchange, 404, "application/json", Json.error("There is no " + path + " (or it needs POST)."));
                return;
            }
        } catch (IllegalArgumentException e) {
            WebServer.send(exchange, 400, "application/json", Json.error(e.getMessage()));
            return;
        }
        WebServer.send(exchange, 200, "application/json", json);
    }

    // ================================================================ signing in

    /** Signs in. 3 wrong passwords in a row lock the account for 24 hours, even for the right password. */
    private String login(HttpExchange exchange, HashMap<String, String> params) throws SQLException {
        User user = db.findUser(text(params, "username").trim().toLowerCase());
        String password = text(params, "password");
        if (user != null && user.isLocked()) {
            throw new IllegalArgumentException("This account is locked until " + user.getLockedUntil()
                    + " because of " + MAX_WRONG_PASSWORDS + " wrong passwords.");
        }
        if (user == null || !user.checkPassword(password)) {
            if (user != null) {
                int wrong = (user.lockEnded() ? 0 : user.getFailedLogins()) + 1;
                if (wrong >= MAX_WRONG_PASSWORDS) {
                    db.lockUser(user.getId(), wrong, LOCK_HOURS);
                    throw new IllegalArgumentException(MAX_WRONG_PASSWORDS + " wrong passwords in a row: this account"
                            + " is locked for " + LOCK_HOURS + " hours, until "
                            + db.findUser(user.getId()).getLockedUntil() + ".");
                }
                db.setFailedLogins(user.getId(), wrong);
            }
            throw new IllegalArgumentException("Wrong username or password. " + MAX_WRONG_PASSWORDS
                    + " wrong passwords in a row lock the account for " + LOCK_HOURS + " hours.");
        }
        if (user.hasOldPasswordHash()) {
            db.setPasswordHash(user.getId(), Crypto.hashPassword(password));
        }
        startSession(exchange, user);
        return "{\"user\": " + db.findUser(user.getId()).toJson() + "}";
    }

    private String signup(HttpExchange exchange, HashMap<String, String> params) throws SQLException {
        String fullName = text(params, "fullName").trim();
        String username = text(params, "username").trim().toLowerCase();
        String password = text(params, "password");
        if (fullName.isEmpty() || fullName.length() > 60) {
            throw new IllegalArgumentException("Please type your name (up to 60 characters).");
        }
        if (!username.matches("[a-z0-9._]{3,30}")) {
            throw new IllegalArgumentException("A username has 3 to 30 letters, numbers, dots or underscores.");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("A password needs at least 6 characters.");
        }
        if (db.findUser(username) != null) {
            throw new IllegalArgumentException("The username " + username + " is taken. Please pick another one.");
        }
        db.addUser(username, Crypto.hashPassword(password), fullName);
        User user = db.findUser(username);
        startSession(exchange, user);
        return "{\"user\": " + db.findUser(user.getId()).toJson() + "}";
    }

    /** A random token for the browser's cookie. MySQL keeps only its hash. */
    private void startSession(HttpExchange exchange, User user) throws SQLException {
        String token = UUID.randomUUID().toString();
        db.addSession(token, user.getId());
        exchange.getResponseHeaders().add("Set-Cookie", "token=" + token + cookieSettings(exchange));
    }

    /** Online (https) the cookie also gets Secure, so the browser only ever sends it encrypted. */
    private static String cookieSettings(HttpExchange exchange) {
        boolean https = "https".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        return "; Path=/; HttpOnly; SameSite=Lax" + (https ? "; Secure" : "");
    }

    private String logout(HttpExchange exchange) throws SQLException {
        String token = tokenOf(exchange);
        if (token != null) {
            db.deleteSession(token);
        }
        exchange.getResponseHeaders().add("Set-Cookie", "token=" + cookieSettings(exchange) + "; Max-Age=0");
        return "{\"signedIn\": false}";
    }

    private String me(User user) {
        if (user == null) {
            return "{\"signedIn\": false}";
        }
        return "{\"signedIn\": true, \"user\": " + user.toJson() + "}";
    }

    /** The token from the browser's cookie, or null. */
    private String tokenOf(HttpExchange exchange) {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String cookie : cookies.split(";")) {
            cookie = cookie.trim();
            if (cookie.startsWith("token=")) {
                return cookie.substring("token=".length());
            }
        }
        return null;
    }

    private User signedInUser(HttpExchange exchange) throws SQLException {
        String token = tokenOf(exchange);
        if (token == null) {
            return null;
        }
        return db.findUserByToken(token);
    }

    // ================================================================ fountains

    /** Every fountain, and the map area that shows them all with 20% extra around it. */
    private String stations(User user) throws SQLException {
        app.updateStatuses();
        ArrayList<Integer> favorites = db.favorites(user.getId());
        ArrayList<double[]> spots = new ArrayList<>();
        StringBuilder json = new StringBuilder("{\"stations\": [");
        for (int i = 0; i < app.getStations().size(); i++) {
            Station s = app.getStations().get(i);
            if (i > 0) {
                json.append(", ");
            }
            json.append(s.toJson(favorites.contains(s.getId())));
            spots.add(new double[] {s.getLat(), s.getLng()});
        }
        return json.append("], \"bounds\": ").append(bounds(spots, 0.2)).append("}").toString();
    }

    /** One fountain with its 8 newest reports. */
    private String station(HashMap<String, String> params, User user) throws SQLException {
        Station s = stationParam(params);
        ArrayList<Report> reports = app.updateStatuses();
        StringBuilder list = new StringBuilder("[");
        int shown = 0;
        for (Report r : reports) {
            if (r.getStationId() == s.getId() && shown < 8) {
                if (shown > 0) {
                    list.append(", ");
                }
                list.append(r.toJson(user.isAdmin()));
                shown++;
            }
        }
        list.append("]");
        boolean favorite = db.favorites(user.getId()).contains(s.getId());
        return "{" + s.jsonFields(favorite) + ", \"reports\": " + list + "}";
    }

    private String recommend(HashMap<String, String> params) throws SQLException {
        double lat = number(params, "lat");
        double lng = number(params, "lng");
        int needs = needs(params);
        String bottle = oneOf(params, "bottle", BOTTLES, "MEDIUM");
        boolean hideBroken = "true".equals(params.get("hideBroken"));   // a form sends only ticked boxes

        app.updateStatuses();
        ArrayList<Recommender.Choice> best = Recommender.recommend(app, lat, lng, needs, bottle, hideBroken, 5);
        int nearby = app.getStationTree().within(lat, lng, Recommender.SEARCH_RADIUS).size();
        PathPoint start = app.nearestPathPoint(lat, lng);
        long toWalkway = Math.round(Distance.meters(lat, lng, start.getLat(), start.getLng()));

        StringBuilder json = new StringBuilder("{\"position\": " + Json.text(Labels.position(lat, lng))
                + ", \"nearby\": " + nearby
                + ", \"metersToWalkway\": " + toWalkway
                + ", \"outsideCampus\": " + (toWalkway > OUTSIDE_CAMPUS_METERS)
                + ", \"results\": [");
        for (int i = 0; i < best.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(best.get(i).toJson());
        }
        return json.append("]}").toString();
    }

    private String route(HashMap<String, String> params) throws SQLException {
        double lat = number(params, "lat");
        double lng = number(params, "lng");
        Station s = stationParam(params);
        PathPoint start = app.nearestPathPoint(lat, lng);
        CampusGraph.ShortestPaths walks = app.getGraph().dijkstra(start.getId());
        double meters = app.walkingMeters(lat, lng, start, walks, s);
        if (meters == Double.MAX_VALUE) {
            throw new IllegalArgumentException("No walkway reaches " + s.getName() + ".");
        }
        double minutes = meters / Recommender.METERS_PER_MINUTE;

        // you, the walkway points, then the fountain
        ArrayList<double[]> line = new ArrayList<>();
        line.add(new double[] {lat, lng});
        for (PathPoint p : walks.pathTo(s.getPointId())) {
            line.add(new double[] {p.getLat(), p.getLng()});
        }
        line.add(new double[] {s.getLat(), s.getLng()});

        return "{\"stationId\": " + s.getId() + ", \"meters\": " + Math.round(meters)
                + ", \"minutes\": " + Math.round(minutes * 10) / 10.0
                + ", \"walkMinutes\": " + Labels.walkMinutes(minutes)
                + ", \"line\": " + spotsJson(line)
                + ", \"bounds\": " + bounds(line, 0.3) + "}";
    }

    /** The walkway graph to draw, and where You start (the Main Gate). */
    private String paths() throws SQLException {
        ArrayList<PathPoint> points = app.getPathPoints();
        PathPoint start = points.get(0);
        StringBuilder json = new StringBuilder("{\"points\": [");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(points.get(i).toJson());
            if (points.get(i).getName().equals("Main Gate")) {
                start = points.get(i);
            }
        }
        json.append("], \"paths\": [");
        StringBuilder lines = new StringBuilder("[");
        ArrayList<int[]> paths = db.loadPaths();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                json.append(", ");
                lines.append(", ");
            }
            PathPoint a = app.getGraph().getPoint(paths.get(i)[0]);
            PathPoint b = app.getGraph().getPoint(paths.get(i)[1]);
            json.append("[").append(a.getId()).append(", ").append(b.getId()).append("]");
            lines.append("[[").append(a.getLat()).append(", ").append(a.getLng()).append("], [")
                    .append(b.getLat()).append(", ").append(b.getLng()).append("]]");
        }
        lines.append("]");
        return json.append("], \"lines\": ").append(lines)
                .append(", \"walkways\": ").append(app.getGraph().getWalkwayCount())
                .append(", \"start\": ").append(start.toJson()).append("}").toString();
    }

    // ================================================================ reports, refills, favorites, preferences

    private String report(HashMap<String, String> params, User user) throws SQLException {
        Station s = stationParam(params);
        String status = oneOf(params, "status", Confidence.STATUSES, null);
        String queue = oneOf(params, "queue", QUEUES, "UNKNOWN");
        int stars = (int) numberOr(params, "stars", 0);
        if (stars < 0 || stars > 5) {
            throw new IllegalArgumentException("Stars go from 1 to 5 (or 0 for no rating).");
        }
        String comment = params.getOrDefault("comment", "").trim();
        if (comment.length() > 200) {
            throw new IllegalArgumentException("Please keep the comment under 200 characters.");
        }
        db.addReport(s.getId(), user.getId(), status, queue, stars, comment);
        return "{\"message\": \"Thanks for the report!\"}";
    }

    private String refill(HashMap<String, String> params, User user) throws SQLException {
        Station s = stationParam(params);
        db.addRefill(s.getId(), user.getId());
        return "{\"message\": \"Refill saved: one less plastic bottle.\"}";
    }

    private String favorite(HashMap<String, String> params, User user) throws SQLException {
        Station s = stationParam(params);
        boolean nowFavorite = db.toggleFavorite(user.getId(), s.getId());
        String message = nowFavorite ? "Added to your favorites." : "Removed from your favorites.";
        return "{\"favorite\": " + nowFavorite + ", \"message\": " + Json.text(message) + "}";
    }

    private String preferences(HashMap<String, String> params, User user) throws SQLException {
        String bottle = oneOf(params, "bottle", BOTTLES, "MEDIUM");
        int needs = needs(params);
        db.savePreferences(user.getId(), bottle, needs);
        return "{\"message\": \"Preferences saved.\", \"user\": " + db.findUser(user.getId()).toJson() + "}";
    }

    /** The My profile tab: the account and its preferences. */
    private String profile(User user) throws SQLException {
        return db.findUser(user.getId()).toJson();
    }

    /**
     * The Favorites tab: the user's favorite fountains, nearest first, each with its walk from You (lat, lng),
     * and the map area that shows them and You.
     */
    private String favorites(HashMap<String, String> params, User user) throws SQLException {
        double lat = number(params, "lat");
        double lng = number(params, "lng");
        app.updateStatuses();
        ArrayList<Integer> favorites = db.favorites(user.getId());
        PathPoint start = app.nearestPathPoint(lat, lng);
        CampusGraph.ShortestPaths walks = app.getGraph().dijkstra(start.getId());

        ArrayList<Station> list = new ArrayList<>();
        for (Station s : app.getStations()) {
            if (favorites.contains(s.getId())) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparingDouble(s -> app.walkingMeters(lat, lng, start, walks, s)));

        ArrayList<double[]> spots = new ArrayList<>();
        spots.add(new double[] {lat, lng});
        StringBuilder json = new StringBuilder("{\"favorites\": [");
        for (int i = 0; i < list.size(); i++) {
            Station s = list.get(i);
            double meters = app.walkingMeters(lat, lng, start, walks, s);
            if (i > 0) {
                json.append(", ");
            }
            boolean reachable = meters != Double.MAX_VALUE;
            json.append("{").append(s.jsonFields(true))
                    .append(", \"reachable\": ").append(reachable)
                    .append(", \"walkMinutes\": ").append(reachable ? Labels.walkMinutes(meters / Recommender.METERS_PER_MINUTE) : 0)
                    .append("}");
            spots.add(new double[] {s.getLat(), s.getLng()});
        }
        return json.append("], \"bounds\": ").append(bounds(spots, 0.25)).append("}").toString();
    }

    // ================================================================ maintenance

    private String maintenance(User user) throws SQLException {
        ArrayList<Report> reports = app.updateStatuses();
        ArrayList<Maintenance.Issue> issues = Maintenance.issues(app.getStations(), reports);
        StringBuilder json = new StringBuilder("{\"issues\": [");
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(issues.get(i).toJson(user.isAdmin()));
        }
        boolean showAdminNote = !issues.isEmpty() && !user.isAdmin();
        return json.append("], \"showAdminNote\": ").append(showAdminNote).append("}").toString();
    }

    private String fix(HashMap<String, String> params, User admin) throws SQLException {
        Station s = stationParam(params);
        db.markFixed(s.getId());
        db.addReport(s.getId(), admin.getId(), "WORKING", "UNKNOWN", 0, "Fixed by maintenance");
        return "{\"message\": " + Json.text(s.getName() + " is marked fixed.") + "}";
    }

    /** Deletes a report and its comment. */
    private String deleteReport(HashMap<String, String> params) throws SQLException {
        int id = (int) number(params, "id");
        if (!db.deleteReport(id)) {
            throw new IllegalArgumentException("There is no report number " + id + ".");
        }
        return "{\"message\": \"The comment was removed.\"}";
    }

    // ================================================================ map areas

    /** [[lat, lng], [lat, lng], ...] */
    private static String spotsJson(ArrayList<double[]> spots) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < spots.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append("[").append(spots.get(i)[0]).append(", ").append(spots.get(i)[1]).append("]");
        }
        return json.append("]").toString();
    }

    /**
     * The box around the spots plus "extra" (0.2 = 20%) on every side: [[south, west], [north, east]].
     * It is at least about 60 m wide and high, so one spot alone doesn't zoom the map all the way in.
     */
    private static String bounds(ArrayList<double[]> spots, double extra) {
        double south = spots.get(0)[0];
        double north = south;
        double west = spots.get(0)[1];
        double east = west;
        for (double[] spot : spots) {
            south = Math.min(south, spot[0]);
            north = Math.max(north, spot[0]);
            west = Math.min(west, spot[1]);
            east = Math.max(east, spot[1]);
        }
        double extraLat = Math.max((north - south) * extra, (MIN_BOX - (north - south)) / 2);
        double extraLng = Math.max((east - west) * extra, (MIN_BOX - (east - west)) / 2);
        return "[[" + (south - extraLat) + ", " + (west - extraLng) + "], ["
                + (north + extraLat) + ", " + (east + extraLng) + "]]";
    }

    // ================================================================ reading what the page sent

    /** The parameters from the address and, for POST, from the form. */
    private HashMap<String, String> readParams(HttpExchange exchange) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        addParams(params, exchange.getRequestURI().getRawQuery());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        addParams(params, body);
        return params;
    }

    /** "a=1&b=2" becomes a=1, b=2. A name sent twice (need=1&need=8) becomes need=1,8. */
    private void addParams(HashMap<String, String> params, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (params.containsKey(name)) {
                value = params.get(name) + "," + value;
            }
            params.put(name, value);
        }
    }

    /** The ticked amenities joined with bitwise OR: need=1,8 gives 1 | 8 = 9. */
    private int needs(HashMap<String, String> params) {
        String value = params.get("need");
        int needs = 0;
        if (value == null || value.isEmpty()) {
            return needs;
        }
        for (String part : value.split(",")) {
            int bit;
            try {
                bit = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("need must be a number.");
            }
            if (!Amenity.isAmenity(bit)) {
                throw new IllegalArgumentException("need can't be " + bit + ".");
            }
            needs = needs | bit;
        }
        return needs;
    }

    private String text(HashMap<String, String> params, String name) {
        String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + name + ".");
        }
        return value;
    }

    private double number(HashMap<String, String> params, String name) {
        try {
            return Double.parseDouble(text(params, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
    }

    private double numberOr(HashMap<String, String> params, String name, double fallback) {
        String value = params.get(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return number(params, name);
    }

    /** One of the allowed words, or the fallback when missing (a null fallback means it is required). */
    private String oneOf(HashMap<String, String> params, String name, String[] allowed, String fallback) {
        String value = params.get(name);
        if (value == null || value.isEmpty()) {
            if (fallback == null) {
                throw new IllegalArgumentException("Missing " + name + ".");
            }
            return fallback;
        }
        for (String a : allowed) {
            if (a.equals(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException(name + " can't be " + value + ".");
    }

    /** The fountain named by the id parameter. */
    private Station stationParam(HashMap<String, String> params) {
        int id = (int) number(params, "id");
        Station s = app.getStation(id);
        if (s == null) {
            throw new IllegalArgumentException("There is no fountain number " + id + ".");
        }
        return s;
    }
}
