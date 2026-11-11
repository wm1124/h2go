# H2-Go: Campus Water Refill Stations (simple version)

H2-Go helps students, teachers and visitors find a nearby water refill station (fountain) on campus that is actually working. People report how each fountain is (working, not cold, broken, long line ...), and H2-Go uses those reports to show each fountain's status and how sure it is (the confidence rating), to recommend the best fountain for you, to draw the walking route, and to give the maintenance team a priority list.

- **The data** is in MySQL on your computer (localhost), so you can see and change all of it in **phpMyAdmin**: every account, every sign-in, every report and refill. H2-Go keeps no data of its own.
- **The back end is Java.** Java does all the work: reading and saving the data with JDBC, the data structures, the recommendations, a small web server, and every value and changing word the page shows.
- **The design is HTML and CSS.** The HTML files have every part of the page and every word on it. `style.css` has the whole look: colors, sizes, and the map pins too.
- **Two small JavaScript files connect them.** A browser can only run JavaScript, not Java, so `app.js` and `login.js` ask Java for the data, put it into the HTML, and draw the map. They have no design and no logic of their own.
- **The map** is [Leaflet](https://leafletjs.com) with [OpenStreetMap](https://www.openstreetmap.org), both free, with no API key.
- **Secure accounts.** Names are encrypted (AES-256), passwords are salted and hashed (PBKDF2), and 3 wrong passwords in a row lock the account for 24 hours.
- **Favorites.** Each user can save favorite fountains. The Favorites tab lists them nearest first, marks them with a heart on the map, and draws the walk to each one.
- **One link for everyone.** Put it online for free and anyone can open it by pasting one link, like `https://h2go-dlsl.onrender.com`, into Safari or Chrome, on any phone or computer, even when your laptop is off. See [Put H2-Go online](#put-h2-go-online-one-link-for-everyone).

## Run it

You need Java 11 or newer (IntelliJ), MySQL with phpMyAdmin (for example **XAMPP**), and the MySQL driver (MySQL Connector/J).

**1. Start MySQL.** In XAMPP, start **MySQL Database** and **Apache Web Server** (Apache is what shows phpMyAdmin).

**2. The databases make themselves.** The first time H2-Go starts with a MySQL that has no H2-Go databases, it makes them from `sql/h2go.sql`: `h2go_stations`, `h2go_users` and `h2go_reports`, with the 7 fountains, the walkways and the two admin accounts. In phpMyAdmin (**http://localhost/phpmyadmin**) they appear on the left under **h2go** (click its **+**, and phpMyAdmin's refresh button). You can also make them yourself first: click the **Import** tab, click **Choose File**, pick `sql/h2go.sql` and click **Import** (or **Go**).

**3. Run H2-Go in IntelliJ.**

- *If you already have the old H2-Go-v2 project* (it already has the MySQL driver): in your `H2-Go-v2` folder, delete the folders `src`, `web` and `sql` and the file `README.md`, and copy in the ones from this zip. Keep your own `h2go.properties`. Then open `src/h2go/Main.java` and press the green run button.
- *As a new project:* **File > Open** this folder. Right-click `src` > **Mark Directory as > Sources Root**. Set the SDK to Java 11 or newer (**File > Project Structure > Project**). Add the driver: **File > Project Structure > Libraries > + > From Maven**, type `com.mysql:mysql-connector-j` and pick the newest version. Then run `src/h2go/Main.java`.

The console says which MySQL server H2-Go is using, for example `Connected to MySQL 5.5.5-10.4.28-MariaDB` (XAMPP's MySQL is MariaDB). It should be the same server phpMyAdmin shows (on phpMyAdmin's home page, under **Database server**).

**4. Open http://localhost:8080** and sign in, or create an account.

The first time H2-Go starts, it makes **`h2go.key`** in the H2-Go folder: the secret key that encrypts the names in MySQL. Keep it with H2-Go and don't share it. Without it, the names can't be read (the rest still works).

`h2go.properties` has the MySQL login. XAMPP's is user `root` with no password on port 3306, which is already filled in. For MAMP use `db.port=8889` and `db.password=root`. If you gave MySQL a password, type it after `db.password=`.

**To start over**, import `sql/h2go.sql` in phpMyAdmin again. It deletes the three databases (every account, report, refill and favorite) and makes them again with the fountains and the two admins.

**To update a copy of H2-Go you already run:** in your H2-Go folder, replace the `src`, `web` and `sql` folders (and `README.md`) with the ones from the new zip. Keep your own `h2go.properties` (it has your port and MySQL login). No new import is needed: the first time the new version starts, it updates your database by itself and keeps your accounts and reports. The console shows what it did, for example `Updated the database: encrypted 9 names in h2go_users.users.` From an older version it also removes the points and the five sample users (with their reports), and puts in the 7 fountains instead of the 15 sample ones: their reports, refills and favorites go with them, and every other account stays. To update the online H2-Go, see [Put H2-Go online](#put-h2-go-online-one-link-for-everyone).

## Accounts

H2-Go starts with two admin accounts. Admins can also mark broken fountains fixed and remove comments.

| Username | Password |
|---|---|
| `admin123` | `12345678` |
| `admin` | `admin123` |

Everyone else makes an account on the sign-in page (**Create one**). In phpMyAdmin a password looks like `pbkdf2$65536$...` (its salted hash) and a name like `aes:...` (encrypted): H2-Go never saves either as plain text.

Online, the two admin accounts sign in with the password you chose for `H2GO_ADMIN_PASSWORD` instead (see Put H2-Go online), because these passwords are in this README for anyone to read.

## Where the data goes (watch it in phpMyAdmin)

Everything you do in H2-Go is saved in MySQL right away. Click a table in phpMyAdmin (or **Browse**, or the refresh button) to see it:

| In H2-Go | In phpMyAdmin |
|---|---|
| Create an account | a new row in `h2go_users` > `users` (the name encrypted, the password hashed) |
| Sign in | a new row in `h2go_users` > `sessions` (the hash of the sign-in token), the time in `last_login`, and `failed_logins` back to 0 |
| Type a wrong password | `failed_logins` + 1. The 3rd in a row sets `locked_until` to 24 hours later |
| Sign out | that row in `sessions` is deleted |
| Report a fountain | a new row in `h2go_reports` > `reports` |
| I refilled here | a new row in `h2go_reports` > `refills` |
| Add to favorites (the heart) | a new row in `h2go_users` > `favorites` (the heart again deletes it) |
| Save preferences | the user's `bottle` and `needs` in `users` |
| Mark fixed (admins) | the fountain's problem reports get `fixed` = 1, and a new "Working" report is added |
| Remove a comment (admins) | that report's row in `h2go_reports` > `reports` is deleted |

It also works the other way: change something in phpMyAdmin (for example a fountain's `name`, or its `lat` and `lng` to move its pin) and reload the H2-Go page. H2-Go reads the data from MySQL for every request, so it shows the change straight away.

### See the newest accounts and reports

In phpMyAdmin, click the **SQL** tab, paste one of these, and click **Go**. The newest rows come first. To see new rows later, click **Refresh** above the results.

The newest accounts, with when each one was made and last signed in, and any lock (the names are encrypted, so this shows the usernames):

```sql
SELECT id, username, role, created_at, last_login, failed_logins, locked_until
FROM h2go_users.users
ORDER BY created_at DESC, id DESC;
```

Who reported lately, and about which fountain. This query joins tables from all three databases, so it shows names instead of id numbers:

```sql
SELECT r.created_at, u.username, s.name AS fountain,
       r.status, r.queue, r.stars, r.comment
FROM h2go_reports.reports r
JOIN h2go_users.users u ON u.id = r.user_id
JOIN h2go_stations.stations s ON s.id = r.station_id
ORDER BY r.created_at DESC, r.id DESC
LIMIT 20;
```

Above the second result, phpMyAdmin says "Current selection does not contain a unique column". That is normal: rows made by a JOIN can't be edited there.

## How to use it

- **The map**: green = working, orange = has a problem, red = broken, gray = no reports in the last 3 days. The blue dot is **You**: click the map where you are, or press **Use my location**.
- **Find water**: tick what you need (cold water, filtered water ...), pick your bottle size, and H2-Go lists the 5 best fountains for you, with the walking time and a score.
- **Click a fountain** (on the map or in the list) to see its status, confidence, line, stars and latest reports. **Show route** draws the shortest walk. **I refilled here** saves a refill (one plastic bottle avoided). **♡ Add to favorites** makes it a favorite, and you can **report** how it is. Admins see a **Remove** button next to each report, to delete a comment.
- **Favorites**: your favorite fountains, nearest first from where You are, with the walking time. On the map they wear a pink heart and the other fountains fade. Click one to zoom the map to it, press **Go** for the walking route, or **Details** to open it.
- **Maintenance**: fountains with problems, most urgent first. Admins have a **Mark fixed** button.
- **My profile**: your account, your bottle size and what you always need (these fill in the filters).
- **Show the walkway graph** (at the bottom of Find water) draws the points and walkways the routes use.

## The files

```
h2go.properties      MySQL login and the web server port (on this computer)
h2go.key             the secret key for the encrypted names (made at the first start; keep it, don't share it)
sql/h2go.sql         the three databases, their tables, the fountains, the walkways and the admins (H2-Go runs it in an empty MySQL)
Dockerfile           how a website host builds and runs H2-Go (online)
render.yaml          the settings for Render, the free website host (online)
src/h2go/
  Main.java          starts everything: settings, connecting to MySQL, the web server
  Database.java      all the SQL (JDBC): reading and saving the data in MySQL
  H2Go.java          reads the fountains and walkways and builds the walkway graph and the k-d trees
  Station.java, PathPoint.java, User.java, Report.java    the data classes
  Amenity.java       amenities as bit flags (bitwise filtering)
  KdTree.java        the spatial search tree
  CampusGraph.java   the weighted graph of walkways and Dijkstra's algorithm
  Recommender.java   smart recommendations, ranked with a priority queue
  Confidence.java    each fountain's status and confidence rating
  Maintenance.java   the maintenance priority queue
  Distance.java      meters between two GPS positions
  Labels.java        the words and groups the page shows: "Not cold", "12 min ago", the stars ...
  Crypto.java        AES-256 for names, PBKDF2 for passwords, SHA-256 for sign-in tokens
  WebServer.java     the web server (sends the pages)
  Api.java           the addresses the page calls (/api/...), answering with JSON
  Json.java          a helper for writing JSON
web/
  login.html         the sign-in page: its parts and words (HTML)
  index.html         the map page: its parts and words, and templates for the lists (HTML)
  style.css          how both pages look, the map pins too (CSS)
  login.js, app.js   connect the pages to Java and draw the map (JavaScript, no design or logic)
  leaflet/           the Leaflet map library (not our code)
```

## How it works (for the defense)

**Who does what.** H2-Go is split by job:

| Part | Files | Its job |
|---|---|---|
| Back end | Java (`src/h2go/`) | All the logic and data: MySQL, the data structures and algorithms, the recommendations, routes, confidence and maintenance list, sign-in, and every value and changing word the page shows |
| Structure and words | HTML (`index.html`, `login.html`) | Every part of the page and every word on it. Lists (the best fountains, the reports, the maintenance list ...) are `<template>`s: one item, copied once for each item Java sends |
| Design | CSS (`style.css`) | The whole look: colors, sizes, spacing, the phone layout, and the map pins, the "You" dot and the route line |
| Connector | JavaScript (`app.js`, `login.js`) | Asks Java's API for the data, copies the templates and fills them in, and tells Leaflet where to put the pins. No HTML, no colors and no calculations |

For example, take a fountain with a "not cold" report:
1. **Java** works out its status from the reports (`Confidence.java`): `NOT_COLD`. `Labels.java` turns that into the words `"Not cold"` and the group `"problem"`, and `Api.java` sends them as JSON: `"statusLabel": "Not cold", "statusGroup": "problem"`.
2. **HTML** has the badge in a template: `<span class="badge" data-class="statusGroup" data-text="statusLabel"></span>`.
3. **JavaScript** copies the template, puts `Not cold` in as the text and adds `problem` as a class.
4. **CSS** colors the group: `.problem { background: #ef6c00; }` (orange). The map pin and the legend dot use the same rule.

The same goes for everything else: the time ("12 min ago"), the stars, "3 reports", the walking minutes, where "You" start (the Main Gate), the part of the map to show, and who may see the **Remove** and **Mark fixed** buttons are all worked out in Java.

**Three databases in MySQL** (made from `sql/h2go.sql`): on localhost (XAMPP) on your computer, or in an online MySQL (Aiven) for the online H2-Go. H2-Go connects to them with JDBC (`Database.java`) and keeps no data of its own: it reads the data for every request and saves every change straight away.

| Database | Tables | What is in it |
|---|---|---|
| `h2go_stations` | `stations`, `path_points`, `paths` | The fountains (name, building, floor, latitude, longitude, amenities) and the campus walkways. Every fountain is on the 1st floor. |
| `h2go_users` | `users`, `sessions`, `favorites` | Accounts (username, salted password hash, encrypted name, role USER or ADMIN, bottle size, what they need, when the account was made, last sign-in, wrong passwords in a row, lock), who is signed in (token hashes), and favorite fountains |
| `h2go_reports` | `reports`, `refills` | The crowdsourced reports (status, line, stars, comment, time, fixed or not) and every refill |

The tables are linked with foreign keys: for example `reports.station_id` must be a real `stations.id` and `reports.user_id` a real `users.id`, so MySQL refuses a report about a fountain or a user that doesn't exist.

**1. Spatial search tree (`KdTree.java`).** A k-d tree with k = 2: a binary search tree of places on the map. Level 0 splits by x (east-west), level 1 by y (north-south), and so on. A search skips a whole branch when its splitting line is farther away than the best place found so far, so it doesn't have to check every place. H2-Go uses one tree for the fountains ("which fountains are within 1 km of me?") and one for the walkway points ("which walkway point is closest to me?").

**2. Bitwise filtering (`Amenity.java`).** Each amenity is one bit: cold water 1, hot water 2, sensor tap 4, tall bottle clearance 8, filtered water 32 (16 is not used). A fountain with cold water, a tall bottle clearance and filtered water has 1 + 8 + 32 = 41. A fountain has everything you need when `(fountain & needs) == needs`. The page's checkboxes are a form, so it sends one value for each box you tick (`need=1&need=8`), and Java adds them up with OR: `needs = needs | bit`.

**3. Weighted graph and Dijkstra (`CampusGraph.java`).** The walkway points are the vertices and the walkways are the edges. An edge's weight is its length in meters, worked out from the two points' latitude and longitude. Dijkstra's algorithm starts at the walkway point nearest to you and finds the shortest walking distance to every point. The route on the map is that shortest way. Walking is 1.3 meters per second. H2-Go covers the 1st floor only, so there are no stairs.

**4. Priority queues (Java's `PriorityQueue`).** Three places:
- Dijkstra always continues from the closest point not finished yet (`CampusGraph.java`).
- The recommendations come out best score first (`Recommender.java`).
- The maintenance list comes out highest priority first (`Maintenance.java`).

**Smart recommendation (`Recommender.java`).** The k-d tree finds the fountains within 1 km. Filters drop broken ones (when hidden), ones without what you need, and ones too short for a large bottle. Each one left gets a score out of 100:

```
score = 40 x walking time + 30 x reliability + 20 x line + 10 x stars
```

Each part goes from 0 to 1. A 0-minute walk is 1 and a 10-minute walk is 0. Reliability is the confidence for a working fountain, 0.3 when nobody reported it lately, 0.1 when it has a problem, and 0 when it is broken. No line is 1, a short line 0.6, a long line 0.1. Five stars is 1.

**Confidence rating (`Confidence.java`).** Only reports from the last 3 days count, and problems marked fixed don't count.
- Recency: a report counts 1 when it is less than a day old, 0.5 when 1 to 2 days old, and 0.25 when 2 to 3 days old.
- The status is the one with the most weight.
- Consistency: the share of the weight that agrees with that status.
- Frequency: the number of reports divided by 5 (at most 1).

```
confidence = 100 x consistency x frequency
```

So recent reports that agree give a high confidence, and few, old or conflicting reports give a low one.

**Maintenance priority (`Maintenance.java`).** Problems from the last 7 days that are not fixed:

```
priority = 10 x severity of the worst problem + 3 x number of reports + hours waiting / 6
```

Severity: broken 10, leaking 8, low pressure 6, dirty 5, not cold 3. Hours waiting count up to 72.

**Sign-in (`Api.java`, `Database.java`).** Signing in makes a random token (a UUID). The browser keeps it in a cookie, and MySQL keeps its SHA-256 hash in the `sessions` table with the user's id, so every request tells H2-Go who sent it. Signing out deletes the row.

**Account lock.** Each wrong password adds 1 to the user's `failed_logins`. The 3rd in a row sets `locked_until` to 24 hours later, and until then the account can't sign in, even with the right password. A right password sets `failed_logins` back to 0. An admin can unlock an account early in phpMyAdmin (see Troubleshooting).

**Encryption (`Crypto.java`).** What MySQL (and phpMyAdmin) can't read:
- **Names**: encrypted with AES-256-GCM before they are saved, and decrypted only inside H2-Go. The secret key is in the `h2go.key` file, never in MySQL. Each name gets a random 12-byte IV, so the same name encrypts differently every time.
- **Passwords**: PBKDF2 (HMAC-SHA-256, 65,536 rounds) with a random 16-byte salt per user. It is one-way: H2-Go checks a password by hashing what you type the same way. A password saved by an older H2-Go (unsalted SHA-256) is replaced with a PBKDF2 hash the next time that user signs in.
- **Sign-in tokens**: only their SHA-256 hash is saved, so a copy of the `sessions` table can't be used to sign in.

**Sustainability.** Every refill is saved in `refills`: one plastic bottle avoided.

**The map.** Leaflet draws OpenStreetMap's map tiles. The page asks the Java server for the fountains (`/api/stations`) and puts a pin on each one: a `<div>` with the class names `pin` and its status group, which `style.css` draws as a colored circle with a white ring. The route the server sends (`/api/route`) becomes a line with the class `route`, which `style.css` makes blue. Java also sends the part of the map to show (`bounds`), so the page doesn't calculate anything.

## Put H2-Go online (one link for everyone)

On your computer, H2-Go only runs while your laptop does, at `http://localhost:8080`. To have **one link that anyone can open by pasting it into Safari or Chrome**, on Wi-Fi or mobile data, even when your laptop is off, put H2-Go on a free website host. It takes three free accounts and about 20 minutes, once:

- **GitHub** keeps the code.
- **Aiven** runs the online MySQL (the data).
- **Render** runs H2-Go from the code on GitHub and gives the link, like `https://h2go-dlsl.onrender.com`.

The free plans of all three don't need a credit card.

**1. Put the code on GitHub.**
1. Sign up at https://github.com.
2. Click **+** (top right) > **New repository**. Name it `h2go`, choose **Private**, and click **Create repository**.
3. On the next page, click **uploading an existing file**. Open the unzipped `H2-Go-online` folder, select everything inside it (Cmd+A on a Mac, Ctrl+A on Windows) and drag it onto the page. When every file is listed, click **Commit changes**.
   - Upload the files from the zip, not from the folder you run H2-Go in: the zip has no `h2go.key`, and the secret key doesn't belong on GitHub.
   - `Dockerfile` and `render.yaml` must be at the top of the repository, next to `README.md`, not inside another folder.

**2. Make the online MySQL on Aiven.**
1. Sign up at https://console.aiven.io/signup (you can use Google or GitHub). If it asks for an organization or project name, type any name.
2. Click **Create service** > **MySQL**, and choose the **Free** plan (not a trial). If it asks for a region, pick the one closest to the Philippines (Singapore). Name it `h2go-db` and create it.
3. Wait until the service says **Running** (a few minutes).
4. On the service's **Overview** page, copy the **Service URI** (under Connection information). It looks like `mysql://avnadmin:AVNS_...@h2go-db-....aivencloud.com:12345/defaultdb?ssl-mode=REQUIRED`. It has the database password in it, so keep it secret.

**3. Make the website on Render.**
1. Sign up at https://render.com with **GitHub**, so Render can see your repository.
2. Click **New** > **Blueprint** and connect your `h2go` repository. (If it isn't listed, use the link there to give Render access to it on GitHub.)
3. Render reads `render.yaml` and shows the web service `h2go-dlsl` (free plan, Singapore). It asks for two values:
   - **H2GO_DB_URL**: paste the Service URI from Aiven.
   - **H2GO_ADMIN_PASSWORD**: type a new password for the admin accounts (at least 8 characters). Online, `admin123` and `admin` sign in with it instead of the sample password.

   The third setting, `H2GO_KEY` (the secret key for the names), Render makes by itself.
4. Click **Deploy Blueprint** (or **Apply**). The first time takes about 5 minutes. Open the `h2go-dlsl` service and click **Logs**: when it says `H2-Go is running`, it's online. On this first start H2-Go makes the three databases in Aiven, with the fountains and the admins.
5. **The link** is at the top of the service page, for example `https://h2go-dlsl.onrender.com` (Render adds a few letters if the name is taken). Paste it into Safari or Chrome on any phone or computer, and share it. It stays the same. It is https, so **Use my location** works on phones.

**Good to know**
- **The free plan sleeps** after 15 minutes without visitors. The next visit wakes it up, which takes about a minute; after that it's fast.
- **To update the online H2-Go**, upload the changed files to the same GitHub repository (**Add file > Upload files**, then **Commit changes**). Render deploys the change by itself within a few minutes. The data in Aiven stays, and the new version updates it by itself the way it does on your computer (see Run it).
- **The online data is separate** from the MySQL on your laptop: phpMyAdmin shows the laptop's data. To look at the online data, use a free MySQL program such as MySQL Workbench or DBeaver with the host, port, user and password from Aiven's Overview page (with SSL). The times in it are Philippine time.
- **Aiven can turn a free database off** after a long time without use (it sends an email first). Then the H2-Go link shows an error: open the service in Aiven and turn it back on (**Power on**).

**How it works.** Render builds H2-Go with the `Dockerfile` (it compiles the Java code and adds the MySQL driver) and runs it with these settings, set in Render under the service's **Environment**:

| Setting | What it is |
|---|---|
| `PORT` | the port H2-Go uses (Render sets it) |
| `H2GO_DB_URL` | the online MySQL's link. H2-Go connects with SSL (encrypted), as online databases require |
| `H2GO_KEY` | the secret key for the names. Render's disk is wiped at every restart, so an `h2go.key` file wouldn't last there |
| `H2GO_ADMIN_PASSWORD` | the password of every admin account |

Online, H2-Go uses Philippine time in MySQL (an online MySQL runs on UTC), and connects again by itself when the online MySQL closes a connection that sat unused for a few minutes.

**Same Wi-Fi only (without putting it online):** in `h2go.properties` change `server.host` to `0.0.0.0`. The console then also prints an address like `http://192.168.1.23:8080` for phones on the same Wi-Fi. If the Mac asks whether java may accept incoming connections, click **Allow**. School Wi-Fi often blocks this, and **Use my location** needs https, so there, click the map where you are.

## Moving a fountain or a walkway point

The 7 fountains are where the pink hearts are on the team's Google Maps list of campus fountains, worked out to about 3 m from a screenshot of that map. Their amenities (cold water, filtered water ...) are carried over from the old sample fountains of the same buildings; the Sports Center fountain is set to cold water only. The walkways are the team's campus trace, lined up with the real map; a few still cut across buildings. If a pin is not on the right building, find the right spot (in Google Maps, right-click it and click the coordinates to copy them). Then in phpMyAdmin open `h2go_stations` > `stations` (or `path_points`), click **Edit** on that row, paste the numbers into `lat` and `lng`, and click **Go**. Reload the H2-Go page to see the pin move. To keep the change after you import the file again, change the same numbers in `sql/h2go.sql` too.

## Troubleshooting

- **"Could not connect to MySQL"**: start MySQL (in XAMPP, start MySQL Database) and check `db.port`, `db.user` and `db.password` in `h2go.properties`.
- **"The H2-Go databases in this MySQL are incomplete or from an older H2-Go"**: import `sql/h2go.sql` in phpMyAdmin (it starts over), then run H2-Go again.
- **New accounts don't show up in phpMyAdmin**: H2-Go is using a different MySQL server than phpMyAdmin. Compare the server H2-Go prints when it starts with the one on phpMyAdmin's home page. On a Mac, a MySQL installed with Homebrew can be running too: stop it with `brew services stop mysql`, start XAMPP's MySQL, and run H2-Go again. Also click the refresh button in phpMyAdmin: it doesn't update by itself.
- **"No suitable driver"**: add the MySQL driver (see Run it).
- **"Address already in use"**: another H2-Go is still running. Stop it, or change `server.port`.
- **http://localhost:8080 shows another page** (for example "Welcome to nginx!"): another program on the computer uses port 8080. In `h2go.properties` change `server.port=8080` to `server.port=8090`, run H2-Go again, and open http://localhost:8090.
- **The map has no streets**: the computer is offline. The map pictures come from OpenStreetMap over the internet; the pins still work.
- **Asked to sign in again**: your sign-in was deleted from the `sessions` table (for example by importing `sql/h2go.sql` again).
- **"This account is locked until ..."**: 3 wrong passwords in a row. Wait 24 hours, or unlock it in phpMyAdmin (SQL tab), with the right username:
  `UPDATE h2go_users.users SET failed_logins = 0, locked_until = NULL WHERE username = 'admin123';`
- **Names show "(encrypted with another key)"**: H2-Go is using a different key than the one that encrypted them. On your computer, put the original `h2go.key` back in the H2-Go folder; when you move H2-Go and its database to another computer, copy `h2go.key` too. Online, don't change `H2GO_KEY` in Render.
- **The online link takes a minute to open**: the free plan was asleep (no visitors for 15 minutes). Wait, and it opens.
- **The online link shows an error or doesn't open**: open the service in Render and click **Logs**. `H2GO_DB_URL is not set` or `Could not connect to the online MySQL`: check `H2GO_DB_URL` under **Environment** (it must be Aiven's whole Service URI) and that Aiven says **Running**. After changing a setting, Render restarts H2-Go by itself.
