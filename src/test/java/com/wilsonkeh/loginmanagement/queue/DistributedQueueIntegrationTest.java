package com.wilsonkeh.loginmanagement.queue;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.queue.LoginRecordTask;
import com.wilsonkeh.loginmanagement.queue.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Distributed Queue Integration Test
 */
class DistributedQueueIntegrationTest {

    private HazelcastInstance hazelcastInstance;
    private DistributedDeduplicatingPriorityBlockingQueue<Task<LoginRecordRequest>> distributedQueue;

    @BeforeEach
    void setUp() {
        // Create Hazelcast instance
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        
        // Create distributed queue
        distributedQueue = new DistributedDeduplicatingPriorityBlockingQueue<>(
            "test-queue", hazelcastInstance, 1000);
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
    void testBasicQueueOperations() throws InterruptedException {
        // Create test task
        LoginRecordRequest request = new LoginRecordRequest(
            UUID.randomUUID().toString(), "test@example.com", 
            "192.168.1.1", LocalDateTime.now());
        Task<LoginRecordRequest> task = new LoginRecordTask(request);
        
        // Add task to queue
        boolean offered = distributedQueue.offer(task);
        assertTrue(offered, "Task should be successfully added to queue");
        
        // Get task from queue
        Task<LoginRecordRequest> polledTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(polledTask, "Should be able to get task from queue");
        assertEquals(request.uid(), polledTask.getData().uid(), "Task data should match");
    }

    @Test
    void testDeduplication() throws InterruptedException {
        // Create identical tasks
        LoginRecordRequest request = new LoginRecordRequest(
            "same-uid", "test@example.com", 
            "192.168.1.1", LocalDateTime.now());
        Task<LoginRecordRequest> task1 = new LoginRecordTask(request);
        Task<LoginRecordRequest> task2 = new LoginRecordTask(request);
        
        // Add first task
        boolean offered1 = distributedQueue.offer(task1);
        assertTrue(offered1, "First task should be successfully added");
        
        // Try to add identical task (should be deduplicated)
        boolean offered2 = distributedQueue.offer(task2);
        assertFalse(offered2, "Identical task should be deduplicated");
        
        // Verify only one task in queue
        Task<LoginRecordRequest> polledTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(polledTask, "Should get the first task");
        assertEquals("same-uid", polledTask.getData().uid(), "Should get the correct task");
        
        // Verify queue is empty
        Task<LoginRecordRequest> emptyTask = distributedQueue.poll(1, TimeUnit.SECONDS);
        assertNull(emptyTask, "Queue should be empty after deduplication");
    }

    @Test
    void testPriorityOrdering() throws InterruptedException {
        // Create tasks with different priorities
        LoginRecordRequest request1 = new LoginRecordRequest(
            "uid1", "test1@example.com", "192.168.1.1", LocalDateTime.now());
        LoginRecordRequest request2 = new LoginRecordRequest(
            "uid2", "test2@example.com", "192.168.1.2", LocalDateTime.now());
        
        Task<LoginRecordRequest> task1 = new LoginRecordTask(request1, 1); // Lower priority
        Task<LoginRecordRequest> task2 = new LoginRecordTask(request2, 10); // Higher priority
        
        // Add tasks in reverse priority order
        distributedQueue.offer(task1);
        distributedQueue.offer(task2);
        
        // Verify higher priority task is retrieved first
        Task<LoginRecordRequest> firstTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(firstTask, "Should get first task");
        assertEquals("uid2", firstTask.getData().uid(), "Higher priority task should be retrieved first");
        
        Task<LoginRecordRequest> secondTask = distributedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(secondTask, "Should get second task");
        assertEquals("uid1", secondTask.getData().uid(), "Lower priority task should be retrieved second");
    }

    @Test
    void testQueueCapacity() {
        // Test queue capacity limit
        for (int i = 0; i < 1000; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "uid-" + i, "test" + i + "@example.com", 
                "192.168.1." + (i % 255), LocalDateTime.now());
            Task<LoginRecordRequest> task = new LoginRecordTask(request);
            
            boolean offered = distributedQueue.offer(task);
            if (!offered) {
                assertEquals(1000, i, "Queue should be full at capacity limit");
                break;
            }
        }
        
        // Verify queue is full
        LoginRecordRequest extraRequest = new LoginRecordRequest(
            "extra-uid", "extra@example.com", "192.168.1.100", LocalDateTime.now());
        Task<LoginRecordRequest> extraTask = new LoginRecordTask(extraRequest);
        boolean offered = distributedQueue.offer(extraTask);
        assertFalse(offered, "Queue should reject additional tasks when full");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Test concurrent access to queue
        int threadCount = 10;
        int tasksPerThread = 100;
        
        Thread[] producers = new Thread[threadCount];
        Thread[] consumers = new Thread[threadCount];
        
        // Start producer threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            producers[i] = new Thread(() -> {
                for (int j = 0; j < tasksPerThread; j++) {
                    LoginRecordRequest request = new LoginRecordRequest(
                        "uid-" + threadId + "-" + j, 
                        "test" + threadId + "-" + j + "@example.com",
                        "192.168.1." + (threadId % 255), LocalDateTime.now());
                    Task<LoginRecordRequest> task = new LoginRecordTask(request);
                    distributedQueue.offer(task);
                }
            });
            producers[i].start();
        }
        
        // Start consumer threads
        for (int i = 0; i < threadCount; i++) {
            consumers[i] = new Thread(() -> {
                for (int j = 0; j < tasksPerThread; j++) {
                    try {
                        Task<LoginRecordRequest> task = distributedQueue.poll(5, TimeUnit.SECONDS);
                        assertNotNull(task, "Should be able to consume task");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            consumers[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread producer : producers) {
            producer.join();
        }
        for (Thread consumer : consumers) {
            consumer.join();
        }
        
        // Verify queue is empty
        assertTrue(distributedQueue.isEmpty(), "Queue should be empty after all tasks are consumed");
    }
} 