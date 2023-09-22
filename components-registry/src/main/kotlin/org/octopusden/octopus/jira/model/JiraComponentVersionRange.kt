package org.octopusden.octopus.jira.model

import org.octopusden.octopus.releng.dto.JiraComponent

data class JiraComponentVersionRange(
    val componentName: String,
    val versionRange: String,
    val jiraComponent: JiraComponent,
    val distribution: Distribution,
    val vcsSettings: VCSSettings
)
