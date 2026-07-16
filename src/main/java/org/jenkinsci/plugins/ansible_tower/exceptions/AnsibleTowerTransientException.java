package org.jenkinsci.plugins.ansible_tower.exceptions;

public class AnsibleTowerTransientException extends AnsibleTowerException implements ConsoleDiagnosedException {
    private final int statusCode;

    public AnsibleTowerTransientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AnsibleTowerTransientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
