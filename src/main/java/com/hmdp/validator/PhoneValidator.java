package com.hmdp.validator;

import com.hmdp.validator.interfaces.ValidPhone;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Valid;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-08 17:00
 */

public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null) return true; // 让 @NotNull 处理空值
        return phone.matches("^1[3-9]\\d{9}$");
    }
}
