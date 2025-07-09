package com.wilsonkeh.loginmanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.queue")
public class QueueConfig {
    
    private Map<String, QueueProperties> queues;
    
    @Data
    public static class QueueProperties {
        private String name;
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        private int queueCapacity = 1000;
        private String threadNamePrefix;
        private int batchSize = 20;
        private int maxBatchSize = 50;
        private long processIntervalMs = 5000;
        private int maxRetryAttempts = 3;
        private long retryDelayMs = 1000;
        private double retryMultiplier = 2.0;
        private boolean enableDeduplication = true;
        private String deduplicationKeyStrategy = "DEFAULT"; // DEFAULT, CUSTOM, NONE
        private int maxQueueSize = 10000;
        private boolean enableMetrics = true;
        private boolean enableParallelProcessing = false; // 是否启用并行处理
        private int parallelThreadPoolSize = 4; // 并行处理线程池大小
    }
    
    /**
     * 获取指定队列的配置，如果不存在则返回默认配置
     */
    public QueueProperties getQueueProperties(String queueName) {
        return queues.getOrDefault(queueName, createDefaultProperties(queueName));
    }
    
    private QueueProperties createDefaultProperties(String queueName) {
        QueueProperties props = new QueueProperties();
        props.setName(queueName);
        props.setThreadNamePrefix(queueName + "-");
        return props;
    }
} 