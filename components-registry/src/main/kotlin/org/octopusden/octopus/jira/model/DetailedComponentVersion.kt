package org.octopusden.octopus.jira.model

data class DetailedComponentVersion(
    val component: String,
    val lineVersion: ComponentRegistryVersion,
    val minorVersion: ComponentRegistryVersion,
    val buildVersion: ComponentRegistryVersion,
    val rcVersion: ComponentRegistryVersion,
    val releaseVersion: ComponentRegistryVersion
)
