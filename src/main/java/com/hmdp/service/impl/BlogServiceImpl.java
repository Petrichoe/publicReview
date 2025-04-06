package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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

        String key="blog:liked"+blog.getId();
        Boolean isMember=stringRedisTemplate.opsForSet().isMember(key,user.getId().toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));

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

        String key="blog:liked"+id;
        Boolean isMember=stringRedisTemplate.opsForSet().isMember(key,userId.toString());

        //判断是否已点赞
        if (BooleanUtil.isFalse(isMember)){
            //update 表 set liked=liked+1 where id =#{id}
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else{
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }
}
