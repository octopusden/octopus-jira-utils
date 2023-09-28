package org.octopusden.octopus.jira.utils.impl;

import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.ServiceOutcome;
import com.atlassian.jira.bc.ServiceResult;
import com.atlassian.jira.bc.project.version.DeleteVersionWithReplacementsParameterBuilder;
import com.atlassian.jira.bc.project.version.VersionBuilder;
import com.atlassian.jira.bc.project.version.VersionService;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.DeleteVersionWithCustomFieldParameters;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.WarningCollection;
import com.atlassian.jira.web.util.AttachmentException;
import org.octopusden.octopus.jira.enums.JiraCustomField;
import org.octopusden.octopus.jira.exception.JiraApplicationException;
import org.octopusden.octopus.jira.exception.JiraInternalErrorException;
import org.octopusden.octopus.jira.utils.JiraCreateService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JiraCreateServiceImpl implements JiraCreateService {
    static final Logger LOG = LoggerFactory.getLogger(JiraCreateServiceImpl.class);

    private final VersionService versionService;
    private final AttachmentManager attachmentManager;
    private final CustomFieldManager customFieldManager;

    public JiraCreateServiceImpl(VersionService versionService,
                                 AttachmentManager attachmentManager,
                                 CustomFieldManager customFieldManager) {
        this.versionService = versionService;
        this.attachmentManager = attachmentManager;
        this.customFieldManager = customFieldManager;
    }

    private static String createVersionErrorMessage(String buildVersion, ServiceOutcome<Version> versionServiceOutcome) {
        return MessageFormat.format("Version {0} creation failed because of the following errors: {1}", buildVersion,
                StringUtils.join(versionServiceOutcome.getErrorCollection(), ", "));
    }

    @Override
    public Version createVersion(String buildVersion, Date buildDate, ApplicationUser user, Project project) {
        return createVersion(buildVersion, "Auto-created by Release-Engineering Plugin", buildDate, user, project);
    }

    @Override
    public Version createVersion(final String name, final String description, final Date date, ApplicationUser user, Project project) {
        final VersionBuilder versionBuilder = versionService.newVersionBuilder()
                .name(name)
                .description(description)
                .releaseDate(date)
                .projectId(project.getId());
        return validateAndDo(name, user, versionBuilder, versionService::validateCreate, versionService::create);
    }

    @Override
    public Version updateVersion(String currentVersionName, String newVersionName, ApplicationUser user, Project project) {
        return updateVersion(currentVersionName, newVersionName, user, project, (b, v) -> {});
    }

    @Override
    public Version updateVersion(String currentVersionName, String newVersionName, Date newReleaseDate, ApplicationUser user, Project project) {
        return updateVersion(currentVersionName, newVersionName, user, project, (b, v) -> b.releaseDate(newReleaseDate));
    }

    @Override
    public void deleteVersionAndSwap(String deletingVersion, String movingToVersion, ApplicationUser user, Project project) {
        final Version movingTo = getVersion(user, project, movingToVersion);
        final DeleteVersionWithReplacementsParameterBuilder builder = versionService.createVersionDeletaAndReplaceParameters(getVersion(user, project, deletingVersion))
                .moveAffectedIssuesTo(movingTo)
                .moveFixIssuesTo(movingTo);

        Stream.of(JiraCustomField.RC_VERSIONS, JiraCustomField.APPROVED_FOR_RELEASE, JiraCustomField.HIGHLIGHT, JiraCustomField.IMPACTS_ON)
                .flatMap(cfs -> customFieldManager.getCustomFieldObjectsByName(cfs.getName()).stream())
                .map(com.atlassian.jira.issue.fields.CustomField::getIdAsLong)
                .forEach(cfId -> builder.moveCustomFieldTo(cfId, movingTo));

        final DeleteVersionWithCustomFieldParameters customFieldParameters = builder.build();

        final ServiceResult serviceResult = versionService.deleteVersionAndSwap(new JiraServiceContextImpl(user), customFieldParameters);

        if (serviceResult.hasWarnings()) {
            logWarnings(serviceResult.getWarningCollection());
        }

        if (!serviceResult.isValid()) {
            final Map<String, String> errors = serviceResult.getErrorCollection()
                    .getErrors();
            logErrorsAndThrowJiraApplicationException(errors);
        }
    }

    private Version updateVersion(String currentVersionName, String newVersionName, ApplicationUser user, Project project, BiConsumer<VersionBuilder, Version> extraModify) {
        final Version existingVersion = versionService.getVersionByProjectAndName(user, project, currentVersionName)
                .getVersion();
        final VersionBuilder versionBuilder = versionService.newVersionBuilder(existingVersion)
                .name(newVersionName);
        extraModify.accept(versionBuilder, existingVersion);
        return validateAndDo(currentVersionName, user, versionBuilder, versionService::validateUpdate, versionService::update);
    }

    private Version getVersion(ApplicationUser user, Project project, String version) {
        final VersionService.VersionResult versionResult = versionService.getVersionByProjectAndName(user, project, version);
        if (!versionResult.isValid()) {
            final Map<String, String> errors = versionResult.getErrorCollection()
                    .getErrors();
            logErrorsAndThrowJiraApplicationException(errors);
        }
        if (versionResult.hasWarnings()) {
            logWarnings(versionResult.getWarningCollection());
        }
        return versionResult.getVersion();
    }

    private void logWarnings(WarningCollection warningCollection) {
        final String warningMessage = String.join(", ", warningCollection.getWarnings());
        LOG.warn(warningMessage);
    }

    private void logErrorsAndThrowJiraApplicationException(Map<String, String> errors) {
        final String errorMessage = errors
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));
        LOG.error(errorMessage);
        throw new JiraApplicationException(errorMessage);
    }

    private Version validateAndDo(String buildVersion,
                                  ApplicationUser user,
                                  VersionBuilder versionBuilder,
                                  BiFunction<ApplicationUser, VersionBuilder, VersionService.VersionBuilderValidationResult> validateFunction,
                                  BiFunction<ApplicationUser, VersionService.VersionBuilderValidationResult, ServiceOutcome<Version>> doFunction) {
        final VersionService.VersionBuilderValidationResult validationResult = validateFunction.apply(user, versionBuilder);
        if (!validationResult.isValid()) {
            throw new JiraApplicationException(validateCreateVersionErrorMessage(buildVersion, validationResult));
        }
        final ServiceOutcome<Version> versionServiceOutcome = doFunction.apply(user, validationResult);
        if (!versionServiceOutcome.isValid()) {
            throw new JiraApplicationException(createVersionErrorMessage(buildVersion, versionServiceOutcome));
        }
        return versionServiceOutcome.getReturnedValue();
    }

    @Override
    public void createAttachmentForIssue(Issue issue, String fileContent, String fileName) {
        CreateAttachmentParamsBean.Builder attachmentBuilder = new CreateAttachmentParamsBean.Builder();
        attachmentBuilder.author((issue.getReporter()));
        attachmentBuilder.filename(fileName);
        attachmentBuilder.issue(issue);
        attachmentBuilder.contentType("text/plain");
        File file = createTempFile(fileContent, fileName);
        attachmentBuilder.file(file);

        try {
            attachmentManager.createAttachment(attachmentBuilder.build());
        } catch (AttachmentException e) {
            throw new JiraInternalErrorException(String.format("Could not create attachment for issue %s", issue.getKey()), e);
        }

        LOG.info("Attachment " + fileName + " for " + issue.getKey() + " created");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Attachment for " + issue.getKey() + ": " + fileContent);
        }
    }

    File createTempFile(String fileContent, String fileName) {
        File tempFile = null;
        PrintWriter out = null;
        try {
            tempFile = File.createTempFile(fileName, ".txt");
            out = new PrintWriter(tempFile, "UTF-8");
            out.println(fileContent);
        } catch (IOException e) {
            LOG.error("Error while creating file ", e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return tempFile;
    }

    private String validateCreateVersionErrorMessage(String version, VersionService.VersionBuilderValidationResult versionBuilderValidationResult) {
        return MessageFormat.format("Version {0} creation cannot be performed because of the following errors: {1}", version,
                StringUtils.join(versionBuilderValidationResult.getErrorCollection(), ", "));
    }
}
