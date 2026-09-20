@echo off
setlocal
mvn -f "%~dp0backend\pom.xml" %*
