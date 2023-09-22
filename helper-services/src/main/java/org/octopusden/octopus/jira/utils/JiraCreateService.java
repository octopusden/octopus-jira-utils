package org.octopusden.octopus.jira.utils;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;

import java.util.Date;

public interface JiraCreateService {

    Version createVersion(String buildVersion, Date buildDate, ApplicationUser user, Project project);

    Version createVersion(String name, String description, Date date, ApplicationUser user, Project project);

    Version updateVersion(String currentVersionName, String newVersionName, ApplicationUser user, Project project);

    Version updateVersion(String currentVersionName, String newVersionName, Date newReleaseDate, ApplicationUser user, Project project);

    void deleteVersionAndSwap(String buildVersion, String movingToVersion, ApplicationUser user, Project project);

    void createAttachmentForIssue(Issue issue, String fileContent, String fileName);

}
