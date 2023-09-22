package org.octopusden.octopus.jira.exception;


/**
 * Basic runtime exception class for any exception during jira process
 */
public class JiraRuntimeException extends RuntimeException {

    public JiraRuntimeException(String message) {
        super(message);
    }

    public JiraRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
