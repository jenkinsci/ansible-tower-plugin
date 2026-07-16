package org.jenkinsci.plugins.ansible_tower.util;

/*
    This class is intended for potential future use. All non-Jenkins logging in the other classes
    flow through here.
 */

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TowerLogger implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(TowerLogger.class.getName());
    private static final String PREFIX = "[Ansible-Tower] ";

    private boolean debugging = false;
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

    public static void writeMessage(String message) {
        log(Level.INFO, message);
    }

    private static void log(Level level, String message) {
        LOGGER.log(level, "{0}{1}", new Object[] {PREFIX, message});
    }
}
