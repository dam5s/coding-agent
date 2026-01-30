package org.example

import tools.jackson.module.kotlin.readValue
import java.io.File

class DeleteFile(val root: File) : FileCommand {

    override fun execute(input: String): Any {
        val argument = objectMapper.readValue<PathArgument>(input)
        val file = File(root, argument.path)
        file.delete()

        return mapOf("result" to "success")
    }
}
