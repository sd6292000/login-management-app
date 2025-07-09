package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resilience4j频率限制性能测试
 */
@SpringBootTest
@ActiveProfiles("test")
class Resilience4jRateLimitPerformanceTest {

    @Autowired
    private LoginRateLimitService loginRateLimitService;

    @Test
    void testRateLimitPerformance() throws InterruptedException {
        // 测试参数
        int threadCount = 10;
        int requestsPerThread = 100;
        int totalRequests = threadCount * requestsPerThread;
        
        System.out.println("=== Resilience4j频率限制性能测试 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程请求数: " + requestsPerThread);
        System.out.println("总请求数: " + totalRequests);

        // 创建测试数据
        List<LoginRecordRequest> testRequests = createTestRequests(totalRequests);

        // 执行并发测试
        long startTime = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        AtomicLong totalDelayTime = new AtomicLong(0);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    int requestIndex = threadId * requestsPerThread + j;
                    LoginRecordRequest request = testRequests.get(requestIndex);
                    
                    try {
                        LoginRateLimitService.RateLimitResult result = 
                            loginRateLimitService.checkLoginRateLimit(request);
                        
                        if (result.allowed()) {
                            allowedCount.incrementAndGet();
                            if (result.delayMs() > 0) {
                                totalDelayTime.addAndGet(result.delayMs());
                                // 模拟延迟
                                Thread.sleep(result.delayMs());
                            }
                            loginRateLimitService.recordLoginAttempt(request);
                        } else {
                            blockedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("请求处理失败: " + e.getMessage());
                    }
                }
            }, executor);
            
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // 输出结果
        System.out.println("\n=== 测试结果 ===");
        System.out.println("总执行时间: " + totalTime + "ms");
        System.out.println("允许的请求数: " + allowedCount.get());
        System.out.println("被阻止的请求数: " + blockedCount.get());
        System.out.println("总延迟时间: " + totalDelayTime.get() + "ms");
        System.out.println("平均响应时间: " + String.format("%.2f", (double) totalTime / totalRequests) + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", (double) totalRequests / (totalTime / 1000.0)) + " requests/sec");
        
        // 验证结果
        assert allowedCount.get() > 0 : "应该有请求被允许";
        assert blockedCount.get() > 0 : "应该有请求被阻止";
        assert totalTime > 0 : "执行时间应该大于0";
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void testRateLimitAccuracy() throws InterruptedException {
        System.out.println("\n=== 频率限制准确性测试 ===");
        
        String testIp = "192.168.1.100";
        int maxAttempts = 15; // 超过配置的10次/分钟限制
        
        List<LoginRecordRequest> requests = createRequestsForIp(testIp, maxAttempts);
        
        int allowedCount = 0;
        int blockedCount = 0;
        
        for (LoginRecordRequest request : requests) {
            LoginRateLimitService.RateLimitResult result = 
                loginRateLimitService.checkLoginRateLimit(request);
            
            if (result.allowed()) {
                allowedCount++;
                loginRateLimitService.recordLoginAttempt(request);
                System.out.println("请求 " + allowedCount + ": 允许");
            } else {
                blockedCount++;
                System.out.println("请求 " + (allowedCount + blockedCount) + ": 阻止 - " + result.reason());
            }
        }
        
        System.out.println("\n=== 准确性测试结果 ===");
        System.out.println("允许的请求数: " + allowedCount);
        System.out.println("被阻止的请求数: " + blockedCount);
        System.out.println("总请求数: " + maxAttempts);
        
        // 验证频率限制准确性
        assert allowedCount <= 10 : "每分钟最多应该允许10次请求";
        assert blockedCount >= 5 : "应该阻止超过限制的请求";
    }

    @Test
    void testRateLimitReset() throws InterruptedException {
        System.out.println("\n=== 频率限制重置测试 ===");
        
        String testIp = "192.168.1.200";
        
        // 第一次测试：发送10个请求
        List<LoginRecordRequest> requests1 = createRequestsForIp(testIp, 10);
        int allowed1 = 0;
        
        for (LoginRecordRequest request : requests1) {
            LoginRateLimitService.RateLimitResult result = 
                loginRateLimitService.checkLoginRateLimit(request);
            if (result.allowed()) {
                allowed1++;
                loginRateLimitService.recordLoginAttempt(request);
            }
        }
        
        System.out.println("第一轮允许的请求数: " + allowed1);
        
        // 等待1分钟让限制重置
        System.out.println("等待1分钟让频率限制重置...");
        Thread.sleep(61000); // 等待61秒确保重置
        
        // 第二次测试：再次发送10个请求
        List<LoginRecordRequest> requests2 = createRequestsForIp(testIp, 10);
        int allowed2 = 0;
        
        for (LoginRecordRequest request : requests2) {
            LoginRateLimitService.RateLimitResult result = 
                loginRateLimitService.checkLoginRateLimit(request);
            if (result.allowed()) {
                allowed2++;
                loginRateLimitService.recordLoginAttempt(request);
            }
        }
        
        System.out.println("第二轮允许的请求数: " + allowed2);
        
        // 验证重置功能
        assert allowed1 == 10 : "第一轮应该允许10个请求";
        assert allowed2 == 10 : "第二轮应该允许10个请求";
        
        System.out.println("频率限制重置测试通过");
    }

    private List<LoginRecordRequest> createTestRequests(int count) {
        List<LoginRecordRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "test-uid-" + i,
                "test-user-" + i + "@example.com",
                "192.168.1." + (i % 255),
                LocalDateTime.now(),
                "PASSWORD",
                "STRONG",
                "Test User Agent " + i,
                "trace-id-" + i,
                "fingerprint-" + i,
                "session-" + i,
                "DESKTOP",
                "Chrome",
                "Windows",
                "CN",
                "Beijing"
            );
            requests.add(request);
        }
        return requests;
    }

    private List<LoginRecordRequest> createRequestsForIp(String ipAddress, int count) {
        List<LoginRecordRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "test-uid-" + i,
                "test-user-" + i + "@example.com",
                ipAddress,
                LocalDateTime.now(),
                "PASSWORD",
                "STRONG",
                "Test User Agent " + i,
                "trace-id-" + i,
                "fingerprint-" + i,
                "session-" + i,
                "DESKTOP",
                "Chrome",
                "Windows",
                "CN",
                "Beijing"
            );
            requests.add(request);
        }
        return requests;
    }
} 