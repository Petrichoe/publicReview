package com.hmdp.service;

import com.hmdp.assistant.BlogRAGAgent;
import com.hmdp.assistant.DianpingAgent;
import com.hmdp.assistant.RouterAgent;
import com.hmdp.assistant.router.AgentType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class UnifiedAiService {

    @Resource
    private RouterAgent routerAgent;
    @Resource
    private DianpingAgent dianpingAgent;
    @Resource
    private BlogRAGAgent blogRAGAgent;

    public String chat(Long memoryId, String userMessage) {
        AgentType agentType = routerAgent.route(userMessage);

        switch (agentType) {
            case RAG_AGENT:
                System.out.println("路由决策：RAG_AGENT ");
                return blogRAGAgent.answer(memoryId, userMessage);
            case TOOL_AGENT:
            default:
                // 直接调用RAGService，不再通过中间层
                System.out.println("路由决策：TOOL_AGENT(或默认)");
                return dianpingAgent.chat(memoryId, userMessage);
        }
    }
}