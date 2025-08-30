package com.hmdp.tools;

import com.hmdp.dto.Result;
import com.hmdp.service.IBlogService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class BlogTools {

    @Resource
    private IBlogService blogService;

    @Tool(name = "queryHotBlog", value = "查询热门探店笔记")
    public String queryHotBlog(@P("要查询的页码") Integer current) {
        Result result = blogService.queryHotBlog(current);
        if (result.getSuccess()) {
            return "查询成功，热门笔记如下：" + result.getData().toString();
        }
        return "查询热门笔记失败！";
    }


}