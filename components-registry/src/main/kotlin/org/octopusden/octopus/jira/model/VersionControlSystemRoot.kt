package org.octopusden.octopus.jira.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class VersionControlSystemRoot @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("repositoryType") val repositoryType: RepositoryType,
    @JsonProperty("vcsPath") val vcsPath: String,
    @JsonProperty("tag") val tag: String?,
    @JsonProperty("branch") @JsonAlias("rawBranch") val branch: String,
    @JsonProperty("hotfixBranch") val hotfixBranch: String?
)
