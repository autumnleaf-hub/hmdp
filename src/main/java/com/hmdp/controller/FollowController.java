package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@Validated @NotNull(message = "关注对象不可为空") @PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {

        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("common/{id}")
    public Result commonFollow(@PathVariable("id") Long targetUserId) {
        return followService.commonFollow(targetUserId);
    }
}
