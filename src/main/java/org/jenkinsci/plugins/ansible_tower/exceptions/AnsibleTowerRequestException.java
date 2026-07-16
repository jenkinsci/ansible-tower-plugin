package org.jenkinsci.plugins.ansible_tower.exceptions;

/** A request failure whose HTTP or transport diagnostics were already written to the build console. */
public class AnsibleTowerRequestException extends AnsibleTowerException {
    private static final long serialVersionUID = 1L;

    public AnsibleTowerRequestException(String message) {
        super(message);
    }
}
