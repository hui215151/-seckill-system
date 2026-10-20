package com.seckill.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    
    private final SecretKey key;
    private final long expireSeconds;
    
    public JwtUtil(
            @Value("${seckill.jwt.secret:dev-seckill-jwt-secret-change-me-please-0123456789}") String secret,
            @Value("${seckill.jwt.expire-minutes:120}") long expireMinutes) {
        // HS256 要求密钥至少 256 bit（32 字节）
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireMinutes * 60;
    }
    
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 校验签名与过期时间，失败抛 io.jsonwebtoken.JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    public long getExpireSeconds() {
        return expireSeconds;
    }
}
