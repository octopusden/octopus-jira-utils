package org.octopusden.octopus.jira.model

data class Component(
    val id: String,
    val system: Set<String>,
    val clientCode: String?,
    val name: String?,
    val componentOwner: String? = null,
    val releaseManager: String? = null,
    val distribution: Distribution? = null,
    val releasesInDefaultBranch: Boolean?,
    val archived: Boolean,
)
