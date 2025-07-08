package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 队列性能测试
 * 对比不同实现方式的性能差异
 */
public class DeduplicatingPriorityBlockingQueuePerformanceTest {

    @Test
    public void testConcurrentEnqueuePerformance() throws InterruptedException {
        // 测试参数
        int threadCount = 20;
        int tasksPerThread = 1000;
        int totalTasks = threadCount * tasksPerThread;

        // 创建队列
        DeduplicatingPriorityBlockingQueue<LoginRecordRequest> queue =
                new DeduplicatingPriorityBlockingQueue<>(10000, true, Task::getDeduplicationKey);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // 并发提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < tasksPerThread; j++) {
                        LoginRecordRequest request = new LoginRecordRequest(
                                "user-" + threadId, "user" + threadId, "192.168.1." + (threadId % 255),
                                LocalDateTime.now(), "PASSWORD",
                                "STRONG", "test-agent", "trace-" + threadId + "-" + j, "fingerprint-" + threadId
                                , "", "", "", "", "", "");
                        LoginRecordTask task = new LoginRecordTask(request, j % 3);
                        queue.offer(task);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 获取统计信息
        DeduplicatingPriorityBlockingQueue.QueueStats stats = queue.getStats();

        System.out.println("=== 并发入队性能测试 ===");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程任务数: " + tasksPerThread);
        System.out.println("总任务数: " + totalTasks);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("平均吞吐量: " + (totalTasks * 1000.0 / duration) + " tasks/sec");
        System.out.println("队列大小: " + stats.getCurrentSize());
        System.out.println("去重映射大小: " + stats.getDeduplicationMapSize());
        System.out.println("总入队: " + stats.getTotalOffered());
        System.out.println("总去重: " + stats.getTotalDeduplicated());
        System.out.println("总出队: " + stats.getTotalPolled());

        // 验证性能要求
        assertTrue(duration < 10000, "并发入队应该在10秒内完成，实际耗时: " + duration + "ms");
        assertTrue(stats.getTotalOffered() > 0, "应该有任务被入队");
    }

    @Test
    public void testDeduplicationPerformance() throws InterruptedException {
        // 测试去重性能
        int uniqueTasks = 1000;
        int duplicateTasks = 5000;

        DeduplicatingPriorityBlockingQueue<LoginRecordRequest> queue =
                new DeduplicatingPriorityBlockingQueue<>(10000, true, Task::getDeduplicationKey);

        long startTime = System.currentTimeMillis();

        // 先添加唯一任务
        for (int i = 0; i < uniqueTasks; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                    "unique-user-" + i, "user" + i, "192.168.1." + (i % 255),
                    LocalDateTime.now(), "Password",
                    "STRONG", "test-agent", "trace-unique-" + i, "fingerprint-" + i
                    , "", "", "", "", "", "");
            LoginRecordTask task = new LoginRecordTask(request, 0);
            queue.offer(task);
        }

        // 再添加重复任务
        for (int i = 0; i < duplicateTasks; i++) {
            int userIndex = i % uniqueTasks; // 重复使用相同的用户ID
            LoginRecordRequest request = new LoginRecordRequest(
                    "unique-user-" + userIndex, "user" + userIndex, "192.168.1." + (userIndex % 255),
                    LocalDateTime.now(), "Password",
                    "STRONG", "test-agent", "trace-duplicate-" + i, "fingerprint-" + userIndex
                    , "", "", "", "", "", "");
            LoginRecordTask task = new LoginRecordTask(request, 0);
            queue.offer(task);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        DeduplicatingPriorityBlockingQueue.QueueStats stats = queue.getStats();

        System.out.println("=== 去重性能测试 ===");
        System.out.println("唯一任务数: " + uniqueTasks);
        System.out.println("重复任务数: " + duplicateTasks);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("平均吞吐量: " + ((uniqueTasks + duplicateTasks) * 1000.0 / duration) + " tasks/sec");
        System.out.println("队列大小: " + stats.getCurrentSize());
        System.out.println("去重映射大小: " + stats.getDeduplicationMapSize());
        System.out.println("总入队: " + stats.getTotalOffered());
        System.out.println("总去重: " + stats.getTotalDeduplicated());
        System.out.println("去重率: " + (stats.getTotalDeduplicated() * 100.0 / stats.getTotalOffered()) + "%");

        // 验证去重效果
        assertEquals(uniqueTasks, stats.getCurrentSize(), "队列中应该只有唯一任务");
        assertEquals(uniqueTasks, stats.getDeduplicationMapSize(), "去重映射大小应该等于唯一任务数");
        assertEquals(uniqueTasks + duplicateTasks, stats.getTotalOffered(), "总入队数应该等于唯一任务数加重复任务数");
        assertEquals(duplicateTasks, stats.getTotalDeduplicated(), "去重数应该等于重复任务数");
    }

    @Test
    public void testMemoryEfficiency() {
        // 测试内存效率
        int taskCount = 10000;

        DeduplicatingPriorityBlockingQueue<LoginRecordRequest> queue =
                new DeduplicatingPriorityBlockingQueue<>(taskCount, true, Task::getDeduplicationKey);

        // 记录初始内存使用
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // 添加任务
        for (int i = 0; i < taskCount; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                    "user-" + i, "user" + i, "192.168.1." + (i % 255),
                    LocalDateTime.now(), "Password",
                    "STRONG", "test-agent", "trace-" + i, "fingerprint-" + i
                    , "", "", "", "", "", "");
            LoginRecordTask task = new LoginRecordTask(request, i % 3);
            queue.offer(task);
        }

        // 记录最终内存使用
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;

        DeduplicatingPriorityBlockingQueue.QueueStats stats = queue.getStats();

        System.out.println("=== 内存效率测试 ===");
        System.out.println("任务数: " + taskCount);
        System.out.println("内存使用: " + memoryUsed + " bytes");
        System.out.println("平均每任务内存: " + (memoryUsed / (double) taskCount) + " bytes");
        System.out.println("队列大小: " + stats.getCurrentSize());
        System.out.println("去重映射大小: " + stats.getDeduplicationMapSize());

        // 验证内存使用合理
        assertTrue(memoryUsed > 0, "应该有内存使用");
        assertTrue(memoryUsed < taskCount * 1000, "内存使用应该在合理范围内"); // 每任务平均不超过1KB
    }
} 