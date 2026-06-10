package org.jenkinsci.plugins.ansible_aap.exceptions;

/*
    Just our own type of exception
 */

public class AnsibleAAPException extends Exception {
    public AnsibleAAPException(String message) {
        super(message);
    }
}
