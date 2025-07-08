package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class LoginRecordQueueServiceTest {

    @Autowired
    private LoginRecordQueueService queueService;

    @Test
    public void testEnqueueAndDeduplication() throws InterruptedException {
        // 创建相同的请求
        LoginRecordRequest request1 = new LoginRecordRequest(
            "test-user-1", "testuser", "192.168.1.100", 
            LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD, 
            "STRONG", "test-agent", "trace-001", "fingerprint-001"
        );

        LoginRecordRequest request2 = new LoginRecordRequest(
            "test-user-1", "testuser", "192.168.1.100", 
            LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD, 
            "STRONG", "test-agent", "trace-002", "fingerprint-001"
        );

        // 加入队列
        queueService.enqueueLoginRecord(request1);
        queueService.enqueueLoginRecord(request2);

        // 等待一段时间让队列处理
        Thread.sleep(1000);

        // 验证去重效果
        assertEquals(1, queueService.getDeduplicatedQueueSize());
    }

    @Test
    public void testConcurrentEnqueue() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 并发提交请求
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        LoginRecordRequest request = new LoginRecordRequest(
                            "user-" + threadId, "user" + threadId, "192.168.1." + threadId, 
                            LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD, 
                            "STRONG", "test-agent", "trace-" + threadId + "-" + j, "fingerprint-" + threadId
                        );
                        queueService.enqueueLoginRecord(request);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 验证队列状态
        assertTrue(queueService.getQueueSize() > 0);
        assertTrue(queueService.getDeduplicatedQueueSize() > 0);
    }

    @Test
    public void testQueuePerformance() {
        long startTime = System.currentTimeMillis();
        
        // 批量提交请求
        for (int i = 0; i < 1000; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "perf-user-" + i, "user" + i, "192.168.1." + (i % 255), 
                LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD, 
                "STRONG", "test-agent", "trace-perf-" + i, "fingerprint-" + i
            );
            queueService.enqueueLoginRecord(request);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证性能（1000个请求应该在合理时间内完成）
        assertTrue(duration < 1000, "队列处理1000个请求应该少于1秒，实际耗时: " + duration + "ms");
        assertEquals(1000, queueService.getDeduplicatedQueueSize());
    }
} 