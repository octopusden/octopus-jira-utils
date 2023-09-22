package org.octopusden.octopus.jira.utils.impl;

import com.atlassian.jira.bc.project.version.VersionService;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.issue.link.IssueLinkTypeManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import org.octopusden.octopus.jira.utils.JiraGetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class JiraGetServiceImpl implements JiraGetService {

    static final Logger LOG = LoggerFactory.getLogger(JiraCreateServiceImpl.class);

    private final IssueTypeManager issueTypeManager;
    private final IssueLinkTypeManager issueLinkTypeManager;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final UserManager userManager;
    private final VersionService versionService;
    private final ProjectManager projectManager;

    public JiraGetServiceImpl(IssueTypeManager issueTypeManager, IssueLinkTypeManager issueLinkTypeManager, JiraAuthenticationContext jiraAuthenticationContext,
                              UserManager userManager, VersionService versionService, ProjectManager projectManager) {
        this.issueTypeManager = issueTypeManager;
        this.issueLinkTypeManager = issueLinkTypeManager;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.userManager = userManager;
        this.versionService = versionService;
        this.projectManager = projectManager;
    }


    @Override
    public Optional<IssueType> getIssueTypeByName(String name) {
        Collection<IssueType> issueTypes = issueTypeManager.getIssueTypes();
        IssueType resIssueType = null;
        for (IssueType issueType : issueTypes) {
            if (issueType.getName().equalsIgnoreCase(name)) {
                resIssueType = issueType;
            }
        }
        return Optional.ofNullable(resIssueType);
    }

    @Override
    public Optional<IssueLinkType> getIssueLinkTypeByName(String name) {
        Collection<IssueLinkType> issueLinkTypes = issueLinkTypeManager.getIssueLinkTypes();
        IssueLinkType resIssueLinkType = null;
        for (IssueLinkType issueLinkType : issueLinkTypes) {
            if (issueLinkType.getName().equalsIgnoreCase(name)) {
                resIssueLinkType = issueLinkType;
            }
        }
        return Optional.ofNullable(resIssueLinkType);
    }

    @Override
    public Optional<ApplicationUser> getCurrentLoggedInUser() {
        return Optional.ofNullable(jiraAuthenticationContext.getLoggedInUser());
    }

    @Override
    public Optional<ApplicationUser> getUserByName(String applicationUserName) {
        return Optional.ofNullable(userManager.getUserByName(applicationUserName));
    }

    @Override
    public void setLoggedInUser(ApplicationUser user) {
        ApplicationUser applicationUser = userManager.getUserByName(user.getName());
        jiraAuthenticationContext.setLoggedInUser(applicationUser);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Logged as {}", user.getName());
        }
    }


    @Override
    public VersionService.VersionResult getVersion(ApplicationUser user, Project project, String versionName) {
        return versionService.getVersionByProjectAndName(user, project, versionName);
    }

    @Override
    public Optional<Project> getProject(String projectKey) {
        return Optional.ofNullable(projectManager.getProjectByCurrentKeyIgnoreCase(projectKey));
    }
}
