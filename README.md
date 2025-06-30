# 用户登录数据管理系统

## 项目概述

这是一个基于Spring Boot 3 + JPA的用户登录数据管理系统，用于记录和分析用户登录行为，提供安全风险评估和数据分析功能。

## 技术栈

- **框架**: Spring Boot 3.3.0
- **Java版本**: 21
- **数据库**: MySQL 8.0
- **ORM**: Spring Data JPA
- **构建工具**: Maven
- **其他**: Lombok, Spring Security, Validation

## 功能特性

### 1. 登录记录管理
- 记录用户登录信息（UID、IP地址、登录时间、用户名、登录方式等）
- 支持多种登录方式（Password、DUO、Nevis、SecurID、SSO、OAuth、API Key）
- 设备指纹识别和地理位置记录
- 风险评分计算和可疑活动标记

### 2. 数据分析功能
- 查询单个用户最近登录记录
- 查询多个用户最近登录记录
- 用户安全分析报告
- 多用户安全分析对比

### 3. 安全特性
- 隐私信息保护（API响应中不暴露敏感信息）
- 风险评分算法
- 可疑活动检测
- 地理位置异常检测
- 设备异常检测

## 数据库设计

### 主要表结构

#### 1. user_login_records（用户登录记录表）
- 存储所有用户登录记录
- 包含登录详情、设备信息、地理位置、风险评分等
- 支持高效查询和索引优化

#### 2. user_security_analysis（用户安全分析表）
- 存储用户安全分析结果
- 包含登录统计、风险等级、异常检测结果等
- 支持定期更新和实时分析

## API接口

### 1. 创建登录记录
```
POST /api/login-records
Content-Type: application/json

{
  "uid": "user001",
  "username": "john.doe",
  "ipAddress": "192.168.1.100",
  "loginTime": "2024-01-15T09:30:00",
  "loginMethod": "PASSWORD",
  "passwordStrength": "STRONG",
  "userAgent": "Mozilla/5.0...",
  "traceId": "trace_001",
  "fingerprint": "fp_001",
  "deviceType": "DESKTOP",
  "browserInfo": "Chrome",
  "osInfo": "Windows 10",
  "locationCountry": "CN",
  "locationCity": "Beijing"
}
```

### 2. 查询用户最近登录记录
```
GET /api/login-records/user/{uid}?page=0&size=10
```

### 3. 查询多个用户最近登录记录
```
GET /api/login-records/users?uids=user001,user002,user003&page=0&size=10
```

### 4. 获取用户安全分析
```
GET /api/login-records/security-analysis/{uid}
```

### 5. 获取多个用户安全分析
```
GET /api/login-records/security-analysis/users?uids=user001,user002,user003
```

## 安装和运行

### 1. 环境要求
- Java 21+
- MySQL 8.0+
- Maven 3.6+

### 2. 数据库配置
1. 创建MySQL数据库
2. 执行 `src/main/resources/schema.sql` 脚本
3. 修改 `application.yml` 中的数据库连接信息

### 3. 运行应用
```bash
# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run
```

### 4. 访问应用
- 应用地址: http://localhost:8080
- 默认用户名: admin
- 默认密码: admin123

## 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/login_management
    username: your_username
    password: your_password
```

### 安全配置
```yaml
spring:
  security:
    user:
      name: admin
      password: admin123
```

## 性能优化

### 1. 数据库索引
- 为常用查询字段创建复合索引
- 支持分页查询优化
- 使用覆盖索引减少IO

### 2. 查询优化
- 使用JPQL优化复杂查询
- 实现分页查询避免大数据量问题
- 使用DTO减少数据传输量

### 3. 缓存策略
- 可扩展Redis缓存支持
- 查询结果缓存
- 安全分析结果缓存

## 安全考虑

### 1. 数据保护
- API响应中不暴露敏感信息（如用户名）
- 使用DTO进行数据传输
- 输入验证和SQL注入防护

### 2. 访问控制
- Spring Security集成
- API访问权限控制
- 敏感操作日志记录

### 3. 风险检测
- 实时风险评分计算
- 异常行为检测
- 地理位置异常检测

## 扩展功能

### 1. 可扩展的风险评分算法
- 支持自定义风险规则
- 机器学习模型集成
- 实时威胁情报集成

### 2. 监控和告警
- 实时监控面板
- 异常行为告警
- 安全事件通知

### 3. 数据导出
- 支持多种格式导出
- 定期报告生成
- 数据备份功能

## 开发规范

### 1. 代码规范
- 遵循SOLID原则
- 使用Lombok简化代码
- 统一异常处理
- 完整的API文档

### 2. 测试
- 单元测试覆盖
- 集成测试
- API测试

## 许可证

MIT License 