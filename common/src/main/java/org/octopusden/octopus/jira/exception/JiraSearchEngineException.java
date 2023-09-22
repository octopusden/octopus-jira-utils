package org.octopusden.octopus.jira.exception;

public class JiraSearchEngineException extends JiraInternalErrorException {
    public JiraSearchEngineException(String message) {
        super(message);
    }

    public JiraSearchEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
