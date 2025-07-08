package com.wilsonkeh.loginmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record LoginRecordRequest(
    @NotBlank(message = "用户ID不能为空") String uid,
    @NotBlank(message = "用户名不能为空") String username,
    @NotBlank(message = "IP地址不能为空") String ipAddress,
    @NotNull(message = "登录时间不能为空") LocalDateTime loginTime,
    @NotBlank(message = "登录方式不能为空") String loginMethod,
    String passwordStrength,
    String userAgent,
    @NotBlank(message = "Trace ID不能为空") String traceId,
    String fingerprint,
    String sessionId,
    String deviceType,
    String browserInfo,
    String osInfo,
    String locationCountry,
    String locationCity
) {
    public LoginRecordRequest {
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("IP地址不能为空");
        }
        if (loginTime == null) {
            throw new IllegalArgumentException("登录时间不能为空");
        }
        if (loginMethod == null || loginMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("登录方式不能为空");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Trace ID不能为空");
        }
    }
} 