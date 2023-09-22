package org.octopusden.octopus.jira.exception;

public class JiraInternalErrorException extends JiraRuntimeException {
    public JiraInternalErrorException(String message) {
        super(message);
    }

    public JiraInternalErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
