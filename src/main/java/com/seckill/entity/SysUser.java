package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    
    /** BCrypt 加密后的密码 */
    private String password;
    
    private String nickname;
    
    /** 1-正常 0-禁用 */
    private Integer status;
    
    private LocalDateTime createTime;
}
