package com.wilsonkeh.loginmanagement.dto;

import com.wilsonkeh.loginmanagement.entity.UserLoginRecord;

import java.time.LocalDateTime;

public record LoginRecordResponse(
    Long id,
    String uid,
    String ipAddress,
    LocalDateTime loginTime,
    String loginMethod,
    String loginStatus,
    String deviceType,
    String locationCountry,
    String locationCity,
    Boolean isSuspicious,
    Integer riskScore,
    LocalDateTime createdAt
) {
    public static LoginRecordResponse fromEntity(UserLoginRecord entity) {
        return new LoginRecordResponse(
            entity.getId(),
            entity.getUid(),
            entity.getIpAddress(),
            entity.getLoginTime(),
            entity.getLoginMethod().name(),
            entity.getLoginStatus().name(),
            entity.getDeviceType(),
            entity.getLocationCountry(),
            entity.getLocationCity(),
            entity.getIsSuspicious(),
            entity.getRiskScore(),
            entity.getCreatedAt()
        );
    }
} 