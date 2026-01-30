package org.example

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.*
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: <program> <path_to_prompt_file>")
        exitProcess(1)
    }

    val filePath = args[0]
    val file = File(filePath)

    if (!file.exists()) {
        println("Error: File not found at $filePath")
        exitProcess(1)
    }

    val prompt = file.readText().trim()
    if (prompt.isEmpty()) {
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

    // Define a simple tool
    val tool = ChatCompletionTool.builder()
        .type(ChatCompletionTool.Type.FUNCTION)
        .function(
            FunctionDefinition.builder()
                .name("get_weather")
                .description("Get the current weather in a given location")
                .parameters(
                    FunctionParameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(mapOf(
                            "location" to mapOf(
                                "type" to "string",
                                "description" to "The city and state, e.g. San Francisco, CA"
                            )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(listOf("location")))
                        .build()
                )
                .build()
        )
        .build()

    val messages = mutableListOf<ChatCompletionMessageParam>(
        ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
            ChatCompletionUserMessageParam.builder()
                .role(ChatCompletionUserMessageParam.Role.USER)
                .content(ChatCompletionUserMessageParam.Content.ofTextContent(prompt))
                .build()
        )
    )

    var params = ChatCompletionCreateParams.builder()
        .model(ChatModel.GPT_4O)
        .messages(messages)
        .tools(listOf(tool))
        .build()

    try {
        var chatCompletion = client.chat().completions().create(params)
        var choice = chatCompletion.choices().first()
        var message = choice.message()

        if (message.toolCalls().isPresent && message.toolCalls().get().isNotEmpty()) {
            val toolCalls = message.toolCalls().get()
            println("AI wants to call tools: ${toolCalls.joinToString { it.function().name() }}")

            // Add the assistant's message with tool calls to the conversation
            messages.add(ChatCompletionMessageParam.ofChatCompletionAssistantMessageParam(
                ChatCompletionAssistantMessageParam.builder()
                    .role(ChatCompletionAssistantMessageParam.Role.ASSISTANT)
                    .toolCalls(toolCalls)
                    .build()
            ))

            // Process each tool call
            for (toolCall in toolCalls) {
                if (toolCall.function().name() == "get_weather") {
                    val argsJson = toolCall.function().arguments()
                    println("Tool arguments: $argsJson")
                    
                    // Simulate tool execution
                    val toolResult = "{\"location\": \"San Francisco, CA\", \"temperature\": \"22\", \"unit\": \"celsius\", \"description\": \"Sunny\"}"
                    
                    // Add tool response to the conversation
                    messages.add(ChatCompletionMessageParam.ofChatCompletionToolMessageParam(
                        ChatCompletionToolMessageParam.builder()
                            .role(ChatCompletionToolMessageParam.Role.TOOL)
                            .toolCallId(toolCall.id())
                            .content(ChatCompletionToolMessageParam.Content.ofTextContent(toolResult))
                            .build()
                    ))
                }
            }

            // Get the final response from the model
            params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O)
                .messages(messages)
                .build()
            
            chatCompletion = client.chat().completions().create(params)
            choice = chatCompletion.choices().first()
            message = choice.message()
        }

        val result = message.content()
        if (result.isPresent) {
            println(result.get())
        } else {
            println("Error: No response from OpenAI")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}
