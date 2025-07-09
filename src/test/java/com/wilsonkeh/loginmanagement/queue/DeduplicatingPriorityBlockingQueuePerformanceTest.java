package com.wilsonkeh.loginmanagement.queue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Queue Performance Test
 * Compare performance differences of different implementations
 */
class DeduplicatingPriorityBlockingQueuePerformanceTest {

    @Test
    void testConcurrentOfferPerformance() throws InterruptedException {
        // Test parameters
        int threadCount = 8;
        int tasksPerThread = 100_000;
        int totalTasks = threadCount * tasksPerThread;

        // Create queue
        DeduplicatingPriorityBlockingQueue<String> queue = new DeduplicatingPriorityBlockingQueue<>(1000000);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long start = System.currentTimeMillis();

        // Concurrently offer tasks
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < tasksPerThread; j++) {
                    queue.offer("task-" + threadId + "-" + j);
                }
                latch.countDown();
            });
        }

        latch.await();
        long end = System.currentTimeMillis();
        long duration = end - start;

        // Get statistics
        DeduplicatingPriorityBlockingQueue.QueueStats stats = queue.getStats();

        System.out.println("=== Concurrent Offer Performance Test ===");
        System.out.println("Thread count: " + threadCount);
        System.out.println("Tasks per thread: " + tasksPerThread);
        System.out.println("Total tasks: " + totalTasks);
        System.out.println("Total time: " + duration + "ms");
        System.out.println("Average throughput: " + (totalTasks * 1000.0 / duration) + " tasks/sec");
        System.out.println("Queue size: " + stats.getCurrentSize());
        System.out.println("Deduplication map size: " + stats.getDeduplicationMapSize());
        System.out.println("Total offered: " + stats.getTotalOffered());
        System.out.println("Total deduplicated: " + stats.getTotalDeduplicated());
        System.out.println("Total polled: " + stats.getTotalPolled());

        // Verify performance requirements
        assert stats.getCurrentSize() == totalTasks;
        assert stats.getTotalOffered() == totalTasks;
        assert stats.getTotalDeduplicated() == 0;
    }
} 