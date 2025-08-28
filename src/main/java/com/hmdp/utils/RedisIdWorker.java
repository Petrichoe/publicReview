package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //开始时间戳
    private  static  final  long BEGIN_TIMESTAMP=16409955200L;
    //序列号的位数
    private static final int COUNT_BITS=32;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now=LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp= nowSecond-BEGIN_TIMESTAMP;

        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接并返回
        return timestamp <<COUNT_BITS |  count;



    }


}
