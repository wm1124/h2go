package h2go;

/** A person who uses H2-Go. Their role is USER or ADMIN. */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String fullName;     // decrypted by Database
    private String role;
    private String bottle;       // SMALL, MEDIUM or LARGE
    private int needs;           // amenities they always want (bit flags)
    private String createdAt;
    private String lastLogin;    // null if they never signed in
    private int failedLogins;    // wrong passwords in a row
    private String lockedUntil;  // null, or when the lock ends (for example "Oct 23, 2026 1:44 PM")
    private boolean locked;      // true while lockedUntil is in the future

    public User(int id, String username, String passwordHash, String fullName, String role,
                String bottle, int needs, String createdAt, String lastLogin,
                int failedLogins, String lockedUntil, boolean locked) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.bottle = bottle;
        this.needs = needs;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.failedLogins = failedLogins;
        this.lockedUntil = lockedUntil;
        this.locked = locked;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isAdmin() {
        return role.equals("ADMIN");
    }

    public int getFailedLogins() {
        return failedLogins;
    }

    public String getLockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked() {
        return locked;
    }

    /** True when a lock was set and its 24 hours are over. */
    public boolean lockEnded() {
        return lockedUntil != null && !locked;
    }

    public boolean checkPassword(String password) {
        return Crypto.checkPassword(password, passwordHash);
    }

    /** True when the password is still saved as an old unsalted hash. */
    public boolean hasOldPasswordHash() {
        return Crypto.isOldHash(passwordHash);
    }

    /** This user as JSON (never with the password hash). */
    public String toJson() {
        return "{" + jsonFields() + "}";
    }

    /** The JSON fields without the { }. */
    public String jsonFields() {
        boolean neverSignedIn = lastLogin == null || lastLogin.isEmpty();
        return "\"id\": " + id
                + ", \"username\": " + Json.text(username)
                + ", \"fullName\": " + Json.text(fullName)
                + ", \"role\": " + Json.text(role)
                + ", \"admin\": " + isAdmin()
                + ", \"accountType\": " + Json.text(isAdmin() ? "Admin" : "User")
                + ", \"bottle\": " + Json.text(bottle)
                + ", \"needs\": " + needs
                + ", \"preferences\": " + preferencesJson()
                + ", \"createdAt\": " + Json.text(createdAt)
                + ", \"lastLogin\": " + Json.text(lastLogin)
                + ", \"lastSignIn\": " + Json.text(neverSignedIn ? "never" : lastLogin);
    }

    /** The preferences the way the page's forms send them: {"bottle": "LARGE", "need": ["1", "8"]}. */
    private String preferencesJson() {
        StringBuilder need = new StringBuilder("[");
        for (int bit : Amenity.bitsIn(needs)) {
            if (need.length() > 1) {
                need.append(", ");
            }
            need.append("\"").append(bit).append("\"");
        }
        need.append("]");
        return "{\"bottle\": " + Json.text(bottle) + ", \"need\": " + need + "}";
    }
}
