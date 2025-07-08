package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 分布式登录记录任务处理器
 * 支持从分布式队列中获取和处理任务
 */
@Slf4j
@Component
public class DistributedLoginRecordTaskProcessor {

    @Autowired
    private DistributedTaskQueueManager<LoginRecordRequest> distributedTaskQueueManager;

    @Autowired
    private LoginRecordService loginRecordService;

    @Value("${app.queue.distributed.enabled:true}")
    private boolean distributedEnabled;

    @Value("${app.queue.distributed.processor.enabled:true}")
    private boolean processorEnabled;

    @Value("${app.queue.distributed.processor.threads:2}")
    private int processorThreads;

    @Value("${app.queue.distributed.processor.poll-timeout:5}")
    private long pollTimeout;

    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DistributedLoginRecordTaskProcessor() {
        this.executorService = Executors.newCachedThreadPool();
    }

    @PostConstruct
    public void init() {
        if (distributedEnabled && processorEnabled) {
            log.info("分布式登录记录任务处理器初始化完成");
            log.info("处理器配置 - 线程数: {}, 轮询超时: {}秒", processorThreads, pollTimeout);
            startProcessing();
        } else {
            log.info("分布式任务处理器已禁用 - distributedEnabled: {}, processorEnabled: {}", 
                    distributedEnabled, processorEnabled);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭分布式登录记录任务处理器...");
        stopProcessing();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("分布式登录记录任务处理器已关闭");
    }

    /**
     * 启动任务处理
     */
    public void startProcessing() {
        if (running.compareAndSet(false, true)) {
            log.info("启动分布式任务处理...");
            for (int i = 0; i < processorThreads; i++) {
                executorService.submit(new TaskProcessorWorker("Worker-" + i));
            }
        }
    }

    /**
     * 停止任务处理
     */
    public void stopProcessing() {
        if (running.compareAndSet(true, false)) {
            log.info("停止分布式任务处理...");
        }
    }

    /**
     * 定时任务：检查队列状态
     */
    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void checkQueueStatus() {
        if (!distributedEnabled || !processorEnabled) {
            return;
        }

        try {
            var stats = distributedTaskQueueManager.getQueueStats();
            if (stats.getCurrentSize() > 0) {
                log.info("分布式队列状态 - 队列大小: {}, 去重Map大小: {}, 总提交: {}, 总去重: {}, 总处理: {}", 
                        stats.getCurrentSize(), stats.getDeduplicationMapSize(),
                        stats.getTotalOffered(), stats.getTotalDeduplicated(), stats.getTotalPolled());
            }
        } catch (Exception e) {
            log.error("检查分布式队列状态失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时任务：清理过期的去重记录
     */
    @Scheduled(fixedRate = 300000) // 每5分钟清理一次
    public void cleanupExpiredRecords() {
        if (!distributedEnabled || !processorEnabled) {
            return;
        }

        try {
            distributedTaskQueueManager.cleanupExpiredDeduplicationRecords();
        } catch (Exception e) {
            log.error("清理过期去重记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 任务处理工作线程
     */
    private class TaskProcessorWorker implements Runnable {
        private final String workerName;

        public TaskProcessorWorker(String workerName) {
            this.workerName = workerName;
        }

        @Override
        public void run() {
            log.info("分布式任务处理工作线程启动: {}", workerName);
            
            while (running.get()) {
                try {
                    // 从分布式队列中获取任务
                    Task<LoginRecordRequest> task = distributedTaskQueueManager.pollTask(pollTimeout, TimeUnit.SECONDS);
                    
                    if (task != null) {
                        processTask(task);
                    }
                } catch (InterruptedException e) {
                    log.warn("工作线程被中断: {}", workerName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("工作线程处理任务时发生错误: {}, 错误: {}", workerName, e.getMessage(), e);
                    // 继续处理下一个任务
                }
            }
            
            log.info("分布式任务处理工作线程停止: {}", workerName);
        }

        @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
        )
        private void processTask(Task<LoginRecordRequest> task) throws Exception {
            try {
                LoginRecordRequest request = task.getData();
                loginRecordService.createLoginRecord(request);
                log.debug("分布式登录记录任务处理成功 - 工作线程: {}, taskId: {}, uid: {}", 
                         workerName, task.getTaskId(), request.uid());
            } catch (Exception e) {
                log.error("处理分布式登录记录任务失败 - 工作线程: {}, taskId: {}, uid: {}, 错误: {}", 
                         workerName, task.getTaskId(), task.getData().uid(), e.getMessage(), e);
                throw e; // 重新抛出异常以便重试机制处理
            }
        }
    }

    /**
     * 获取处理器状态
     */
    public String getProcessorStatus() {
        if (!distributedEnabled || !processorEnabled) {
            return "分布式任务处理器已禁用";
        }

        try {
            var stats = distributedTaskQueueManager.getQueueStats();
            String clusterInfo = distributedTaskQueueManager.getClusterInfo();
            boolean clusterHealthy = distributedTaskQueueManager.isClusterHealthy();
            
            return String.format(
                "分布式任务处理器状态:\n" +
                "- 运行状态: %s\n" +
                "- 工作线程数: %d\n" +
                "- 集群状态: %s\n" +
                "- 集群信息: %s\n" +
                "- 队列大小: %d\n" +
                "- 去重Map大小: %d\n" +
                "- 总提交任务数: %d\n" +
                "- 总去重任务数: %d\n" +
                "- 总处理任务数: %d",
                running.get() ? "运行中" : "已停止",
                processorThreads,
                clusterHealthy ? "健康" : "异常",
                clusterInfo,
                stats.getCurrentSize(),
                stats.getDeduplicationMapSize(),
                stats.getTotalOffered(),
                stats.getTotalDeduplicated(),
                stats.getTotalPolled()
            );
        } catch (Exception e) {
            log.error("获取处理器状态失败: {}", e.getMessage(), e);
            return "获取处理器状态失败: " + e.getMessage();
        }
    }
} 