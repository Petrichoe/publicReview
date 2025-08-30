package com.hmdp.tools;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
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

    /**
     * 改造后的通用推荐工具
     * @param category 商铺类别，例如 "餐厅", "火锅", "KTV"
     * @param sortBy 排序方式，例如 "评分", "价格", "销量"
     * @param limit 推荐数量
     * @return 推荐结果
     */
    @Tool(name = "recommendShops", value = "根据用户的偏好（如类别、排序方式）推荐商铺")
    public String recommendShops(
            @P("商铺的类别，例如 '美食', 'KTV' 等") String category,
            @P("排序的依据，可以是 '评分'(例如用户说'分数高'、'口碑好'), '价格'(例如'便宜'), '销量'") String sortBy,
            @P("需要推荐的数量") int limit
    ) {
        Result result = shopService.recommendShops(category, sortBy, limit);
        if (result.getSuccess()) {
            return "根据您的要求，找到了这些超棒的店铺，快看看吧！✨ \n" + result.getData().toString();
        }
        return "抱歉，暂时没有找到完全符合您要求的店铺呢。";
    }
}