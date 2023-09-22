package org.octopusden.octopus.jira.utils;

import com.atlassian.jira.bc.project.version.VersionService;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;

import java.util.Optional;

public interface JiraGetService {

    Optional<IssueType> getIssueTypeByName(String name);

    Optional<IssueLinkType> getIssueLinkTypeByName(String name);

    Optional<ApplicationUser> getCurrentLoggedInUser();

    Optional<ApplicationUser> getUserByName(String applicationUserName);

    void setLoggedInUser(ApplicationUser user);

    VersionService.VersionResult getVersion(ApplicationUser user, Project project, String versionName);

    Optional<Project> getProject(String projectKey);


}
