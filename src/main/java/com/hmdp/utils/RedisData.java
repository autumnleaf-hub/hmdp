package com.hmdp.utils;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hmdp.entity.Shop;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Shop.class, name = "shop"),
    })
    private Object data;
}
