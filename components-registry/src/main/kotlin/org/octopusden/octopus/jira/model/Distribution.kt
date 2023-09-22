package org.octopusden.octopus.jira.model

data class Distribution(
        val explicit: Boolean,
        val external: Boolean,
        val GAV: String = ""
)
