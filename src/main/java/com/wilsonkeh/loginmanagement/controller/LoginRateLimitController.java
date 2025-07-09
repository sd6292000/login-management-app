package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.service.LoginRateLimitService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 登录频率限制控制器
 * 使用Resilience4j注解实现频率限制
 */
@Slf4j
@RestController
@RequestMapping("/api/login-rate-limit")
public class LoginRateLimitController {

    @Autowired
    private LoginRateLimitService loginRateLimitService;

    /**
     * 检查登录频率限制（使用注解方式）
     */
    @RateLimiter(name = "login-default", fallbackMethod = "checkRateLimitFallback")
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<LoginRateLimitService.RateLimitResult>> checkRateLimit(
            @Valid @RequestBody LoginRecordRequest request) {
        
        LoginRateLimitService.RateLimitResult result = loginRateLimitService.checkLoginRateLimit(request);
        
        if (result.allowed()) {
            // 如果允许但有延迟，应用延迟
            if (result.delayMs() > 0) {
                try {
                    Thread.sleep(result.delayMs());
                    log.debug("应用登录延迟: {}ms", result.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("登录延迟被中断");
                }
            }
            
            // 记录登录尝试
            loginRateLimitService.recordLoginAttempt(request);
            
            return ResponseEntity.ok(ApiResponse.success("频率检查通过", result));
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(result.reason()));
        }
    }

    /**
     * 频率限制检查的回退方法
     */
    public ResponseEntity<ApiResponse<LoginRateLimitService.RateLimitResult>> checkRateLimitFallback(
            LoginRecordRequest request, RequestNotPermitted ex) {
        
        log.warn("IP地址 {} 触发频率限制: {}", request.ipAddress(), ex.getMessage());
        
        LoginRateLimitService.RateLimitResult result = new LoginRateLimitService.RateLimitResult(
            false, 
            "登录频率过高，请稍后再试", 
            0, 
            0, 
            System.currentTimeMillis() + 60000
        );
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(result.reason()));
    }

    /**
     * 获取IP地址的登录统计信息
     */
    @GetMapping("/stats/{ipAddress}")
    public ResponseEntity<ApiResponse<LoginRateLimitService.IpLoginStats>> getIpStats(
            @PathVariable String ipAddress) {
        
        LoginRateLimitService.IpLoginStats stats = loginRateLimitService.getIpLoginStats(ipAddress);
        return ResponseEntity.ok(ApiResponse.success("获取IP统计信息成功", stats));
    }

    /**
     * 手动检查登录频率限制（编程式API）
     */
    @PostMapping("/check-manual")
    public ResponseEntity<ApiResponse<LoginRateLimitService.RateLimitResult>> checkRateLimitManual(
            @Valid @RequestBody LoginRecordRequest request) {
        
        LoginRateLimitService.RateLimitResult result = loginRateLimitService.checkLoginRateLimit(request);
        
        if (result.allowed()) {
            // 应用延迟
            if (result.delayMs() > 0) {
                try {
                    Thread.sleep(result.delayMs());
                    log.debug("应用登录延迟: {}ms", result.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("登录延迟被中断");
                }
            }
            
            // 记录登录尝试
            loginRateLimitService.recordLoginAttempt(request);
            
            return ResponseEntity.ok(ApiResponse.success("频率检查通过", result));
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(result.reason()));
        }
    }
} 