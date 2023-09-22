package org.octopusden.octopus.jira.utils;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;
import org.octopusden.octopus.jira.enums.CustomField;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface IJiraHelper {

    Project getAndValidateProject(String key);

    Version getAndValidateVersion(ApplicationUser user, Project project, String versionName);

    IssueLinkType getAndValidateIssueLinkTypeInclude();

    IssueType getAndValidateIssueTypeReleaseRequest();

    Object getCustomFieldValue(Issue issue, CustomField customField);

    FieldConfig getFieldConfig(com.atlassian.jira.issue.fields.CustomField customField, String projectKey);

    FieldConfig getFieldConfigSafe(com.atlassian.jira.issue.fields.CustomField customField, String projectKey);

    String getReleaseVersionNameByBuild(String releaseBuild);

    boolean crdBuild(String releaseBuild);

    void logTime(Logger logger, String method, long startNs, String... params);

    boolean versionExists(ApplicationUser user, Project project, String version);

    com.atlassian.jira.issue.fields.CustomField getCustomFieldSafe(String fieldName);

    com.atlassian.jira.issue.fields.CustomField getCustomField(String fieldName);

    boolean customFieldExists(String fieldName);

    String getCustomFieldValueAsString(com.atlassian.jira.issue.fields.CustomField customField, Issue issue);

    String getClientReleaseNotesValue(com.atlassian.jira.issue.fields.CustomField clientReleaseNotesField, Issue issue);

    List<Version> getCustomFieldValueAsVersionList(com.atlassian.jira.issue.fields.CustomField customField, Issue issue);

    List<Issue> getIssueWithPartialReopen(ApplicationUser user, Project project, List<Version> filteredVersions);

    List<Issue> getIssuesWithPartialReopenUpdatedAfterDate(ApplicationUser user, Project project, List<Version> versions, Date date);

    Long[] getVersionIds(Collection<Version> versions);

    Issue getIssue(String key);

    List<Issue> getIssuesFromQuery(ApplicationUser user, Query query);

    Query getQueryOrderByIssueKey(JqlQueryBuilder builder);

    String[] getVersionStringIds(Collection<Version> versions);

    List<String> toKeyList(Collection<Issue> issues);
}
