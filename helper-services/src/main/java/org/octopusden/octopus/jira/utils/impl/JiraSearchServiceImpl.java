package org.octopusden.octopus.jira.utils.impl;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.MessageSet;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.query.order.SortOrder;
import org.octopusden.octopus.jira.exception.JiraSearchEngineException;
import org.octopusden.octopus.jira.utils.JiraSearchService;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class JiraSearchServiceImpl implements JiraSearchService {
    public static final String DONE = "Done";
    public static final String UNRESOLVED = "Unresolved";
    static final Logger LOG = LoggerFactory.getLogger(JiraSearchServiceImpl.class);
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final SearchService searchService;

    public JiraSearchServiceImpl(JiraAuthenticationContext jiraAuthenticationContext, SearchService searchService) {
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.searchService = searchService;
    }


    @Override
    public List<Issue> findIssues(ApplicationUser user, Project project, Version version) {
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
        addVersionResolvedCondition(builder.where().project(project.getId())).and().fixVersion(version.getId());
        Query query = builder.orderBy().issueKey(SortOrder.ASC).endOrderBy().buildQuery();

        return getIssuesFromQuery(user, query);
    }

    @Override
    public List<Issue> findIssues(ApplicationUser user, Project project, List<Version> versions) {
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();

        ArrayList<Long> versionIDs = new ArrayList<>();
        for (Version version : versions) {
            versionIDs.add(version.getId());
        }
        addVersionResolvedCondition(builder.where().project(project.getId())).and().fixVersion().in(versionIDs.toArray(new Long[versionIDs.size()]));
        Query query = builder.orderBy().issueKey(SortOrder.ASC).endOrderBy().buildQuery();

        return getIssuesFromQuery(user, query);
    }


    @Override
    public List<Issue> findReleasedIssues(Version version) {
        Validate.notNull(version, "version can't be null");
        ApplicationUser currentUser = jiraAuthenticationContext.getLoggedInUser();
        Validate.notNull(currentUser, "Current session is not authenticated");
        return findIssues(currentUser, version.getProject(), version);
    }

    @Override
    public List<Issue> findIssues(ApplicationUser user, Project project, Version version, IssueType issueType, boolean isReleased) {
        Validate.notNull(version, "version can't be null");
        Validate.notNull(issueType, "issueType can't be null");

        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
        builder.where().project(project.getId()).and().issueType(issueType.getId()).and().fixVersion(version.getId());
        builder.where().and().resolution(isReleased ? DONE : UNRESOLVED);
        Query query = builder.orderBy().issueKey(SortOrder.ASC).endOrderBy().buildQuery();

        return getIssuesFromQuery(user, query);
    }

    @Override
    public List<Issue> findIssues(ApplicationUser user, Version version, IssueType issueType, boolean isReleased) {
        return findIssues(user, version.getProject(), version, issueType, isReleased);
    }

    @Override
    public List<Issue> findIssuesByCustomFieldValue(ApplicationUser user, CustomField cf, String value) {
        return new ArrayList<>(findIssuesByCustomFieldValue(user, cf, value, false));
    }

    @Override
    public Collection<Issue> findIssuesByCustomFieldValue(ApplicationUser user, CustomField cf, String searchFieldValue, boolean equalComparison) {
        final long cfId = cf.getIdAsLong();
        final Query query = JqlQueryBuilder.newBuilder().where().customField(cfId).like(searchFieldValue).buildQuery();
        final MessageSet messageSet = searchService.validateQuery(user, query);
        checkMessageSet(messageSet, query, user);
        final List<Issue> issues = getIssuesFromQuery(user, query);
        if (!equalComparison) {
            return issues;
        }
        return issues.stream().filter(issue -> issue.getCustomFieldValue(cf).equals(searchFieldValue)).collect(Collectors.toList());
    }

    private JqlClauseBuilder addVersionResolvedCondition(JqlClauseBuilder builder) {
        return builder.and().resolution(DONE);
    }

    @Override
    public List<Issue> getIssuesFromQuery(ApplicationUser user, Query query) {
        MessageSet messageSet = searchService.validateQuery(user, query);
        checkMessageSet(messageSet, query, user);
        try {
            SearchResults<Issue> searchResults = searchService.search(user, query, PagerFilter.getUnlimitedFilter());
            return searchResults.getResults();
        } catch (SearchException e) {
            throw new JiraSearchEngineException("Jira Search Service Failed", e);
        }
    }

    private void checkMessageSet(MessageSet messageSet, Query query, ApplicationUser user) {

        if (messageSet.hasAnyWarnings()) {
            LOG.warn(messageSet.getWarningMessages().size() + " warning(s) has been generated:");
            for (String warning : messageSet.getWarningMessages()) {
                LOG.warn("\t" + warning);
            }
            LOG.warn("Query " + query.getQueryString() + " by " + user.getName());
        }
        if (messageSet.hasAnyErrors()) {
            LOG.error(messageSet.getErrorMessages().size() + " error(s) has been found:");
            for (String error : messageSet.getErrorMessages()) {
                LOG.error("\t" + error);
            }
            LOG.error("Query " + query.getQueryString() + " by " + user.getName());
        }
    }
}
