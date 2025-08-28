package com.hmdp.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode = EXPLICIT,
        //streamingChatModel = "openAiStreamingChatModel", // 使用流式模型
        chatModel = "openAiChatModel", // 使用非流式模型
        chatMemoryProvider = "chatMemoryProviderDianping", // 指定聊天记忆提供者
        tools = {"shopTools", "blogTools"} // 指定可以使用的工具
)
public interface DianpingAgent {

    @SystemMessage(fromResource = "dianping-prompt-template.txt")
    String chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}