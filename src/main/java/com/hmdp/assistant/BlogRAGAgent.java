package com.hmdp.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        // 核心改动：在这里指定ContentRetriever的Bean名称
        contentRetriever = "contentRetrieverPincone",
        chatMemoryProvider = "chatMemoryProviderDianping",
        wiringMode = EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface BlogRAGAgent {

    @SystemMessage("""
        你是一个名叫“点评小二”的智能助手。
        你的回答必须严格基于用户问题中提供的上下文信息（探店笔记）。
        如果上下文信息不足以回答，请明确告知用户“根据现有的探店笔记，我暂时无法回答您的问题”。
        绝对不允许自行编造或使用你的通用知识。
        """)
    String answer(@MemoryId Long memoryId, @UserMessage String userMessage);
}
