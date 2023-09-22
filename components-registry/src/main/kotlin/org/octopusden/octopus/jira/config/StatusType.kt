package org.octopusden.octopus.jira.config

enum class StatusType(val message: String) {
    DIR_NOT_FOUND("Corresponding external registry dir not found"),
    IS_NOT_PREPARED("VCS Settings is not loaded to external registry version folder"),
    PARSE_ERROR("Failed to parse vcs settings"),
    OK("OK"),
    UNKNOWN_PRODUCT_FOR_VCS("Unknown product for loading from VCS"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    companion object {
        fun findByName(name: String) = StatusType.values().find { it.name == name } ?: INTERNAL_ERROR
    }
}
