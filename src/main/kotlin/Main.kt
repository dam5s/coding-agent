package org.example

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.*
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File
import kotlin.system.exitProcess

data class PathArgument(val path: String)

data class DirectoryItem(val name: String, val isDirectory: Boolean)

data class DirectoryListing(val items: List<DirectoryItem>)

data class FileContents(val contents: String)

fun listFiles(root: File, pathArgument: PathArgument): DirectoryListing {
    val directory = File(root, pathArgument.path)
    val items = directory.listFiles()?.map {
        DirectoryItem(it.name, it.isDirectory)
    } ?: emptyList()
    return DirectoryListing(items)
}

fun readFile(root: File, pathArgument: PathArgument): FileContents {
    val file = File(root, pathArgument.path)
    return FileContents(file.readText())
}

fun writeFile(root: File, pathArgument: PathArgument, contents: FileContents) {
    root.mkdirs()
    val file = File(root, pathArgument.path)
    file.parentFile?.mkdirs()
    file.writeText(contents.contents)
}

val objectMapper = jacksonObjectMapper()

inline fun <reified T> parse(json: String): T = objectMapper.readValue<T>(json)

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

    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey == null) {
        println("Error: OPENAI_API_KEY environment variable not set")
        exitProcess(1)
    }

    val client: OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    fun createCompletion(params: ChatCompletionCreateParams): ChatCompletion {
        val startTime = System.currentTimeMillis()
        val chatCompletion = client.chat().completions().create(params)
        val endTime = System.currentTimeMillis()
        println("Completion took ${endTime - startTime}ms")
        return chatCompletion
    }

    val systemPrompt = """
        You are a helpful assistant that can perform various file system operations.
        
        # File Copy
        
        You can do copies by reading the contents of a file and writing them to another file.
        
        # Completion
        
        Once you are done with your job, invoke the "job_complete" tool.
    """.trimIndent()

    // Define a simple tool
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

    val writeFileTool = ChatCompletionTool.builder()
        .type(ChatCompletionTool.Type.FUNCTION)
        .function(
            FunctionDefinition.builder()
                .name("write_file")
                .description("Write contents into a file with the contents and path passed as arguments, creates the file if it doesn't exist and any needed parent directories.")
                .parameters(
                    FunctionParameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(mapOf(
                            "path" to mapOf(
                                "type" to "string",
                                "description" to "Relative path to the file we are writing"
                            ),
                            "contents" to mapOf(
                                "type" to "string",
                                "description" to "Contents to write into the file"
                            )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(listOf("path", "contents")))
                        .build()
                )
                .build()
        )
        .build()

    val jobCompleteTool = ChatCompletionTool.builder()
        .type(ChatCompletionTool.Type.FUNCTION)
        .function(
            FunctionDefinition.builder()
                .name("job_complete")
                .description("Invoke this once you are done with your job")
                .build()
        )
        .build()

    val messages = mutableListOf(
        ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
            ChatCompletionSystemMessageParam.builder()
                .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                .content(ChatCompletionSystemMessageParam.Content.ofTextContent(systemPrompt))
                .build()
        ),
        ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
            ChatCompletionUserMessageParam.builder()
                .role(ChatCompletionUserMessageParam.Role.USER)
                .content(ChatCompletionUserMessageParam.Content.ofTextContent(userPrompt))
                .build()
        ),
    )

    val tools = listOf(listFilesTool, readFileTool, writeFileTool, jobCompleteTool)

    var params = ChatCompletionCreateParams.builder()
        .model(ChatModel.GPT_4O)
        .messages(messages)
        .tools(tools)
        .build()

    try {
        var jobDone = false
        var chatCompletion = createCompletion(params)

        var choice = chatCompletion.choices().first()
        var message = choice.message()

        while (!jobDone) {
            println("Looking at message to decide what to do next")
            val executingToolCall = message.toolCalls().isPresent && message.toolCalls().get().isNotEmpty()

            if (executingToolCall) {
                jobDone = executeToolCallAndUpdateMessages(projectRoot, message, messages)

                params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O)
                    .messages(messages)
                    .tools(tools)
                    .build()

                chatCompletion = createCompletion(params)
                choice = chatCompletion.choices().first()
                message = choice.message()
            } else {
                message.printResult()

                println("Adding prompt to invoke tool")
                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionAssistantMessageParam(
                        ChatCompletionAssistantMessageParam.builder()
                            .role(ChatCompletionAssistantMessageParam.Role.ASSISTANT)
                            .content(message.content().orElse(""))
                            .build()
                    )
                )
                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                        ChatCompletionSystemMessageParam.builder()
                            .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                            .content(ChatCompletionSystemMessageParam.Content.ofTextContent("Invoke the necessary tool for your next step."))
                            .build()
                    ),
                )

                println("Settings params with tools")
                params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O)
                    .messages(messages)
                    .tools(tools)
                    .build()

                chatCompletion = createCompletion(params)

                print("API Call done")
                choice = chatCompletion.choices().first()
                message = choice.message()
            }
        }

    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun ChatCompletionMessage.printResult() {
    val result = content()

    if (result.isPresent) {
        println(result.get())
    } else {
        println("Error: No response from OpenAI")
    }
}

private fun executeToolCallAndUpdateMessages(
    root: File,
    message: ChatCompletionMessage,
    messages: MutableList<ChatCompletionMessageParam>,
): Boolean {
    val toolCalls = message.toolCalls().get()
    println("AI wants to call tools: ${toolCalls.joinToString { it.function().name() }}")

    // Add the assistant's message with tool calls to the conversation
    messages.add(
        ChatCompletionMessageParam.ofChatCompletionAssistantMessageParam(
            ChatCompletionAssistantMessageParam.builder()
                .role(ChatCompletionAssistantMessageParam.Role.ASSISTANT)
                .toolCalls(toolCalls)
                .build()
        )
    )

    for (toolCall in toolCalls) {
        when (toolCall.function().name()) {
            "job_complete" -> {
                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
                        ChatCompletionToolMessageParam.builder()
                            .role(ChatCompletionToolMessageParam.Role.TOOL)
                            .toolCallId(toolCall.id())
                            .content(ChatCompletionToolMessageParam.Content.ofTextContent("Job complete"))
                            .build()
                    )
                )
                return true
            }

            "list_files" -> {
                val pathArgument = parse<PathArgument>(toolCall.function().arguments())
                val result = listFiles(root, pathArgument)
                val resultJson = objectMapper.writeValueAsString(result)

                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
                        ChatCompletionToolMessageParam.builder()
                            .role(ChatCompletionToolMessageParam.Role.TOOL)
                            .toolCallId(toolCall.id())
                            .content(ChatCompletionToolMessageParam.Content.ofTextContent(resultJson))
                            .build()
                    )
                )
            }

            "read_file" -> {
                val pathArgument = parse<PathArgument>(toolCall.function().arguments())
                val result = readFile(root, pathArgument)
                val resultJson = objectMapper.writeValueAsString(result)

                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
                        ChatCompletionToolMessageParam.builder()
                            .role(ChatCompletionToolMessageParam.Role.TOOL)
                            .toolCallId(toolCall.id())
                            .content(ChatCompletionToolMessageParam.Content.ofTextContent(resultJson))
                            .build()
                    )
                )
            }

            "write_file" -> {
                val pathArgument = parse<PathArgument>(toolCall.function().arguments())
                val contentsArgument = parse<FileContents>(toolCall.function().arguments())
                writeFile(root, pathArgument, contentsArgument)

                val resultJson = objectMapper.writeValueAsString(mapOf("result" to "success"))

                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
                        ChatCompletionToolMessageParam.builder()
                            .role(ChatCompletionToolMessageParam.Role.TOOL)
                            .toolCallId(toolCall.id())
                            .content(ChatCompletionToolMessageParam.Content.ofTextContent(resultJson))
                            .build()
                    )
                )
            }
        }
    }

    return false
}
