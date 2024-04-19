package org.octopusden.octopus.jira.model

data class Component(
    val id: String,
    val name: String?,
    val componentOwner: String? = null,
    val releaseManager: String? = null,
    val distribution: Distribution? = null,
    val releasesInDefaultBranch: Boolean?,
)
