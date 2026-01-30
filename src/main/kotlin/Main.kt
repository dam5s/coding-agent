package org.example

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.File
import kotlin.system.exitProcess

data class Message(val role: String, val content: String)
data class ChatRequest(val model: String, val messages: List<Message>)
data class Choice(val message: Message)
data class ChatResponse(val choices: List<Choice>)

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

    val client = OkHttpClient()
    val gson = Gson()

    val chatRequest = ChatRequest(
        model = "gpt-5",
        messages = listOf(Message(role = "user", content = prompt))
    )

    val requestBody = gson.toJson(chatRequest).toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Error: OpenAI API call failed with code ${response.code}")
                println(response.body?.string())
                exitProcess(1)
            }

            val responseBody = response.body?.string()
            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)

            val result = chatResponse.choices.firstOrNull()?.message?.content
            if (result != null) {
                println(result)
            } else {
                println("Error: No response from OpenAI")
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}
