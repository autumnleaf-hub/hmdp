package com.hmdp.utils;

import io.jsonwebtoken.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT工具类
 */
public class JwtUtil {
    // 下列信息，在使用 springboot 时，可以将他们配置到 application.yml 中，动态注入
    // HTTP 请求中 Jwt 令牌所在的头字段
    public static final String JWT_HEADER_FIELD = "Authorization";

    // 默认有效期，单位毫秒
    // public static final Long JWT_TTL = 24 * 60 * 60 * 1000L;  // 一天
    public static final Long JWT_TTL = -1L;  // 无限制

    // 设置签名加密算法的秘钥明文
    private static final String JWT_KEY = "asdkljflkasdjfa1234";

    // 默认签发者
    public static final String JWT_ISSUER = "leaf";

    // 签名算法
    public static final String SIGN_ALGORITHM = "HS256";



    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 生成jwt
     *
     * @param subject token中要存放的数据（可以是json格式字符串）
     * @return
     */
    public static String createJWT(String subject) {
        JwtBuilder builder = getJwtBuilder(getUUID(), subject, null, null);
        return builder.compact();
    }

    /**
     * 生成jwt
     *
     * @param subject   token中要存放的数据（可以是json格式字符串）
     * @param ttlMillis token超时时长。单位毫秒。这个值为负时表示永不过期，为 null 时表示使用默认过期时间。
     * @return
     */
    public static String createJWT(String subject, Long ttlMillis) {
        JwtBuilder builder = getJwtBuilder(getUUID(), subject, ttlMillis, null); // 设置过期时间
        return builder.compact();
    }

    /**
     * 创建jwt
     *
     * @param subject   token中要存放的数据（可以是json格式字符串）
     * @param ttlMillis token超时时长。单位毫秒。这个值为负时表示永不过期，为 null 时表示使用默认过期时间。
     * @param claims    jwt中存放的额外信息
     * @return
     */
    public static String createJWT(String subject, Long ttlMillis, Map<String, Object> claims) {
        JwtBuilder builder = getJwtBuilder(getUUID(), subject, ttlMillis, claims); // 设置过期时间
        return builder.compact();
    }


    private static JwtBuilder getJwtBuilder(String uuid, String subject, Long ttlMillis, Map<String, Object> claims) {
        SignatureAlgorithm signatureAlgorithm = null;
        switch (SIGN_ALGORITHM) {
            case "RS256": signatureAlgorithm = SignatureAlgorithm.RS256; break;
            default: signatureAlgorithm = SignatureAlgorithm.HS256; break;
        }
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        JwtBuilder builder = Jwts.builder()
                .setId(uuid)              //唯一的ID
                .setSubject(subject)   // 主题  可以是JSON数据
                .setIssuer(JWT_ISSUER)     // 签发者
                .setIssuedAt(now)      // 签发时间
                .signWith(signatureAlgorithm, secretKey); //使用HS256对称加密算法签名, 第二个参数为秘钥
        if (ttlMillis == null) {
            ttlMillis = JwtUtil.JWT_TTL;
        }
        if (claims != null) {
            builder.setClaims(claims);
        }
        if (ttlMillis < 0) return builder;  // 如果ttlMillis小于0，则表示设置一个无限大的过期时间

        long expMillis = nowMillis + ttlMillis;
        Date expDate = new Date(expMillis);
        return builder.setExpiration(expDate);
    }

    /**
     * 生成加密后的秘钥 secretKey
     *
     * @return
     */
    public static SecretKey generalKey() {
        // 直接使用字符串字节生成密钥
        byte[] keyBytes = JwtUtil.JWT_KEY.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * 解析 jwt 令牌
     *
     * @param jwt
     * @return
     * @throws IllegalArgumentException
     */
    public static Claims parseJWT(String jwt) throws Exception{
        try {
            SecretKey secretKey = generalKey();
            return Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(jwt)
                    .getBody();
        } catch (JwtException e) {
            // 可以根据 e.getClass() 判断是签名错误、过期等
            throw new IllegalArgumentException("JWT 解析失败: " + e.getMessage());
        }
    }

    /**
     * 获取 jwt 中的 subject
     *
     * @param jwt
     * @return
     */
    public static String getSubject(String jwt) throws Exception{
        Claims claims = parseJWT(jwt);
        return claims.getSubject();
    }


    /**
     * 验证 JWT token 是否有效
     *
     * @param jwt 待验证的 JWT 字符串
     * @return true 表示有效，false 表示无效
     */
    public static boolean isValid(String jwt) {
        if (jwt == null || jwt.trim().isEmpty()) {
            return false;
        }

        try {
            // 解析 JWT，如果签名不匹配或格式错误会抛异常
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(generalKey())
                    .parseClaimsJws(jwt);

            // 获取过期时间
            Claims claims = jws.getBody();
            Date expiration = claims.getExpiration();

            // 判断是否已过期
            return expiration == null || !new Date().after(expiration);
        } catch (JwtException e) {
            // 捕获各种 JWT 异常，如签名失败、非法格式等
            System.out.println("JWT 解析失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("JWT 验证出错: " + e.getMessage());
            return false;
        }
    }


}