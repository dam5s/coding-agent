package org.example

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionUserMessageParam
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

    val params = ChatCompletionCreateParams.builder()
        .model(ChatModel.GPT_4O)
        .addMessage(ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
            ChatCompletionUserMessageParam.builder()
                .role(ChatCompletionUserMessageParam.Role.USER)
                .content(ChatCompletionUserMessageParam.Content.ofTextContent(prompt))
                .build()
        ))
        .build()

    try {
        val chatCompletion = client.chat().completions().create(params)
        val result = chatCompletion.choices().firstOrNull()?.message()?.content()
        if (result != null && result.isPresent) {
            println(result.get())
        } else {
            println("Error: No response from OpenAI")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}
