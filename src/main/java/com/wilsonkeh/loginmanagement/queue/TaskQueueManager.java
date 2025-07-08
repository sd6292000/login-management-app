package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.config.QueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务队列管理器，负责管理多个队列实例
 */
@Slf4j
@Component
public class TaskQueueManager {

    @Autowired
    private QueueConfig queueConfig;

    // 存储所有队列实例
    private final Map<String, GenericTaskQueue<?>> queues = new ConcurrentHashMap<>();

    /**
     * 获取或创建队列实例
     */
    @SuppressWarnings("unchecked")
    public <T> GenericTaskQueue<T> getQueue(String queueName) {
        return (GenericTaskQueue<T>) queues.computeIfAbsent(queueName, name -> {
            log.info("创建新的任务队列: {}", name);
            return new GenericTaskQueue<>(name);
        });
    }

    /**
     * 获取所有队列的统计信息
     */
    public Map<String, GenericTaskQueue.QueueStatistics> getAllQueueStatistics() {
        Map<String, GenericTaskQueue.QueueStatistics> statistics = new ConcurrentHashMap<>();
        queues.forEach((name, queue) -> statistics.put(name, queue.getStatistics()));
        return statistics;
    }

    /**
     * 获取指定队列的统计信息
     */
    public GenericTaskQueue.QueueStatistics getQueueStatistics(String queueName) {
        GenericTaskQueue<?> queue = queues.get(queueName);
        return queue != null ? queue.getStatistics() : null;
    }

    /**
     * 清空指定队列
     */
    public void clearQueue(String queueName) {
        GenericTaskQueue<?> queue = queues.get(queueName);
        if (queue != null) {
            queue.clearQueue();
            log.info("队列已清空: {}", queueName);
        } else {
            log.warn("队列不存在: {}", queueName);
        }
    }

    /**
     * 获取所有队列名称
     */
    public java.util.Set<String> getQueueNames() {
        return queues.keySet();
    }

    /**
     * 检查队列是否存在
     */
    public boolean queueExists(String queueName) {
        return queues.containsKey(queueName);
    }

    /**
     * 获取队列总数
     */
    public int getQueueCount() {
        return queues.size();
    }
} 