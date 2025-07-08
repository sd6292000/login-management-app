package com.wilsonkeh.loginmanagement.service.impl;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.queue.GenericTaskQueue;
import com.wilsonkeh.loginmanagement.queue.LoginRecordTask;
import com.wilsonkeh.loginmanagement.queue.TaskQueueManager;
import com.wilsonkeh.loginmanagement.queue.DistributedTaskQueueManager;
import com.wilsonkeh.loginmanagement.service.LoginRecordQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoginRecordQueueServiceImpl implements LoginRecordQueueService {

    @Autowired
    private TaskQueueManager taskQueueManager;

    @Autowired
    private DistributedTaskQueueManager<LoginRecordRequest> distributedTaskQueueManager;

    @Value("${app.queue.distributed.enabled:true}")
    private boolean distributedEnabled;

    private static final String QUEUE_NAME = "login-record-queue";

    @Override
    public boolean enqueueLoginRecord(LoginRecordRequest request) {
        return enqueueLoginRecord(request, 0);
    }

    @Override
    public boolean enqueueLoginRecord(LoginRecordRequest request, int priority) {
        try {
            LoginRecordTask task = new LoginRecordTask(request, priority);
            
            boolean success;
            if (distributedEnabled) {
                // 使用分布式队列
                success = distributedTaskQueueManager.offerTask(task);
                if (success) {
                    log.debug("登录记录请求已加入分布式队列，uid: {}, priority: {}", request.uid(), priority);
                } else {
                    log.warn("登录记录请求加入分布式队列失败，uid: {}", request.uid());
                }
            } else {
                // 使用本地队列
                GenericTaskQueue<LoginRecordRequest> queue = taskQueueManager.getQueue(QUEUE_NAME);
                success = queue.enqueueTask(task);
                if (success) {
                    log.debug("登录记录请求已加入本地队列，uid: {}, priority: {}", request.uid(), priority);
                } else {
                    log.warn("登录记录请求加入本地队列失败，uid: {}", request.uid());
                }
            }

            return success;
        } catch (Exception e) {
            log.error("加入登录记录队列时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int getQueueSize() {
        if (distributedEnabled) {
            return distributedTaskQueueManager.getDistributedQueue().size();
        } else {
            var queue = taskQueueManager.getQueue(QUEUE_NAME);
            return queue.getQueueSize();
        }
    }

    @Override
    public int getDeduplicatedQueueSize() {
        if (distributedEnabled) {
            var stats = distributedTaskQueueManager.getQueueStats();
            return stats.getDeduplicationMapSize();
        } else {
            var queue = taskQueueManager.getQueue(QUEUE_NAME);
            return queue.getDeduplicationMapSize();
        }
    }

    @Override
    public String getQueueStatistics() {
        if (distributedEnabled) {
            return distributedTaskQueueManager.getQueueStatusReport();
        } else {
            var queue = taskQueueManager.getQueue(QUEUE_NAME);
            var stats = queue.getStatistics();
            return String.format("本地队列统计 - 队列名称: %s, 队列大小: %d, 去重映射: %d, 总入队: %d, 总去重: %d, 总处理: %d, 总失败: %d, 最后处理时间: %d",
                    stats.getQueueName(), stats.getQueueSize(), stats.getDeduplicationMapSize(),
                    stats.getTotalEnqueued(), stats.getTotalDeduplicated(),
                    stats.getTotalProcessed(), stats.getTotalFailed(), stats.getLastProcessTime());
        }
    }
}


