package org.example

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: <program> <path_to_prompt_file> <path_to_project_root>")
        exitProcess(1)
    }

    val filePath = args[0]
    val file = File(filePath)

    if (!file.exists()) {
        println("Error: Prompt file not found at $filePath")
        exitProcess(1)
    }

    val projectRootPath = args[1]
    val projectRoot = File(projectRootPath)

    projectRoot.mkdirs()

    if (!projectRoot.exists() || !projectRoot.isDirectory) {
        println("Error: Project root should be a directory at $projectRootPath")
        exitProcess(1)
    }

    val userPrompt = file.readText().trim()
    if (userPrompt.isEmpty()) {
        println("Error: Prompt file is empty")
        exitProcess(1)
    }

    val systemPrompt = """
        You are a helpful assistant that can perform various file system operations.
        
        # File Copy
        
        You can do copies by reading the contents of a file and writing them to another file.
        
        # Completion
        
        Once you are done with your job, invoke the "job_complete" tool.
    """.trimIndent()

    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey == null) {
        println("Error: OPENAI_API_KEY environment variable not set")
        exitProcess(1)
    }

    val client: OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    val fileCommands = mapOf(
        "read_file" to ReadFileCommand(projectRoot),
        "list_files" to ListFilesCommand(projectRoot),
        "write_file" to WriteFileCommand(projectRoot),
        "delete_file" to DeleteFileCommand(projectRoot),
        "job_complete" to JobCompleteCommand(),
    )

    val tools = listOf(listFilesTool, readFileTool, writeFileTool, deleteFileTool, jobCompleteTool)
    val app = App(userPrompt, systemPrompt, client, fileCommands, tools)

    try {
        app.run()
    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}
