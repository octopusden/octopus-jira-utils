package org.octopusden.octopus.jira.config

import com.atlassian.jira.project.Project
import com.atlassian.jira.project.version.Version
import org.octopusden.octopus.jira.model.Component
import org.octopusden.octopus.jira.model.DetailedComponentVersion
import org.octopusden.octopus.jira.model.DetailedComponentVersions
import org.octopusden.octopus.jira.model.Distribution
import org.octopusden.octopus.jira.model.JiraComponentVersionRange
import org.octopusden.octopus.jira.model.JiraProjectVersion
import org.octopusden.octopus.jira.model.VCSSettings
import org.octopusden.octopus.releng.dto.ComponentVersion
import org.octopusden.octopus.releng.dto.JiraComponentVersion
import java.util.Optional
import org.octopusden.octopus.releng.JiraComponentVersionFormatter
import org.octopusden.releng.versions.VersionNames

interface ComponentRegistryService {

    fun getAllComponents(): List<Component>

    fun getComponent(component: String): Optional<Component>

    fun isVersionMinor(version: Version): Boolean

    fun getMinorVersion(version: Version): Optional<String>

    fun getMinorVersion(versionName: String, project: Project): Optional<String>

    fun getJiraComponentByProjectAndVersion(jiraProjectVersion: JiraProjectVersion): Optional<JiraComponentVersion>

    fun getJiraComponentsByProject(projectName: String): Set<String>

    fun getJiraComponentVersionRangesByProject(projectKey: String): Set<JiraComponentVersionRange>

    fun componentExists(projectVersion: JiraProjectVersion): Boolean

    fun componentExists(projectKey: String): Boolean

    fun getJiraComponentByComponentNameAndVersion(componentVersion: ComponentVersion): Optional<JiraComponentVersion>

    fun getDistribution(jiraProjectVersion: JiraProjectVersion): Optional<Distribution>

    fun getDistribution(componentVersion: ComponentVersion): Optional<Distribution>

    fun getVCSSettings(jiraProjectVersion: JiraProjectVersion): Optional<VCSSettings>

    fun getVCSSettings(componentVersion: ComponentVersion): Optional<VCSSettings>

    fun getComponentsDistributionByJiraProject(projectKey: String): Map<String, Distribution>

    fun getAllJiraComponentVersionRanges(): Set<JiraComponentVersionRange>

    fun getDetailedComponentVersion(componentVersion: ComponentVersion): DetailedComponentVersion

    fun getDetailedComponentVersions(component: String, versions: Set<String>): DetailedComponentVersions

    fun checkCacheActualityAndClean(forceClean: Boolean = false)

    fun getVersionNames(): VersionNames

    fun getComponentVersionFormatter(): JiraComponentVersionFormatter
}
