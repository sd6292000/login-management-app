package com.wilsonkeh.loginmanagement.queue;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.collection.IQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 分布式任务队列管理器
 * 管理分布式队列的生命周期和配置
 */
@Slf4j
@Component
public class DistributedTaskQueueManager<T> {
    
    private final HazelcastInstance hazelcastInstance;
    private final DistributedDeduplicatingPriorityBlockingQueue<T> distributedQueue;
    
    @Value("${app.queue.distributed.enabled:true}")
    private boolean distributedEnabled;
    
    @Value("${app.queue.distributed.max-size:10000}")
    private int maxQueueSize;
    
    @Value("${app.queue.distributed.deduplication.enabled:true}")
    private boolean deduplicationEnabled;

    @Autowired
    public DistributedTaskQueueManager(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        
        // 创建去重键提取器 - 可以根据具体任务类型定制
        Function<Task<T>, String> deduplicationKeyExtractor = this::createDeduplicationKey;
        
        this.distributedQueue = new DistributedDeduplicatingPriorityBlockingQueue<>(
            hazelcastInstance, maxQueueSize, deduplicationEnabled, deduplicationKeyExtractor);
    }

    @PostConstruct
    public void init() {
        if (distributedEnabled) {
            log.info("分布式任务队列管理器初始化完成");
            log.info("集群信息: {}", hazelcastInstance.getCluster().getMembers());
            log.info("分布式队列配置 - 最大大小: {}, 去重启用: {}", maxQueueSize, deduplicationEnabled);
        } else {
            log.warn("分布式队列功能已禁用");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (distributedEnabled) {
            log.info("正在关闭分布式任务队列管理器...");
            try {
                // 清空队列和去重Map
                distributedQueue.clear();
                log.info("分布式任务队列管理器已关闭");
            } catch (Exception e) {
                log.error("关闭分布式任务队列管理器时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取分布式队列实例
     */
    public DistributedDeduplicatingPriorityBlockingQueue<T> getDistributedQueue() {
        return distributedQueue;
    }

    /**
     * 添加任务到分布式队列
     */
    public boolean offerTask(Task<T> task) {
        if (!distributedEnabled) {
            log.warn("分布式队列功能已禁用，无法添加任务");
            return false;
        }
        return distributedQueue.offer(task);
    }

    /**
     * 从分布式队列获取任务
     */
    public Task<T> takeTask() throws InterruptedException {
        if (!distributedEnabled) {
            log.warn("分布式队列功能已禁用，无法获取任务");
            return null;
        }
        return distributedQueue.take();
    }

    /**
     * 从分布式队列轮询任务
     */
    public Task<T> pollTask() {
        if (!distributedEnabled) {
            log.warn("分布式队列功能已禁用，无法轮询任务");
            return null;
        }
        return distributedQueue.poll();
    }

    /**
     * 带超时的任务获取
     */
    public Task<T> pollTask(long timeout, TimeUnit unit) throws InterruptedException {
        if (!distributedEnabled) {
            log.warn("分布式队列功能已禁用，无法获取任务");
            return null;
        }
        return distributedQueue.poll(timeout, unit);
    }

    /**
     * 获取队列统计信息
     */
    public DistributedDeduplicatingPriorityBlockingQueue.DistributedQueueStats getQueueStats() {
        return distributedQueue.getStats();
    }

    /**
     * 获取集群信息
     */
    public String getClusterInfo() {
        try {
            return String.format("集群成员数: %d, 当前节点: %s", 
                hazelcastInstance.getCluster().getMembers().size(),
                hazelcastInstance.getCluster().getLocalMember().getAddress());
        } catch (Exception e) {
            log.error("获取集群信息失败: {}", e.getMessage(), e);
            return "获取集群信息失败";
        }
    }

    /**
     * 检查集群健康状态
     */
    public boolean isClusterHealthy() {
        try {
            return hazelcastInstance.getLifecycleService().isRunning() && 
                   !hazelcastInstance.getCluster().getMembers().isEmpty();
        } catch (Exception e) {
            log.error("检查集群健康状态失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建去重键
     * 可以根据具体业务需求定制去重逻辑
     */
    private String createDeduplicationKey(Task<T> task) {
        if (task == null || task.getData() == null) {
            return "null-task";
        }
        
        // 示例：使用任务数据的关键字段组合作为去重键
        // 这里可以根据具体的任务类型和数据结构进行定制
        return String.format("%s-%s-%d", 
            task.getData().getClass().getSimpleName(),
            task.getData().toString().hashCode(),
            task.getPriority());
    }

    /**
     * 手动清理过期的去重记录
     */
    public void cleanupExpiredDeduplicationRecords() {
        if (!distributedEnabled || !deduplicationEnabled) {
            return;
        }
        
        try {
            IMap<String, Boolean> deduplicationMap = hazelcastInstance.getMap(HazelcastConfig.DEDUPLICATION_MAP_NAME);
            int beforeSize = deduplicationMap.size();
            
            // 这里可以实现更复杂的清理逻辑，比如基于时间戳的清理
            // 目前Hazelcast配置了TTL，会自动清理过期记录
            
            log.info("去重记录清理完成 - 清理前: {}, 清理后: {}", beforeSize, deduplicationMap.size());
        } catch (Exception e) {
            log.error("清理过期去重记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取分布式队列状态报告
     */
    public String getQueueStatusReport() {
        if (!distributedEnabled) {
            return "分布式队列功能已禁用";
        }
        
        try {
            DistributedDeduplicatingPriorityBlockingQueue.DistributedQueueStats stats = getQueueStats();
            String clusterInfo = getClusterInfo();
            boolean clusterHealthy = isClusterHealthy();
            
            return String.format(
                "分布式队列状态报告:\n" +
                "- 集群状态: %s\n" +
                "- 集群信息: %s\n" +
                "- 队列大小: %d\n" +
                "- 去重Map大小: %d\n" +
                "- 总提交任务数: %d\n" +
                "- 总去重任务数: %d\n" +
                "- 总处理任务数: %d",
                clusterHealthy ? "健康" : "异常",
                clusterInfo,
                stats.getCurrentSize(),
                stats.getDeduplicationMapSize(),
                stats.getTotalOffered(),
                stats.getTotalDeduplicated(),
                stats.getTotalPolled()
            );
        } catch (Exception e) {
            log.error("生成队列状态报告失败: {}", e.getMessage(), e);
            return "生成状态报告失败: " + e.getMessage();
        }
    }
} 