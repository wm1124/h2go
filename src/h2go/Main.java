package h2go;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

/**
 * H2-Go: find a nearby, working water refill station on campus.
 *
 * On this computer: start MySQL in XAMPP, run this class and open http://localhost:8080.
 * The settings are in h2go.properties.
 *
 * Online: a website host (Render) runs it with the Dockerfile and these settings (environment variables):
 *   PORT                 the port to use (Render sets it)
 *   H2GO_DB_URL          the online MySQL's link, for example Aiven's Service URI
 *   H2GO_KEY             the secret key for the names (Render makes it, see render.yaml)
 *   H2GO_ADMIN_PASSWORD  the password of the admin accounts (optional)
 */
public class Main {

    private static final Path SQL_FILE = Paths.get("sql", "h2go.sql");

    public static void main(String[] args) {
        if (!start(System.getenv("PORT") != null)) {
            System.exit(1);
        }
    }

    /** False when H2-Go could not start (the console says why). */
    private static boolean start(boolean online) {
        Properties settings = online ? new Properties() : readSettings();
        Crypto crypto = online ? keyFromHost() : keyFromFile();
        if (crypto == null) {
            return false;
        }
        Database db = online ? connectOnline(crypto) : connectHere(settings, crypto);
        if (db == null || !makeDatabasesIfNew(db, online)) {
            return false;
        }

        String serverHost = online ? "0.0.0.0" : setting(settings, "server.host", "localhost");
        int serverPort = Integer.parseInt(online ? System.getenv("PORT") : setting(settings, "server.port", "8080"));
        try {
            for (String change : db.upgrade(SQL_FILE)) {
                System.out.println("Updated the database: " + change + ".");
            }
            String adminLogin = setAdminPassword(db);
            H2Go app = new H2Go(db);
            app.reload();
            new WebServer(app, serverHost, serverPort).start();

            System.out.println();
            System.out.println("H2-Go is running: " + app.getStations().size() + " fountains, "
                    + app.getPathPoints().size() + " walkway points, " + app.getGraph().getWalkwayCount() + " walkways.");
            if (online) {
                String link = System.getenv("RENDER_EXTERNAL_URL");   // Render's link, for example https://h2go.onrender.com
                System.out.println("Open " + (link != null ? link : "this website's link") + " on any phone or computer.");
            } else {
                System.out.println("The data is in MySQL: h2go_stations, h2go_users and h2go_reports (see them in phpMyAdmin).");
                System.out.println("Open http://localhost:" + serverPort + " in your browser.");
                if (!serverHost.equals("localhost") && !serverHost.equals("127.0.0.1")) {
                    printWifiLinks(serverPort);
                }
            }
            System.out.println("Admin login: " + adminLogin);
            if (!online) {
                System.out.println("Stop it with the red stop button in IntelliJ (or Ctrl+C).");
            }
            return true;
        } catch (Exception e) {
            System.out.println("H2-Go could not start: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                System.out.println("Another program uses port " + serverPort + ". Stop it, or change server.port"
                        + " in h2go.properties.");
            }
            return false;
        }
    }

    private static Properties readSettings() {
        Properties settings = new Properties();
        try (FileInputStream in = new FileInputStream("h2go.properties")) {
            settings.load(in);
        } catch (IOException e) {
            System.out.println("h2go.properties was not found, so the default settings are used.");
        }
        return settings;
    }

    private static String setting(Properties settings, String name, String standard) {
        return settings.getProperty(name, standard).trim();
    }

    /** On this computer the key is in h2go.key (made the first time). */
    private static Crypto keyFromFile() {
        try {
            return Crypto.loadKey(Paths.get("h2go.key"));
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Could not read the secret key in h2go.key: " + e.getMessage());
            return null;
        }
    }

    /** Online the key is in H2GO_KEY: a website host deletes its files at every restart, so h2go.key would not last. */
    private static Crypto keyFromHost() {
        String key = System.getenv("H2GO_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("H2GO_KEY is not set. In Render, open the H2-Go service > Environment and add H2GO_KEY"
                    + " with the line in your h2go.key file (or any long random text) as the value.");
            return null;
        }
        return Crypto.fromText(key);
    }

    private static Database connectHere(Properties settings, Crypto crypto) {
        String dbHost = setting(settings, "db.host", "localhost");
        String dbPort = setting(settings, "db.port", "3306");
        String dbUser = setting(settings, "db.user", "root");
        System.out.println("H2-Go is connecting to MySQL at " + dbHost + ":" + dbPort + " as " + dbUser + " ...");
        try {
            Database db = Database.local(dbHost, dbPort, dbUser, setting(settings, "db.password", ""), crypto);
            System.out.println("Connected to " + db.serverVersion() + ".");
            return db;
        } catch (SQLException e) {
            System.out.println("Could not connect to MySQL: " + e.getMessage());
            if (e.getMessage().contains("No suitable driver")) {
                System.out.println("The MySQL driver is missing. In IntelliJ: File > Project Structure > Libraries > +"
                        + " > From Maven > com.mysql:mysql-connector-j");
            } else {
                System.out.println("Is MySQL running (in XAMPP: Start MySQL)? Are db.port, db.user and db.password"
                        + " in h2go.properties right?");
            }
            return null;
        }
    }

    private static Database connectOnline(Crypto crypto) {
        String link = System.getenv("H2GO_DB_URL");
        if (link == null || link.isBlank()) {
            System.out.println("H2GO_DB_URL is not set. In Render, open the H2-Go service > Environment and add"
                    + " H2GO_DB_URL with your online MySQL's link (in Aiven: the Service URI) as the value.");
            return null;
        }
        try {
            Database db = Database.online(link, crypto);
            System.out.println("Connected to " + db.serverVersion() + " at " + db.getHost() + ".");
            return db;
        } catch (IllegalArgumentException e) {
            System.out.println("H2GO_DB_URL should look like mysql://user:password@host:port/defaultdb ("
                    + e.getMessage() + ").");
        } catch (SQLException e) {
            System.out.println("Could not connect to the online MySQL: " + e.getMessage());
            System.out.println("Is H2GO_DB_URL right, and is the database running? (In Aiven it should say Running.)");
        }
        return null;
    }

    /** Makes the H2-Go databases from sql/h2go.sql when this MySQL has none of them yet (a new MySQL). */
    private static boolean makeDatabasesIfNew(Database db, boolean online) {
        if (db.hasTables()) {
            return true;
        }
        try {
            if (db.hasH2GoDatabases()) {
                System.out.println("The H2-Go databases in this MySQL are incomplete or from an older H2-Go.");
                System.out.println(online
                        ? "Delete h2go_stations, h2go_users and h2go_reports (in Aiven: the Databases tab) and restart H2-Go."
                        : "Import sql/h2go.sql in phpMyAdmin (it starts over), then run H2-Go again.");
                return false;
            }
            System.out.println("This MySQL has no H2-Go databases yet, so H2-Go is making them from sql/h2go.sql ...");
            db.runSqlFile(SQL_FILE);
            System.out.println("Made h2go_stations, h2go_users and h2go_reports with the fountains and the admins.");
            return true;
        } catch (IOException | SQLException e) {
            System.out.println("Could not make the H2-Go databases: " + e.getMessage());
            db.dropDatabases();
            return false;
        }
    }

    /**
     * Online everyone could read the sample admin password in the code, so H2GO_ADMIN_PASSWORD (when it is set)
     * becomes the password of every admin account. Returns the admin login to print.
     */
    private static String setAdminPassword(Database db) throws SQLException {
        String password = System.getenv("H2GO_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            return "admin123 / 12345678";
        }
        if (password.trim().length() < 8) {
            System.out.println("H2GO_ADMIN_PASSWORD needs at least 8 characters, so the admin password was not changed.");
            return "admin123 / 12345678";
        }
        int admins = db.setAdminPasswords(Crypto.hashPassword(password.trim()));
        System.out.println("Gave " + Labels.count(admins, "admin account", "admin accounts")
                + " the password in H2GO_ADMIN_PASSWORD.");
        return "admin123 / the password in H2GO_ADMIN_PASSWORD";
    }

    /** This computer's addresses, for phones on the same Wi-Fi. */
    private static void printWifiLinks(int port) {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                        System.out.println("On a phone on the same Wi-Fi: http://" + address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("(Could not read this computer's network addresses.)");
        }
    }
}
