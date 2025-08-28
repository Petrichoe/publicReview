package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog=getById(id);
        if (blog==null){
            return Result.fail("笔记不存在");
        }
        //查询用户
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 用于前端判断点赞是否亮起
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setIsLike(false); // 未登录用户默认未点赞
            return;
        }

        String key=RedisConstants.BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score!=null);

    }

    private void queryBlogUser(Blog blog) {
        Long userId=blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞逻辑实现
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();

        String key=RedisConstants.BLOG_LIKED_KEY +id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        //判断是否已点赞
        if (score==null){
            //update 表 set liked=liked+1 where id =#{id}
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key= RedisConstants.BLOG_LIKED_KEY +id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5==null|| top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //从字符串类型转换为Long类型，并收集到列表中
        List<Long> ids = top5.stream().
                map(Long::valueOf).
                collect(Collectors.toList());

        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(userDTOS);
    }

    /**
     * 滚动分页查询逻辑实现
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key= RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples.isEmpty() || typedTuples ==null){
            return Result.ok();
        }
        //获取博客id,获取滚动分页查询中必要的 offset与minTime
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;  //最小时间
        int os=1;  //偏移量
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //获取分数(时间戳)
            long time = tuple.getScore().longValue();
            if (time==minTime){
                os++;
            }else {
                minTime =time;
                os=1;
            }
        }

        //根据id查询blog  select * from blog where id in(1001,1002,1003) order by FIELD(id,1001,1002,1003)
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setMinTime(minTime);
        r.setOffset(os);

        return Result.ok(r);
    }
}
