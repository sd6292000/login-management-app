package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
import com.wilsonkeh.loginmanagement.service.LoginRecordQueueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/login-records")
public class LoginRecordController {

    @Autowired
    private LoginRecordService loginRecordService;
    
    @Autowired
    private LoginRecordQueueService loginRecordQueueService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createLoginRecord(@Valid @RequestBody LoginRecordRequest request) {
        try {
            // 将请求加入队列，立即返回成功响应
            boolean success = loginRecordQueueService.enqueueLoginRecord(request);
            if (success) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.success("登录记录请求已接受，正在处理中", null));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("队列已满，请稍后重试"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/user/{uid}")
    public ResponseEntity<ApiResponse<Page<LoginRecordResponse>>> getUserRecentLoginRecords(
            @PathVariable String uid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<LoginRecordResponse> records = loginRecordService.getUserRecentLoginRecords(uid, pageable);
            return ResponseEntity.ok(ApiResponse.success("获取用户登录记录成功", records));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<LoginRecordResponse>>> getMultipleUsersRecentLoginRecords(
            @RequestParam List<String> uids,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<LoginRecordResponse> records = loginRecordService.getMultipleUsersRecentLoginRecords(uids, pageable);
            return ResponseEntity.ok(ApiResponse.success("获取多用户登录记录成功", records));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/security-analysis/{uid}")
    public ResponseEntity<ApiResponse<UserSecurityAnalysisResponse>> getUserSecurityAnalysis(@PathVariable String uid) {
        try {
            UserSecurityAnalysisResponse analysis = loginRecordService.getUserSecurityAnalysis(uid);
            return ResponseEntity.ok(ApiResponse.success("获取用户安全分析成功", analysis));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/security-analysis/users")
    public ResponseEntity<ApiResponse<List<UserSecurityAnalysisResponse>>> getMultipleUsersSecurityAnalysis(
            @RequestParam List<String> uids) {
        try {
            List<UserSecurityAnalysisResponse> analyses = loginRecordService.getMultipleUsersSecurityAnalysis(uids);
            return ResponseEntity.ok(ApiResponse.success("获取多用户安全分析成功", analyses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取队列状态信息（仅用于监控，生产环境建议添加权限控制）
     */
    @GetMapping("/queue/status")
    public ResponseEntity<ApiResponse<Object>> getQueueStatus() {
        try {
            var status = new java.util.HashMap<String, Object>();
            status.put("queueSize", loginRecordQueueService.getQueueSize());
            status.put("deduplicatedQueueSize", loginRecordQueueService.getDeduplicatedQueueSize());
            status.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(ApiResponse.success("获取队列状态成功", status));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取详细的队列统计信息（仅用于监控，生产环境建议添加权限控制）
     */
    @GetMapping("/queue/statistics")
    public ResponseEntity<ApiResponse<String>> getQueueStatistics() {
        try {
            String statistics = loginRecordQueueService.getQueueStatistics();
            return ResponseEntity.ok(ApiResponse.success("获取队列统计成功", statistics));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
} 