package com.hmdp.utils;

import cn.hutool.jwt.JWT;
import org.springframework.util.StringUtils;

import java.util.Date;

public class JwtHelper {

    //token的有效时间
    private static final long tokenExpiration = 24 * 60 * 60 * 60 * 1000L;

    // 加密token
    public static String tokenSignKey = "xxzhp";

    // 根据id⽣成token
    public static String creatToken(Long id) {
        String token = JWT.create()
                // 将一个名为"id"的声明添加到JWT中，并将其值设置为变量id的值
                .setPayload("id", id)
                .setExpiresAt(new Date(System.currentTimeMillis() + tokenExpiration))
                // 设置JWT的签名密钥,并转为字节数组
                .setKey(tokenSignKey.getBytes())
                // 对JWT进行签名。它使用之前设置的声明、过期时间和密钥来生成JWT的签名部分
                .sign();

        return token;
    }

    //从token字符串获取id
    public static Long getId(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        return (Long) JWT.of(token).getPayload("id");
    }
}
