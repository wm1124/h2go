-- H2-Go database. H2-Go runs this file by itself when it starts with a MySQL that has no H2-Go databases.
-- To start over, import it in phpMyAdmin (Import tab > Choose File > h2go.sql > Import):
-- it deletes the three databases first.

DROP DATABASE IF EXISTS h2go_reports;
DROP DATABASE IF EXISTS h2go_users;
DROP DATABASE IF EXISTS h2go_stations;

CREATE DATABASE h2go_stations;
CREATE DATABASE h2go_users;
CREATE DATABASE h2go_reports;


-- ---------------------------------------------------------------- h2go_stations

-- Points on the campus walkways. With paths they make the weighted graph for routes.
CREATE TABLE h2go_stations.path_points (
    id   INT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    lat  DOUBLE NOT NULL,
    lng  DOUBLE NOT NULL
);

CREATE TABLE h2go_stations.paths (
    from_point INT NOT NULL,
    to_point   INT NOT NULL,
    PRIMARY KEY (from_point, to_point),
    FOREIGN KEY (from_point) REFERENCES h2go_stations.path_points (id),
    FOREIGN KEY (to_point)   REFERENCES h2go_stations.path_points (id)
);

-- amenities: 1 cold water, 2 hot water, 4 sensor tap, 8 tall bottle clearance, 32 filtered water (added up)
CREATE TABLE h2go_stations.stations (
    id        INT PRIMARY KEY,
    name      VARCHAR(60) NOT NULL,
    building  VARCHAR(60) NOT NULL,
    floor     INT NOT NULL DEFAULT 1,
    lat       DOUBLE NOT NULL,
    lng       DOUBLE NOT NULL,
    amenities INT NOT NULL DEFAULT 0,
    point_id  INT NOT NULL,    -- the walkway point where routes end
    FOREIGN KEY (point_id) REFERENCES h2go_stations.path_points (id)
);


-- ---------------------------------------------------------------- h2go_users

-- password_hash: PBKDF2 with a random salt. full_name: AES-256 encrypted by H2-Go
-- (the sample names below are encrypted the first time H2-Go starts).
-- 3 wrong passwords in a row set locked_until to 24 hours later.
CREATE TABLE h2go_users.users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    full_name     VARCHAR(400) NOT NULL,
    role          VARCHAR(10) NOT NULL DEFAULT 'USER',
    bottle        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    needs         INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login    DATETIME NULL,
    failed_logins INT NOT NULL DEFAULT 0,
    locked_until  DATETIME NULL
);

-- One row per signed-in browser. token is the SHA-256 hash of the cookie's token.
CREATE TABLE h2go_users.sessions (
    token      CHAR(64) PRIMARY KEY,
    user_id    INT NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES h2go_users.users (id)
);

CREATE TABLE h2go_users.favorites (
    user_id    INT NOT NULL,
    station_id INT NOT NULL,
    PRIMARY KEY (user_id, station_id),
    FOREIGN KEY (user_id)    REFERENCES h2go_users.users (id),
    FOREIGN KEY (station_id) REFERENCES h2go_stations.stations (id)
);


-- ---------------------------------------------------------------- h2go_reports

-- status: WORKING, NOT_COLD, LOW_PRESSURE, DIRTY, LEAKING or BROKEN. queue: NONE, SHORT, LONG or UNKNOWN.
-- stars: 1 to 5, or 0 for no rating. fixed: TRUE once an admin marked the problem fixed.
CREATE TABLE h2go_reports.reports (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    station_id INT NOT NULL,
    user_id    INT NOT NULL,
    status     VARCHAR(20) NOT NULL,
    queue      VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',
    stars      INT NOT NULL DEFAULT 0,
    comment    VARCHAR(200) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL,
    fixed      BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (station_id) REFERENCES h2go_stations.stations (id),
    FOREIGN KEY (user_id)    REFERENCES h2go_users.users (id)
);

-- One refill = one plastic bottle avoided.
CREATE TABLE h2go_reports.refills (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    station_id INT NOT NULL,
    user_id    INT NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (station_id) REFERENCES h2go_stations.stations (id),
    FOREIGN KEY (user_id)    REFERENCES h2go_users.users (id)
);


-- ---------------------------------------------------------------- data: De La Salle Lipa

-- The walkway points and walkways the routes use.
INSERT INTO h2go_stations.path_points (id, name, lat, lng) VALUES
(1, 'West Gate', 13.9414832, 121.1453166),
(2, 'Main Gate', 13.9414706, 121.1474146),
(3, 'San Lorenzo Gate', 13.9421829, 121.1493823),
(4, 'Sports Center entrance', 13.9428049, 121.1445877),
(5, 'Walkway', 13.9427262, 121.1450164),
(6, 'Walkway', 13.9424717, 121.1451207),
(7, 'Walkway', 13.9422324, 121.1451768),
(8, 'Walkway', 13.9417681, 121.1452736),
(9, 'Student Center entrance', 13.9422545, 121.1452752),
(10, 'Mabini Hall entrance', 13.9417900, 121.1454254),
(11, 'G. Zara entrance', 13.9427552, 121.1458347),
(12, 'Walkway', 13.9426864, 121.1458495),
(13, 'Walkway', 13.9426719, 121.1460815),
(14, 'Walkway', 13.9423905, 121.1461115),
(15, 'Chez Rafael entrance', 13.9423415, 121.1464576),
(16, 'Walkway', 13.9423885, 121.1464579),
(17, 'Walkway', 13.9423876, 121.1466119),
(18, 'Walkway', 13.9430082, 121.1466158),
(19, 'Sentrum entrance', 13.9426945, 121.1471720),
(20, 'Walkway', 13.9423839, 121.1472278),
(21, 'Walkway', 13.9426942, 121.1472298),
(22, 'Walkway', 13.9430327, 121.1472319),
(23, 'Walkway', 13.9424966, 121.1472478),
(24, 'Walkway', 13.9421582, 121.1472531),
(25, 'Walkway', 13.9435960, 121.1473894),
(26, 'Claro M. Recto entrance', 13.9421759, 121.1474180),
(27, 'Walkway', 13.9424184, 121.1477381),
(28, 'Walkway', 13.9421738, 121.1477655),
(29, 'Walkway', 13.9415528, 121.1478193),
(30, 'Walkway', 13.9423766, 121.1484500),
(31, 'Walkway', 13.9422258, 121.1484972),
(32, 'Walkway', 13.9440867, 121.1486725),
(33, 'Walkway', 13.9439352, 121.1488447),
(34, 'Walkway', 13.9422496, 121.1492384);

INSERT INTO h2go_stations.paths (from_point, to_point) VALUES
(1, 8), (2, 24), (2, 29), (3, 34), (4, 5), (5, 6), (6, 7), (6, 12),
(7, 8), (7, 9), (8, 10), (11, 12), (12, 13), (13, 14), (14, 16), (15, 16),
(16, 17), (17, 18), (17, 20), (18, 22), (19, 21), (20, 21), (20, 23), (20, 24),
(21, 22), (22, 25), (23, 27), (24, 26), (25, 32), (27, 28), (27, 30), (28, 29),
(28, 31), (30, 31), (31, 34), (32, 33), (33, 34);

-- The fountains, placed where the pink hearts are on the campus map.
INSERT INTO h2go_stations.stations (id, name, building, floor, lat, lng, amenities, point_id) VALUES
(1, 'Sports Center Fountain', 'Sports Center', 1, 13.9428901, 121.1442755, 1, 4),
(2, 'Mabini Hall Fountain', 'Mabini Hall', 1, 13.9417413, 121.1454321, 37, 10),
(3, 'Student Center Fountain', 'Student Center', 1, 13.9422200, 121.1454348, 45, 9),
(4, 'G. Zara Fountain', 'G. Zara', 1, 13.9427093, 121.1456797, 1, 11),
(5, 'Chez Rafael Fountain', 'Chez Rafael', 1, 13.9422120, 121.1464690, 3, 15),
(6, 'Sentrum Fountain', 'Sentrum', 1, 13.9426268, 121.1471684, 45, 19),
(7, 'Claro M. Recto Fountain', 'Claro M. Recto', 1, 13.9420604, 121.1473643, 1, 26);

-- The admins. Logins: admin123 / 12345678 and admin / admin123. Everyone else makes an account on the sign-in page.
INSERT INTO h2go_users.users (id, username, password_hash, full_name, role, bottle, needs) VALUES
(1, 'admin', 'pbkdf2$65536$PKM0ctf74XoBKTiTMuYF+w==$tdBkLH2piLPVKbRa8jI8xT5/U/5aXAEvy13b5dqiEUM=', 'H2-Go Admin', 'ADMIN', 'MEDIUM', 0),
(2, 'admin123', 'pbkdf2$65536$oGvLgLK2wCeuLZWT6kieDA==$4EUKi6cEf9AzJZOsmpBqHYBKOsTAqpLjbjFNO5qI0eM=', 'Admin', 'ADMIN', 'MEDIUM', 0);
