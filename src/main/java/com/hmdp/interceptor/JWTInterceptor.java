package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtil;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-05-11 17:21
 * 用于从请求头中获取JWT令牌，解析令牌，获取用户信息，并将用户信息存储到ThreadLocal中，
 */
@Component
@Slf4j
public class JWTInterceptor implements HandlerInterceptor {

    @Resource
    RedisUtil redisUtil;

    // 拦截所有请求，获取请求中的token，解析token，获取用户信息，将用户信息存储到ThreadLocal中，方便后续使用
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String jwt = request.getHeader(JwtUtil.JWT_HEADER_FIELD);
        if (!JwtUtil.isValid(jwt)) return true;
        String tokenId = null;
        try {
            tokenId = JwtUtil.getSubject(jwt);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        User user = null;
        try {
            user = redisUtil.getObject(RedisConstants.LOGIN_USER_KEY + tokenId, User.class);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        if (user != null) {
            UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
            redisUtil.expire(RedisConstants.LOGIN_USER_KEY + tokenId, 7, TimeUnit.DAYS);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
