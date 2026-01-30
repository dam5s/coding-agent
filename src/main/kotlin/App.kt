package org.example

import com.openai.client.OpenAIClient
import com.openai.models.*

class App(
    val userPrompt: String,
    val systemPrompt: String,
    val client: OpenAIClient,
    val fileCommands: Map<String, FileCommand>,
    val tools: List<ChatCompletionTool>
) {

    private fun createCompletion(params: ChatCompletionCreateParams): ChatCompletion {
        val startTime = System.currentTimeMillis()
        val chatCompletion = client.chat().completions().create(params)
        val endTime = System.currentTimeMillis()
        println("Completion took ${endTime - startTime}ms")
        return chatCompletion
    }

    fun run() {
        val messages = mutableListOf(
            ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                ChatCompletionSystemMessageParam.builder().role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                    .content(ChatCompletionSystemMessageParam.Content.ofTextContent(systemPrompt)).build()
            ), ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                ChatCompletionUserMessageParam.builder().role(ChatCompletionUserMessageParam.Role.USER)
                    .content(ChatCompletionUserMessageParam.Content.ofTextContent(userPrompt)).build()
            )
        )


        var params =
            ChatCompletionCreateParams.builder().model(ChatModel.GPT_4O).messages(messages).tools(tools).build()

        var jobDone = false
        var chatCompletion = createCompletion(params)

        var choice = chatCompletion.choices().first()
        var message = choice.message()

        while (!jobDone) {
            println("Looking at message to decide what to do next")
            val executingToolCall = message.toolCalls().isPresent && message.toolCalls().get().isNotEmpty()

            if (executingToolCall) {
                jobDone = executeToolCallAndUpdateMessages(message, messages)

                params =
                    ChatCompletionCreateParams.builder().model(ChatModel.GPT_4O).messages(messages).tools(tools).build()

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
                            .content(message.content().orElse("")).build()
                    )
                )
                messages.add(
                    ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                        ChatCompletionSystemMessageParam.builder().role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                            .content(ChatCompletionSystemMessageParam.Content.ofTextContent("Invoke the necessary tool for your next step."))
                            .build()
                    ),
                )

                println("Settings params with tools")
                params =
                    ChatCompletionCreateParams.builder().model(ChatModel.GPT_4O).messages(messages).tools(tools).build()

                chatCompletion = createCompletion(params)

                print("API Call done")
                choice = chatCompletion.choices().first()
                message = choice.message()
            }
        }
    }

    private fun executeToolCallAndUpdateMessages(
        message: ChatCompletionMessage,
        messages: MutableList<ChatCompletionMessageParam>,
    ): Boolean {
        val toolCalls = message.toolCalls().get()
        println("AI wants to call tools: ${toolCalls.joinToString { it.function().name() }}")

        // Add the assistant's message with tool calls to the conversation
        messages.add(
            ChatCompletionMessageParam.ofChatCompletionAssistantMessageParam(
                ChatCompletionAssistantMessageParam.builder().role(ChatCompletionAssistantMessageParam.Role.ASSISTANT)
                    .toolCalls(toolCalls).build()
            )
        )

        for (toolCall in toolCalls) {
            val toolName = toolCall.function().name()


            val command = fileCommands[toolName]
                ?: continue

            val result = command.execute(toolCall.function().arguments())
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

            if (toolName == "job_complete") {
                return true
            }
        }

        return false
    }

    private fun ChatCompletionMessage.printResult() {
        val result = content()

        if (result.isPresent) {
            println(result.get())
        } else {
            println("Error: No response from OpenAI")
        }
    }
}
