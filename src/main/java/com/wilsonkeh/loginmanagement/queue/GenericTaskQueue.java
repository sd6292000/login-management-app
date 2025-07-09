package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.config.QueueConfig;
import com.wilsonkeh.loginmanagement.monitoring.QueueMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

    @Autowired
    private QueueMetrics queueMetrics;

    private final String queueName;
    private final QueueConfig.QueueProperties properties;
    
    // 支持去重的优先级队列
    private final DeduplicatingPriorityBlockingQueue<T> taskQueue;
    
    // 统计计数器
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicLong lastProcessTime = new AtomicLong(0);
    
    // 监控指标
    private final QueueMetrics.QueueMetricSet metrics;

    public GenericTaskQueue(String queueName) {
        this.queueName = queueName;
        this.properties = queueConfig.getQueueProperties(queueName);
        this.taskQueue = new DeduplicatingPriorityBlockingQueue<>(
            properties.getMaxQueueSize(),
            properties.isEnableDeduplication(),
            Task::getDeduplicationKey
        );
        
        // 初始化监控指标
        this.metrics = queueMetrics.getOrCreateQueueMetrics(queueName);
        this.metrics.setQueueCapacity(properties.getMaxQueueSize());
    }

    /**
     * 将任务加入队列
     */
    public boolean enqueueTask(Task<T> task) {
        try {
            boolean success = taskQueue.offer(task);
            
            if (success) {
                metrics.recordTaskEnqueued();
                log.debug("任务已加入队列，taskId: {}, queueName: {}, 当前队列大小: {}", 
                         task.getTaskId(), queueName, taskQueue.size());
            } else {
                if (properties.isEnableDeduplication()) {
                    metrics.recordTaskDeduplicated();
                    log.debug("检测到重复任务，已丢弃，taskId: {}, queueName: {}", 
                             task.getTaskId(), queueName);
                } else {
                    log.warn("队列已满，无法添加任务，taskId: {}, queueName: {}", 
                             task.getTaskId(), queueName);
                }
            }
            
            // 更新队列大小指标
            metrics.setQueueSize(taskQueue.size());
            
            return success;

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
                // 开始批处理计时
                var batchTimer = metrics.startBatchProcessingTimer();
                
                try {
                    // 查找对应的处理器
                    TaskProcessor<T> processor = findProcessor(batch.get(0));
                    if (processor != null) {
                        // 根据配置决定是否使用并行处理
                        if (properties.getEnableParallelProcessing()) {
                            int threadPoolSize = properties.getParallelThreadPoolSize();
                            processor.processBatchParallel(batch, threadPoolSize);
                            log.debug("使用并行处理，线程池大小: {}", threadPoolSize);
                        } else {
                            processor.processBatch(batch);
                            log.debug("使用串行处理");
                        }
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

                // 停止批处理计时
                metrics.stopBatchProcessingTimer(batchTimer);
                
                // 记录批处理完成
                metrics.recordBatchProcessed();
            }

            totalProcessed.addAndGet(processedCount);
            totalFailed.addAndGet(failedCount);
            lastProcessTime.set(System.currentTimeMillis());

            // 记录处理结果
            for (int i = 0; i < processedCount; i++) {
                metrics.recordTaskProcessed();
            }
            for (int i = 0; i < failedCount; i++) {
                metrics.recordTaskFailed();
            }

            // 更新队列大小指标
            metrics.setQueueSize(taskQueue.size());

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
        DeduplicatingPriorityBlockingQueue.QueueStats queueStats = taskQueue.getStats();
        return QueueStatistics.builder()
                .queueName(queueName)
                .queueSize(queueStats.getCurrentSize())
                .deduplicationMapSize(queueStats.getDeduplicationMapSize())
                .totalEnqueued(queueStats.getTotalOffered())
                .totalDeduplicated(queueStats.getTotalDeduplicated())
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
        return taskQueue.getStats().getDeduplicationMapSize();
    }

    /**
     * 清空队列（谨慎使用）
     */
    public void clearQueue() {
        taskQueue.clear();
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