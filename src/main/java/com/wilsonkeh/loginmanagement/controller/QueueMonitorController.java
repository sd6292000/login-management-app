package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import com.wilsonkeh.loginmanagement.queue.TaskQueueManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 队列监控控制器
 * 注意：生产环境建议添加权限控制
 */
@RestController
@RequestMapping("/api/queue")
public class QueueMonitorController {

    @Autowired
    private TaskQueueManager taskQueueManager;

    /**
     * 获取所有队列的统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, TaskQueueManager.QueueStatistics>>> getAllQueueStatistics() {
        try {
            Map<String, TaskQueueManager.QueueStatistics> statistics = taskQueueManager.getAllQueueStatistics();
            return ResponseEntity.ok(ApiResponse.success("获取所有队列统计成功", statistics));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取指定队列的统计信息
     */
    @GetMapping("/statistics/{queueName}")
    public ResponseEntity<ApiResponse<TaskQueueManager.QueueStatistics>> getQueueStatistics(@PathVariable String queueName) {
        try {
            TaskQueueManager.QueueStatistics statistics = taskQueueManager.getQueueStatistics(queueName);
            if (statistics != null) {
                return ResponseEntity.ok(ApiResponse.success("获取队列统计成功", statistics));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取所有队列名称
     */
    @GetMapping("/names")
    public ResponseEntity<ApiResponse<java.util.Set<String>>> getQueueNames() {
        try {
            java.util.Set<String> queueNames = taskQueueManager.getQueueNames();
            return ResponseEntity.ok(ApiResponse.success("获取队列名称列表成功", queueNames));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取队列总数
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Integer>> getQueueCount() {
        try {
            int count = taskQueueManager.getQueueCount();
            return ResponseEntity.ok(ApiResponse.success("获取队列总数成功", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 检查队列是否存在
     */
    @GetMapping("/exists/{queueName}")
    public ResponseEntity<ApiResponse<Boolean>> queueExists(@PathVariable String queueName) {
        try {
            boolean exists = taskQueueManager.queueExists(queueName);
            return ResponseEntity.ok(ApiResponse.success("检查队列存在性成功", exists));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 清空指定队列（谨慎使用）
     */
    @DeleteMapping("/clear/{queueName}")
    public ResponseEntity<ApiResponse<Void>> clearQueue(@PathVariable String queueName) {
        try {
            taskQueueManager.clearQueue(queueName);
            return ResponseEntity.ok(ApiResponse.success("队列清空成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取队列健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueueHealth() {
        try {
            Map<String, Object> health = new java.util.HashMap<>();
            health.put("totalQueues", taskQueueManager.getQueueCount());
            health.put("queueNames", taskQueueManager.getQueueNames());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            // 检查每个队列的状态
            Map<String, Object> queueHealth = new java.util.HashMap<>();
            taskQueueManager.getQueueNames().forEach(queueName -> {
                TaskQueueManager.QueueStatistics stats = taskQueueManager.getQueueStatistics(queueName);
                if (stats != null) {
                    Map<String, Object> queueStatus = new java.util.HashMap<>();
                    queueStatus.put("queueSize", stats.getQueueSize());
                    queueStatus.put("isHealthy", stats.getQueueSize() < 1000); // 简单健康检查
                    queueStatus.put("lastProcessTime", stats.getLastProcessTime());
                    queueHealth.put(queueName, queueStatus);
                }
            });
            health.put("queueStatus", queueHealth);
            
            return ResponseEntity.ok(ApiResponse.success("获取队列健康状态成功", health));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
} 