package org.octopusden.octopus.jira.model

import org.apache.commons.lang3.StringUtils

data class VCSSettings(val externalRegistry: String?, val versionControlSystemRoots: List<VersionControlSystemRoot> = emptyList()) {
    fun externalRegistry() = StringUtils.isNotBlank(externalRegistry)
    fun notAvailable() = externalRegistry == "NOT_AVAILABLE"
    fun hasNoConfiguredVCSRoot(): Boolean =
            versionControlSystemRoots.isEmpty() || versionControlSystemRoots.size == 1 && versionControlSystemRoots[0].vcsPath == null
}
