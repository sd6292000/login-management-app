package com.wilsonkeh.loginmanagement.queue;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式队列集成测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.queue.distributed.enabled=true",
    "app.queue.distributed.processor.enabled=false"
})
public class DistributedQueueIntegrationTest {

    private HazelcastInstance hazelcastInstance;
    private DistributedDeduplicatingPriorityBlockingQueue<LoginRecordRequest> distributedQueue;

    @BeforeEach
    void setUp() {
        // 创建Hazelcast实例
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        
        // 创建分布式队列
        distributedQueue = new DistributedDeduplicatingPriorityBlockingQueue<>(
            hazelcastInstance, 1000, true, 
            task -> task.getData().uid() + "_" + task.getPriority()
        );
    }

    @AfterEach
    void tearDown() {
        if (distributedQueue != null) {
            distributedQueue.clear();
        }
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void testOfferAndPollTask() throws InterruptedException {
        // 创建测试任务
        LoginRecordRequest request = new LoginRecordRequest(
            UUID.randomUUID().toString(), "test@example.com", "192.168.1.1", "Chrome"
        );
        LoginRecordTask task = new LoginRecordTask(request, 1);

        // 添加任务到队列
        boolean offered = distributedQueue.offer(task);
        assertTrue(offered, "任务应该成功添加到队列");

        // 从队列获取任务
        Task<LoginRecordRequest> polledTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(polledTask, "应该能够从队列中获取任务");
        assertEquals(request.uid(), polledTask.getData().uid(), "任务数据应该匹配");
    }

    @Test
    void testDeduplication() {
        // 创建相同的任务
        LoginRecordRequest request = new LoginRecordRequest(
            "test-uid", "test@example.com", "192.168.1.1", "Chrome"
        );
        LoginRecordTask task1 = new LoginRecordTask(request, 1);
        LoginRecordTask task2 = new LoginRecordTask(request, 1);

        // 添加第一个任务
        boolean offered1 = distributedQueue.offer(task1);
        assertTrue(offered1, "第一个任务应该成功添加");

        // 尝试添加相同的任务（应该被去重）
        boolean offered2 = distributedQueue.offer(task2);
        assertFalse(offered2, "重复任务应该被拒绝");

        // 验证队列大小
        assertEquals(1, distributedQueue.size(), "队列应该只包含一个任务");
    }

    @Test
    void testQueueSize() {
        // 添加多个任务
        for (int i = 0; i < 5; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "uid-" + i, "test" + i + "@example.com", "192.168.1.1", "Chrome"
            );
            LoginRecordTask task = new LoginRecordTask(request, i);
            distributedQueue.offer(task);
        }

        // 验证队列大小
        assertEquals(5, distributedQueue.size(), "队列大小应该为5");
        assertFalse(distributedQueue.isEmpty(), "队列不应该为空");
    }

    @Test
    void testQueueStats() {
        // 添加一些任务
        for (int i = 0; i < 3; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "uid-" + i, "test" + i + "@example.com", "192.168.1.1", "Chrome"
            );
            LoginRecordTask task = new LoginRecordTask(request, i);
            distributedQueue.offer(task);
        }

        // 获取统计信息
        DistributedDeduplicatingPriorityBlockingQueue.DistributedQueueStats stats = distributedQueue.getStats();
        
        assertNotNull(stats, "统计信息不应该为空");
        assertEquals(3, stats.getCurrentSize(), "当前队列大小应该为3");
        assertEquals(3, stats.getTotalOffered(), "总提交任务数应该为3");
        assertEquals(0, stats.getTotalPolled(), "总处理任务数应该为0");
    }

    @Test
    void testClearQueue() {
        // 添加一些任务
        for (int i = 0; i < 3; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "uid-" + i, "test" + i + "@example.com", "192.168.1.1", "Chrome"
            );
            LoginRecordTask task = new LoginRecordTask(request, i);
            distributedQueue.offer(task);
        }

        // 验证队列不为空
        assertFalse(distributedQueue.isEmpty(), "队列不应该为空");

        // 清空队列
        distributedQueue.clear();

        // 验证队列为空
        assertTrue(distributedQueue.isEmpty(), "队列应该为空");
        assertEquals(0, distributedQueue.size(), "队列大小应该为0");
    }

    @Test
    void testPriorityOrdering() throws InterruptedException {
        // 创建不同优先级的任务
        LoginRecordRequest request1 = new LoginRecordRequest("uid-1", "test1@example.com", "192.168.1.1", "Chrome");
        LoginRecordRequest request2 = new LoginRecordRequest("uid-2", "test2@example.com", "192.168.1.1", "Chrome");
        LoginRecordRequest request3 = new LoginRecordRequest("uid-3", "test3@example.com", "192.168.1.1", "Chrome");

        LoginRecordTask task1 = new LoginRecordTask(request1, 3); // 低优先级
        LoginRecordTask task2 = new LoginRecordTask(request2, 1); // 高优先级
        LoginRecordTask task3 = new LoginRecordTask(request3, 2); // 中优先级

        // 按非优先级顺序添加任务
        distributedQueue.offer(task1);
        distributedQueue.offer(task2);
        distributedQueue.offer(task3);

        // 验证按优先级顺序获取任务
        Task<LoginRecordRequest> firstTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        Task<LoginRecordRequest> secondTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        Task<LoginRecordRequest> thirdTask = distributedQueue.poll(5, TimeUnit.SECONDS);

        assertNotNull(firstTask, "第一个任务不应该为空");
        assertNotNull(secondTask, "第二个任务不应该为空");
        assertNotNull(thirdTask, "第三个任务不应该为空");

        // 验证优先级顺序（数字越小优先级越高）
        assertEquals(1, firstTask.getPriority(), "第一个任务应该是最高优先级");
        assertEquals(2, secondTask.getPriority(), "第二个任务应该是中等优先级");
        assertEquals(3, thirdTask.getPriority(), "第三个任务应该是最低优先级");
    }
} 