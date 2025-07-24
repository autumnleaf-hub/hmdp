package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    @Override
    public Result queryBlogById(Long blogId) {
        // 根据id查询
        Blog one = lambdaQuery().eq(Blog::getId, blogId).one();
        if (one == null) {
            return Result.fail("博文不存在");
        }
        Long userId = UserHolder.getUser().getId();
        String blogLikeRedisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(blogLikeRedisKey, userId.toString());
        one.setIsLike(isMember != null && isMember);
        return Result.ok(one);
    }

    @Override
    public Result likeBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String blogLikeRedisKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(blogLikeRedisKey, userId.toString());
        if (isMember != null && isMember) {
            // 已经点赞，取消点赞
            stringRedisTemplate.opsForSet().remove(blogLikeRedisKey, userId.toString());
            // 减少点赞数量
            lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, blogId).update();
            return Result.ok("取消点赞成功");
        } else {
            // 未点赞，进行点赞
            // 增加点赞数量
            boolean success = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, blogId).update();
            if (success) {
                stringRedisTemplate.opsForSet().add(blogLikeRedisKey, userId.toString());
                return Result.ok("点赞成功");
            }
            return Result.fail("点赞失败");
        }
    }

    @Override
    public Result isBlogLiked(Long blogId) {
        Boolean hasLiked = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + blogId, UserHolder.getUser().getId().toString());
        if (hasLiked != null && hasLiked) {
            return Result.ok(true);
        } else {
            return Result.ok(false);
        }
    }

    @Override
    public Result getBlogLikes(Long blogId) {
        Blog one = lambdaQuery().eq(Blog::getId, blogId).one();
        if (one == null) {
            return Result.fail("博文不存在");
        }
        return Result.ok(one.getLiked());
    }

}
