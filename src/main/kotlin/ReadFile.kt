package org.example

import com.openai.core.JsonValue
import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import tools.jackson.module.kotlin.readValue
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

class ReadFileCommand(val root: File) : FileCommand {

    override fun execute(input: String): Any {
        val pathArgument = objectMapper.readValue<PathArgument>(input)
        val file = File(root, pathArgument.path)
        return FileContents(file.readText())
    }
}
