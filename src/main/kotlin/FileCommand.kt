package org.example

import tools.jackson.module.kotlin.jacksonObjectMapper


interface FileCommand {
    fun execute(input: String): Any
}

data class PathArgument(val path: String)

data class DirectoryItem(val name: String, val isDirectory: Boolean)

data class DirectoryListing(val items: List<DirectoryItem>)

data class FileContents(val contents: String)

val objectMapper = jacksonObjectMapper()
