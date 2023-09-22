package org.octopusden.octopus.jira.utils.impl;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.version.VersionService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.link.IssueLinkType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.query.order.SortOrder;
import com.google.common.collect.Lists;
import org.octopusden.octopus.jira.enums.IssueTypeEnum;
import org.octopusden.octopus.jira.enums.CustomField;
import org.octopusden.octopus.jira.exception.JiraApplicationException;
import org.octopusden.octopus.jira.exception.JiraObjectNotFoundException;
import org.octopusden.octopus.jira.exception.JiraSearchEngineException;
import org.octopusden.octopus.jira.utils.IJiraHelper;
import org.octopusden.octopus.jira.utils.JiraGetService;
import org.octopusden.octopus.jira.utils.JiraSearchService;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiraHelper implements IJiraHelper {

    public static final String INCLUDE_ISSUE_LINK_TYPE = "Include";
    public static final double MILLIS_IN_SECOND = 1000.0;
    public static final int NANO_IN_MILLIS = 1000000;
    private static final Logger LOG = LoggerFactory.getLogger(JiraHelper.class);
    private static final Pattern CRD_BUILD_PATTERN = Pattern.compile("(\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d)[-#]\\d+");
    private static final long TIMEOUT_MS = 60000;
    private final JiraGetService jiraGetService;
    private final JiraSearchService jiraSearchService;

    private final IssueManager issueManager;
    private final CustomFieldManager customFieldManager;
    private final SearchService searchService;

    public JiraHelper(JiraGetService jiraGetService, JiraSearchService jiraSearchService, IssueManager issueManager, CustomFieldManager customFieldManager, SearchService searchService) {
        this.jiraGetService = jiraGetService;
        this.jiraSearchService = jiraSearchService;
        this.issueManager = issueManager;
        this.customFieldManager = customFieldManager;
        this.searchService = searchService;
    }

    @Override
    public Project getAndValidateProject(String key) {
        Validate.notNull(key, "project key should not be null");
        Optional<Project> project = jiraGetService.getProject(key);
        if (!project.isPresent()) {
            throw new JiraObjectNotFoundException(String.format("Could not find JIRA Project %s", key));
        }
        return project.get();
    }

    @Override
    public Version getAndValidateVersion(ApplicationUser user, Project project, String versionName) {
        Validate.notNull(versionName);
        VersionService.VersionResult versionResult = jiraGetService.getVersion(user, project, versionName);
        if (!versionResult.isValid()) {
            String message = String.format("Version %s not found in project %s", versionName, project.getKey());
            LOG.error(message);
            LOG.error(getMessagesFromErrorCollection(versionResult.getErrorCollection()));
            throw new JiraApplicationException(message);
        }
        return versionResult.getVersion();
    }

    @Override
    public IssueLinkType getAndValidateIssueLinkTypeInclude() {
        Optional<IssueLinkType> issueLinkTypeResult = jiraGetService.getIssueLinkTypeByName(INCLUDE_ISSUE_LINK_TYPE);
        if (!issueLinkTypeResult.isPresent()) {
            throw new JiraApplicationException(String.format("Could not find issue link type %s. Please configure JIRA Instance", INCLUDE_ISSUE_LINK_TYPE));
        }
        return issueLinkTypeResult.get();
    }

    @Override
    public IssueType getAndValidateIssueTypeReleaseRequest() {
        Optional<IssueType> issueTypeResult = jiraGetService.getIssueTypeByName(IssueTypeEnum.RELEASE_REQUEST.getName());
        return issueTypeResult.orElseThrow(() ->
                new JiraApplicationException(String.format("Could not find issue type %s. Please configure JIRA " +
                        "Instance", IssueTypeEnum.RELEASE_REQUEST.getName())));
    }

    @Override
    public Object getCustomFieldValue(Issue issue, CustomField owCustomField) {
        com.atlassian.jira.issue.fields.CustomField customField = getCustomField(owCustomField.getName());
        if (customField == null) {
            throw new JiraApplicationException("Unable to find custom field " + owCustomField.getName() + ". Configure JIRA Instance");
        }
        return issue.getCustomFieldValue(customField);
    }

    @Override
    public FieldConfig getFieldConfig(com.atlassian.jira.issue.fields.CustomField customField, String projectKey) {
        FieldConfig fieldConfig = getFieldConfigSafe(customField, projectKey);
        if (fieldConfig == null) {
            throw new JiraApplicationException(String.format("Could not find field '%s' config in project %s",
                    customField.getFieldName(), projectKey));
        }
        return fieldConfig;
    }

    @Override
    public FieldConfig getFieldConfigSafe(com.atlassian.jira.issue.fields.CustomField customField, String projectKey) {
        Validate.notNull(customField, "custom field can't be null");
        List<FieldConfigScheme> schemes = customField.getConfigurationSchemes();
        if (schemes != null && !schemes.isEmpty()) {
            for (FieldConfigScheme scheme : schemes) {
                List<Project> associatedProjects = scheme.getAssociatedProjectObjects();
                for (Project project : associatedProjects) {
                    if (project.getKey().equals(projectKey)) {
                        MultiMap configs = scheme.getConfigsByConfig();
                        if (configs != null && !configs.isEmpty()) {
                            return (FieldConfig) configs.keySet().iterator().next();
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String getReleaseVersionNameByBuild(String releaseBuild) {

        Matcher matcher = CRD_BUILD_PATTERN.matcher(releaseBuild);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }


    @Override
    public boolean crdBuild(String releaseBuild) {
        Matcher matcher = CRD_BUILD_PATTERN.matcher(releaseBuild);
        return matcher.matches();
    }

    @Override
    public void logTime(Logger logger, String method, long startNs, String... params) {
        long elapsedMs = getElapsedMs(startNs);
        double elapsedSec = elapsedMs / MILLIS_IN_SECOND;
        if (elapsedMs > TIMEOUT_MS) {
            logger.error(method + Arrays.toString(params) + " took " + elapsedSec + "s " +
                    "exceeded timeout");
        } else {
            logger.info(method + Arrays.toString(params) + ") took " + elapsedSec + "s");
        }
    }

    private long getElapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / NANO_IN_MILLIS;
    }


    @Override
    public boolean versionExists(ApplicationUser user, Project project, String version) {
        return jiraGetService.getVersion(user, project, version).isValid();
    }

    @Override
    public com.atlassian.jira.issue.fields.CustomField getCustomFieldSafe(String fieldName) {
        com.atlassian.jira.issue.fields.CustomField customField = customFieldManager.getCustomFieldObjectByName(fieldName);
        if (customField == null) {
            throw new JiraApplicationException(String.format("Custom field '%s' not found. Configure Jira Instance", fieldName));
        }
        return customField;
    }

    @Override
    public com.atlassian.jira.issue.fields.CustomField getCustomField(String fieldName) {
        return customFieldManager.getCustomFieldObjectByName(fieldName);
    }

    @Override
    public boolean customFieldExists(String fieldName) {
        return customFieldManager.getCustomFieldObjectByName(fieldName) != null;
    }

    @Override
    public String getCustomFieldValueAsString(com.atlassian.jira.issue.fields.CustomField customField, Issue issue) {
        if (customField != null) {
            Object fieldValue = issue.getCustomFieldValue(customField);
            return fieldValue != null ? fieldValue.toString() : null;
        }
        return "";
    }

    @Override
    public String getClientReleaseNotesValue(com.atlassian.jira.issue.fields.CustomField clientReleaseNotesField, Issue issue) {
        if (issue.getIssueType().getName().equals(IssueTypeEnum.BACKPORTING.getName())) {
            issue = issue.getParentObject();
        }

        return getCustomFieldValueAsString(clientReleaseNotesField, issue);
    }

    @Override
    public List<Version> getCustomFieldValueAsVersionList(com.atlassian.jira.issue.fields.CustomField customField, Issue issue) {
        if (customField != null) {
            List<Version> fieldVersions = (List<Version>) issue.getCustomFieldValue(customField);
            if (fieldVersions == null) {
                return Collections.emptyList();
            }
            return fieldVersions;
        }
        return Collections.emptyList();
    }


    @Override
    public List<Issue> getIssueWithPartialReopen(ApplicationUser user, Project project, List<Version> versions) {
        return getIssuesWithPartialReopenUpdatedAfterDate(user, project, versions, null);
    }

    @Override
    public List<Issue> getIssuesWithPartialReopenUpdatedAfterDate(ApplicationUser user, Project project, List<Version> versions, Date date) {
        return getIssuesWithVersionsPickerCustomFieldUpdatedAfterDate(CustomField.APPROVED_FOR_RELEASE.getName(), user, project, versions, date);
    }

    private List<Issue> getIssuesWithVersionsPickerCustomFieldUpdatedAfterDate(String customFieldName, ApplicationUser user, Project project, List<Version> versions, Date date) {
        if (!customFieldExists(customFieldName)) {
            return Collections.emptyList();
        }
        com.atlassian.jira.issue.fields.CustomField customField = getCustomFieldSafe(customFieldName);
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();


        builder.where().project(project.getId()).and().customField(customField.getIdAsLong()).in(getVersionIds(versions));
        if (date != null) {
            builder.where().and().updatedAfter(date);
        }
        Query query = builder.orderBy().issueKey(SortOrder.ASC).endOrderBy().buildQuery();
        return jiraSearchService.getIssuesFromQuery(user, query);


    }

    @Override
    public Long[] getVersionIds(Collection<Version> versions) {
        Long[] versionIds = new Long[versions.size()];
        int i = 0;
        for (Version version : versions) {
            versionIds[i] = version.getId();
            i++;
        }
        return versionIds;
    }

    @Override
    public Issue getIssue(String key) {
        return issueManager.getIssueByCurrentKey(key);
    }

    @Override
    public List<Issue> getIssuesFromQuery(ApplicationUser user, Query query) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing " + searchService.getJqlString(query));
            }
            SearchResults<Issue> searchResults = searchService.search(user, query, PagerFilter.getUnlimitedFilter());
            return searchResults.getResults();
        } catch (SearchException e) {
            throw new JiraSearchEngineException("Error while searching issues for project %s version %s", e);
        }
    }

    @Override
    public Query getQueryOrderByIssueKey(JqlQueryBuilder builder) {
        return builder.orderBy().issueKey(SortOrder.ASC).endOrderBy().buildQuery();
    }

    @Override
    public String[] getVersionStringIds(Collection<Version> versions) {
        Long[] versionIds = getVersionIds(versions);
        String[] versionIdsString = new String[versionIds.length];
        for (int i = 0; i < versionIds.length; i++) {
            versionIdsString[i] = String.valueOf(versionIds[i]);
        }
        return versionIdsString;
    }

    @Override
    public List<String> toKeyList(Collection<Issue> issues) {
        Validate.notNull(issues);
        List<String> issuesKeys = Lists.newArrayList();
        for (Issue issue : issues) {
            issuesKeys.add(issue.getKey());
        }
        return issuesKeys;
    }


    public String getMessagesFromErrorCollection(ErrorCollection errorCollection) {
        return StringUtils.join(errorCollection.getErrorMessages().toArray(), ", ");
    }


}
