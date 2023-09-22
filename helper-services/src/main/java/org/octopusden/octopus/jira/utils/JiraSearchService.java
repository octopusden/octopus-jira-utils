package org.octopusden.octopus.jira.utils;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;

import java.util.Collection;
import java.util.List;

public interface JiraSearchService {

    List<Issue> findIssues(ApplicationUser user, Project project, Version version);

    List<Issue> findIssues(ApplicationUser user, Project project, List<Version> versions);

    List<Issue> findReleasedIssues(Version version);

    List<Issue> getIssuesFromQuery(ApplicationUser user, Query query);

    List<Issue> findIssues(ApplicationUser user, Project project, Version version, IssueType issueType, boolean isReleased);

    List<Issue> findIssues(ApplicationUser user, Version version, IssueType issueType, boolean isReleased);

    /**
     * Find issues by custom field like comparison.
     * Wrapper for {@link #findIssuesByCustomFieldValue(ApplicationUser, CustomField, String, boolean)} with equalComparison = false.
     */
    List<Issue> findIssuesByCustomFieldValue(ApplicationUser user, CustomField cf, String value);

    /**
     * Find issues which custom field is equals or likes by given search criteria.
     * @param user Jira application user
     * @param cf custom field
     * @param searchFieldValue searched value
     * @param equalComparison equal comparison if true, otherwise like comparison
     * @return Returns collection of found issues
     */
    Collection<Issue> findIssuesByCustomFieldValue(ApplicationUser user, CustomField cf, String searchFieldValue, boolean equalComparison);
}
