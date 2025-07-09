# 缓存使用指南

## 概述

本指南介绍如何在登录管理应用中使用带有Caffeine备用缓存的Hazelcast缓存，实现高可用的缓存解决方案。

## 缓存架构

### 主缓存：Hazelcast
- 跨集群节点的分布式缓存
- 高可用性和可扩展性
- 自动数据复制

### 备用缓存：Caffeine
- 本地内存缓存
- 快速访问和低延迟
- Hazelcast不可用时自动切换

## 配置

### 应用属性配置

```yaml
app:
  cache:
    hazelcast:
      enabled: true
    fallback:
      enabled: true
      max-size: 1000
      expire-after-write-seconds: 300
      expire-after-access-seconds: 600
```

### 缓存配置

`HazelcastCacheConfig` 类提供：
- `hazelcastCacheManager()`: Hazelcast主缓存管理器
- `fallbackCacheManager()`: Caffeine备用缓存管理器
- `resilientCacheManager()`: 具有自动切换功能的弹性缓存管理器

## 使用示例

### 1. 基本缓存使用 @Cacheable

```java
@Service
public class LoginRecordService {
    
    @Cacheable(value = "login-records", key = "#id", unless = "#result == null")
    public LoginRecordResponse getLoginRecordById(Long id) {
        // 此方法将被缓存
        // 如果Hazelcast失败，将自动使用Caffeine备用缓存
        return userLoginRecordRepository.findById(id)
                .map(this::mapToResponse)
                .orElse(null);
    }
}
```

### 2. 自定义键的缓存

```java
@Cacheable(value = "user-login-records", key = "#email", unless = "#result.isEmpty()")
public List<LoginRecordResponse> getLoginRecordsByEmail(String email) {
    return userLoginRecordRepository.findByEmailOrderByLoginTimeDesc(email)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
}
```

### 3. 缓存更新 @CachePut

```java
@CachePut(value = "login-records", key = "#result.id")
@CacheEvict(value = {"user-login-records", "ip-login-records"}, allEntries = true)
public LoginRecordResponse createLoginRecord(LoginRecordRequest request) {
    // 创建记录并更新缓存
    UserLoginRecord record = new UserLoginRecord();
    // ... 设置属性
    UserLoginRecord savedRecord = userLoginRecordRepository.save(record);
    return mapToResponse(savedRecord);
}
```

### 4. 缓存清除

```java
@CacheEvict(value = {"login-records", "user-login-records", "ip-login-records"}, allEntries = true)
public void deleteLoginRecord(Long id) {
    userLoginRecordRepository.deleteById(id);
}
```

## 缓存名称

### 预定义缓存名称

- `login-records`: 按ID的单个登录记录
- `user-login-records`: 按用户邮箱的登录记录
- `ip-login-records`: 按IP地址的登录记录
- `recent-login-records`: 最近的登录记录
- `user-security-analysis`: 用户安全分析数据

### 自定义缓存名称

您可以为特定用例定义自定义缓存名称：

```java
@Cacheable(value = "custom-cache", key = "#customKey")
public CustomResponse getCustomData(String customKey) {
    // 自定义缓存逻辑
}
```

## 缓存监控

### 缓存状态端点

```bash
# 获取缓存状态
GET /api/cache/status

# 响应示例：
{
  "result": "SUCCESS",
  "message": "Cache status retrieved successfully",
  "data": {
    "usingFallback": false,
    "cacheType": "Hazelcast (Primary)",
    "cacheNames": ["login-records", "user-login-records", "ip-login-records"],
    "cacheStatistics": {
      "login-records": {
        "name": "login-records",
        "nativeCache": "IMap",
        "size": 150
      }
    }
  }
}
```

### 缓存管理端点

```bash
# 重置到主缓存（Hazelcast）
POST /api/cache/reset-to-primary

# 强制使用备用缓存（Caffeine）
POST /api/cache/force-fallback

# 清除特定缓存
DELETE /api/cache/login-records

# 清除所有缓存
DELETE /api/cache/clear-all

# 获取缓存值
GET /api/cache/login-records/123

# 存储缓存值
PUT /api/cache/login-records/123
Content-Type: application/json
{
  "id": 123,
  "uid": "user123",
  "email": "user@example.com"
}

# 清除缓存值
DELETE /api/cache/login-records/123
```

## 自动切换行为

### 何时触发备用缓存

1. **Hazelcast连接失败**: 当Hazelcast实例不可用时
2. **缓存操作异常**: 当任何缓存操作抛出异常时
3. **手动强制**: 通过API端点手动强制时

### 切换过程

1. **检测**: 系统检测到Hazelcast失败
2. **切换**: 自动切换到Caffeine缓存
3. **日志记录**: 记录切换事件
4. **恢复**: 当Hazelcast可用时尝试恢复

### 恢复过程

1. **健康检查**: 系统定期检查Hazelcast可用性
2. **重置**: 当Hazelcast健康时，重置到主缓存
3. **同步**: 如需要，在缓存之间同步数据

## 最佳实践

### 1. 缓存键设计

```java
// 好：简单且唯一的键
@Cacheable(value = "login-records", key = "#id")

// 好：复杂查询的复合键
@Cacheable(value = "user-records", key = "#user.id + '_' + #user.email")

// 避免：复杂对象作为键
@Cacheable(value = "records", key = "#user") // 不要这样做
```

### 2. 缓存清除策略

```java
// 数据更改时清除相关缓存
@CacheEvict(value = {"login-records", "user-login-records"}, allEntries = true)
public void updateLoginRecord(Long id, LoginRecordRequest request) {
    // 更新逻辑
}

// 尽可能使用特定键
@CacheEvict(value = "login-records", key = "#id")
public void deleteLoginRecord(Long id) {
    // 删除逻辑
}
```

### 3. 缓存条件

```java
// 只缓存非空结果
@Cacheable(value = "records", unless = "#result == null")

// 只缓存非空集合
@Cacheable(value = "lists", unless = "#result.isEmpty()")

// 基于条件的缓存
@Cacheable(value = "records", condition = "#id > 0")
```

### 4. 性能考虑

```java
// 使用适当的缓存大小
@Cacheable(value = "small-cache", key = "#id") // 用于小数据集

// 对时间敏感的数据使用TTL
@Cacheable(value = "temporary-cache", key = "#id") // 在属性中配置TTL

// 避免缓存大对象
@Cacheable(value = "large-objects", key = "#id") // 考虑是否真的需要
```

## 故障排除

### 常见问题

1. **缓存不工作**
   - 检查是否启用了 `@EnableCaching`
   - 验证缓存管理器是否正确配置
   - 检查缓存名称是否匹配配置

2. **备用缓存未触发**
   - 验证备用缓存是否启用
   - 检查Hazelcast连接状态
   - 查看日志中的切换事件

3. **缓存性能问题**
   - 监控缓存命中率
   - 检查缓存大小和清除策略
   - 审查缓存键设计

### 调试命令

```bash
# 检查缓存状态
curl -X GET http://localhost:8080/api/cache/status

# 强制切换以测试
curl -X POST http://localhost:8080/api/cache/force-fallback

# 重置到主缓存
curl -X POST http://localhost:8080/api/cache/reset-to-primary

# 清除所有缓存
curl -X DELETE http://localhost:8080/api/cache/clear-all
```

### 日志记录

启用缓存操作的调试日志：

```yaml
logging:
  level:
    com.wilsonkeh.loginmanagement.config.HazelcastCacheConfig: DEBUG
    org.springframework.cache: DEBUG
```

## 监控和指标

### 缓存指标

缓存系统提供监控指标：

- 缓存命中/未命中率
- 缓存大小
- 清除计数
- 备用缓存使用统计

### 健康检查

```bash
# 缓存健康检查
GET /actuator/health

# 自定义缓存健康端点
GET /api/cache/status
```

## 安全考虑

1. **缓存键验证**: 验证缓存键以防止注入攻击
2. **敏感数据**: 避免缓存敏感信息
3. **访问控制**: 为缓存管理端点实施适当的访问控制
4. **数据加密**: 如需要，考虑加密缓存数据

## 性能调优

### 缓存配置调优

```yaml
app:
  cache:
    fallback:
      max-size: 2000                    # 增加内存使用
      expire-after-write-seconds: 600   # 增加TTL
      expire-after-access-seconds: 1200 # 增加访问TTL
```

### JVM调优

```bash
# 增加堆大小用于缓存
-Xmx2g -Xms1g

# 启用GC日志用于缓存监控
-XX:+PrintGCDetails -XX:+PrintGCTimeStamps
```

这个缓存系统为您的登录管理应用提供了一个健壮、弹性的缓存解决方案，能够自动处理故障并提供高可用性。 