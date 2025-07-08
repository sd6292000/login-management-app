package com.wilsonkeh.loginmanagement.service.impl;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.queue.LoginRecordTask;
import com.wilsonkeh.loginmanagement.queue.TaskQueueManager;
import com.wilsonkeh.loginmanagement.service.LoginRecordQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoginRecordQueueServiceImpl implements LoginRecordQueueService {

    @Autowired
    private TaskQueueManager taskQueueManager;

    private static final String QUEUE_NAME = "login-record-queue";

    @Override
    public boolean enqueueLoginRecord(LoginRecordRequest request) {
        return enqueueLoginRecord(request, 0);
    }

    @Override
    public boolean enqueueLoginRecord(LoginRecordRequest request, int priority) {
        try {
            LoginRecordTask task = new LoginRecordTask(request, priority);
            var queue = taskQueueManager.getQueue(QUEUE_NAME);
            boolean success = queue.enqueueTask(task);
            
            if (success) {
                log.debug("登录记录请求已加入队列，uid: {}, priority: {}", request.uid(), priority);
            } else {
                log.warn("登录记录请求加入队列失败，uid: {}", request.uid());
            }
            
            return success;
        } catch (Exception e) {
            log.error("加入登录记录队列时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int getQueueSize() {
        var queue = taskQueueManager.getQueue(QUEUE_NAME);
        return queue.getQueueSize();
    }

    @Override
    public int getDeduplicatedQueueSize() {
        var queue = taskQueueManager.getQueue(QUEUE_NAME);
        return queue.getDeduplicationMapSize();
    }

    @Override
    public String getQueueStatistics() {
        var queue = taskQueueManager.getQueue(QUEUE_NAME);
        var stats = queue.getStatistics();
        return String.format("队列统计 - 队列名称: %s, 队列大小: %d, 去重映射: %d, 总入队: %d, 总去重: %d, 总处理: %d, 总失败: %d, 最后处理时间: %d",
                stats.getQueueName(), stats.getQueueSize(), stats.getDeduplicationMapSize(),
                stats.getTotalEnqueued(), stats.getTotalDeduplicated(), 
                stats.getTotalProcessed(), stats.getTotalFailed(), stats.getLastProcessTime());
    }
}


} 