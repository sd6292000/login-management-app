package com.wilsonkeh.loginmanagement.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hazelcast配置类
 * 配置分布式Map用于去重，分布式队列用于任务存储
 */
@Configuration
public class HazelcastConfig {

    public static final String DEDUPLICATION_MAP_NAME = "login-record-deduplication";
    public static final String TASK_QUEUE_NAME = "login-record-task-queue";
    public static final String CLUSTER_NAME = "login-management-cluster";

    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName(CLUSTER_NAME);
        
        // 配置分布式Map用于去重
        MapConfig deduplicationMapConfig = new MapConfig(DEDUPLICATION_MAP_NAME);
        deduplicationMapConfig.setTimeToLiveSeconds(3600); // 1小时TTL
        deduplicationMapConfig.setMaxIdleSeconds(1800); // 30分钟空闲时间
        config.addMapConfig(deduplicationMapConfig);
        
        // 配置分布式队列
        QueueConfig queueConfig = new QueueConfig(TASK_QUEUE_NAME);
        queueConfig.setMaxSize(10000); // 最大队列大小
        config.addQueueConfig(queueConfig);
        
        return Hazelcast.newHazelcastInstance(config);
    }
} 