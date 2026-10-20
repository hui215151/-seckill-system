package com.seckill.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response, "未登录，请先调用 /api/auth/login 获取 token");
            return false;
        }
        
        try {
            Claims claims = jwtUtil.parse(header.substring(BEARER_PREFIX.length()));
            Object userId = claims.get("userId");
            if (!(userId instanceof Number)) {
                reject(response, "token 缺少 userId");
                return false;
            }
            request.setAttribute(ATTR_USER_ID, ((Number) userId).longValue());
            request.setAttribute(ATTR_USERNAME, claims.getSubject());
            return true;
        } catch (Exception e) {
            reject(response, "token 无效或已过期");
            return false;
        }
    }
    
    private void reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
