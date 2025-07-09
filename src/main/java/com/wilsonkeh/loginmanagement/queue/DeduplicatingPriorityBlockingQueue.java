package com.wilsonkeh.loginmanagement.queue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 支持去重的优先级阻塞队列
 * 使用 ConcurrentHashMap 进行高效的 O(1) 去重检查
 */
public class DeduplicatingPriorityBlockingQueue<T> {
    
    private final PriorityBlockingQueue<Task<T>> queue;
    private final ConcurrentHashMap<String, Boolean> deduplicationMap;
    private final Function<Task<T>, String> deduplicationKeyExtractor;
    private final boolean enableDeduplication;
    private final int maxSize;
    
    // 统计计数器
    private final AtomicInteger totalOffered = new AtomicInteger(0);
    private final AtomicInteger totalDeduplicated = new AtomicInteger(0);
    private final AtomicInteger totalPolled = new AtomicInteger(0);

    public DeduplicatingPriorityBlockingQueue(int maxSize, boolean enableDeduplication, 
                                            Function<Task<T>, String> deduplicationKeyExtractor) {
        // Use LinkedBlockingQueue instead of PriorityBlockingQueue since task ordering/prioritization is not required.
        // If you do not need to sort tasks by priority, LinkedBlockingQueue is more appropriate and efficient.
        // If you need FIFO (first-in, first-out) order, LinkedBlockingQueue guarantees this.
        // If you want to support prioritization in the future, you can switch back to PriorityBlockingQueue.
        this.queue = new java.util.concurrent.LinkedBlockingQueue<>(maxSize);
        this.deduplicationMap = new ConcurrentHashMap<>();
        this.maxSize = maxSize;
        this.enableDeduplication = enableDeduplication;
        this.deduplicationKeyExtractor = deduplicationKeyExtractor;
    }

    /**
     * 添加任务到队列，支持去重
     */
    public boolean offer(Task<T> task) {
        if (task == null) {
            return false;
        }

        totalOffered.incrementAndGet();

        if (enableDeduplication) {
            String deduplicationKey = deduplicationKeyExtractor.apply(task);
            
            // 使用 ConcurrentHashMap 进行 O(1) 去重检查
            Boolean existing = deduplicationMap.putIfAbsent(deduplicationKey, Boolean.TRUE);
            if (existing != null) {
                totalDeduplicated.incrementAndGet();
                return false; // 重复任务，拒绝添加
            }
        }

        // 检查队列大小
        if (queue.size() >= maxSize) {
            if (task.isDiscardable()) {
                return false; // 可丢弃任务，直接拒绝
            } else {
                // 不可丢弃任务，需要等待空间
                return false;
            }
        }
        
        // 添加新任务
        return queue.offer(task);
    }

    /**
     * 阻塞式获取任务
     */
    public Task<T> take() throws InterruptedException {
        Task<T> task = queue.take();
        if (task != null) {
            totalPolled.incrementAndGet();
            // 从去重映射中移除已处理的任务
            if (enableDeduplication) {
                String deduplicationKey = deduplicationKeyExtractor.apply(task);
                deduplicationMap.remove(deduplicationKey);
            }
        }
        return task;
    }

    /**
     * 非阻塞式获取任务
     */
    public Task<T> poll() {
        Task<T> task = queue.poll();
        if (task != null) {
            totalPolled.incrementAndGet();
            // 从去重映射中移除已处理的任务
            if (enableDeduplication) {
                String deduplicationKey = deduplicationKeyExtractor.apply(task);
                deduplicationMap.remove(deduplicationKey);
            }
        }
        return task;
    }

    /**
     * 带超时的阻塞式获取任务
     */
    public Task<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
        Task<T> task = queue.poll(timeout, unit);
        if (task != null) {
            totalPolled.incrementAndGet();
            // 从去重映射中移除已处理的任务
            if (enableDeduplication) {
                String deduplicationKey = deduplicationKeyExtractor.apply(task);
                deduplicationMap.remove(deduplicationKey);
            }
        }
        return task;
    }

    /**
     * 获取队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 清空队列
     */
    public void clear() {
        queue.clear();
        if (enableDeduplication) {
            deduplicationMap.clear();
        }
    }

    /**
     * 获取统计信息
     */
    public QueueStats getStats() {
        return new QueueStats(
            queue.size(),
            deduplicationMap.size(),
            totalOffered.get(),
            totalDeduplicated.get(),
            totalPolled.get()
        );
    }

    /**
     * 队列统计信息
     */
    public static class QueueStats {
        private final int currentSize;
        private final int deduplicationMapSize;
        private final int totalOffered;
        private final int totalDeduplicated;
        private final int totalPolled;

        public QueueStats(int currentSize, int deduplicationMapSize, int totalOffered, int totalDeduplicated, int totalPolled) {
            this.currentSize = currentSize;
            this.deduplicationMapSize = deduplicationMapSize;
            this.totalOffered = totalOffered;
            this.totalDeduplicated = totalDeduplicated;
            this.totalPolled = totalPolled;
        }

        public int getCurrentSize() { return currentSize; }
        public int getDeduplicationMapSize() { return deduplicationMapSize; }
        public int getTotalOffered() { return totalOffered; }
        public int getTotalDeduplicated() { return totalDeduplicated; }
        public int getTotalPolled() { return totalPolled; }
    }
} 