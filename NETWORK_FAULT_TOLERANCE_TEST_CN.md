# 网络容错功能测试指南

## 概述

本文档描述了如何测试Hazelcast集群的网络容错功能，包括自动重连、健康检查、故障恢复等机制。

## 测试环境准备

### 1. 启动测试集群

```bash
# 启动Consul
consul agent -server -bootstrap-expect=1 -ui -client=0.0.0.0

# 启动应用实例1
java -jar login-management-app.jar --server.port=8080 --app.hazelcast.network.port=5701

# 启动应用实例2  
java -jar login-management-app.jar --server.port=8081 --app.hazelcast.network.port=5702

# 启动应用实例3
java -jar login-management-app.jar --server.port=8082 --app.hazelcast.network.port=5703
```

### 2. 验证集群状态

```bash
# 检查集群信息
curl -X GET http://localhost:8080/api/distributed-queue/cluster/info

# 检查网络健康状态
curl -X GET http://localhost:8080/api/distributed-queue/network/health

# 检查网络配置
curl -X GET http://localhost:8080/api/distributed-queue/network/config
```

## 测试场景

### 1. 网络分区测试

#### 测试步骤：
1. 启动3个应用实例
2. 验证集群正常状态
3. 模拟网络分区（断开某个实例的网络）
4. 观察自动重连机制
5. 恢复网络连接
6. 验证集群恢复

#### 模拟网络分区：

```bash
# 在Linux/Mac上模拟网络分区
sudo iptables -A INPUT -p tcp --dport 5702 -j DROP  # 阻止实例2的Hazelcast端口

# 在Windows上模拟网络分区
netsh advfirewall firewall add rule name="Block Hazelcast" dir=in action=block protocol=TCP localport=5702
```

#### 验证重连：

```bash
# 监控网络健康状态
watch -n 5 'curl -s http://localhost:8080/api/distributed-queue/network/health'

# 查看日志
tail -f logs/application.log | grep -E "(重连|网络|集群)"
```

### 2. 实例重启测试

#### 测试步骤：
1. 启动3个应用实例
2. 验证集群正常状态
3. 重启其中一个实例
4. 观察其他实例的检测和恢复
5. 验证重启实例重新加入集群

#### 执行重启：

```bash
# 重启实例2
pkill -f "server.port=8081"
sleep 10
java -jar login-management-app.jar --server.port=8081 --app.hazelcast.network.port=5702 &
```

### 3. 手动重连测试

#### 测试步骤：
1. 启动应用实例
2. 手动触发重连
3. 观察重连过程
4. 验证重连结果

#### 执行手动重连：

```bash
# 触发手动重连
curl -X POST http://localhost:8080/api/distributed-queue/network/reconnect

# 查看重连状态
curl -X GET http://localhost:8080/api/distributed-queue/network/health
```

### 4. 健康检查测试

#### 测试步骤：
1. 启动应用实例
2. 模拟成员不可达
3. 观察健康检查机制
4. 验证故障检测

#### 模拟成员不可达：

```bash
# 阻止某个成员的ICMP ping
sudo iptables -A INPUT -p icmp -j DROP

# 或者阻止TCP连接
sudo iptables -A INPUT -p tcp --dport 5701 -j DROP
```

## 监控和验证

### 1. 日志监控

```bash
# 监控网络容错相关日志
tail -f logs/application.log | grep -E "(NetworkFaultToleranceManager|重连|集群|成员)"

# 监控Hazelcast生命周期日志
tail -f logs/application.log | grep -E "(Lifecycle|Membership|STARTED|SHUTDOWN)"
```

### 2. 指标监控

```bash
# 获取集群指标
curl -X GET http://localhost:8080/actuator/metrics/hazelcast.cluster.members

# 获取网络指标
curl -X GET http://localhost:8080/actuator/metrics/hazelcast.network.connections
```

### 3. 健康检查

```bash
# 应用健康检查
curl -X GET http://localhost:8080/actuator/health

# 网络健康检查
curl -X GET http://localhost:8080/api/distributed-queue/network/health
```

## 预期结果

### 1. 正常状态

```
集群健康报告:
- 总成员数: 3
- 健康成员数: 3
- 不健康成员数: 0
- 集群健康状态: 健康
- 重连尝试次数: 0
- 最后重连时间: 从未
- 最后健康检查: 2024-01-01 12:00:00
- 是否正在重连: 否
```

### 2. 故障状态

```
集群健康报告:
- 总成员数: 2
- 健康成员数: 2
- 不健康成员数: 1
- 集群健康状态: 不健康
- 重连尝试次数: 1
- 最后重连时间: 2024-01-01 12:05:00
- 最后健康检查: 2024-01-01 12:05:00
- 是否正在重连: 是
```

### 3. 恢复状态

```
集群健康报告:
- 总成员数: 3
- 健康成员数: 3
- 不健康成员数: 0
- 集群健康状态: 健康
- 重连尝试次数: 0
- 最后重连时间: 2024-01-01 12:10:00
- 最后健康检查: 2024-01-01 12:10:00
- 是否正在重连: 否
```

## 故障排除

### 1. 重连失败

**问题**：重连尝试失败，无法重新加入集群

**排查步骤**：
1. 检查网络连接
2. 验证Consul服务发现
3. 查看Hazelcast配置
4. 检查防火墙设置

**解决方案**：
```bash
# 检查网络连通性
telnet <target-host> <hazelcast-port>

# 验证Consul服务
curl -X GET http://localhost:8500/v1/catalog/service/login-management-app

# 重置网络健康状态
curl -X POST http://localhost:8080/api/distributed-queue/network/reset
```

### 2. 健康检查异常

**问题**：健康检查报告异常，但集群实际正常

**排查步骤**：
1. 检查健康检查配置
2. 验证超时设置
3. 查看网络延迟

**解决方案**：
```yaml
# 调整健康检查参数
app:
  hazelcast:
    network:
      fault-tolerance:
        heartbeat-check-interval-seconds: 60  # 增加检查间隔
        max-consecutive-failures: 5           # 增加失败容忍度
```

### 3. 监听器注册失败

**问题**：生命周期监听器或成员监听器注册失败

**排查步骤**：
1. 检查Hazelcast实例状态
2. 验证监听器实现
3. 查看初始化顺序

**解决方案**：
```java
// 确保在Hazelcast实例完全启动后注册监听器
@PostConstruct
public void init() {
    if (hazelcastInstance != null && 
        hazelcastInstance.getLifecycleService().isRunning()) {
        registerListeners();
    }
}
```

## 性能测试

### 1. 重连性能

```bash
# 测试重连时间
time curl -X POST http://localhost:8080/api/distributed-queue/network/reconnect

# 监控重连过程中的性能指标
curl -X GET http://localhost:8080/actuator/metrics/jvm.memory.used
```

### 2. 健康检查开销

```bash
# 监控健康检查的CPU和内存使用
top -p $(pgrep -f "login-management-app")

# 查看健康检查的执行时间
curl -X GET http://localhost:8080/actuator/metrics/process.cpu.usage
```

## 最佳实践

### 1. 配置优化

```yaml
# 生产环境推荐配置
app:
  hazelcast:
    network:
      fault-tolerance:
        heartbeat-check-interval-seconds: 30    # 适中的检查间隔
        max-consecutive-failures: 3             # 合理的失败容忍度
        recovery-window-seconds: 300           # 避免频繁重连
        auto-reconnect:
          max-attempts: 10                     # 足够的重连尝试
          backoff-seconds: 5                   # 合理的退避时间
```

### 2. 监控告警

```yaml
# 配置监控告警
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 日志配置

```yaml
# 配置网络容错相关日志
logging:
  level:
    com.wilsonkeh.loginmanagement.config.NetworkFaultToleranceManager: INFO
    com.hazelcast: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 总结

通过以上测试，可以验证网络容错功能的完整性和可靠性。建议在生产环境中定期执行这些测试，确保系统在网络异常情况下能够自动恢复。

## 关键改进点

### 1. 主动重连机制

- **生命周期监听器**：监听Hazelcast实例的生命周期变化
- **成员监听器**：监听集群成员的变化
- **主动检测**：定期检查集群连接状态
- **智能重连**：根据故障类型选择不同的重连策略

### 2. 健康检查增强

- **多维度检查**：检查实例状态、集群连接、成员可达性
- **TCP连接测试**：通过建立TCP连接验证网络可达性
- **Consul集成**：通过服务发现重新发现集群成员
- **状态跟踪**：实时跟踪集群健康状态

### 3. 监控和调试

- **详细日志**：记录所有重连和健康检查过程
- **监控端点**：提供REST API查看网络状态
- **手动操作**：支持手动触发重连和重置状态
- **性能监控**：监控重连过程的性能影响

### 4. 配置灵活性

- **可配置参数**：心跳间隔、失败容忍度、重连策略等
- **环境适配**：支持Consul和静态成员两种模式
- **超时控制**：可配置连接测试超时时间
- **恢复窗口**：避免频繁重连的恢复窗口机制 