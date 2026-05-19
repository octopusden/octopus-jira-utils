package org.octopusden.octopus.jira.config

import com.atlassian.cache.Cache
import com.atlassian.cache.CacheManager
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.version.Version
import feign.FeignException
import java.util.Optional
import java.util.SortedSet
import javax.inject.Inject
import javax.inject.Named
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
import org.octopusden.octopus.jira.model.DetailedComponent
import org.octopusden.octopus.jira.model.DetailedComponentVersion
import org.octopusden.octopus.jira.model.DetailedComponentVersions
import org.octopusden.octopus.jira.model.Distribution
import org.octopusden.octopus.jira.model.JiraComponentVersionRange
import org.octopusden.octopus.jira.model.JiraProjectVersion
import org.octopusden.octopus.jira.model.RepositoryType
import org.octopusden.octopus.jira.model.UpdateCacheResult
import org.octopusden.octopus.jira.model.VCSSettings
import org.octopusden.octopus.jira.model.VcsRootWrapper
import org.octopusden.octopus.jira.model.VersionControlSystemRoot
import org.octopusden.octopus.releng.JiraComponentVersionFormatter
import org.octopusden.octopus.releng.dto.ComponentInfo
import org.octopusden.octopus.releng.dto.ComponentVersion
import org.octopusden.octopus.releng.dto.JiraComponent
import org.octopusden.octopus.releng.dto.JiraComponentVersion
import org.octopusden.releng.versions.ComponentVersionFormat
import org.octopusden.releng.versions.VersionNames
import org.slf4j.LoggerFactory

@Named
@Suppress("unused")
class ComponentRegistryServiceImpl @Inject constructor(
        private val client: ComponentsRegistryServiceClient,
        private val cacheManager: CacheManager
) : ComponentRegistryService {

    private lateinit var versionNames: VersionNames

    private val jiraComponentVersionFormatter = createJiraComponentVersionFormatter()

    @Volatile
    private var fixedRemoteStatus: Any? = null

    /**
     * Tracks loader activity so we can detect H4 — a loader call that started BEFORE
     * [checkCacheActualityAndClean] ran `clear()`, then finished AFTER, silently
     * re-inserting a stale value into the cache.
     *
     * - `inFlight` is incremented when a loader starts, decremented when it finishes.
     * - `lastCleanAtNanos` is the timestamp of the most recent clear() invocation.
     * - When a loader finishes, if it STARTED before `lastCleanAtNanos`, we log a
     *   warning identifying the cache, the key, and the elapsed time. That entry is
     *   the prime suspect for stale data in the cache.
     */
    private val loaderTracker = LoaderTracker()

    private class LoaderTracker {
        val inFlight = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile var lastCleanAtNanos: Long = 0L

        fun markCleaned() {
            lastCleanAtNanos = System.nanoTime()
        }

        fun <K, V> wrap(cacheName: String, fn: (K) -> V): (K) -> V = { key ->
            val startedAt = System.nanoTime()
            inFlight.incrementAndGet()
            try {
                val value = fn(key)
                val finishedAt = System.nanoTime()
                val cleanAt = lastCleanAtNanos
                // If a clean happened strictly between our start and our finish,
                // our about-to-be-cached value may be stale and overwrites the clean.
                if (cleanAt in (startedAt + 1)..finishedAt) {
                    val elapsedMs = (finishedAt - startedAt) / 1_000_000
                    log.warn(
                        "POSSIBLE STALE REINSERT in cache '{}': loader for key='{}' started {}ms before clear() ran, " +
                            "finishing now will overwrite the cleared entry with data fetched from the pre-clean state of CR. " +
                            "elapsedMs={}, inFlightNow={}",
                        cacheName, key, (cleanAt - startedAt) / 1_000_000, elapsedMs, inFlight.get()
                    )
                }
                value
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

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

    private data class DetailedComponentCacheRequest(val component: String, val version: String)

    private val detailedComponentVersionsCache = cacheManager.getCache(
        CacheId.DETAILED_COMPONENT_VERSIONS.id(),
        loaderTracker.wrap<DetailedComponentVersionsCacheRequest, DetailedComponentVersions>(CacheId.DETAILED_COMPONENT_VERSIONS.id()) { req ->
            DetailedComponentVersions(
                req.versions.chunked(50) {
                    client.getDetailedComponentVersions(req.component, VersionRequest(it)).versions.mapValues { entry -> entry.value.toModel() }
                }.fold(mutableMapOf()) { result, element -> result.apply { putAll(element) } }
            )
        }
    )

    private val detailedComponentCache = cacheManager.getCache(
        CacheId.DETAILED_COMPONENT.id(),
        loaderTracker.wrap<DetailedComponentCacheRequest, DetailedComponent>(CacheId.DETAILED_COMPONENT.id()) { req ->
            client.getDetailedComponent(req.component, req.version)
                .toModel()
        }
    )

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

    private val detailedComponentVersionCache = cacheManager.getCache(
        CacheId.DETAILED_COMPONENT_VERSION.id(),
        loaderTracker.wrap<ComponentVersion, DetailedComponentVersion>(CacheId.DETAILED_COMPONENT_VERSION.id()) { componentVersion ->
            client.getDetailedComponentVersion(componentVersion.componentName, componentVersion.version)
                .toModel()
        }
    )

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

    override fun getDetailedComponent(component: String, version: String): DetailedComponent
    = detailedComponentCache.get(DetailedComponentCacheRequest(component, version))!!

    /**
     * Map of every [CacheId] to its in-process [Cache] reference.
     * We clear via these direct references (not via [CacheManager.getManagedCache])
     * because these are the exact instances the loader lambdas are bound to and that
     * `get()` calls read from — so clearing them is guaranteed to take effect.
     */
    private val cachesById: Map<CacheId, Cache<*, *>> by lazy {
        mapOf(
            CacheId.ALL_COMPONENTS to allComponentsCache,
            CacheId.COMPONENT to componentsCache,
            CacheId.IS_VERSION_MINOR to isMinorVersionCache,
            CacheId.MINOR_VERSION_BY_VERSION to minorVersionByVersionCache,
            CacheId.MINOR_VERSION_BY_VERSION_NAME_AND_PROJECT to minorVersionByVersionNameAndProjectCache,
            CacheId.JIRA_COMPONENT_BY_PROJECT_VERSION to jiraComponentByProjectAndVersion,
            CacheId.JIRA_COMPONENTS_BY_PROJECT_KEY to jiraComponentsByProjectCache,
            CacheId.JIRA_COMPONENT_VERSION_RANGES_BY_PROJECT_KEY to jiraComponentVersionRangesByProjectCache,
            CacheId.DISTRIBUTION_BY_JIRA_PROJECT_VERSION to distributionCacheByJiraProjectVersion,
            CacheId.DISTRIBUTION_BY_COMPONENT_VERSION to distributionByComponentVersionCache,
            CacheId.VCS_SETTINGS_BY_JIRA_PROJECT_VERSION to vcsSettingsByJiraProjectVersionCache,
            CacheId.VCS_SETTINGS_BY_COMPONENT_VERSION to vcsSettingsByComponentVersionCache,
            CacheId.DETAILED_COMPONENT_VERSIONS to detailedComponentVersionsCache,
            CacheId.DETAILED_COMPONENT to detailedComponentCache,
            CacheId.DISTRIBUTION_BY_PROJECT_KEY to componentsDistributionByJiraProjectCache,
            CacheId.IS_COMPONENT_EXISTS_BY_PROJECT_VERSION to componentExistsByJiraProjectVersionCache,
            CacheId.IS_COMPONENT_EXISTS_BY_PROJECT_KEY to componentExistsByJiraProjectCache,
            CacheId.JIRA_COMPONENT_BY_COMPONENT_VERSION to jiraComponentByComponentNameAndVersionCache,
            CacheId.ALL_JIRA_COMPONENT_VERSION_RANGES to allJiraComponentVersionRangesCache,
            CacheId.DETAILED_COMPONENT_VERSION to detailedComponentVersionCache
        )
    }

    override fun checkCacheActualityAndClean(forceClean: Boolean): UpdateCacheResult {
        val serviceStatus = client.getServiceStatus()
        val remoteStatus = serviceStatus.versionControlRevision ?: serviceStatus.cacheUpdatedAt

        val previousFixedRemoteStatus = this.fixedRemoteStatus
        val needClean = forceClean || previousFixedRemoteStatus != remoteStatus

        val message = if (needClean) {
            val inFlightAtCleanStart = loaderTracker.inFlight.get()
            val cleared = clearAllCaches()
            // Stamp the clean time AFTER clear() runs, so any loader whose put() happens
            // after this point and whose start was before this point gets flagged
            // by LoaderTracker as a POSSIBLE STALE REINSERT.
            loaderTracker.markCleaned()
            this.fixedRemoteStatus = remoteStatus
            log.info("Cleaned CR cache: clearedCaches={}, loadersInFlightAtCleanStart={}", cleared, inFlightAtCleanStart)
            if (inFlightAtCleanStart > 0) {
                log.warn(
                    "{} loader call(s) were in flight when clear() started. " +
                        "Any that finish AFTER this point may re-insert stale data. " +
                        "Watch for 'POSSIBLE STALE REINSERT' warnings.",
                    inFlightAtCleanStart
                )
            }
            "Cleaned CR cache"
        } else {
            "Skip clean CR cache"
        }
        return UpdateCacheResult(
            "$message force='$forceClean', fixedRemoteStatus='$previousFixedRemoteStatus', remoteStatus='$remoteStatus'"
        )
    }

    /**
     * Clears every cache via its direct in-process [Cache] reference (the same instance
     * bound to the loader lambda), so we are guaranteed to be hitting the map that
     * subsequent `get()` calls will read from. Returns the list of cleared cache ids.
     */
    private fun clearAllCaches(): List<String> {
        val cleared = mutableListOf<String>()
        for ((cacheId, cache) in cachesById) {
            try {
                cache.removeAll()
                cleared += cacheId.id()
            } catch (e: Exception) {
                log.warn("removeAll() failed for cache '${cacheId.id()}': ${e.message}", e)
            }
        }
        return cleared
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

    private fun org.octopusden.octopus.components.registry.core.dto.DetailedComponent.toModel(): DetailedComponent {
        return DetailedComponent(id, system, clientCode, name, componentOwner,buildSystem, vcsSettings.toModel(), jiraComponentVersion.toModel(), detailedComponentVersion.toModel())
    }

    private fun org.octopusden.octopus.components.registry.core.dto.Component.toModel(): Component {
        return Component(id, system, clientCode, name, componentOwner, releaseManager, distribution?.toModel(), releasesInDefaultBranch, archived)
    }

    private fun JiraComponentVersionDTO.toModel(): JiraComponentVersion {
        val componentVersion = ComponentVersion.create(name, version)
        return JiraComponentVersion(componentVersion, component.toModel(isHotfixEnabled(componentVersion)), jiraComponentVersionFormatter)
    }

    private fun JiraComponentDTO.toModel(isHotfixEnabled: Boolean): JiraComponent {
        return JiraComponent(projectKey, displayName, this.componentVersionFormat.toModel(), this.componentInfo.toModel(), technical, isHotfixEnabled)
    }

    private fun VersionControlSystemRootDTO.toModel(): VersionControlSystemRoot {
        return VersionControlSystemRoot(name, RepositoryType.valueOf(type.name), vcsPath, tag, branch, hotfixBranch)
    }

    private fun JiraComponentVersionRangeDTO.toModel(): JiraComponentVersionRange {
        val vcsSettingsModel = vcsSettings.toModel()
        return JiraComponentVersionRange(
            componentName,
            versionRange,
            component.toModel(isHotfixEnabled(vcsSettingsModel)),
            distribution.toModel(),
            vcsSettingsModel
        )
    }

    private fun VersionNamesDTO.toModel(): VersionNames {
        return VersionNames(serviceBranch, service, minor)
    }

    private fun VcsRootDateDTO.toModel(): VcsRootWrapper {
        return VcsRootWrapper(root.toModel())
    }

    private fun ComponentInfoDTO.toModel(): ComponentInfo {
        return ComponentInfo(versionPrefix, versionFormat)
    }

    private fun ComponentVersionFormatDTO.toModel(): ComponentVersionFormat {
        return ComponentVersionFormat.create(majorVersionFormat, releaseVersionFormat, buildVersionFormat, lineVersionFormat, hotfixVersionFormat)
    }

    private fun DistributionDTO.toModel(): Distribution {
        return Distribution(explicit, external, gav?:"")
    }

    private fun VCSSettingsDTO.toModel(): VCSSettings {
        return VCSSettings(externalRegistry, versionControlSystemRoots.map { it.toModel() })
    }

    private fun org.octopusden.octopus.components.registry.core.dto.DetailedComponentVersion.toModel(): DetailedComponentVersion {
        return DetailedComponentVersion(component, lineVersion.toModel(), minorVersion.toModel(), buildVersion.toModel(),
                rcVersion.toModel(), releaseVersion.toModel(), hotfixVersion?.toModel())
    }

    private fun org.octopusden.octopus.components.registry.core.dto.ComponentRegistryVersion.toModel(): ComponentRegistryVersion {
        return ComponentRegistryVersion(version, jiraVersion)
    }

    private fun isHotfixEnabled(componentVersion: ComponentVersion): Boolean {
        return getVCSSettings(componentVersion)
            .map { vcsSettings -> isHotfixEnabled(vcsSettings) }
            .orElse(false)
    }

    private fun isHotfixEnabled(vcsSettings: VCSSettings): Boolean {
        return vcsSettings.versionControlSystemRoots.any { vcsRoot ->
            !vcsRoot.hotfixBranch.isNullOrEmpty()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ComponentRegistryServiceImpl::class.java)
    }
}
