package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import com.wilsonkeh.loginmanagement.queue.DistributedLoginRecordTaskProcessor;
import com.wilsonkeh.loginmanagement.queue.DistributedTaskQueueManager;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.config.NetworkFaultToleranceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 分布式队列监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/distributed-queue")
public class DistributedQueueMonitorController {

    @Autowired
    private DistributedTaskQueueManager<LoginRecordRequest> distributedTaskQueueManager;

    @Autowired
    private DistributedLoginRecordTaskProcessor distributedLoginRecordTaskProcessor;

    @Autowired
    private NetworkFaultToleranceManager networkFaultToleranceManager;

    /**
     * 获取分布式队列状态
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<String>> getQueueStatus() {
        try {
            String status = distributedTaskQueueManager.getQueueStatusReport();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "获取分布式队列状态成功", status));
        } catch (Exception e) {
            log.error("获取分布式队列状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "获取分布式队列状态失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取分布式任务处理器状态
     */
    @GetMapping("/processor/status")
    public ResponseEntity<ApiResponse<String>> getProcessorStatus() {
        try {
            String status = distributedLoginRecordTaskProcessor.getProcessorStatus();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "获取分布式任务处理器状态成功", status));
        } catch (Exception e) {
            log.error("获取分布式任务处理器状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "获取分布式任务处理器状态失败: " + e.getMessage(), null));
        }
    }

    /**
     * 启动分布式任务处理器
     */
    @PostMapping("/processor/start")
    public ResponseEntity<ApiResponse<String>> startProcessor() {
        try {
            distributedLoginRecordTaskProcessor.startProcessing();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "分布式任务处理器启动成功", "处理器已启动"));
        } catch (Exception e) {
            log.error("启动分布式任务处理器失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "启动分布式任务处理器失败: " + e.getMessage(), null));
        }
    }

    /**
     * 停止分布式任务处理器
     */
    @PostMapping("/processor/stop")
    public ResponseEntity<ApiResponse<String>> stopProcessor() {
        try {
            distributedLoginRecordTaskProcessor.stopProcessing();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "分布式任务处理器停止成功", "处理器已停止"));
        } catch (Exception e) {
            log.error("停止分布式任务处理器失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "停止分布式任务处理器失败: " + e.getMessage(), null));
        }
    }

    /**
     * 清理过期的去重记录
     */
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> cleanupExpiredRecords() {
        try {
            distributedTaskQueueManager.cleanupExpiredDeduplicationRecords();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "清理过期去重记录成功", "清理完成"));
        } catch (Exception e) {
            log.error("清理过期去重记录失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "清理过期去重记录失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取集群信息
     */
    @GetMapping("/cluster/info")
    public ResponseEntity<ApiResponse<String>> getClusterInfo() {
        try {
            String clusterInfo = distributedTaskQueueManager.getClusterInfo();
            boolean clusterHealthy = distributedTaskQueueManager.isClusterHealthy();
            
            String info = String.format("集群信息:\n%s\n集群健康状态: %s", 
                    clusterInfo, clusterHealthy ? "健康" : "异常");
            
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "获取集群信息成功", info));
        } catch (Exception e) {
            log.error("获取集群信息失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "获取集群信息失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取队列统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getQueueStats() {
        try {
            var stats = distributedTaskQueueManager.getQueueStats();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "获取队列统计信息成功", stats));
        } catch (Exception e) {
            log.error("获取队列统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "获取队列统计信息失败: " + e.getMessage(), null));
        }
    }

    /**
     * 清空分布式队列
     */
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<String>> clearQueue() {
        try {
            distributedTaskQueueManager.getDistributedQueue().clear();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "清空分布式队列成功", "队列已清空"));
        } catch (Exception e) {
            log.error("清空分布式队列失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "清空分布式队列失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取网络容错健康报告
     */
    @GetMapping("/network/health")
    public ResponseEntity<ApiResponse<String>> getNetworkHealth() {
        try {
            String healthReport = networkFaultToleranceManager.getClusterHealthReport();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "获取网络健康报告成功", healthReport));
        } catch (Exception e) {
            log.error("获取网络健康报告失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "获取网络健康报告失败: " + e.getMessage(), null));
        }
    }

    /**
     * 手动触发重连
     */
    @PostMapping("/network/reconnect")
    public ResponseEntity<ApiResponse<String>> triggerReconnection() {
        try {
            networkFaultToleranceManager.triggerReconnection();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "触发重连成功", "重连操作已启动"));
        } catch (Exception e) {
            log.error("触发重连失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "触发重连失败: " + e.getMessage(), null));
        }
    }

    /**
     * 重置网络健康状态
     */
    @PostMapping("/network/reset")
    public ResponseEntity<ApiResponse<String>> resetNetworkHealth() {
        try {
            networkFaultToleranceManager.resetHealthStatus();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "重置网络健康状态成功", "健康状态已重置"));
        } catch (Exception e) {
            log.error("重置网络健康状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "重置网络健康状态失败: " + e.getMessage(), null));
        }
    }
} 