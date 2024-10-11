package org.octopusden.octopus.jira.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Legacy DTO using to ExternalRegistryVcsSettings store
 */
data class VcsRootWrapper @JsonCreator constructor(
    @JsonProperty("root") @JsonAlias("first") val root: VersionControlSystemRoot
)
