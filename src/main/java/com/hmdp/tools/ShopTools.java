package com.hmdp.tools;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ShopTools {

    @Resource
    private IShopService shopService;

    @Tool(name = "queryShopById", value = "根据ID查询商铺的详细信息")
    public String queryShopById(@P("商铺的ID") Long id) {
        Result result = shopService.queryById(id);
        if (result.getSuccess()) {
            return "查询成功，商铺信息如下：" + result.getData().toString();
        }
        return "查询失败：" + result.getErrorMsg();
    }

    @Tool(name = "queryShopByType", value = "根据商铺类型分页查询商铺信息")
    public String queryShopByType(
            @P("商铺类型的ID") Integer typeId,
            @P("查询的页码") Integer current) {
        // 实际项目中可以调用 IShopService 的分页查询方法
        // 这里为了简化，我们直接返回一个提示
        return "正在为您查询类型ID为 " + typeId + " 的第 " + current + " 页商铺信息...";
    }
}