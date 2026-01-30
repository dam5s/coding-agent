package org.example

import com.openai.core.JsonValue
import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import tools.jackson.module.kotlin.readValue
import java.io.File

val writeFileTool = ChatCompletionTool.builder()
    .type(ChatCompletionTool.Type.FUNCTION)
    .function(
        FunctionDefinition.builder()
            .name("write_file")
            .description("Write contents into a file with the contents and path passed as arguments, creates the file if it doesn't exist and any needed parent directories.")
            .parameters(
                FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty(
                        "properties", JsonValue.from(
                            mapOf(
                                "path" to mapOf(
                                    "type" to "string",
                                    "description" to "Relative path to the file we are writing"
                                ),
                                "contents" to mapOf(
                                    "type" to "string",
                                    "description" to "Contents to write into the file"
                                )
                            )
                        )
                    )
                    .putAdditionalProperty("required", JsonValue.from(listOf("path", "contents")))
                    .build()
            )
            .build()
    )
    .build()

class WriteFileCommand(val root: File) : FileCommand {

    override fun execute(input: String): Any {
        val contentsArgument = objectMapper.readValue<FileContents>(input)
        val pathArgument = objectMapper.readValue<PathArgument>(input)

        root.mkdirs()

        val file = File(root, pathArgument.path)
        file.parentFile?.mkdirs()
        file.writeText(contentsArgument.contents)

        return mapOf("result" to "success")
    }
}
