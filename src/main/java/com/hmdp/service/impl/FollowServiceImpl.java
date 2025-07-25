package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    /**
     * 关注或取关目标用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String followRedisKey = RedisConstants.FOLLOW_USER_KEY + userId;
        if (isFollow) {
            // 关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean success = save(follow);
            if (!success) {
                return Result.fail("关注失败");
            } else {
                stringRedisTemplate.opsForSet().add(followRedisKey, followUserId.toString());
            }
        } else {
            // 取关
            // MyBatis Plus版本兼容性问题导致需要添加 getWrapper
            boolean success = remove(lambdaQuery()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
                    .getWrapper());
            if (!success) {
                return Result.fail("取关失败");
            } else {
                stringRedisTemplate.opsForSet().remove(followRedisKey, followUserId.toString());
            }
        }
        return Result.ok("操作成功");
    }

    /**
     * 判断是否已关注某个用户
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long count = lambdaQuery().eq(Follow::getUserId, UserHolder.getUser().getId())
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        return Result.ok(count > 0);
    }

    /**
     * 获取当前用户和目标用户的共同关注用户
     * @param targetUserId
     * @return
     */
    @Override
    public Result commonFollow(Long targetUserId) {
        Long curUserId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOW_USER_KEY + curUserId;
        String key2 = RedisConstants.FOLLOW_USER_KEY + targetUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }
}
