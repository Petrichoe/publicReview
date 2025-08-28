package com.hmdp.controller;

import com.hmdp.assistant.DianpingAgent;
import com.hmdp.dto.ChatFormDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private DianpingAgent dianpingAgent;

    @PostMapping(value = "/chat")
    public String chat(@RequestBody ChatFormDTO chatForm) {
         return dianpingAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());

        //流式处理
        /*// 从 Agent 获取原始的“词块”流
        Flux<String> tokenStream = dianpingAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());

        // --- 核心处理逻辑 ---
        return tokenStream
                // 1. 将每个“词块” (token) 拆分成单个字符 (character) 的流
                .flatMap(token -> Flux.fromArray(token.split("")))
                // 2. (可选) 为每个字符之间增加一个微小的延迟，使打字效果更平滑
                .delayElements(Duration.ofMillis(30));*/
    }
}