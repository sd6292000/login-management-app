package com.wilsonkeh.loginmanagement.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 队列监控指标
 * 提供与Grafana集成的Micrometer指标
 */
@Component
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, QueueMetricSet> queueMetrics = new ConcurrentHashMap<>();

    public QueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 为指定队列创建或获取监控指标
     */
    public QueueMetricSet getOrCreateQueueMetrics(String queueName) {
        return queueMetrics.computeIfAbsent(queueName, this::createQueueMetrics);
    }

    /**
     * 创建队列监控指标集
     */
    private QueueMetricSet createQueueMetrics(String queueName) {
        return new QueueMetricSet(queueName, meterRegistry);
    }

    /**
     * 移除队列监控指标
     */
    public void removeQueueMetrics(String queueName) {
        QueueMetricSet metricSet = queueMetrics.remove(queueName);
        if (metricSet != null) {
            metricSet.remove();
        }
    }

    /**
     * 队列监控指标集
     */
    public static class QueueMetricSet {
        private final String queueName;
        private final MeterRegistry meterRegistry;

        // 计数器指标
        private final Counter tasksEnqueuedCounter;
        private final Counter tasksDeduplicatedCounter;
        private final Counter tasksProcessedCounter;
        private final Counter tasksFailedCounter;
        private final Counter batchProcessedCounter;

        // 计时器指标
        private final Timer taskProcessingTimer;
        private final Timer batchProcessingTimer;

        // 仪表指标
        private final Gauge queueSizeGauge;
        private final Gauge queueCapacityGauge;

        public QueueMetricSet(String queueName, MeterRegistry meterRegistry) {
            this.queueName = queueName;
            this.meterRegistry = meterRegistry;

            // 创建计数器
            this.tasksEnqueuedCounter = Counter.builder("queue.tasks.enqueued")
                    .tag("queue", queueName)
                    .description("Number of tasks enqueued")
                    .register(meterRegistry);

            this.tasksDeduplicatedCounter = Counter.builder("queue.tasks.deduplicated")
                    .tag("queue", queueName)
                    .description("Number of tasks deduplicated")
                    .register(meterRegistry);

            this.tasksProcessedCounter = Counter.builder("queue.tasks.processed")
                    .tag("queue", queueName)
                    .description("Number of tasks processed")
                    .register(meterRegistry);

            this.tasksFailedCounter = Counter.builder("queue.tasks.failed")
                    .tag("queue", queueName)
                    .description("Number of tasks failed")
                    .register(meterRegistry);

            this.batchProcessedCounter = Counter.builder("queue.batches.processed")
                    .tag("queue", queueName)
                    .description("Number of batches processed")
                    .register(meterRegistry);

            // 创建计时器
            this.taskProcessingTimer = Timer.builder("queue.task.processing.time")
                    .tag("queue", queueName)
                    .description("Time taken to process individual tasks")
                    .register(meterRegistry);

            this.batchProcessingTimer = Timer.builder("queue.batch.processing.time")
                    .tag("queue", queueName)
                    .description("Time taken to process batches")
                    .register(meterRegistry);

            // 创建仪表（初始值为0）
            this.queueSizeGauge = Gauge.builder("queue.size", this::getQueueSize)
                    .tag("queue", queueName)
                    .description("Current queue size").register(meterRegistry);

            this.queueCapacityGauge = Gauge.builder("queue.capacity",this::getQueueCapacity)
                    .tag("queue", queueName)
                    .description("Queue capacity")
                    .register(meterRegistry);
        }

        // 更新队列大小的方法
        private int currentQueueSize = 0;
        private int currentQueueCapacity = 10000;

        public void setQueueSize(int size) {
            this.currentQueueSize = size;
        }

        public void setQueueCapacity(int capacity) {
            this.currentQueueCapacity = capacity;
        }

        private double getQueueSize() {
            return currentQueueSize;
        }

        private double getQueueCapacity() {
            return currentQueueCapacity;
        }

        // 记录任务入队
        public void recordTaskEnqueued() {
            tasksEnqueuedCounter.increment();
        }

        // 记录任务去重
        public void recordTaskDeduplicated() {
            tasksDeduplicatedCounter.increment();
        }

        // 记录任务处理
        public void recordTaskProcessed() {
            tasksProcessedCounter.increment();
        }

        // 记录任务失败
        public void recordTaskFailed() {
            tasksFailedCounter.increment();
        }

        // 记录批处理
        public void recordBatchProcessed() {
            batchProcessedCounter.increment();
        }

        // 记录任务处理时间
        public Timer.Sample startTaskProcessingTimer() {
            return Timer.start(meterRegistry);
        }

        public void stopTaskProcessingTimer(Timer.Sample sample) {
            sample.stop(taskProcessingTimer);
        }

        // 记录批处理时间
        public Timer.Sample startBatchProcessingTimer() {
            return Timer.start(meterRegistry);
        }

        public void stopBatchProcessingTimer(Timer.Sample sample) {
            sample.stop(batchProcessingTimer);
        }

        // 移除所有指标
        public void remove() {
            meterRegistry.remove(tasksEnqueuedCounter);
            meterRegistry.remove(tasksDeduplicatedCounter);
            meterRegistry.remove(tasksProcessedCounter);
            meterRegistry.remove(tasksFailedCounter);
            meterRegistry.remove(batchProcessedCounter);
            meterRegistry.remove(taskProcessingTimer);
            meterRegistry.remove(batchProcessingTimer);
            meterRegistry.remove(queueSizeGauge);
            meterRegistry.remove(queueCapacityGauge);
        }
    }
} 