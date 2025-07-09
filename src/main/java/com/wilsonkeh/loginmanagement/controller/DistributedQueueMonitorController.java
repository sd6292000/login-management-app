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

import java.util.HashMap;
import java.util.Map;

/**
 * Distributed queue monitor controller
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
     * Get distributed queue status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<String>> getQueueStatus() {
        try {
            String status = distributedTaskQueueManager.getQueueStatusReport();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Distributed queue status retrieved successfully", status));
        } catch (Exception e) {
            log.error("Failed to get distributed queue status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get distributed queue status: " + e.getMessage(), null));
        }
    }

    /**
     * Get distributed task processor status
     */
    @GetMapping("/processor/status")
    public ResponseEntity<ApiResponse<String>> getProcessorStatus() {
        try {
            String status = distributedLoginRecordTaskProcessor.getProcessorStatus();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Distributed task processor status retrieved successfully", status));
        } catch (Exception e) {
            log.error("Failed to get distributed task processor status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get distributed task processor status: " + e.getMessage(), null));
        }
    }

    /**
     * Start distributed task processor
     */
    @PostMapping("/processor/start")
    public ResponseEntity<ApiResponse<String>> startProcessor() {
        try {
            distributedLoginRecordTaskProcessor.startProcessing();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Distributed task processor started successfully", "Processor started"));
        } catch (Exception e) {
            log.error("Failed to start distributed task processor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to start distributed task processor: " + e.getMessage(), null));
        }
    }

    /**
     * Stop distributed task processor
     */
    @PostMapping("/processor/stop")
    public ResponseEntity<ApiResponse<String>> stopProcessor() {
        try {
            distributedLoginRecordTaskProcessor.stopProcessing();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Distributed task processor stopped successfully", "Processor stopped"));
        } catch (Exception e) {
            log.error("Failed to stop distributed task processor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to stop distributed task processor: " + e.getMessage(), null));
        }
    }

    /**
     * Clean up expired deduplication records
     */
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> cleanupExpiredRecords() {
        try {
            distributedTaskQueueManager.cleanupExpiredDeduplicationRecords();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Expired deduplication records cleaned up successfully", "Cleanup complete"));
        } catch (Exception e) {
            log.error("Failed to clean up expired deduplication records: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to clean up expired deduplication records: " + e.getMessage(), null));
        }
    }

    /**
     * Get cluster information
     */
    @GetMapping("/cluster/info")
    public ResponseEntity<ApiResponse<String>> getClusterInfo() {
        try {
            String clusterInfo = distributedTaskQueueManager.getClusterInfo();
            boolean clusterHealthy = distributedTaskQueueManager.isClusterHealthy();
            
            String info = String.format("Cluster Info:\n%s\nCluster Health Status: %s", 
                    clusterInfo, clusterHealthy ? "Healthy" : "Unhealthy");
            
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cluster information retrieved successfully", info));
        } catch (Exception e) {
            log.error("Failed to get cluster information: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get cluster information: " + e.getMessage(), null));
        }
    }

    /**
     * Get queue statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getQueueStats() {
        try {
            var stats = distributedTaskQueueManager.getQueueStats();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Queue statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Failed to get queue statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get queue statistics: " + e.getMessage(), null));
        }
    }

    /**
     * Clear distributed queue
     */
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<String>> clearQueue() {
        try {
            distributedTaskQueueManager.getDistributedQueue().clear();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Distributed queue cleared successfully", "Queue cleared"));
        } catch (Exception e) {
            log.error("Failed to clear distributed queue: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to clear distributed queue: " + e.getMessage(), null));
        }
    }

    /**
     * Get network fault tolerance health report
     */
    @GetMapping("/network/health")
    public ResponseEntity<ApiResponse<String>> getNetworkHealth() {
        try {
            String healthReport = networkFaultToleranceManager.getClusterHealthReport();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Network health report retrieved successfully", healthReport));
        } catch (Exception e) {
            log.error("Failed to get network health report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get network health report: " + e.getMessage(), null));
        }
    }

    /**
     * Manually trigger network reconnection
     */
    @PostMapping("/network/reconnect")
    public ResponseEntity<ApiResponse<String>> triggerNetworkReconnect() {
        try {
            networkFaultToleranceManager.triggerReconnection();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Network reconnection triggered", "Reconnection operation is executing in background"));
        } catch (Exception e) {
            log.error("Failed to trigger network reconnection: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to trigger network reconnection: " + e.getMessage(), null));
        }
    }

    /**
     * Reset network health status
     */
    @PostMapping("/network/reset")
    public ResponseEntity<ApiResponse<String>> resetNetworkHealth() {
        try {
            networkFaultToleranceManager.resetHealthStatus();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Network health status reset successfully", "All health status counters have been cleared"));
        } catch (Exception e) {
            log.error("Failed to reset network health status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to reset network health status: " + e.getMessage(), null));
        }
    }

    /**
     * Get network fault tolerance configuration information
     */
    @GetMapping("/network/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("faultToleranceEnabled", networkFaultToleranceManager.isClusterHealthy());
            config.put("clusterHealthy", networkFaultToleranceManager.isClusterHealthy());
            
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Network configuration information retrieved successfully", config));
        } catch (Exception e) {
            log.error("Failed to get network configuration information: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get network configuration information: " + e.getMessage(), null));
        }
    }
} 