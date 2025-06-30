-- 创建数据库
CREATE DATABASE IF NOT EXISTS login_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE login_management;

-- 用户登录记录表
CREATE TABLE IF NOT EXISTS user_login_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uid VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    login_time DATETIME NOT NULL,
    login_method VARCHAR(20) NOT NULL,
    password_strength VARCHAR(20),
    user_agent VARCHAR(500),
    trace_id VARCHAR(100) NOT NULL UNIQUE,
    fingerprint VARCHAR(255),
    login_status VARCHAR(20) DEFAULT 'SUCCESS',
    failure_reason VARCHAR(200),
    session_id VARCHAR(100),
    device_type VARCHAR(50),
    browser_info VARCHAR(100),
    os_info VARCHAR(100),
    location_country VARCHAR(50),
    location_city VARCHAR(100),
    is_suspicious BOOLEAN DEFAULT FALSE,
    risk_score INTEGER DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_uid (uid),
    INDEX idx_username (username),
    INDEX idx_login_time (login_time),
    INDEX idx_ip_address (ip_address),
    INDEX idx_login_method (login_method),
    INDEX idx_trace_id (trace_id),
    INDEX idx_uid_login_time (uid, login_time),
    INDEX idx_suspicious (is_suspicious),
    INDEX idx_risk_score (risk_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户安全分析表
CREATE TABLE IF NOT EXISTS user_security_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uid VARCHAR(100) NOT NULL,
    analysis_date DATETIME NOT NULL,
    total_logins INTEGER DEFAULT 0,
    successful_logins INTEGER DEFAULT 0,
    failed_logins INTEGER DEFAULT 0,
    unique_ip_addresses INTEGER DEFAULT 0,
    unique_devices INTEGER DEFAULT 0,
    suspicious_activities INTEGER DEFAULT 0,
    avg_risk_score DECIMAL(5,2) DEFAULT 0.00,
    max_risk_score INTEGER DEFAULT 0,
    login_methods_used VARCHAR(200),
    most_common_ip VARCHAR(45),
    most_common_device VARCHAR(100),
    last_login_time DATETIME,
    first_login_time DATETIME,
    avg_login_interval_hours DECIMAL(10,2),
    unusual_login_patterns BOOLEAN DEFAULT FALSE,
    geographic_anomalies BOOLEAN DEFAULT FALSE,
    time_anomalies BOOLEAN DEFAULT FALSE,
    device_anomalies BOOLEAN DEFAULT FALSE,
    risk_level VARCHAR(20) DEFAULT 'LOW',
    security_recommendations VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_uid (uid),
    INDEX idx_analysis_date (analysis_date),
    INDEX idx_risk_level (risk_level),
    INDEX idx_uid_analysis_date (uid, analysis_date),
    INDEX idx_suspicious_activities (suspicious_activities),
    INDEX idx_avg_risk_score (avg_risk_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入一些测试数据
INSERT INTO user_login_records (uid, username, ip_address, login_time, login_method, password_strength, user_agent, trace_id, fingerprint, device_type, browser_info, os_info, location_country, location_city, risk_score) VALUES
('user001', 'john.doe', '192.168.1.100', '2024-01-15 09:30:00', 'PASSWORD', 'STRONG', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'trace_001', 'fp_001', 'DESKTOP', 'Chrome', 'Windows 10', 'CN', 'Beijing', 10),
('user001', 'john.doe', '192.168.1.100', '2024-01-15 14:20:00', 'PASSWORD', 'STRONG', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'trace_002', 'fp_001', 'DESKTOP', 'Chrome', 'Windows 10', 'CN', 'Beijing', 10),
('user002', 'jane.smith', '10.0.0.50', '2024-01-15 10:15:00', 'DUO', 'N/A', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'trace_003', 'fp_002', 'DESKTOP', 'Safari', 'macOS', 'US', 'New York', 5),
('user003', 'bob.wilson', '172.16.0.25', '2024-01-15 11:45:00', 'NEVIS', 'N/A', 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15', 'trace_004', 'fp_003', 'MOBILE', 'Safari', 'iOS', 'UK', 'London', 15);

-- 插入安全分析测试数据
INSERT INTO user_security_analysis (uid, analysis_date, total_logins, successful_logins, unique_ip_addresses, unique_devices, suspicious_activities, avg_risk_score, max_risk_score, login_methods_used, risk_level, last_login_time, first_login_time) VALUES
('user001', '2024-01-15 15:00:00', 2, 2, 1, 1, 0, 10.0, 10, 'PASSWORD', 'LOW', '2024-01-15 14:20:00', '2024-01-15 09:30:00'),
('user002', '2024-01-15 15:00:00', 1, 1, 1, 1, 0, 5.0, 5, 'DUO', 'LOW', '2024-01-15 10:15:00', '2024-01-15 10:15:00'),
('user003', '2024-01-15 15:00:00', 1, 1, 1, 1, 0, 15.0, 15, 'NEVIS', 'LOW', '2024-01-15 11:45:00', '2024-01-15 11:45:00'); 