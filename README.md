# Jira PAT Connection Tester (Java Swing GUI)

A lightweight Java Swing desktop application to test connection and access to JIRA servers using Personal Access Tokens (PAT).

## Features

- **Responsive UI:** Network operations run in a background thread (`SwingWorker`) so the interface doesn't freeze during connection timeouts.
- **Configurable Settings:**
  - Target URL (defaults to `https://tso-jira.mcw.usmc.mil/rest/api/2/myself`).
  - Personal Access Token (hidden input by default, toggleable to show).
  - Connection & Read Timeouts.
- **SSL Bypass Support:** Option to bypass SSL/TLS certificate validation for self-signed certificates or internal intranet endpoints.
- **Detailed Execution Logs:** Monospaced result log viewer showing timestamps, response headers, response payload, or detailed exception trace upon failure.

## Files Created

- [JiraPatConnectionGui.java](file:///C:/Users/braqv/Documents/work%20projects/Jira-PAT-test/JiraPatConnectionGui.java) - The complete Swing application source code.
- [run.bat](file:///C:/Users/braqv/Documents/work%20projects/Jira-PAT-test/run.bat) - A quick-launch batch file to compile and run the application.

## How to Run

### Method 1: Using the Batch File (Windows)
Double-click [run.bat](file:///C:/Users/braqv/Documents/work%20projects/Jira-PAT-test/run.bat) or run it from the command line:
```cmd
run.bat
```

### Method 2: Manual compilation & run
Open command prompt or terminal in this folder and execute:
```cmd
javac -encoding UTF-8 JiraPatConnectionGui.java
java JiraPatConnectionGui
```

## Security Note

> [!WARNING]
> Bypassing SSL validation (accepting all certificates) is only intended for test utilities/local development. Do not use certificate validation bypass in production applications.
