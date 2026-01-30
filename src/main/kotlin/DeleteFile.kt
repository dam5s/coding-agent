package org.example

import com.openai.core.JsonValue
import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import tools.jackson.module.kotlin.readValue
import java.io.File

val deleteFileTool = ChatCompletionTool.builder()
    .type(ChatCompletionTool.Type.FUNCTION)
    .function(
        FunctionDefinition.builder()
            .name("delete_file")
            .description("Delete a file or directory")
            .parameters(FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(mapOf(
                    "path" to mapOf(
                        "type" to "string",
                        "description" to "Relative path to the directory or file to delete"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(listOf("path")))
                .build())
            .build()
    )
    .build()

class DeleteFileCommand(val root: File) : FileCommand {

    override fun execute(input: String): Any {
        val argument = objectMapper.readValue<PathArgument>(input)
        val file = File(root, argument.path)
        file.deleteRecursively()

        return mapOf("result" to "success")
    }
}
