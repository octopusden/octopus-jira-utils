package org.octopusden.octopus.jira.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class VcsRootLastChangeDate @JsonCreator constructor(
    @JsonProperty("root") @JsonAlias("first") val root: VersionControlSystemRoot,
    @JsonProperty("date") @JsonAlias("second") val date: Date?
)
