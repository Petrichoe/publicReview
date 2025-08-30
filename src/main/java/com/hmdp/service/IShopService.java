package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    /**
     * 新增的通用推荐接口
     * @param category 商铺类别
     * @param sortBy 排序方式
     * @param limit 数量
     * @return 商铺列表
     */
    Result recommendShops(String category, String sortBy, int limit);
}
