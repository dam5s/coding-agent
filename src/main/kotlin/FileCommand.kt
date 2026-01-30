package org.example

import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionMessageToolCall
import com.openai.models.ChatCompletionToolMessageParam
import tools.jackson.module.kotlin.jacksonObjectMapper

interface FileCommand {
    fun execute(toolCall: ChatCompletionMessageToolCall): ChatCompletionMessageParam
}

fun toolMessage(toolCallId: String, resultJson: String) = ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
    ChatCompletionToolMessageParam.builder()
        .role(ChatCompletionToolMessageParam.Role.TOOL)
        .toolCallId(toolCallId)
        .content(ChatCompletionToolMessageParam.Content.ofTextContent(resultJson))
        .build()
)

data class PathArgument(val path: String)

data class DirectoryItem(val name: String, val isDirectory: Boolean)

data class DirectoryListing(val items: List<DirectoryItem>)

data class FileContents(val contents: String)

val objectMapper = jacksonObjectMapper()
