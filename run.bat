@echo off
rem Compiles and runs H2-Go on Windows. Needs the MySQL driver .jar in the lib folder.
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -encoding UTF-8 -d out src\h2go\*.java || exit /b 1
java -cp "out;lib/*" h2go.Main %*
