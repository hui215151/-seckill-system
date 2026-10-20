package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seckill.dto.LoginResponse;
import com.seckill.entity.SysUser;
import com.seckill.mapper.SysUserMapper;
import com.seckill.security.AuthException;
import com.seckill.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Autowired
    private SysUserMapper sysUserMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    public LoginResponse login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AuthException("用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username.trim()));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new AuthException("用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername(), jwtUtil.getExpireSeconds());
    }
}
