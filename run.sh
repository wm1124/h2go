#!/bin/sh
# Compiles and runs H2-Go from Terminal (Mac or Linux): sh run.sh
# Needs Java 11 or newer, MySQL running, and the MySQL driver .jar in the lib folder.
cd "$(dirname "$0")" || exit 1
rm -rf out
javac -encoding UTF-8 -d out src/h2go/*.java || exit 1
java -cp "out:lib/*" h2go.Main "$@"
