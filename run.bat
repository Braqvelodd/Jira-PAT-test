@echo off
echo Compiling JiraPatConnectionGui.java...
javac -encoding UTF-8 JiraPatConnectionGui.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)
echo Running JiraPatConnectionGui...
java JiraPatConnectionGui
