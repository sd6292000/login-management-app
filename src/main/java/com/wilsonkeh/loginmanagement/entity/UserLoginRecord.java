package com.wilsonkeh.loginmanagement.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_login_records", indexes = {
    @Index(name = "idx_uid", columnList = "uid"),
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_login_time", columnList = "login_time"),
    @Index(name = "idx_ip_address", columnList = "ip_address"),
    @Index(name = "idx_login_method", columnList = "login_method"),
    @Index(name = "idx_trace_id", columnList = "trace_id")
})
@Data
public class UserLoginRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_login_record_seq")
    @SequenceGenerator(name = "user_login_record_seq", sequenceName = "user_login_record_id_seq", allocationSize = 1)
    private Long id;

    // 替代方案：使用UUID作为主键（取消注释下面的代码并注释上面的ID字段）
    /*
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    */

    @NotBlank(message = "User ID should not be empty")
    @Column(name = "uid", nullable = false, length = 100)
    private String uid;

    @NotBlank(message = "用户名不能为空")
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @NotBlank(message = "IP地址不能为空")
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @NotNull(message = "登录时间不能为空")
    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @NotBlank(message = "登录方式不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", nullable = false, length = 20)
    private LoginMethod loginMethod;

    @Column(name = "password_strength", length = 20)
    private String passwordStrength;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @NotBlank(message = "Trace ID不能为空")
    @Column(name = "trace_id", nullable = false, length = 100, unique = true)
    private String traceId;

    @Column(name = "fingerprint", length = 255)
    private String fingerprint;

    @Column(name = "login_status", length = 20)
    @Enumerated(EnumType.STRING)
    private LoginStatus loginStatus = LoginStatus.SUCCESS;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser_info", length = 100)
    private String browserInfo;

    @Column(name = "os_info", length = 100)
    private String osInfo;

    @Column(name = "location_country", length = 50)
    private String locationCountry;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Column(name = "is_suspicious", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isSuspicious = false;

    @Column(name = "risk_score", columnDefinition = "INTEGER DEFAULT 0")
    private Integer riskScore = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum LoginMethod {
        PASSWORD, DUO, NEVIS, SECURID, SSO, OAUTH, API_KEY
    }

    public enum LoginStatus {
        SUCCESS, FAILED, BLOCKED, PENDING_VERIFICATION
    }
} 