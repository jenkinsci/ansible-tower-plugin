package org.jenkinsci.plugins.ansible_aap.exceptions;

/*
    Just our own type of exception
 */

public class AnsibleAAPDoesNotSupportAuthToken extends AnsibleAAPException {
    public AnsibleAAPDoesNotSupportAuthToken(String message) {
        super(message);
    }
}
