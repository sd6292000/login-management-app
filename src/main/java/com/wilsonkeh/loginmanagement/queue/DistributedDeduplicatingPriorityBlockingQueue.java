package com.wilsonkeh.loginmanagement.queue;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.collection.IQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 分布式去重优先级阻塞队列
 * 使用Hazelcast实现跨机器的去重功能
 */
@Slf4j
@Component
public class DistributedDeduplicatingPriorityBlockingQueue<T> {
    
    private final IQueue<Task<T>> distributedQueue;
    private final IMap<String, Boolean> distributedDeduplicationMap;
    private final Function<Task<T>, String> deduplicationKeyExtractor;
    private final boolean enableDeduplication;
    private final int maxSize;
    
    // 统计计数器
    private final AtomicInteger totalOffered = new AtomicInteger(0);
    private final AtomicInteger totalDeduplicated = new AtomicInteger(0);
    private final AtomicInteger totalPolled = new AtomicInteger(0);

    @Autowired
    public DistributedDeduplicatingPriorityBlockingQueue(HazelcastInstance hazelcastInstance,
                                                        Function<Task<T>, String> deduplicationKeyExtractor) {
        this(hazelcastInstance, 10000, true, deduplicationKeyExtractor);
    }

    public DistributedDeduplicatingPriorityBlockingQueue(HazelcastInstance hazelcastInstance,
                                                        int maxSize, 
                                                        boolean enableDeduplication,
                                                        Function<Task<T>, String> deduplicationKeyExtractor) {
        this.distributedQueue = hazelcastInstance.getQueue(HazelcastConfig.TASK_QUEUE_NAME);
        this.distributedDeduplicationMap = hazelcastInstance.getMap(HazelcastConfig.DEDUPLICATION_MAP_NAME);
        this.maxSize = maxSize;
        this.enableDeduplication = enableDeduplication;
        this.deduplicationKeyExtractor = deduplicationKeyExtractor;
        
        log.info("分布式去重队列初始化完成 - 队列名称: {}, 去重Map名称: {}", 
                HazelcastConfig.TASK_QUEUE_NAME, HazelcastConfig.DEDUPLICATION_MAP_NAME);
    }

    /**
     * 添加任务到分布式队列，支持跨机器去重
     */
    public boolean offer(Task<T> task) {
        if (task == null) {
            return false;
        }

        totalOffered.incrementAndGet();

        if (enableDeduplication) {
            String deduplicationKey = deduplicationKeyExtractor.apply(task);
            
            try {
                // 使用分布式Map进行去重检查，支持跨机器
                Boolean existing = distributedDeduplicationMap.putIfAbsent(deduplicationKey, Boolean.TRUE);
                if (existing != null) {
                    totalDeduplicated.incrementAndGet();
                    log.debug("检测到重复任务，拒绝添加: {}", deduplicationKey);
                    return false; // 重复任务，拒绝添加
                }
            } catch (Exception e) {
                log.error("分布式去重检查失败: {}", e.getMessage(), e);
                // 如果去重检查失败，仍然允许添加任务以避免阻塞
            }
        }

        // 检查队列大小
        if (distributedQueue.size() >= maxSize) {
            if (task.isDiscardable()) {
                log.warn("队列已满，丢弃可丢弃任务: {}", task);
                return false; // 可丢弃任务，直接拒绝
            } else {
                log.warn("队列已满，拒绝不可丢弃任务: {}", task);
                return false;
            }
        }
        
        try {
            // 添加新任务到分布式队列
            boolean result = distributedQueue.offer(task);
            if (result) {
                log.debug("成功添加任务到分布式队列: {}", task);
            }
            return result;
        } catch (Exception e) {
            log.error("添加任务到分布式队列失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 阻塞式获取任务
     */
    public Task<T> take() throws InterruptedException {
        try {
            Task<T> task = distributedQueue.take();
            if (task != null) {
                totalPolled.incrementAndGet();
                // 从分布式去重映射中移除已处理的任务
                if (enableDeduplication) {
                    String deduplicationKey = deduplicationKeyExtractor.apply(task);
                    distributedDeduplicationMap.remove(deduplicationKey);
                    log.debug("从分布式去重Map中移除已处理任务: {}", deduplicationKey);
                }
                log.debug("从分布式队列中获取任务: {}", task);
            }
            return task;
        } catch (InterruptedException e) {
            log.warn("获取任务时被中断");
            throw e;
        } catch (Exception e) {
            log.error("从分布式队列获取任务失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 非阻塞式获取任务
     */
    public Task<T> poll() {
        try {
            Task<T> task = distributedQueue.poll();
            if (task != null) {
                totalPolled.incrementAndGet();
                // 从分布式去重映射中移除已处理的任务
                if (enableDeduplication) {
                    String deduplicationKey = deduplicationKeyExtractor.apply(task);
                    distributedDeduplicationMap.remove(deduplicationKey);
                    log.debug("从分布式去重Map中移除已处理任务: {}", deduplicationKey);
                }
                log.debug("从分布式队列中轮询获取任务: {}", task);
            }
            return task;
        } catch (Exception e) {
            log.error("从分布式队列轮询任务失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 带超时的阻塞式获取任务
     */
    public Task<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            Task<T> task = distributedQueue.poll(timeout, unit);
            if (task != null) {
                totalPolled.incrementAndGet();
                // 从分布式去重映射中移除已处理的任务
                if (enableDeduplication) {
                    String deduplicationKey = deduplicationKeyExtractor.apply(task);
                    distributedDeduplicationMap.remove(deduplicationKey);
                    log.debug("从分布式去重Map中移除已处理任务: {}", deduplicationKey);
                }
                log.debug("从分布式队列中获取任务(带超时): {}", task);
            }
            return task;
        } catch (InterruptedException e) {
            log.warn("获取任务时被中断(带超时)");
            throw e;
        } catch (Exception e) {
            log.error("从分布式队列获取任务失败(带超时): {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取队列大小
     */
    public int size() {
        try {
            return distributedQueue.size();
        } catch (Exception e) {
            log.error("获取分布式队列大小失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        try {
            return distributedQueue.isEmpty();
        } catch (Exception e) {
            log.error("检查分布式队列是否为空失败: {}", e.getMessage(), e);
            return true;
        }
    }

    /**
     * 清空队列
     */
    public void clear() {
        try {
            distributedQueue.clear();
            if (enableDeduplication) {
                distributedDeduplicationMap.clear();
            }
            log.info("分布式队列和去重Map已清空");
        } catch (Exception e) {
            log.error("清空分布式队列失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取统计信息
     */
    public DistributedQueueStats getStats() {
        try {
            return new DistributedQueueStats(
                distributedQueue.size(),
                distributedDeduplicationMap.size(),
                totalOffered.get(),
                totalDeduplicated.get(),
                totalPolled.get()
            );
        } catch (Exception e) {
            log.error("获取分布式队列统计信息失败: {}", e.getMessage(), e);
            return new DistributedQueueStats(0, 0, 0, 0, 0);
        }
    }

    /**
     * 分布式队列统计信息
     */
    public static class DistributedQueueStats {
        private final int currentSize;
        private final int deduplicationMapSize;
        private final int totalOffered;
        private final int totalDeduplicated;
        private final int totalPolled;

        public DistributedQueueStats(int currentSize, int deduplicationMapSize, int totalOffered, int totalDeduplicated, int totalPolled) {
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

        @Override
        public String toString() {
            return String.format("DistributedQueueStats{currentSize=%d, deduplicationMapSize=%d, totalOffered=%d, totalDeduplicated=%d, totalPolled=%d}",
                    currentSize, deduplicationMapSize, totalOffered, totalDeduplicated, totalPolled);
        }
    }
} 