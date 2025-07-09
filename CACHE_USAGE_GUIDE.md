# Cache Usage Guide

## Overview

This guide explains how to use the Hazelcast cache with Caffeine fallback for resilient caching in the login management application.

## Cache Architecture

### Primary Cache: Hazelcast
- Distributed cache across cluster nodes
- High availability and scalability
- Automatic data replication

### Fallback Cache: Caffeine
- Local in-memory cache
- Fast access and low latency
- Automatic fallback when Hazelcast is unavailable

## Configuration

### Application Properties

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

### Cache Configuration

The `HazelcastCacheConfig` class provides:
- `hazelcastCacheManager()`: Primary Hazelcast cache manager
- `fallbackCacheManager()`: Caffeine fallback cache manager
- `resilientCacheManager()`: Resilient cache manager with automatic fallback

## Usage Examples

### 1. Basic Caching with @Cacheable

```java
@Service
public class LoginRecordService {
    
    @Cacheable(value = "login-records", key = "#id", unless = "#result == null")
    public LoginRecordResponse getLoginRecordById(Long id) {
        // This method will be cached
        // If Hazelcast fails, it will automatically use Caffeine fallback
        return userLoginRecordRepository.findById(id)
                .map(this::mapToResponse)
                .orElse(null);
    }
}
```

### 2. Cache with Custom Key

```java
@Cacheable(value = "user-login-records", key = "#email", unless = "#result.isEmpty()")
public List<LoginRecordResponse> getLoginRecordsByEmail(String email) {
    return userLoginRecordRepository.findByEmailOrderByLoginTimeDesc(email)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
}
```

### 3. Cache Update with @CachePut

```java
@CachePut(value = "login-records", key = "#result.id")
@CacheEvict(value = {"user-login-records", "ip-login-records"}, allEntries = true)
public LoginRecordResponse createLoginRecord(LoginRecordRequest request) {
    // Create record and update cache
    UserLoginRecord record = new UserLoginRecord();
    // ... set properties
    UserLoginRecord savedRecord = userLoginRecordRepository.save(record);
    return mapToResponse(savedRecord);
}
```

### 4. Cache Eviction

```java
@CacheEvict(value = {"login-records", "user-login-records", "ip-login-records"}, allEntries = true)
public void deleteLoginRecord(Long id) {
    userLoginRecordRepository.deleteById(id);
}
```

## Cache Names

### Predefined Cache Names

- `login-records`: Individual login records by ID
- `user-login-records`: Login records by user email
- `ip-login-records`: Login records by IP address
- `recent-login-records`: Recent login records
- `user-security-analysis`: User security analysis data

### Custom Cache Names

You can define custom cache names for specific use cases:

```java
@Cacheable(value = "custom-cache", key = "#customKey")
public CustomResponse getCustomData(String customKey) {
    // Custom caching logic
}
```

## Cache Monitoring

### Cache Status Endpoint

```bash
# Get cache status
GET /api/cache/status

# Response example:
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

### Cache Management Endpoints

```bash
# Reset to primary cache (Hazelcast)
POST /api/cache/reset-to-primary

# Force fallback cache (Caffeine)
POST /api/cache/force-fallback

# Clear specific cache
DELETE /api/cache/login-records

# Clear all caches
DELETE /api/cache/clear-all

# Get cache value
GET /api/cache/login-records/123

# Put cache value
PUT /api/cache/login-records/123
Content-Type: application/json
{
  "id": 123,
  "uid": "user123",
  "email": "user@example.com"
}

# Evict cache value
DELETE /api/cache/login-records/123
```

## Automatic Fallback Behavior

### When Fallback is Triggered

1. **Hazelcast Connection Failure**: When Hazelcast instance is not available
2. **Cache Operation Exception**: When any cache operation throws an exception
3. **Manual Force**: When manually forced via API endpoint

### Fallback Process

1. **Detection**: System detects Hazelcast failure
2. **Switch**: Automatically switches to Caffeine cache
3. **Logging**: Logs the fallback event
4. **Recovery**: Attempts to recover when Hazelcast becomes available

### Recovery Process

1. **Health Check**: System periodically checks Hazelcast availability
2. **Reset**: When Hazelcast is healthy, resets to primary cache
3. **Sync**: Synchronizes data between caches if needed

## Best Practices

### 1. Cache Key Design

```java
// Good: Simple and unique keys
@Cacheable(value = "login-records", key = "#id")

// Good: Composite keys for complex queries
@Cacheable(value = "user-records", key = "#user.id + '_' + #user.email")

// Avoid: Complex objects as keys
@Cacheable(value = "records", key = "#user") // Don't do this
```

### 2. Cache Eviction Strategy

```java
// Evict related caches when data changes
@CacheEvict(value = {"login-records", "user-login-records"}, allEntries = true)
public void updateLoginRecord(Long id, LoginRecordRequest request) {
    // Update logic
}

// Use specific keys when possible
@CacheEvict(value = "login-records", key = "#id")
public void deleteLoginRecord(Long id) {
    // Delete logic
}
```

### 3. Cache Conditions

```java
// Cache only non-null results
@Cacheable(value = "records", unless = "#result == null")

// Cache only non-empty collections
@Cacheable(value = "lists", unless = "#result.isEmpty()")

// Cache based on condition
@Cacheable(value = "records", condition = "#id > 0")
```

### 4. Performance Considerations

```java
// Use appropriate cache sizes
@Cacheable(value = "small-cache", key = "#id") // For small datasets

// Use TTL for time-sensitive data
@Cacheable(value = "temporary-cache", key = "#id") // Configure TTL in properties

// Avoid caching large objects
@Cacheable(value = "large-objects", key = "#id") // Consider if really needed
```

## Troubleshooting

### Common Issues

1. **Cache Not Working**
   - Check if `@EnableCaching` is enabled
   - Verify cache manager is properly configured
   - Check cache names match configuration

2. **Fallback Not Triggering**
   - Verify fallback cache is enabled
   - Check Hazelcast connection status
   - Review logs for fallback events

3. **Cache Performance Issues**
   - Monitor cache hit rates
   - Check cache sizes and eviction policies
   - Review cache key design

### Debug Commands

```bash
# Check cache status
curl -X GET http://localhost:8080/api/cache/status

# Force fallback to test
curl -X POST http://localhost:8080/api/cache/force-fallback

# Reset to primary
curl -X POST http://localhost:8080/api/cache/reset-to-primary

# Clear all caches
curl -X DELETE http://localhost:8080/api/cache/clear-all
```

### Logging

Enable debug logging for cache operations:

```yaml
logging:
  level:
    com.wilsonkeh.loginmanagement.config.HazelcastCacheConfig: DEBUG
    org.springframework.cache: DEBUG
```

## Monitoring and Metrics

### Cache Metrics

The cache system provides metrics for monitoring:

- Cache hit/miss rates
- Cache sizes
- Eviction counts
- Fallback usage statistics

### Health Checks

```bash
# Cache health check
GET /actuator/health

# Custom cache health endpoint
GET /api/cache/status
```

## Security Considerations

1. **Cache Key Validation**: Validate cache keys to prevent injection attacks
2. **Sensitive Data**: Avoid caching sensitive information
3. **Access Control**: Implement proper access control for cache management endpoints
4. **Data Encryption**: Consider encrypting cached data if needed

## Performance Tuning

### Cache Configuration Tuning

```yaml
app:
  cache:
    fallback:
      max-size: 2000                    # Increase for more memory
      expire-after-write-seconds: 600   # Increase TTL
      expire-after-access-seconds: 1200 # Increase access TTL
```

### JVM Tuning

```bash
# Increase heap size for caching
-Xmx2g -Xms1g

# Enable GC logging for cache monitoring
-XX:+PrintGCDetails -XX:+PrintGCTimeStamps
```

This cache system provides a robust, resilient caching solution that automatically handles failures and provides high availability for your login management application. 