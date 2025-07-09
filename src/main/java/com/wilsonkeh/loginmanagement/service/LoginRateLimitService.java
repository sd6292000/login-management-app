package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;

/**
 * 基于Resilience4j的登录频率限制服务
 */
public interface LoginRateLimitService {
    
    /**
     * 检查登录请求是否被允许
     * @param request 登录记录请求
     * @return 检查结果
     */
    RateLimitResult checkLoginRateLimit(LoginRecordRequest request);
    
    /**
     * 记录登录尝试
     * @param request 登录记录请求
     */
    void recordLoginAttempt(LoginRecordRequest request);
    
    /**
     * 获取IP地址的登录统计信息
     * @param ipAddress IP地址
     * @return 统计信息
     */
    IpLoginStats getIpLoginStats(String ipAddress);
    
    /**
     * 频率限制检查结果
     */
    record RateLimitResult(
        boolean allowed,           // 是否允许登录
        String reason,             // 拒绝原因
        long delayMs,              // 延迟时间（毫秒）
        int remainingAttempts,     // 剩余尝试次数
        long resetTimeMs           // 重置时间（毫秒）
    ) {}
    
    /**
     * IP登录统计信息
     */
    record IpLoginStats(
        String ipAddress,          // IP地址
        int totalAttempts,         // 总尝试次数
        int successfulLogins,      // 成功登录次数
        int failedLogins,          // 失败登录次数
        long lastLoginTime,        // 最后登录时间
        long firstLoginTime,       // 首次登录时间
        double averageIntervalMs,  // 平均登录间隔（毫秒）
        boolean isBlocked,         // 是否被阻止
        long blockUntilTime        // 阻止直到时间
    ) {}
} 