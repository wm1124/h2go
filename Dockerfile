# Builds and runs H2-Go on a website host (Render). See "Put H2-Go online" in README.md.

# 1. Compile the Java code.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /h2go
COPY src src
RUN javac -encoding UTF-8 -d out src/h2go/*.java

# 2. Run it with the MySQL driver (MySQL Connector/J, from Maven Central).
FROM eclipse-temurin:21-jre
WORKDIR /h2go
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar lib/
COPY --from=build /h2go/out out
COPY web web
COPY sql sql
CMD ["java", "-Xmx256m", "-XX:+UseSerialGC", "-cp", "out:lib/*", "h2go.Main"]
