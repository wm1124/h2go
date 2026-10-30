package h2go;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;

/**
 * All the SQL (JDBC). The data lives in three MySQL databases, made from sql/h2go.sql:
 *   h2go_stations: stations, path_points, paths
 *   h2go_users:    users, sessions, favorites
 *   h2go_reports:  reports, refills
 * Names are saved encrypted and sign-in tokens as hashes (see Crypto.java).
 */
public class Database {

    // Every user query reads these columns. locked is 1 while locked_until is in the future.
    private static final String USER_COLUMNS = "u.*, u.locked_until > NOW() AS locked,"
            + " DATE_FORMAT(u.locked_until, '%b %e, %Y %l:%i %p') AS locked_until_text";

    // The sample users of older versions of H2-Go. Only the admins stay.
    private static final String[] SAMPLE_USERS = {"ana.reyes", "carlo.mendoza", "liza.santos", "joy.navarro",
            "miguel.cruz"};

    // The 15 sample fountains of older versions. The fountains in sql/h2go.sql replace them.
    private static final String[] SAMPLE_FOUNTAINS = {"Mabini Hall Fountain", "G. Zara Fountain",
            "Chez Rafael Fountain", "Student Center Fountain", "Noli Fili Hall Fountain", "CBEAM Hall Fountain",
            "Sentrum Fountain", "Br. Jaime Hilario Fountain", "Diokno Hall Fountain", "College LRC Fountain",
            "Claro M. Recto Fountain", "St. Mutien Marie Fountain", "BB Hall Fountain", "Oval Fountain",
            "Centennial Courts Fountain"};

    // An online MySQL runs on UTC, so H2-Go sets Philippine time there. NOW() is then campus time,
    // like in XAMPP on a computer in the Philippines.
    private static final String CAMPUS_TIME_ZONE = "+08:00";

    private String url;
    private String host;
    private String user;
    private String password;
    private boolean online;
    private Connection conn;
    private long lastUsed;
    private Crypto crypto;

    private Database(String host, String port, String user, String password, boolean online, Crypto crypto)
            throws SQLException {
        this.url = "jdbc:mysql://" + host + ":" + port + "/?sslMode=" + (online ? "REQUIRED" : "DISABLED")
                + "&allowPublicKeyRetrieval=true&connectTimeout=10000";
        this.host = host;
        this.user = user;
        this.password = password;
        this.online = online;
        this.crypto = crypto;
        connect();
    }

    /** MySQL on this computer (XAMPP). */
    public static Database local(String host, String port, String user, String password, Crypto crypto)
            throws SQLException {
        return new Database(host, port, user, password, false, crypto);
    }

    /**
     * An online MySQL, from its link, for example Aiven's Service URI:
     *     mysql://avnadmin:PASSWORD@mysql-h2go-abc.aivencloud.com:12345/defaultdb?ssl-mode=REQUIRED
     * The connection is encrypted (SSL), as online databases require.
     */
    public static Database online(String link, Crypto crypto) throws SQLException {
        URI uri = URI.create(link.trim());
        if (uri.getHost() == null || uri.getRawUserInfo() == null) {
            throw new IllegalArgumentException("the user, password or host is missing");
        }
        String[] login = uri.getRawUserInfo().split(":", 2);
        String port = uri.getPort() > 0 ? String.valueOf(uri.getPort()) : "3306";
        return new Database(uri.getHost(), port, decode(login[0]), login.length > 1 ? decode(login[1]) : "",
                true, crypto);
    }

    /** %40 back to @ and so on. A + stays a +. */
    private static String decode(String text) {
        return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private void connect() throws SQLException {
        conn = DriverManager.getConnection(url, user, password);
        if (online) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET time_zone = '" + CAMPUS_TIME_ZONE + "'");
            }
        }
        lastUsed = System.currentTimeMillis();
    }

    /**
     * The connection to MySQL. An online MySQL closes connections that sit unused for a few minutes,
     * so after 30 quiet seconds H2-Go checks the connection first, and connects again when it was closed.
     */
    private Connection connection() throws SQLException {
        long now = System.currentTimeMillis();
        if (conn.isClosed() || (now - lastUsed > 30_000 && !conn.isValid(3))) {
            try {
                conn.close();
            } catch (SQLException alreadyGone) {
                // nothing to close
            }
            connect();
        }
        lastUsed = now;
        return conn;
    }

    public String getHost() {
        return host;
    }

    /** For example "MySQL 5.5.5-10.4.28-MariaDB". */
    public String serverVersion() throws SQLException {
        return connection().getMetaData().getDatabaseProductName() + " " + connection().getMetaData().getDatabaseProductVersion();
    }

    /** True when the H2-Go tables are there. */
    public boolean hasTables() {
        try (Statement st = connection().createStatement()) {
            st.executeQuery("SELECT point_id FROM h2go_stations.stations LIMIT 1").close();
            st.executeQuery("SELECT token FROM h2go_users.sessions LIMIT 1").close();
            st.executeQuery("SELECT fixed FROM h2go_reports.reports LIMIT 1").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** True when this MySQL has any of the three H2-Go databases. */
    public boolean hasH2GoDatabases() throws SQLException {
        return count("SELECT COUNT(*) FROM information_schema.SCHEMATA"
                + " WHERE SCHEMA_NAME IN ('h2go_stations', 'h2go_users', 'h2go_reports')") > 0;
    }

    /** Runs every statement in an SQL file: sql/h2go.sql makes the databases in a new MySQL. */
    public void runSqlFile(Path file) throws IOException, SQLException {
        try (Statement st = connection().createStatement()) {
            for (String statement : splitStatements(Files.readString(file))) {
                st.execute(statement);
            }
        }
    }

    /** The statements in SQL text, split at each ; that is not inside 'quotes', without the -- comments. */
    static ArrayList<String> splitStatements(String sql) {
        ArrayList<String> statements = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!inQuotes && sql.startsWith("--", i)) {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                statement.append('\n');
            } else if (!inQuotes && c == ';') {
                addStatement(statements, statement);
            } else {
                if (c == '\'') {
                    inQuotes = !inQuotes;   // '' inside quotes switches twice, so it stays inside
                } else if (c == '\\' && inQuotes && i + 1 < sql.length()) {
                    statement.append(c);
                    c = sql.charAt(++i);    // the character after a \ never ends the quotes
                }
                statement.append(c);
            }
        }
        addStatement(statements, statement);
        return statements;
    }

    private static void addStatement(ArrayList<String> statements, StringBuilder statement) {
        if (!statement.toString().isBlank()) {
            statements.add(statement.toString().trim());
        }
        statement.setLength(0);
    }

    /** Deletes the three H2-Go databases, so a first start that failed half-way can start over. */
    public void dropDatabases() {
        try {
            run("DROP DATABASE IF EXISTS h2go_reports");
            run("DROP DATABASE IF EXISTS h2go_users");
            run("DROP DATABASE IF EXISTS h2go_stations");
        } catch (SQLException e) {
            System.out.println("Could not delete the half-made databases: " + e.getMessage());
        }
    }

    /**
     * Brings a database made by an older H2-Go up to date, and encrypts names that are not encrypted yet
     * (for example the admins' names from sql/h2go.sql). Returns what it changed.
     */
    public ArrayList<String> upgrade(Path sqlFile) throws IOException, SQLException {
        ArrayList<String> changes = new ArrayList<>();
        if (columnLength("h2go_users", "users", "points") >= 0) {
            run("ALTER TABLE h2go_users.users DROP COLUMN points");
            changes.add("removed the points from h2go_users.users");
        }
        int samples = deleteSampleUsers();
        if (samples > 0) {
            changes.add("removed " + Labels.count(samples, "sample user", "sample users")
                    + " with their sign-ins, favorites, reports and refills");
        }
        if (hasSampleFountains()) {
            replaceFountains(sqlFile);
            changes.add("replaced the 15 sample fountains and their walkways with the "
                    + count("SELECT COUNT(*) FROM h2go_stations.stations") + " fountains in sql/h2go.sql");
        }
        if (columnLength("h2go_users", "users", "failed_logins") < 0) {
            run("ALTER TABLE h2go_users.users ADD COLUMN failed_logins INT NOT NULL DEFAULT 0,"
                    + " ADD COLUMN locked_until DATETIME NULL");
            changes.add("added failed_logins and locked_until to h2go_users.users");
        }
        if (columnLength("h2go_users", "users", "password_hash") < 200) {
            run("ALTER TABLE h2go_users.users MODIFY password_hash VARCHAR(200) NOT NULL");
            changes.add("made room for salted password hashes");
        }
        if (columnLength("h2go_users", "users", "full_name") < 400) {
            run("ALTER TABLE h2go_users.users MODIFY full_name VARCHAR(400) NOT NULL");
            changes.add("made room for encrypted names");
        }
        if (columnLength("h2go_users", "sessions", "token") < 64) {
            run("ALTER TABLE h2go_users.sessions MODIFY token CHAR(64) NOT NULL");
            run("UPDATE h2go_users.sessions SET token = SHA2(token, 256) WHERE LENGTH(token) = 36");
            changes.add("saved the sign-in tokens as SHA-256 hashes");
        }
        int names = encryptNames();
        if (names > 0) {
            changes.add("encrypted " + names + (names == 1 ? " name" : " names") + " in h2go_users.users");
        }
        return changes;
    }

    /** Deletes the sample users of older versions and everything they made. Returns how many there were. */
    private int deleteSampleUsers() throws SQLException {
        String theirs = " WHERE user_id IN (SELECT id FROM h2go_users.users WHERE username IN "
                + placeholders(SAMPLE_USERS.length) + ")";
        for (String table : new String[] {"h2go_users.sessions", "h2go_users.favorites", "h2go_reports.reports",
                "h2go_reports.refills"}) {
            update("DELETE FROM " + table + theirs, (Object[]) SAMPLE_USERS);
        }
        return update("DELETE FROM h2go_users.users WHERE username IN " + placeholders(SAMPLE_USERS.length),
                (Object[]) SAMPLE_USERS);
    }

    /** True while the fountains are exactly the 15 sample fountains of older versions. */
    private boolean hasSampleFountains() throws SQLException {
        int samples = count("SELECT COUNT(*) FROM h2go_stations.stations WHERE name IN "
                + placeholders(SAMPLE_FOUNTAINS.length), (Object[]) SAMPLE_FOUNTAINS);
        return samples == SAMPLE_FOUNTAINS.length
                && count("SELECT COUNT(*) FROM h2go_stations.stations") == SAMPLE_FOUNTAINS.length;
    }

    /**
     * Swaps the fountains and the walkway graph for the ones in sql/h2go.sql (its INSERT INTO h2go_stations
     * lines). The reports, refills and favorites of the old fountains go too. All or nothing: a transaction.
     */
    private void replaceFountains(Path sqlFile) throws IOException, SQLException {
        Connection c = connection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM h2go_reports.reports");
            st.executeUpdate("DELETE FROM h2go_reports.refills");
            st.executeUpdate("DELETE FROM h2go_users.favorites");
            st.executeUpdate("DELETE FROM h2go_stations.stations");
            st.executeUpdate("DELETE FROM h2go_stations.paths");
            st.executeUpdate("DELETE FROM h2go_stations.path_points");
            for (String statement : splitStatements(Files.readString(sqlFile))) {
                if (statement.startsWith("INSERT INTO h2go_stations.")) {
                    st.executeUpdate(statement);
                }
            }
            c.commit();
        } catch (IOException | SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** The column's length (0 for a number column), or -1 when there is no such column. */
    private int columnLength(String database, String table, String column) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, database);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private int encryptNames() throws SQLException {
        int count = 0;
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, full_name FROM h2go_users.users WHERE full_name NOT LIKE 'aes:%'");
             PreparedStatement update = connection().prepareStatement("UPDATE h2go_users.users SET full_name = ? WHERE id = ?")) {
            while (rs.next()) {
                update.setString(1, crypto.encrypt(rs.getString("full_name")));
                update.setInt(2, rs.getInt("id"));
                update.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private void run(String sql) throws SQLException {
        try (Statement st = connection().createStatement()) {
            st.executeUpdate(sql);
        }
    }

    // ================================================================ h2go_stations

    public ArrayList<PathPoint> loadPathPoints() throws SQLException {
        ArrayList<PathPoint> list = new ArrayList<>();
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, lat, lng FROM h2go_stations.path_points ORDER BY id")) {
            while (rs.next()) {
                list.add(new PathPoint(rs.getInt("id"), rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng")));
            }
        }
        return list;
    }

    /** Each walkway as {from point, to point}. */
    public ArrayList<int[]> loadPaths() throws SQLException {
        ArrayList<int[]> list = new ArrayList<>();
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT from_point, to_point FROM h2go_stations.paths")) {
            while (rs.next()) {
                list.add(new int[] {rs.getInt("from_point"), rs.getInt("to_point")});
            }
        }
        return list;
    }

    public ArrayList<Station> loadStations() throws SQLException {
        ArrayList<Station> list = new ArrayList<>();
        String sql = "SELECT id, name, building, floor, lat, lng, amenities, point_id FROM h2go_stations.stations ORDER BY id";
        try (Statement st = connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Station(rs.getInt("id"), rs.getString("name"), rs.getString("building"), rs.getInt("floor"),
                        rs.getDouble("lat"), rs.getDouble("lng"), rs.getInt("amenities"), rs.getInt("point_id")));
            }
        }
        return list;
    }

    // ================================================================ h2go_users

    private User readUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
                crypto.decrypt(rs.getString("full_name")), rs.getString("role"), rs.getString("bottle"),
                rs.getInt("needs"), rs.getString("created_at"), rs.getString("last_login"),
                rs.getInt("failed_logins"), rs.getString("locked_until_text"), rs.getBoolean("locked"));
    }

    private User findUser(String where, Object value) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM h2go_users.users u WHERE " + where;
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readUser(rs) : null;
            }
        }
    }

    /** The user with this username, or null. */
    public User findUser(String username) throws SQLException {
        return findUser("u.username = ?", username);
    }

    /** The user with this id, or null. */
    public User findUser(int id) throws SQLException {
        return findUser("u.id = ?", id);
    }

    /** The user signed in with this cookie token, or null. Only the token's hash is in MySQL. */
    public User findUserByToken(String token) throws SQLException {
        return findUser("u.id = (SELECT user_id FROM h2go_users.sessions WHERE token = ?)", Crypto.sha256(token));
    }

    /** A new account (role USER). The name is saved encrypted. */
    public void addUser(String username, String passwordHash, String fullName) throws SQLException {
        String sql = "INSERT INTO h2go_users.users (username, password_hash, full_name, role, created_at)"
                + " VALUES (?, ?, ?, 'USER', NOW())";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, crypto.encrypt(fullName));
            ps.executeUpdate();
        }
    }

    public void setPasswordHash(int userId, String passwordHash) throws SQLException {
        update("UPDATE h2go_users.users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    /** Gives every admin account this password (H2GO_ADMIN_PASSWORD). Returns how many there are. */
    public int setAdminPasswords(String passwordHash) throws SQLException {
        return update("UPDATE h2go_users.users SET password_hash = ? WHERE role = 'ADMIN'", passwordHash);
    }

    /** A sign-in: saves the token's hash, the time, and clears the wrong-password count. */
    public void addSession(String token, int userId) throws SQLException {
        update("INSERT INTO h2go_users.sessions (token, user_id, created_at) VALUES (?, ?, NOW())",
                Crypto.sha256(token), userId);
        update("UPDATE h2go_users.users SET last_login = NOW(), failed_logins = 0, locked_until = NULL WHERE id = ?",
                userId);
    }

    public void deleteSession(String token) throws SQLException {
        update("DELETE FROM h2go_users.sessions WHERE token = ?", Crypto.sha256(token));
    }

    public void setFailedLogins(int userId, int failedLogins) throws SQLException {
        update("UPDATE h2go_users.users SET failed_logins = ?, locked_until = NULL WHERE id = ?", failedLogins, userId);
    }

    /** Locks the account for this many hours. */
    public void lockUser(int userId, int failedLogins, int hours) throws SQLException {
        update("UPDATE h2go_users.users SET failed_logins = ?, locked_until = NOW() + INTERVAL ? HOUR WHERE id = ?",
                failedLogins, hours, userId);
    }

    public void savePreferences(int userId, String bottle, int needs) throws SQLException {
        update("UPDATE h2go_users.users SET bottle = ?, needs = ? WHERE id = ?", bottle, needs, userId);
    }

    /** The ids of the user's favorite fountains. */
    public ArrayList<Integer> favorites(int userId) throws SQLException {
        ArrayList<Integer> list = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement("SELECT station_id FROM h2go_users.favorites WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("station_id"));
                }
            }
        }
        return list;
    }

    /** Adds or removes a favorite. True when it is a favorite now. */
    public boolean toggleFavorite(int userId, int stationId) throws SQLException {
        boolean wasFavorite = favorites(userId).contains(stationId);
        update(wasFavorite
                ? "DELETE FROM h2go_users.favorites WHERE user_id = ? AND station_id = ?"
                : "INSERT INTO h2go_users.favorites (user_id, station_id) VALUES (?, ?)", userId, stationId);
        return !wasFavorite;
    }

    // ================================================================ h2go_reports

    /** Every report, newest first, with the reporter's name and the report's age in minutes. */
    public ArrayList<Report> loadReports() throws SQLException {
        ArrayList<Report> list = new ArrayList<>();
        String sql = "SELECT r.id, r.station_id, u.full_name, r.status, r.queue, r.stars, r.comment, r.fixed,"
                + " TIMESTAMPDIFF(MINUTE, r.created_at, NOW()) AS minutes_ago"
                + " FROM h2go_reports.reports r JOIN h2go_users.users u ON u.id = r.user_id"
                + " ORDER BY r.created_at DESC, r.id DESC";
        try (Statement st = connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Report(rs.getInt("id"), rs.getInt("station_id"), crypto.decrypt(rs.getString("full_name")),
                        rs.getString("status"), rs.getString("queue"), rs.getInt("stars"), rs.getString("comment"),
                        rs.getInt("minutes_ago"), rs.getBoolean("fixed")));
            }
        }
        return list;
    }

    public void addReport(int stationId, int userId, String status, String queue, int stars, String comment)
            throws SQLException {
        update("INSERT INTO h2go_reports.reports (station_id, user_id, status, queue, stars, comment, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, NOW())", stationId, userId, status, queue, stars, comment);
    }

    /** Marks every open problem report of a fountain as fixed. */
    public void markFixed(int stationId) throws SQLException {
        update("UPDATE h2go_reports.reports SET fixed = TRUE WHERE station_id = ? AND status <> 'WORKING'", stationId);
    }

    /** False when there is no report with that id. */
    public boolean deleteReport(int reportId) throws SQLException {
        return update("DELETE FROM h2go_reports.reports WHERE id = ?", reportId) > 0;
    }

    public void addRefill(int stationId, int userId) throws SQLException {
        update("INSERT INTO h2go_reports.refills (station_id, user_id, created_at) VALUES (?, ?, NOW())",
                stationId, userId);
    }

    /** Runs a SELECT COUNT(*) with ? placeholders. */
    private int count(String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** "(?, ?, ?)" for 3 values. */
    private static String placeholders(int howMany) {
        return "(" + String.join(", ", Collections.nCopies(howMany, "?")) + ")";
    }

    /** Runs an INSERT, UPDATE or DELETE with ? placeholders. Returns how many rows it changed. */
    private int update(String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            return ps.executeUpdate();
        }
    }
}
