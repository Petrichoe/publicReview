package com.hmdp.assistant.router;

public enum AgentType {
    TOOL_AGENT, // 工具调用Agent
    RAG_AGENT,  // RAG问答Agent
    UNKNOWN     // 无法判断
}