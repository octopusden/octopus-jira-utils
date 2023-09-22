package org.octopusden.octopus.jira.exception;

public class JiraObjectNotFoundException extends JiraApplicationException{
    public JiraObjectNotFoundException(String message) {
        super(message);
    }

    public JiraObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
