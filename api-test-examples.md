# API 测试示例

## 1. 创建登录记录

### 请求示例
```bash
curl -X POST http://localhost:8080/api/login-records \
  -H "Content-Type: application/json" \
  -d '{
    "uid": "user001",
    "username": "john.doe",
    "ipAddress": "192.168.1.100",
    "loginTime": "2024-01-15T09:30:00",
    "loginMethod": "PASSWORD",
    "passwordStrength": "STRONG",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "traceId": "trace_001",
    "fingerprint": "fp_001",
    "deviceType": "DESKTOP",
    "browserInfo": "Chrome",
    "osInfo": "Windows 10",
    "locationCountry": "CN",
    "locationCity": "Beijing"
  }'
```

### 响应示例
```json
{
  "result": "SUCCESS",
  "message": "登录记录创建成功",
  "data": {
    "id": 1,
    "uid": "user001",
    "ipAddress": "192.168.1.100",
    "loginTime": "2024-01-15T09:30:00",
    "loginMethod": "PASSWORD",
    "loginStatus": "SUCCESS",
    "deviceType": "DESKTOP",
    "locationCountry": "CN",
    "locationCity": "Beijing",
    "isSuspicious": false,
    "riskScore": 10,
    "createdAt": "2024-01-15T09:30:00"
  }
}
```

## 2. 查询用户最近登录记录

### 请求示例
```bash
curl -X GET "http://localhost:8080/api/login-records/user/user001?page=0&size=10"
```

### 响应示例
```json
{
  "result": "SUCCESS",
  "message": "获取用户登录记录成功",
  "data": {
    "content": [
      {
        "id": 1,
        "uid": "user001",
        "ipAddress": "192.168.1.100",
        "loginTime": "2024-01-15T09:30:00",
        "loginMethod": "PASSWORD",
        "loginStatus": "SUCCESS",
        "deviceType": "DESKTOP",
        "locationCountry": "CN",
        "locationCity": "Beijing",
        "isSuspicious": false,
        "riskScore": 10,
        "createdAt": "2024-01-15T09:30:00"
      }
    ],
    "pageable": {
      "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
      },
      "offset": 0,
      "pageNumber": 0,
      "pageSize": 10,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "size": 10,
    "number": 0,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "numberOfElements": 1,
    "first": true,
    "empty": false
  }
}
```

## 3. 查询多个用户最近登录记录

### 请求示例
```bash
curl -X GET "http://localhost:8080/api/login-records/users?uids=user001,user002,user003&page=0&size=10"
```

## 4. 获取用户安全分析

### 请求示例
```bash
curl -X GET "http://localhost:8080/api/login-records/security-analysis/user001"
```

### 响应示例
```json
{
  "result": "SUCCESS",
  "message": "获取用户安全分析成功",
  "data": {
    "uid": "user001",
    "analysisDate": "2024-01-15T15:00:00",
    "totalLogins": 2,
    "successfulLogins": 2,
    "failedLogins": 0,
    "uniqueIpAddresses": 1,
    "uniqueDevices": 1,
    "suspiciousActivities": 0,
    "avgRiskScore": 10.0,
    "maxRiskScore": 10,
    "loginMethodsUsed": "PASSWORD",
    "riskLevel": "LOW",
    "unusualLoginPatterns": false,
    "geographicAnomalies": false,
    "timeAnomalies": false,
    "deviceAnomalies": false,
    "securityRecommendations": "账户安全状况良好",
    "lastLoginTime": "2024-01-15T14:20:00",
    "firstLoginTime": "2024-01-15T09:30:00",
    "avgLoginIntervalHours": 4.83
  }
}
```

## 5. 获取多个用户安全分析

### 请求示例
```bash
curl -X GET "http://localhost:8080/api/login-records/security-analysis/users?uids=user001,user002,user003"
```

## 测试数据准备

在运行测试之前，请确保：

1. 数据库已创建并运行
2. 应用已启动
3. 执行了 `schema.sql` 脚本创建表结构和测试数据

## 错误处理示例

### 重复的Trace ID
```bash
curl -X POST http://localhost:8080/api/login-records \
  -H "Content-Type: application/json" \
  -d '{
    "uid": "user001",
    "username": "john.doe",
    "ipAddress": "192.168.1.100",
    "loginTime": "2024-01-15T09:30:00",
    "loginMethod": "PASSWORD",
    "traceId": "trace_001"
  }'
```

### 响应
```json
{
  "result": "ERROR",
  "message": "Trace ID已存在: trace_001",
  "data": null
}
```

### 缺少必需字段
```bash
curl -X POST http://localhost:8080/api/login-records \
  -H "Content-Type: application/json" \
  -d '{
    "uid": "user001",
    "ipAddress": "192.168.1.100",
    "loginTime": "2024-01-15T09:30:00"
  }'
```

### 响应
```json
{
  "result": "ERROR",
  "message": "用户名不能为空",
  "data": null
}
``` 