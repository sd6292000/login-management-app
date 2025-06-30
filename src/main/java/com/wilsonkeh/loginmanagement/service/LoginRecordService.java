package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LoginRecordService {

    LoginRecordResponse createLoginRecord(LoginRecordRequest request);

    Page<LoginRecordResponse> getUserRecentLoginRecords(String uid, Pageable pageable);

    Page<LoginRecordResponse> getMultipleUsersRecentLoginRecords(List<String> uids, Pageable pageable);

    UserSecurityAnalysisResponse getUserSecurityAnalysis(String uid);

    List<UserSecurityAnalysisResponse> getMultipleUsersSecurityAnalysis(List<String> uids);
} 