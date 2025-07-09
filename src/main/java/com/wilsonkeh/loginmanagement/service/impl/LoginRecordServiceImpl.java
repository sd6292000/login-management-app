package com.wilsonkeh.loginmanagement.service.impl;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;
import com.wilsonkeh.loginmanagement.entity.UserSecurityAnalysis;
import com.wilsonkeh.loginmanagement.repository.UserLoginRecordRepository;
import com.wilsonkeh.loginmanagement.repository.UserSecurityAnalysisRepository;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

@Service
public class LoginRecordServiceImpl implements LoginRecordService {

    @Autowired
    private UserLoginRecordRepository loginRecordRepository;

    @Autowired
    private UserSecurityAnalysisRepository securityAnalysisRepository;

    @Override
    @Transactional
    @CachePut(value = "login-records", key = "#result.id")
    @CacheEvict(value = {"user-login-records", "ip-login-records", "recent-login-records"}, allEntries = true)
    public LoginRecordResponse createLoginRecord(LoginRecordRequest request) {
        // 检查traceId是否已存在
        loginRecordRepository.findByTraceId(request.traceId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Trace ID已存在: " + request.traceId());
                });

        UserLoginRecord loginRecord = new UserLoginRecord();
        loginRecord.setUid(request.uid());
        loginRecord.setUsername(request.username());
        loginRecord.setIpAddress(request.ipAddress());
        loginRecord.setLoginTime(request.loginTime());
        loginRecord.setLoginMethod(UserLoginRecord.LoginMethod.valueOf(request.loginMethod().toUpperCase()));
        loginRecord.setPasswordStrength(request.passwordStrength());
        loginRecord.setUserAgent(request.userAgent());
        loginRecord.setTraceId(request.traceId());
        loginRecord.setFingerprint(request.fingerprint());
        loginRecord.setSessionId(request.sessionId());
        loginRecord.setDeviceType(request.deviceType());
        loginRecord.setBrowserInfo(request.browserInfo());
        loginRecord.setOsInfo(request.osInfo());
        loginRecord.setLocationCountry(request.locationCountry());
        loginRecord.setLocationCity(request.locationCity());

        // 计算风险评分
        int riskScore = calculateRiskScore(loginRecord);
        loginRecord.setRiskScore(riskScore);
        loginRecord.setIsSuspicious(riskScore > 70);

        UserLoginRecord savedRecord = loginRecordRepository.save(loginRecord);

        // 异步更新安全分析（这里简化处理，实际应该用异步任务）
        updateUserSecurityAnalysis(request.uid());

        return LoginRecordResponse.fromEntity(savedRecord);
    }

    @Override
    public Page<LoginRecordResponse> getUserRecentLoginRecords(String uid, Pageable pageable) {
        Page<UserLoginRecord> records = loginRecordRepository.findByUidOrderByLoginTimeDesc(uid, pageable);
        return records.map(LoginRecordResponse::fromEntity);
    }

    @Override
    public Page<LoginRecordResponse> getMultipleUsersRecentLoginRecords(List<String> uids, Pageable pageable) {
        Page<UserLoginRecord> records = loginRecordRepository.findByUidsOrderByLoginTimeDesc(uids, pageable);
        return records.map(LoginRecordResponse::fromEntity);
    }

    @Override
    @Cacheable(value = "user-security-analysis", key = "#uid", unless = "#result == null")
    public UserSecurityAnalysisResponse getUserSecurityAnalysis(String uid) {
        UserSecurityAnalysis analysis = securityAnalysisRepository.findLatestAnalysisByUid(uid)
                .orElseGet(() -> generateSecurityAnalysis(uid));
        return UserSecurityAnalysisResponse.fromEntity(analysis);
    }

    @Override
    public List<UserSecurityAnalysisResponse> getMultipleUsersSecurityAnalysis(List<String> uids) {
        List<UserSecurityAnalysis> analyses = securityAnalysisRepository.findByUidsOrderByAnalysisDateDesc(uids);
        return analyses.stream()
                .map(UserSecurityAnalysisResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private int calculateRiskScore(UserLoginRecord loginRecord) {
        int score = 0;

        // 基于IP地址的风险评分
        if (isKnownMaliciousIp(loginRecord.getIpAddress())) {
            score += 50;
        }

        // 基于登录时间的风险评分
        if (isUnusualLoginTime(loginRecord.getLoginTime())) {
            score += 20;
        }

        // 基于设备指纹的风险评分
        if (isNewDevice(loginRecord.getUid(), loginRecord.getFingerprint())) {
            score += 15;
        }

        // 基于地理位置的风险评分
        if (isUnusualLocation(loginRecord.getUid(), loginRecord.getLocationCountry(), loginRecord.getLocationCity())) {
            score += 25;
        }

        // 基于登录方式的风险评分
        if (UserLoginRecord.LoginMethod.PASSWORD.equals(loginRecord.getLoginMethod())) {
            score += 5;
        }

        return Math.min(score, 100);
    }

    private boolean isKnownMaliciousIp(String ipAddress) {
        // 这里应该调用IP黑名单服务，简化处理
        return false;
    }

    private boolean isUnusualLoginTime(LocalDateTime loginTime) {
        int hour = loginTime.getHour();
        return hour < 6 || hour > 23;
    }

    private boolean isNewDevice(String uid, String fingerprint) {
        if (fingerprint == null) return false;
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<UserLoginRecord> recentLogins = loginRecordRepository
                .findByUidAndLoginTimeAfterOrderByLoginTimeDesc(uid, thirtyDaysAgo);
        
        return recentLogins.stream()
                .noneMatch(login -> fingerprint.equals(login.getFingerprint()));
    }

    private boolean isUnusualLocation(String uid, String country, String city) {
        if (country == null) return false;
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<UserLoginRecord> recentLogins = loginRecordRepository
                .findByUidAndLoginTimeAfterOrderByLoginTimeDesc(uid, thirtyDaysAgo);
        
        return recentLogins.stream()
                .noneMatch(login -> country.equals(login.getLocationCountry()));
    }

    @Transactional
    @CachePut(value = "user-security-analysis", key = "#uid")
    @CacheEvict(value = "user-security-analysis", key = "#uid")
    private void updateUserSecurityAnalysis(String uid) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        UserSecurityAnalysis analysis = new UserSecurityAnalysis();
        analysis.setUid(uid);
        analysis.setAnalysisDate(LocalDateTime.now());
        
        // 统计登录数据
        Long totalLogins = loginRecordRepository.countByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        Long uniqueIps = loginRecordRepository.countUniqueIpAddressesByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        Long uniqueDevices = loginRecordRepository.countUniqueDevicesByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        Long suspiciousActivities = loginRecordRepository.countSuspiciousActivitiesByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        
        analysis.setTotalLogins(totalLogins.intValue());
        analysis.setUniqueIpAddresses(uniqueIps.intValue());
        analysis.setUniqueDevices(uniqueDevices.intValue());
        analysis.setSuspiciousActivities(suspiciousActivities.intValue());
        
        // 计算风险评分
        Double avgRiskScore = loginRecordRepository.getAverageRiskScoreByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        Integer maxRiskScore = loginRecordRepository.getMaxRiskScoreByUidAndLoginTimeAfter(uid, thirtyDaysAgo);
        
        analysis.setAvgRiskScore(avgRiskScore != null ? avgRiskScore : 0.0);
        analysis.setMaxRiskScore(maxRiskScore != null ? maxRiskScore : 0);
        
        // 设置风险等级
        if (maxRiskScore != null && maxRiskScore > 80) {
            analysis.setRiskLevel(UserSecurityAnalysis.RiskLevel.CRITICAL);
        } else if (maxRiskScore != null && maxRiskScore > 60) {
            analysis.setRiskLevel(UserSecurityAnalysis.RiskLevel.HIGH);
        } else if (maxRiskScore != null && maxRiskScore > 30) {
            analysis.setRiskLevel(UserSecurityAnalysis.RiskLevel.MEDIUM);
        } else {
            analysis.setRiskLevel(UserSecurityAnalysis.RiskLevel.LOW);
        }
        
        // 获取最新和首次登录时间
        loginRecordRepository.findLatestLoginByUid(uid).ifPresent(login -> {
            analysis.setLastLoginTime(login.getLoginTime());
        });
        
        loginRecordRepository.findFirstLoginByUid(uid).ifPresent(login -> {
            analysis.setFirstLoginTime(login.getLoginTime());
        });
        
        securityAnalysisRepository.save(analysis);
    }

    private UserSecurityAnalysis generateSecurityAnalysis(String uid) {
        updateUserSecurityAnalysis(uid);
        return securityAnalysisRepository.findLatestAnalysisByUid(uid)
                .orElseThrow(() -> new RuntimeException("无法生成安全分析数据"));
    }
} 