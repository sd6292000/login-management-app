package com.wilsonkeh.loginmanagement.dto;

import com.wilsonkeh.loginmanagement.entity.UserSecurityAnalysis;

import java.time.LocalDateTime;

public record UserSecurityAnalysisResponse(
    String uid,
    LocalDateTime analysisDate,
    Integer totalLogins,
    Integer successfulLogins,
    Integer failedLogins,
    Integer uniqueIpAddresses,
    Integer uniqueDevices,
    Integer suspiciousActivities,
    Double avgRiskScore,
    Integer maxRiskScore,
    String loginMethodsUsed,
    String riskLevel,
    Boolean unusualLoginPatterns,
    Boolean geographicAnomalies,
    Boolean timeAnomalies,
    Boolean deviceAnomalies,
    String securityRecommendations,
    LocalDateTime lastLoginTime,
    LocalDateTime firstLoginTime,
    Double avgLoginIntervalHours
) {
    public static UserSecurityAnalysisResponse fromEntity(UserSecurityAnalysis entity) {
        return new UserSecurityAnalysisResponse(
            entity.getUid(),
            entity.getAnalysisDate(),
            entity.getTotalLogins(),
            entity.getSuccessfulLogins(),
            entity.getFailedLogins(),
            entity.getUniqueIpAddresses(),
            entity.getUniqueDevices(),
            entity.getSuspiciousActivities(),
            entity.getAvgRiskScore(),
            entity.getMaxRiskScore(),
            entity.getLoginMethodsUsed(),
            entity.getRiskLevel().name(),
            entity.getUnusualLoginPatterns(),
            entity.getGeographicAnomalies(),
            entity.getTimeAnomalies(),
            entity.getDeviceAnomalies(),
            entity.getSecurityRecommendations(),
            entity.getLastLoginTime(),
            entity.getFirstLoginTime(),
            entity.getAvgLoginIntervalHours()
        );
    }
} 