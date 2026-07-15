package org.jenkinsci.plugins.ansible_tower.exceptions;

/*
    Just our own type of exception
 */

public class AnsibleTowerException extends Exception {
    public AnsibleTowerException(String message) {
        super(message);
    }

    public AnsibleTowerException(String message, Throwable cause) {
        super(message, cause);
    }
}
