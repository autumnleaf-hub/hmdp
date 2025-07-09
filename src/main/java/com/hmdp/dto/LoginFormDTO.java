package com.hmdp.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginFormDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{4,6}$", message = "验证码格式不正确")
    private String code;

    private String password;

    // 自定义校验：code 和 password 至少有一个不为空
    @AssertTrue(message = "验证码和密码至少需要提供一个")
    public boolean isValidLoginForm() {
        return (code != null && !code.trim().isEmpty()) ||
                (password != null && !password.trim().isEmpty());
    }
}
