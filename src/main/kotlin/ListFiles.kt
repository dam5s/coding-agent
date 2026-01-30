package org.example

import com.openai.core.JsonValue
import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionMessageToolCall
import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import java.io.File

val listFilesTool = ChatCompletionTool.builder()
    .type(ChatCompletionTool.Type.FUNCTION)
    .function(
        FunctionDefinition.builder()
            .name("list_files")
            .description("Get the list of files in the directory passed as argument")
            .parameters(
                FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(mapOf(
                        "path" to mapOf(
                            "type" to "string",
                            "description" to "Relative path to the directory we are listing"
                        )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(listOf("path")))
                    .build()
            )
            .build()
    )
    .build()

fun listFiles(root: File, pathArgument: PathArgument): DirectoryListing {
    val directory = File(root, pathArgument.path)
    val items = directory.listFiles()?.map {
        DirectoryItem(it.name, it.isDirectory)
    } ?: emptyList()
    return DirectoryListing(items)
}

class ListFilesCommand(val root: File) : FileCommand {
    override fun execute(toolCall: ChatCompletionMessageToolCall): ChatCompletionMessageParam {
        val pathArgument = parse<PathArgument>(toolCall.function().arguments())
        val result = listFiles(root, pathArgument)
        val resultJson = objectMapper.writeValueAsString(result)
        return toolMessage(toolCall.id(), resultJson)
    }
}