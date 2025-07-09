package com.wilsonkeh.loginmanagement.service.impl;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.service.LoginRateLimitService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Resilience4j的登录频率限制服务实现
 */
@Slf4j
@Service
public class LoginRateLimitServiceImpl implements LoginRateLimitService {

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.max-attempts-per-minute:10}")
    private int maxAttemptsPerMinute;

    @Value("${app.rate-limit.delay-threshold:5}")
    private int delayThreshold;

    @Value("${app.rate-limit.delay-duration-ms:1000}")
    private long delayDurationMs;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    // 存储IP地址的登录统计信息
    private final Map<String, IpLoginStats> ipStatsMap = new ConcurrentHashMap<>();

    @Override
    @Cacheable(value = "rate-limit-check", key = "#request.ipAddress")
    public RateLimitResult checkLoginRateLimit(LoginRecordRequest request) {
        if (!rateLimitEnabled) {
            return new RateLimitResult(true, "频率限制已禁用", 0, Integer.MAX_VALUE, 0);
        }

        String ipAddress = request.ipAddress();
        
        try {
            // 获取或创建该IP的RateLimiter
            RateLimiter rateLimiter = getOrCreateRateLimiter(ipAddress);
            
            // 尝试获取权限
            boolean permitted = rateLimiter.acquirePermission();
            
            if (permitted) {
                // 检查是否需要延迟
                long delayMs = calculateDelay(ipAddress);
                
                int remainingAttempts = (int) rateLimiter.getMetrics().getAvailablePermissions();
                long resetTimeMs = System.currentTimeMillis() + 
                    rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod().toMillis();
                
                return new RateLimitResult(
                    true, 
                    "登录请求被允许", 
                    delayMs, 
                    remainingAttempts, 
                    resetTimeMs
                );
            } else {
                // 被限流
                long waitTime = rateLimiter.getMetrics().getNanosToWait();
                return new RateLimitResult(
                    false, 
                    "登录频率过高，请稍后再试", 
                    0, 
                    0, 
                    System.currentTimeMillis() + (waitTime / 1_000_000)
                );
            }
            
        } catch (RequestNotPermitted e) {
            log.warn("IP地址 {} 触发频率限制", ipAddress);
            return new RateLimitResult(
                false, 
                "登录频率过高，IP地址已被限制", 
                0, 
                0, 
                System.currentTimeMillis() + 60000 // 1分钟后重置
            );
        }
    }

    @Override
    @CacheEvict(value = "rate-limit-check", key = "#request.ipAddress")
    public void recordLoginAttempt(LoginRecordRequest request) {
        if (!rateLimitEnabled) {
            return;
        }

        String ipAddress = request.ipAddress();
        long currentTime = System.currentTimeMillis();
        
        // 更新统计信息
        IpLoginStats stats = ipStatsMap.computeIfAbsent(ipAddress, 
            k -> new IpLoginStats(ipAddress, 0, 0, 0, 0, 0, 0.0, false, 0));
        
        // 这里可以扩展记录成功/失败登录
        // 目前简化处理，只记录总尝试次数
        log.debug("记录IP地址 {} 的登录尝试", ipAddress);
    }

    @Override
    @Cacheable(value = "ip-login-stats", key = "#ipAddress")
    public IpLoginStats getIpLoginStats(String ipAddress) {
        IpLoginStats stats = ipStatsMap.get(ipAddress);
        if (stats == null) {
            return new IpLoginStats(
                ipAddress, 0, 0, 0, 0, 0, 0.0, false, 0
            );
        }

        // 获取RateLimiter的实时信息
        try {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("login-" + ipAddress);
            boolean isBlocked = rateLimiter.getMetrics().getAvailablePermissions() <= 0;
            long blockUntilTime = isBlocked ? 
                System.currentTimeMillis() + rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod().toMillis() : 0;
            
            return new IpLoginStats(
                ipAddress,
                stats.totalAttempts(),
                stats.successfulLogins(),
                stats.failedLogins(),
                stats.lastLoginTime(),
                stats.firstLoginTime(),
                stats.averageIntervalMs(),
                isBlocked,
                blockUntilTime
            );
        } catch (Exception e) {
            return stats;
        }
    }

    /**
     * 获取或创建IP地址的RateLimiter
     */
    private RateLimiter getOrCreateRateLimiter(String ipAddress) {
        String rateLimiterName = "login-" + ipAddress;
        
        try {
            return rateLimiterRegistry.rateLimiter(rateLimiterName);
        } catch (Exception e) {
            // 如果不存在，创建新的RateLimiter
            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(maxAttemptsPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO) // 立即拒绝，不等待
                .build();
            
            return rateLimiterRegistry.rateLimiter(rateLimiterName, config);
        }
    }

    /**
     * 计算延迟时间
     */
    private long calculateDelay(String ipAddress) {
        try {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("login-" + ipAddress);
            int availablePermissions = (int) rateLimiter.getMetrics().getAvailablePermissions();
            
            if (availablePermissions <= (maxAttemptsPerMinute - delayThreshold)) {
                return delayDurationMs;
            }
        } catch (Exception e) {
            log.debug("计算延迟时发生错误: {}", e.getMessage());
        }
        
        return 0;
    }
} 