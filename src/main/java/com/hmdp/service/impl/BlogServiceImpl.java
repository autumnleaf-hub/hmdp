package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    @Resource
    IFollowService followService;

    @Override
    public Result queryBlogById(Long blogId) {
        // 根据id查询
        Blog one = lambdaQuery().eq(Blog::getId, blogId).one();
        if (one == null) {
            return Result.fail("博文不存在");
        }
        // 在查询到的博文中设置当前用户是否点赞过
        queryBlogUser(one);
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

    /**
     * 存储博文，并同时将博文推送给用户的粉丝。
     * 后者通过 redis 的 zset 实现 feed 流。
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 保存博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("博文保存失败");
        }

        // 查询
        followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId())
                .list()
                .forEach(follow -> {
                    // 获取粉丝的id
                    Long followerId = follow.getUserId();
                    // 将博文推送给粉丝
                    String feedKey = RedisConstants.FEED_KEY + followerId;
                    // 使用 zset 存储，score 为当前时间戳
                    stringRedisTemplate.opsForZSet().add(feedKey, blog.getId().toString(), System.currentTimeMillis());
                });

        return Result.ok(blog.getId());
    }

    /**
     * 查询关注用户的博文
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String feedKey = RedisConstants.FEED_KEY + userId;
        // 获取当前用户的关注列表 ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]
        // WITHSCORES 表示同时返回成员和分数
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(feedKey, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析数据
        // offset 的设置是为了处理同一时间戳的多条博文
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2.获取分数(时间戳）
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        // 如果 minTime 和 max 相等
        os = minTime == max ? os : os + offset;

        // 5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.查询blog有关的用户
            queryBlogUser(blog);
            // 5.2.查询blog是否被点赞
            isBlogLiked(blog);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        // 根据blog中的userId查询用户信息
        Long userId = blog.getUserId();
        if (userId != null) {
            // 查询用户信息
            User user = userService.getById(userId);
            if (user != null) {
                // 设置用户相关信息到blog对象中
                blog.setIcon(user.getIcon());
                blog.setName(user.getNickName());
            }
        }
    }

}
