package org.octopusden.octopus.jira.model

enum class RepositoryType(val defaultBranch: String) {
    CVS("HEAD"), MERCURIAL("default"), GIT("master");
}
