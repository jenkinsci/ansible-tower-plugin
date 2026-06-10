package org.jenkinsci.plugins.ansible_aap.exceptions;

/*
    Just our own type of exception
 */

public class AnsibleAAPRefusesToGiveToken extends AnsibleAAPException {
    public AnsibleAAPRefusesToGiveToken(String message) {
        super(message);
    }
}
