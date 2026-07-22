package com.seckill.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", e.getMessage());
        return result;
    }
}
