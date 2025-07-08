package com.wilsonkeh.loginmanagement.example;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;
import com.wilsonkeh.loginmanagement.queue.LoginRecordTask;
import com.wilsonkeh.loginmanagement.queue.TaskQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 队列使用示例
 * 展示如何使用通用队列框架
 */
@Slf4j
@Component
public class QueueUsageExample {

    @Autowired
    private TaskQueueManager taskQueueManager;

    /**
     * 示例：使用登录记录队列
     */
    public void loginRecordQueueExample() {
        // 获取登录记录队列
        var loginQueue = taskQueueManager.getQueue("login-record-queue");

        // 创建登录记录请求
        LoginRecordRequest request = new LoginRecordRequest(
            "user123", "john.doe", "192.168.1.100",
            LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD,
            "STRONG", "Mozilla/5.0", "trace-001", "fingerprint-001"
        );

        // 创建任务并加入队列
        LoginRecordTask task = new LoginRecordTask(request, 0);
        boolean success = loginQueue.enqueueTask(task);

        if (success) {
            log.info("登录记录任务已加入队列");
        } else {
            log.warn("登录记录任务加入队列失败");
        }
    }

    /**
     * 示例：使用高优先级队列
     */
    public void highPriorityQueueExample() {
        // 获取高优先级队列
        var highPriorityQueue = taskQueueManager.getQueue("high-priority-queue");

        // 创建高优先级任务
        LoginRecordRequest request = new LoginRecordRequest(
            "admin001", "admin", "10.0.0.1",
            LocalDateTime.now(), UserLoginRecord.LoginMethod.DUO,
            "N/A", "Admin Client", "trace-admin-001", "fingerprint-admin"
        );

        // 创建高优先级任务（优先级为-1，数字越小优先级越高）
        LoginRecordTask task = new LoginRecordTask(request, -1);
        boolean success = highPriorityQueue.enqueueTask(task);

        if (success) {
            log.info("高优先级任务已加入队列");
        } else {
            log.warn("高优先级任务加入队列失败");
        }
    }

    /**
     * 示例：批量提交任务
     */
    public void batchSubmitExample() {
        var queue = taskQueueManager.getQueue("login-record-queue");
        int successCount = 0;
        int failCount = 0;

        // 批量提交100个任务
        for (int i = 0; i < 100; i++) {
            LoginRecordRequest request = new LoginRecordRequest(
                "user" + i, "user" + i, "192.168.1." + (i % 255),
                LocalDateTime.now(), UserLoginRecord.LoginMethod.PASSWORD,
                "STRONG", "Test Client", "trace-batch-" + i, "fingerprint-" + i
            );

            LoginRecordTask task = new LoginRecordTask(request, i % 3); // 不同优先级
            boolean success = queue.enqueueTask(task);

            if (success) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("批量提交完成 - 成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 示例：监控队列状态
     */
    public void monitorQueueExample() {
        // 获取所有队列统计信息
        var allStats = taskQueueManager.getAllQueueStatistics();
        log.info("所有队列统计信息: {}", allStats);

        // 获取特定队列统计信息
        var loginStats = taskQueueManager.getQueueStatistics("login-record-queue");
        if (loginStats != null) {
            log.info("登录记录队列统计: {}", loginStats);
        }

        // 检查队列健康状态
        var queueNames = taskQueueManager.getQueueNames();
        log.info("当前活跃队列: {}", queueNames);

        queueNames.forEach(queueName -> {
            var stats = taskQueueManager.getQueueStatistics(queueName);
            if (stats != null) {
                log.info("队列 {} - 大小: {}, 处理: {}, 失败: {}", 
                         queueName, stats.getQueueSize(), 
                         stats.getTotalProcessed(), stats.getTotalFailed());
            }
        });
    }

    /**
     * 示例：队列管理操作
     */
    public void queueManagementExample() {
        // 检查队列是否存在
        boolean exists = taskQueueManager.queueExists("login-record-queue");
        log.info("登录记录队列是否存在: {}", exists);

        // 获取队列总数
        int count = taskQueueManager.getQueueCount();
        log.info("当前队列总数: {}", count);

        // 清空队列（谨慎使用）
        // taskQueueManager.clearQueue("login-record-queue");
        // log.info("队列已清空");
    }
} 