package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
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

    @PostMapping
    public ResponseEntity<ApiResponse<LoginRecordResponse>> createLoginRecord(@Valid @RequestBody LoginRecordRequest request) {
        try {
            LoginRecordResponse response = loginRecordService.createLoginRecord(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("登录记录创建成功", response));
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
} 