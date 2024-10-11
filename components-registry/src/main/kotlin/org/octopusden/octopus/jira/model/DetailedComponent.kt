package org.octopusden.octopus.jira.model

import org.octopusden.octopus.components.registry.core.dto.BuildSystem
import org.octopusden.octopus.releng.dto.JiraComponentVersion

data class DetailedComponent(
    val id: String,
    val name: String?,
    val componentOwner: String,
    val buildSystem: BuildSystem,
    val vcsSettings: VCSSettings,
    val jiraComponentVersion: JiraComponentVersion,
    val detailedComponentVersion: DetailedComponentVersion
)
