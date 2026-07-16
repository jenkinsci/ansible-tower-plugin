package org.jenkinsci.plugins.ansible_tower.util;

/*
    This class is intended for potential future use. All non-Jenkins logging in the other classes
    flow through here.
 */

import java.io.Serializable;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TowerLogger implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(TowerLogger.class.getName());
    private static final String PREFIX = "[Ansible-Tower] ";

    private boolean debugging = false;
    private transient PrintStream console;

    public TowerLogger() {
    }

    public TowerLogger(PrintStream console) {
        this.console = console;
    }

    public void setConsole(PrintStream console) { this.console = console; }
    public void setDebugging(boolean debugging) { this.debugging = debugging; }

    /**
     * @deprecated Use {@link #debug(String)} for diagnostic messages.
     */
    @Deprecated
    public void logMessage(String message) {
        debug(message);
    }

    public void debug(String message) {
        if(debugging) {
            log(Level.FINE, message);
        }
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warning(String message) {
        log(Level.WARNING, message);
    }

    public void severe(String message) {
        log(Level.SEVERE, message);
    }

    public void consoleInfo(String message) {
        writeConsole("INFO", message);
    }

    public void consoleWarning(String message) {
        String safeMessage = sanitizeMessage(message);
        warning(safeMessage);
        writeConsole("WARNING", safeMessage);
    }

    public void consoleError(String message) {
        String safeMessage = sanitizeMessage(message);
        severe(safeMessage);
        writeConsole("ERROR", safeMessage);
    }

    private void writeConsole(String level, String message) {
        if(console != null) {
            console.println(PREFIX + level + ": " + sanitizeMessage(message));
        }
    }

    public static String sanitizeEndpoint(String endpoint) {
        if(endpoint == null) { return "unknown"; }
        int query = endpoint.indexOf('?');
        if(query < 0) { return endpoint; }
        StringBuilder sanitized = new StringBuilder(endpoint.substring(0, query));
        String[] parameters = endpoint.substring(query + 1).split("&");
        for(int i = 0; i < parameters.length; i++) {
            String name = parameters[i];
            int equals = name.indexOf('=');
            if(equals >= 0) { name = name.substring(0, equals); }
            sanitized.append(i == 0 ? '?' : '&').append(name).append("=<redacted>");
        }
        return sanitized.toString();
    }

    public static String sanitizeMessage(String message) {
        if(message == null) { return "unknown error"; }
        return message
            .replaceAll("(?i)(authorization|password|token|credential|extra_vars)=[^,\\s]+", "$1=<redacted>")
            .replaceAll("([?&][^=,\\s]+)=([^&,\\s]+)", "$1=<redacted>");
    }

    public static void writeMessage(String message) {
        log(Level.INFO, message);
    }

    public static String reportUnexpected(PrintStream console, String operation, RuntimeException failure) {
        String detail = operation + " failed unexpectedly: exception="
            + failure.getClass().getSimpleName() + ", message=" + sanitizeMessage(failure.getMessage());
        LOGGER.log(Level.SEVERE, PREFIX + detail, failure);
        if(console != null) {
            console.println(PREFIX + "ERROR: " + detail
                + ". See the Jenkins System Log for the full stack trace.");
        }
        return detail;
    }

    private static void log(Level level, String message) {
        LOGGER.log(level, "{0}{1}", new Object[] {PREFIX, message});
    }
}
