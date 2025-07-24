package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    @Override
    public Result queryBlogById(Long blogId) {
        // 根据id查询
        Blog one = lambdaQuery().eq(Blog::getId, blogId).one();
        if (one == null) {
            return Result.fail("博文不存在");
        }
        // 在查询到的博文中设置当前用户是否点赞过
        isBlogLiked(one);
        return Result.ok(one);
    }

    @Override
    public Result likeBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String blogLikeRedisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        Double isMember = stringRedisTemplate.opsForZSet().score(blogLikeRedisKey, userId.toString());
        if (isMember != null) {
            // 已经点赞，取消点赞
            stringRedisTemplate.opsForZSet().remove(blogLikeRedisKey, userId.toString());
            // 减少点赞数量
            lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, blogId).update();
            return Result.ok("取消点赞成功");
        } else {
            // 未点赞，进行点赞
            // 增加点赞数量
            boolean success = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, blogId).update();
            if (success) {
                // 改为通过 sortedSet 存储点赞信息。 zadd key value score
                stringRedisTemplate.opsForZSet().add(blogLikeRedisKey, userId.toString(), System.currentTimeMillis());
                return Result.ok("点赞成功");
            }
            return Result.fail("点赞失败");
        }
    }

    /**
     * 判断当前用户是否点赞了该博文
     * @param blog 博文
     */
    public void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }

    /**
     * 按照顺序获取点赞前五的用户信息
     * @param blogId
     * @return
     */
    @Override
    public Result getBlogLikes(Long blogId) {
        String blogLikeRedisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        // 获取点赞的前5个用户
        // TODO 值得注意的是，range 方法返回的是一个 LikedHashSet<String>，因此能够保证有序性
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(blogLikeRedisKey, 0, 5);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).toList();
        String idStr = String.join(",", top5);
        List<UserDTO> userDTOS = userService.lambdaQuery()
                .in(User::getId, ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(userDTOS);
    }

}
