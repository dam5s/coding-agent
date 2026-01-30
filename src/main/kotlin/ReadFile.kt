package org.example

import com.openai.core.JsonValue
import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionMessageToolCall
import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import java.io.File

val readFileTool = ChatCompletionTool.builder()
    .type(ChatCompletionTool.Type.FUNCTION)
    .function(
        FunctionDefinition.builder()
            .name("read_file")
            .description("Get the contents of a file with the path passed as argument")
            .parameters(
                FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(mapOf(
                        "path" to mapOf(
                            "type" to "string",
                            "description" to "Relative path to the file we are reading"
                        )
                    )))
                    .putAdditionalProperty("required", JsonValue.from(listOf("path")))
                    .build()
            )
            .build()
    )
    .build()

fun readFile(root: File, pathArgument: PathArgument): FileContents {
    val file = File(root, pathArgument.path)
    return FileContents(file.readText())
}

class ReadFileCommand(val root: File) : FileCommand {
    override fun execute(toolCall: ChatCompletionMessageToolCall): ChatCompletionMessageParam {
        val pathArgument = parse<PathArgument>(toolCall.function().arguments())
        val result = readFile(root, pathArgument)
        val resultJson = objectMapper.writeValueAsString(result)
        return toolMessage(toolCall.id(), resultJson)
    }
}