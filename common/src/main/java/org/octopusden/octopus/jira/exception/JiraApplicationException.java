package org.octopusden.octopus.jira.exception;

public class JiraApplicationException extends JiraRuntimeException {
    public JiraApplicationException(String message) {
        super(message);
    }

    public JiraApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
