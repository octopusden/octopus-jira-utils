package org.octopusden.octopus.jira.config

sealed class LoadResult<T> {
    class Success<T>(val result: T) : LoadResult<T>()
    class Failure<T>(val error: String) : LoadResult<T>()
}
