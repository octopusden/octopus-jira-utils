package org.octopusden.octopus.jira.config

import com.atlassian.cache.CacheManager
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.version.Version
import feign.FeignException
import java.util.Date
import java.util.Optional
import java.util.SortedSet
import org.octopusden.octopus.components.registry.client.ComponentsRegistryServiceClient
import org.octopusden.octopus.components.registry.core.dto.ComponentInfoDTO
import org.octopusden.octopus.components.registry.core.dto.ComponentVersionFormatDTO
import org.octopusden.octopus.components.registry.core.dto.DistributionDTO
import org.octopusden.octopus.components.registry.core.dto.JiraComponentDTO
import org.octopusden.octopus.components.registry.core.dto.JiraComponentVersionDTO
import org.octopusden.octopus.components.registry.core.dto.JiraComponentVersionRangeDTO
import org.octopusden.octopus.components.registry.core.dto.VCSSettingsDTO
import org.octopusden.octopus.components.registry.core.dto.VcsRootDateDTO
import org.octopusden.octopus.components.registry.core.dto.VersionControlSystemRootDTO
import org.octopusden.octopus.components.registry.core.dto.VersionNamesDTO
import org.octopusden.octopus.components.registry.core.dto.VersionRequest
import org.octopusden.octopus.jira.exception.JiraApplicationException
import org.octopusden.octopus.jira.model.Component
import org.octopusden.octopus.jira.model.ComponentRegistryVersion
import org.octopusden.octopus.jira.model.DetailedComponentVersion
import org.octopusden.octopus.jira.model.DetailedComponentVersions
import org.octopusden.octopus.jira.model.Distribution
import org.octopusden.octopus.jira.model.JiraComponentVersionRange
import org.octopusden.octopus.jira.model.JiraProjectVersion
import org.octopusden.octopus.jira.model.RepositoryType
import org.octopusden.octopus.jira.model.VCSSettings
import org.octopusden.octopus.jira.model.VcsRootLastChangeDate
import org.octopusden.octopus.jira.model.VersionControlSystemRoot
import org.octopusden.octopus.releng.JiraComponentVersionFormatter
import org.octopusden.octopus.releng.dto.ComponentInfo
import org.octopusden.octopus.releng.dto.ComponentVersion
import org.octopusden.octopus.releng.dto.JiraComponent
import org.octopusden.octopus.releng.dto.JiraComponentVersion
import org.octopusden.releng.versions.ComponentVersionFormat
import org.octopusden.releng.versions.VersionNames
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Named

@Named
class ComponentRegistryServiceImpl @Inject constructor(
        private val client: ComponentsRegistryServiceClient,
        private val cacheManager: CacheManager
) : ComponentRegistryService {

    private lateinit var versionNames: VersionNames

    private val jiraComponentVersionFormatter = createJiraComponentVersionFormatter()

    private var fixedRemoteDate: Date = Date(0L)

    private val allComponentsCache = cacheManager.getCache(CacheId.ALL_COMPONENTS.id()) { _: Unit ->
        client.getAllComponents().components.map { it.toModel() }
    }

    private val componentsCache = cacheManager.getCache(CacheId.COMPONENT.id()) { component: String ->
        val componentDto = clearResponse {
            client.getById(component).toModel()
        }
        Optional.ofNullable(componentDto)
    }

    private val isMinorVersionCache = cacheManager.getCache(CacheId.IS_VERSION_MINOR.id()) { version: Version ->
        val project = version.project
        val jiraComponentVersion = getJiraComponentByProjectAndVersion(JiraProjectVersion(project.key, version.name))
        jiraComponentVersion.map { jiraComponentVersionValue ->
            jiraComponentVersionFormatter.matchesMajorVersionFormat(jiraComponentVersionValue, version.name)
        }.orElseGet {
            log.error("Version ${project.key}:${version.name} is not found in Components Registry")
            false
        }
    }

    private val minorVersionByVersionCache = cacheManager.getCache(CacheId.MINOR_VERSION_BY_VERSION.id()) { version: Version ->
        getMinorVersion(version.name, version.project)
    }

    private data class MinorVersionCacheReq(val versionName: String, val project: Project)

    private val minorVersionByVersionNameAndProjectCache = cacheManager.getCache(CacheId.MINOR_VERSION_BY_VERSION_NAME_AND_PROJECT.id()) { req: MinorVersionCacheReq ->
        getJiraComponentByProjectAndVersion(JiraProjectVersion(req.project.key, req.versionName))
                .map { jcv -> jiraComponentVersionFormatter.formatMajorVersionFormat(jcv.component, req.versionName) }
    }

    private val jiraComponentByProjectAndVersion = cacheManager.getCache(CacheId.JIRA_COMPONENT_BY_PROJECT_VERSION.id()) { jiraProjectVersion: JiraProjectVersion ->
        val jiraComponentVersion = clearResponse {
            client.getJiraComponentByProjectAndVersion(jiraProjectVersion.projectKey, jiraProjectVersion.version)
                    .toModel()
        }
        Optional.ofNullable(jiraComponentVersion)
    }

    private val jiraComponentsByProjectCache = cacheManager.getCache(CacheId.JIRA_COMPONENTS_BY_PROJECT_KEY.id()) { projectKey: String ->
        clearResponse {
            client.getJiraComponentsByProject(projectKey)
        } ?: emptySet()
    }

    private val jiraComponentVersionRangesByProjectCache = cacheManager.getCache(CacheId.JIRA_COMPONENT_VERSION_RANGES_BY_PROJECT_KEY.id()) { projectKey: String ->
        clearResponse {
            client.getJiraComponentVersionRangesByProject(projectKey)
                    .map { it.toModel() }
                    .toSet()
        } ?: emptySet()
    }

    private val distributionCacheByJiraProjectVersion = cacheManager.getCache(CacheId.DISTRIBUTION_BY_JIRA_PROJECT_VERSION.id()) { jiraProjectVersion: JiraProjectVersion ->
        val distribution = clearResponse {
            client.getDistributionForProject(jiraProjectVersion.projectKey, jiraProjectVersion.version)
                    .toModel()
        }
        Optional.ofNullable(distribution)
    }

    private val distributionByComponentVersionCache = cacheManager.getCache(CacheId.DISTRIBUTION_BY_COMPONENT_VERSION.id()) { componentVersion: ComponentVersion ->
        val distribution = clearResponse {
            client.getComponentDistribution(componentVersion.componentName, componentVersion.version)
                    .toModel()
        }
        Optional.ofNullable(distribution)
    }

    private val vcsSettingsByJiraProjectVersionCache = cacheManager.getCache(CacheId.VCS_SETTINGS_BY_JIRA_PROJECT_VERSION.id()) { jiraProjectVersion: JiraProjectVersion ->
        val vcsSettings = clearResponse {
            client.getVCSSettingForProject(jiraProjectVersion.projectKey, jiraProjectVersion.version)
                    .toModel()
        }
        Optional.ofNullable(vcsSettings)
    }

    private val vcsSettingsByComponentVersionCache = cacheManager.getCache(CacheId.VCS_SETTINGS_BY_COMPONENT_VERSION.id()) { componentVersion: ComponentVersion ->
        val vcsSettings = clearResponse {
            client.getVCSSetting(componentVersion.componentName, componentVersion.version)
                    .toModel()

        }
        Optional.ofNullable(vcsSettings)
    }

    private data class DetailedComponentVersionsCacheRequest(val component: String, val versions: SortedSet<String>)

    private val detailedComponentVersionsCache = cacheManager.getCache(CacheId.DETAILED_COMPONENT_VERSIONS.id()) { req: DetailedComponentVersionsCacheRequest ->
        val versionRequest = VersionRequest(req.versions.toList())
        client.getDetailedComponentVersions(req.component, versionRequest)
                .toModel()
    }

    private val componentsDistributionByJiraProjectCache = cacheManager.getCache(CacheId.DISTRIBUTION_BY_PROJECT_KEY.id()) { projectKey: String ->
        clearResponse {
            client.getComponentsDistributionByJiraProject(projectKey)
                    .map { it.key to it.value.toModel() }
                    .toMap()
        } ?: emptyMap()
    }

    private val componentExistsByJiraProjectVersionCache = cacheManager.getCache(CacheId.IS_COMPONENT_EXISTS_BY_PROJECT_VERSION.id()) { jiraProjectVersion: JiraProjectVersion ->
        clearResponse {
            client.getJiraComponentByProjectAndVersion(jiraProjectVersion.projectKey, jiraProjectVersion.version)
            true
        } ?: false
    }

    private val componentExistsByJiraProjectCache = cacheManager.getCache(CacheId.IS_COMPONENT_EXISTS_BY_PROJECT_KEY.id()) { projectKey: String ->
        clearResponse {
            client.getJiraComponentsByProject(projectKey)
                    .isNotEmpty()
        } ?: false
    }

    private val jiraComponentByComponentNameAndVersionCache = cacheManager.getCache(CacheId.JIRA_COMPONENT_BY_COMPONENT_VERSION.id()) { componentVersion: ComponentVersion ->
        val jiraComponentVersion = clearResponse {
            client.getJiraComponentForComponentAndVersion(componentVersion.componentName, componentVersion.version)
                    .toModel()
        }
        Optional.ofNullable(jiraComponentVersion)
    }

    private val allJiraComponentVersionRangesCache = cacheManager.getCache(CacheId.ALL_JIRA_COMPONENT_VERSION_RANGES.id()) { _: Unit ->
        clearResponse {
            client.getAllJiraComponentVersionRanges()
                    .map { it.toModel() }
                    .toSet()
        } ?: emptySet()
    }

    private val detailedComponentVersionCache = cacheManager.getCache(CacheId.DETAILED_COMPONENT_VERSION.id()) { componentVersion: ComponentVersion ->
        client.getDetailedComponentVersion(componentVersion.componentName, componentVersion.version)
                .toModel()
    }

    private fun createJiraComponentVersionFormatter(): JiraComponentVersionFormatter {
        return JiraComponentVersionFormatter(getVersionNames())
    }

    override fun getAllComponents(): List<Component> {
        return allComponentsCache.get(Unit)!!
    }

    override fun getVersionNames(): VersionNames {
        if (::versionNames.isInitialized) {
            return versionNames
        }
        val vn = client.getVersionNames()
        return vn.toModel()
    }

    override fun getComponent(component: String): Optional<Component> {
        return componentsCache.get(component)!!
    }

    override fun getComponentVersionFormatter() = jiraComponentVersionFormatter

    override fun isVersionMinor(version: Version): Boolean {
        return isMinorVersionCache.get(version)!!
    }

    override fun getMinorVersion(version: Version): Optional<String> {
        return minorVersionByVersionCache.get(version)!!
    }

    override fun getMinorVersion(versionName: String, project: Project): Optional<String> {
        return minorVersionByVersionNameAndProjectCache.get(MinorVersionCacheReq(versionName, project))!!
    }

    override fun getJiraComponentByProjectAndVersion(jiraProjectVersion: JiraProjectVersion): Optional<JiraComponentVersion> {
        return jiraComponentByProjectAndVersion.get(jiraProjectVersion)!!
    }

    override fun getJiraComponentsByProject(projectName: String): Set<String> {
        return jiraComponentsByProjectCache.get(projectName)!!
    }

    override fun getJiraComponentVersionRangesByProject(projectKey: String): Set<JiraComponentVersionRange> {
        return jiraComponentVersionRangesByProjectCache.get(projectKey)!!
    }

    override fun getDistribution(jiraProjectVersion: JiraProjectVersion): Optional<Distribution> {
        return distributionCacheByJiraProjectVersion.get(jiraProjectVersion)!!
    }

    override fun getDistribution(componentVersion: ComponentVersion): Optional<Distribution> {
        return distributionByComponentVersionCache.get(componentVersion)!!
    }

    override fun getVCSSettings(jiraProjectVersion: JiraProjectVersion): Optional<VCSSettings> {
        return vcsSettingsByJiraProjectVersionCache.get(jiraProjectVersion)!!
    }

    override fun getVCSSettings(componentVersion: ComponentVersion): Optional<VCSSettings> {
        return vcsSettingsByComponentVersionCache.get(componentVersion)!!
    }

    override fun getComponentsDistributionByJiraProject(projectKey: String): Map<String, Distribution> {
        return componentsDistributionByJiraProjectCache.get(projectKey)!!
    }

    override fun componentExists(projectVersion: JiraProjectVersion): Boolean {
        return componentExistsByJiraProjectVersionCache.get(projectVersion)!!
    }

    override fun componentExists(projectKey: String): Boolean {
        return componentExistsByJiraProjectCache.get(projectKey)!!
    }

    override fun getJiraComponentByComponentNameAndVersion(componentVersion: ComponentVersion): Optional<JiraComponentVersion> {
        return jiraComponentByComponentNameAndVersionCache.get(componentVersion)!!
    }

    override fun getAllJiraComponentVersionRanges(): Set<JiraComponentVersionRange> {
        return allJiraComponentVersionRangesCache.get(Unit)!!
    }

    override fun getDetailedComponentVersion(componentVersion: ComponentVersion): DetailedComponentVersion {
        return detailedComponentVersionCache.get(componentVersion)!!
    }

    override fun getDetailedComponentVersions(component: String, versions: Set<String>): DetailedComponentVersions {
        return detailedComponentVersionsCache.get(DetailedComponentVersionsCacheRequest(component, versions.toSortedSet()))!!
    }

    override fun checkCacheActualityAndClean(forceClean: Boolean) {
        val remoteDate = client.getServiceStatus().cacheUpdatedAt
        if (forceClean || this.fixedRemoteDate != remoteDate) {
            log.info("Cleaning CR cache force=$forceClean $fixedRemoteDate != $remoteDate")
            CacheId.values()
                .forEach {
                    cacheManager.getManagedCache(it.id())
                        ?.clear()
                }
            this.fixedRemoteDate = remoteDate
        } else {
            log.debug("Skip clean CR cache force=$forceClean fixedRemoteDate=$fixedRemoteDate  remoteDate=$remoteDate")
        }
    }

    private fun <T> clearResponse(function: () -> T): T? {
        return try {
            function.invoke()
        } catch (e: FeignException) {
            throw JiraApplicationException(e.message, e)
        } catch (e: Exception) {
            null
        }
    }

    private fun org.octopusden.octopus.components.registry.core.dto.Component.toModel(): Component {
        return Component(id, name, componentOwner, releaseManager, distribution?.toModel())
    }

    private fun JiraComponentVersionDTO.toModel(): JiraComponentVersion =
            JiraComponentVersion(ComponentVersion.create(name, version), component.toModel(), jiraComponentVersionFormatter)

    private fun JiraComponentDTO.toModel(): JiraComponent {
        return JiraComponent(projectKey, displayName, this.componentVersionFormat.toModel(), this.componentInfo.toModel(), technical)
    }

    private fun VersionControlSystemRootDTO.toModel(): VersionControlSystemRoot {
        return VersionControlSystemRoot(name, RepositoryType.valueOf(type.name), vcsPath, tag, branch)
    }

    private fun JiraComponentVersionRangeDTO.toModel(): JiraComponentVersionRange {
        return JiraComponentVersionRange(
                componentName,
                versionRange,
                component.toModel(),
                distribution.toModel(),
                vcsSettings.toModel()
        )
    }

    private fun VersionNamesDTO.toModel(): VersionNames {
        return VersionNames(serviceBranch, service, minor)
    }

    private fun VcsRootDateDTO.toModel(): VcsRootLastChangeDate {
        return VcsRootLastChangeDate(root.toModel(), date)
    }

    private fun ComponentInfoDTO.toModel(): ComponentInfo {
        return ComponentInfo(versionPrefix, versionFormat)
    }

    private fun ComponentVersionFormatDTO.toModel(): ComponentVersionFormat {
        return ComponentVersionFormat.create(majorVersionFormat, releaseVersionFormat, buildVersionFormat, lineVersionFormat)
    }

    private fun DistributionDTO.toModel(): Distribution {
        return Distribution(explicit, external, gav?:"")
    }

    private fun VCSSettingsDTO.toModel(): VCSSettings {
        return VCSSettings(externalRegistry, versionControlSystemRoots.map { it.toModel() })
    }

    private fun org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion.toModel(): DetailedComponentVersion {
        return DetailedComponentVersion(component, lineVersion.toModel(), minorVersion.toModel(), buildVersion.toModel(),
                rcVersion.toModel(), releaseVersion.toModel())
    }

    private fun org.octopusden.octopus.components.registry.core.dto.ComponentRegistryVersion.toModel(): ComponentRegistryVersion {
        return ComponentRegistryVersion(version, jiraVersion)
    }

    private fun org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersions.toModel(): DetailedComponentVersions {
        return DetailedComponentVersions(versions.map { it.key to it.value.toModel() }.toMap())
    }

    companion object {
        private val log = LoggerFactory.getLogger(ComponentRegistryServiceImpl::class.java)
    }
}
