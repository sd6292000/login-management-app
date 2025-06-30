package com.wilsonkeh.loginmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_security_analysis", indexes = {
    @Index(name = "idx_uid", columnList = "uid"),
    @Index(name = "idx_analysis_date", columnList = "analysis_date"),
    @Index(name = "idx_risk_level", columnList = "risk_level")
})
@Data
public class UserSecurityAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, length = 100)
    private String uid;

    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;

    @Column(name = "total_logins", columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalLogins = 0;

    @Column(name = "successful_logins", columnDefinition = "INTEGER DEFAULT 0")
    private Integer successfulLogins = 0;

    @Column(name = "failed_logins", columnDefinition = "INTEGER DEFAULT 0")
    private Integer failedLogins = 0;

    @Column(name = "unique_ip_addresses", columnDefinition = "INTEGER DEFAULT 0")
    private Integer uniqueIpAddresses = 0;

    @Column(name = "unique_devices", columnDefinition = "INTEGER DEFAULT 0")
    private Integer uniqueDevices = 0;

    @Column(name = "suspicious_activities", columnDefinition = "INTEGER DEFAULT 0")
    private Integer suspiciousActivities = 0;

    @Column(name = "avg_risk_score", columnDefinition = "DECIMAL(5,2) DEFAULT 0.00")
    private Double avgRiskScore = 0.0;

    @Column(name = "max_risk_score", columnDefinition = "INTEGER DEFAULT 0")
    private Integer maxRiskScore = 0;

    @Column(name = "login_methods_used", length = 200)
    private String loginMethodsUsed;

    @Column(name = "most_common_ip", length = 45)
    private String mostCommonIp;

    @Column(name = "most_common_device", length = 100)
    private String mostCommonDevice;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "first_login_time")
    private LocalDateTime firstLoginTime;

    @Column(name = "avg_login_interval_hours", columnDefinition = "DECIMAL(10,2)")
    private Double avgLoginIntervalHours;

    @Column(name = "unusual_login_patterns", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean unusualLoginPatterns = false;

    @Column(name = "geographic_anomalies", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean geographicAnomalies = false;

    @Column(name = "time_anomalies", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean timeAnomalies = false;

    @Column(name = "device_anomalies", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deviceAnomalies = false;

    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "security_recommendations", length = 1000)
    private String securityRecommendations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
} 