package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {

        String key= RedisConstants.CACHE_TYPE_KEY;
        String shopListJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_TYPE_KEY);
        if (StrUtil.isNotBlank(shopListJson)){
            List<ShopType> shoplist = JSONUtil.toList(shopListJson, ShopType.class);//把从Redis中取出的JSON对象转为Java对象然后返回给客户端
            return Result.ok(shoplist);
        }
        //如果缓存中没查到:去数据库查
        List<ShopType> list = query().orderByAsc("sort").list();//SELECT * FROM shop_type ORDER BY sort ASC;
        if (list==null){
            return Result.fail("数据库不存在此数据");
        }
        String jsonStr = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
        return Result.ok(list);
    }


}
