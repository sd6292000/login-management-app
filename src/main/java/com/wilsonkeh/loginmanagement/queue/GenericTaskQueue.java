package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.config.QueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用任务队列管理器
 * @param <T> 任务数据类型
 */
@Slf4j
@Component
public class GenericTaskQueue<T> {

    @Autowired
    private QueueConfig queueConfig;
    
    @Autowired
    private List<TaskProcessor<T>> taskProcessors;

    private final String queueName;
    private final QueueConfig.QueueProperties properties;
    
    // 优先级队列，用于存储待处理的任务
    private final PriorityBlockingQueue<Task<T>> taskQueue;
    
    // 去重映射表
    private final ConcurrentHashMap<String, Task<T>> deduplicationMap;
    
    // 统计计数器
    private final AtomicInteger totalEnqueued = new AtomicInteger(0);
    private final AtomicInteger totalDeduplicated = new AtomicInteger(0);
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicLong lastProcessTime = new AtomicLong(0);

    public GenericTaskQueue(String queueName) {
        this.queueName = queueName;
        this.properties = queueConfig.getQueueProperties(queueName);
        this.taskQueue = new PriorityBlockingQueue<>(properties.getMaxQueueSize(), 
            (t1, t2) -> Integer.compare(t1.getPriority(), t2.getPriority()));
        this.deduplicationMap = new ConcurrentHashMap<>();
    }

    /**
     * 将任务加入队列
     */
    public boolean enqueueTask(Task<T> task) {
        try {
            if (properties.isEnableDeduplication()) {
                String deduplicationKey = task.getDeduplicationKey();
                Task<T> existingTask = deduplicationMap.putIfAbsent(deduplicationKey, task);
                
                if (existingTask != null) {
                    totalDeduplicated.incrementAndGet();
                    log.debug("检测到重复任务，已丢弃，taskId: {}, queueName: {}", 
                             task.getTaskId(), queueName);
                    return false;
                }
            }

            // 检查队列容量
            if (taskQueue.size() >= properties.getMaxQueueSize()) {
                if (task.isDiscardable()) {
                    log.warn("队列已满，丢弃可丢弃任务，taskId: {}, queueName: {}", 
                            task.getTaskId(), queueName);
                    return false;
                } else {
                    log.error("队列已满，无法添加任务，taskId: {}, queueName: {}", 
                             task.getTaskId(), queueName);
                    return false;
                }
            }

            taskQueue.offer(task);
            totalEnqueued.incrementAndGet();
            log.debug("任务已加入队列，taskId: {}, queueName: {}, 当前队列大小: {}", 
                     task.getTaskId(), queueName, taskQueue.size());
            return true;

        } catch (Exception e) {
            log.error("加入任务队列时发生错误，taskId: {}, queueName: {}, 错误: {}", 
                     task.getTaskId(), queueName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 定时批量处理任务
     */
    @Scheduled(fixedRateString = "#{@queueConfig.getQueueProperties('${app.queue.default.name:default}').processIntervalMs}")
    @Async("batchProcessorExecutor")
    public void processBatchTasks() {
        try {
            int queueSize = taskQueue.size();
            if (queueSize == 0) {
                return;
            }

            int batchSize = calculateBatchSize(queueSize);
            log.info("开始批量处理任务，队列: {}, 队列大小: {}, 批处理大小: {}", 
                     queueName, queueSize, batchSize);

            List<Task<T>> batch = new ArrayList<>();
            int processedCount = 0;
            int failedCount = 0;

            // 收集批处理任务
            for (int i = 0; i < batchSize && !taskQueue.isEmpty(); i++) {
                Task<T> task = taskQueue.poll();
                if (task != null) {
                    batch.add(task);
                }
            }

            if (!batch.isEmpty()) {
                try {
                    // 查找对应的处理器
                    TaskProcessor<T> processor = findProcessor(batch.get(0));
                    if (processor != null) {
                        processor.processBatch(batch);
                        processedCount = batch.size();
                    } else {
                        log.error("未找到任务处理器，taskType: {}, queueName: {}", 
                                 batch.get(0).getTaskType(), queueName);
                        failedCount = batch.size();
                    }
                } catch (Exception e) {
                    log.error("批量处理任务时发生错误，queueName: {}, 错误: {}", 
                             queueName, e.getMessage(), e);
                    failedCount = batch.size();
                }

                // 从去重映射中移除已处理的任务
                for (Task<T> task : batch) {
                    if (properties.isEnableDeduplication()) {
                        deduplicationMap.remove(task.getDeduplicationKey());
                    }
                }
            }

            totalProcessed.addAndGet(processedCount);
            totalFailed.addAndGet(failedCount);
            lastProcessTime.set(System.currentTimeMillis());

            log.info("批量处理完成，队列: {}, 处理数量: {}, 失败数量: {}, 剩余队列大小: {}", 
                     queueName, processedCount, failedCount, taskQueue.size());

        } catch (Exception e) {
            log.error("批量处理任务时发生错误，queueName: {}, 错误: {}", 
                     queueName, e.getMessage(), e);
        }
    }

    /**
     * 查找任务处理器
     */
    private TaskProcessor<T> findProcessor(Task<T> task) {
        return taskProcessors.stream()
                .filter(processor -> processor.canProcess(task))
                .findFirst()
                .orElse(null);
    }

    /**
     * 计算批处理大小
     */
    private int calculateBatchSize(int queueSize) {
        if (queueSize <= properties.getBatchSize()) {
            return queueSize;
        } else {
            return Math.min(queueSize, properties.getMaxBatchSize());
        }
    }

    /**
     * 获取队列统计信息
     */
    public QueueStatistics getStatistics() {
        return QueueStatistics.builder()
                .queueName(queueName)
                .queueSize(taskQueue.size())
                .deduplicationMapSize(deduplicationMap.size())
                .totalEnqueued(totalEnqueued.get())
                .totalDeduplicated(totalDeduplicated.get())
                .totalProcessed(totalProcessed.get())
                .totalFailed(totalFailed.get())
                .lastProcessTime(lastProcessTime.get())
                .build();
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 获取去重映射大小
     */
    public int getDeduplicationMapSize() {
        return deduplicationMap.size();
    }

    /**
     * 清空队列（谨慎使用）
     */
    public void clearQueue() {
        taskQueue.clear();
        deduplicationMap.clear();
        log.warn("队列已清空，queueName: {}", queueName);
    }

    /**
     * 队列统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class QueueStatistics {
        private String queueName;
        private int queueSize;
        private int deduplicationMapSize;
        private int totalEnqueued;
        private int totalDeduplicated;
        private int totalProcessed;
        private int totalFailed;
        private long lastProcessTime;
    }
} 