package com.hmdp.assistant;

import com.hmdp.assistant.router.AgentType;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode = EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface RouterAgent {

    @SystemMessage("""
        你是一个智能路由，你的任务是分析用户的问题，并判断应该由哪个专业代理来处理。
        专业代理有两种：
        1. 'TOOL_AGENT': 当用户的问题是明确的指令时，比如“查询”、“推荐评分高的”、“按价格排序”等，这些通常可以由函数或工具直接完成。
        2. 'RAG_AGENT': 当用户的问题是开放式的、需要依赖背景知识来回答时，比如“哪个餐厅适合情侣约会”、“有没有安静的咖啡馆推荐”、“探店笔记里有没有提到过XX菜”等。

        你必须只返回 'TOOL_AGENT' 或 'RAG_AGENT' 这两个字符串之一。
        """)
    AgentType route(@UserMessage String userMessage);
}