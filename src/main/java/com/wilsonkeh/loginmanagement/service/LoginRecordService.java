package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LoginRecordService {

    LoginRecordResponse createLoginRecord(LoginRecordRequest request);

    /**
     * 批量创建登录记录
     * @param requests 登录记录请求列表
     * @return 创建的登录记录响应列表
     */
    List<LoginRecordResponse> createLoginRecordsBatch(List<LoginRecordRequest> requests);

    Page<LoginRecordResponse> getUserRecentLoginRecords(String uid, Pageable pageable);

    Page<LoginRecordResponse> getMultipleUsersRecentLoginRecords(List<String> uids, Pageable pageable);

    UserSecurityAnalysisResponse getUserSecurityAnalysis(String uid);

    List<UserSecurityAnalysisResponse> getMultipleUsersSecurityAnalysis(List<String> uids);
} 