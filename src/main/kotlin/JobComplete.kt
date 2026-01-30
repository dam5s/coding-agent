package org.example

import com.openai.models.ChatCompletionTool
import com.openai.models.FunctionDefinition

val jobCompleteTool = ChatCompletionTool.builder()
    .type(ChatCompletionTool.Type.FUNCTION)
    .function(
        FunctionDefinition.builder()
            .name("job_complete")
            .description("Invoke this once you are done with your job")
            .build()
    )
    .build()