package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    /**
     * 处理用户关注或取关操作
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();

        String key="follows:"+userId;//用于共同关注的键
        //判断到底是关注还是取关
        if (isFollow){
            //关注，新增数据
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);


            //实现好友共同关注逻辑
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关  delete from tb_follow where user_id= ? and follow_user_id=?
            remove(new QueryWrapper<Follow>()
                    .eq("user_id",userId).eq("follow_user_id",followUserId));

            stringRedisTemplate.opsForSet().remove(key,followUserId.toString());//后面这个指明移除followUserId.toString()
        }
        return Result.ok();
    }

    /**
     * 查询返回到前端
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //select count(*) from tb_follow where user_id=? and follow_user_id = ?
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        return Result.ok(count>0);

    }

    /**
     * 获取当前用户与指定用户的共同关注列表。
     * @param id 指定用户的ID
     * @return 包含共同关注用户信息的Result对象，如果无共同关注则返回空列表
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        //求交集，用于返回key与key2中的交集
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect
                .stream()
                .map(Long::valueOf)// 将每个String元素转换为Long
                .collect(Collectors.toList());

        //查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))//将每个user对象转为UserDTO
                .collect(Collectors.toList());

//        等同写法
//        List<User> userList = userService.listByIds(ids);
//        List<UserDTO> userDTOList = new ArrayList<>();
//
//        for (User user : userList) {
//            UserDTO userDTO = new UserDTO();
//            // 手动拷贝每个属性
//            userDTO.setId(user.getId());
//            userDTO.setUsername(user.getUsername());
//            // ...其他属性
//            userDTOList.add(userDTO);
//        }

        return Result.ok(users);
    }
}
