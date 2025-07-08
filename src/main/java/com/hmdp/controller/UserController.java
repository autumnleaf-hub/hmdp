package com.hmdp.controller;


import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    RedisUtil redisUtil;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // generate code
        String code = RandomUtil.randomNumbers(4);
        // save code
        //session.setAttribute(CommonFields.VERIFICATION_CODE, code);
        redisUtil.set(RedisConstants.LOGIN_CODE_KEY + phone, code);
        redisUtil.expire(RedisConstants.LOGIN_CODE_KEY + phone, RedisConstants.LOGIN_CODE_TTL, TimeUnit.HOURS); //dev
        logger.info("For phone number: {}, the verification code is: {}", phone, code);
        // send code

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) throws JsonProcessingException {
        // TODO 实现登录功能
        // 查看其是否已登录
        if (UserHolder.getUser() != null) {
            return Result.ok();
        }

        if(loginForm.getPhone() == null || RegexUtils.isPhoneInvalid(loginForm.getPhone())) return Result.fail("手机号格式错误！");
        if(loginForm.getCode() == null && loginForm.getPassword() == null) return Result.fail("参数错误！");

        // 验证码登录
        if (loginForm.getCode() != null && !RegexUtils.isCodeInvalid(loginForm.getCode())) {
            String trueCode = redisUtil.get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
            if (trueCode == null) return Result.fail("验证码已过期");
            if (!trueCode.equals(loginForm.getCode())) return Result.fail("验证码错误！");

            User one = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, loginForm.getPhone()));
            if(one == null) {   // 用户不存在则创建
                one = new User();
                one.setPhone(loginForm.getPhone());
                one.setPassword(PasswordEncoder.encode(loginForm.getPassword()));
                userService.save(one);
            }

            // 记录已登录用户
            String tokenId = UUID.fastUUID().toString();
            redisUtil.setObject(RedisConstants.LOGIN_USER_KEY + tokenId, one, 1, TimeUnit.DAYS);

            // 验证成功，删除验证码
            redisUtil.delete(RedisConstants.LOGIN_CODE_KEY + loginForm);

            return Result.ok(JwtUtil.createJWT(tokenId));
        }

        if (loginForm.getPassword() != null) {
            // TODO 实现密码登录功能
        }

        logger.info("phone: {} successfully login.", loginForm.getPhone());
        return Result.ok();
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // TODO 实现登出功能
        //session.removeAttribute(CommonFields.LOGIN_USER);
        String tokenID = null;
        try {
            tokenID = JwtUtil.getSubject(request.getHeader(JwtUtil.JWT_HEADER_FIELD));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        if (tokenID == null) return Result.fail("用户未登录");
        redisUtil.delete(RedisConstants.LOGIN_USER_KEY + tokenID);
        logger.info("user_id: {} successfully logout.", UserHolder.getUser().getId());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        Object user = UserHolder.getUser();
        if (user == null) return Result.fail("用户未登录");
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
