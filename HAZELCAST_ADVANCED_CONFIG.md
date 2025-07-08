# Hazelcast高级配置 - Java配置与Consul服务发现

## 概述

本项目已升级为使用Java配置和Consul服务发现，提供更灵活的集群管理和更强的网络容错能力。

## 主要改进

### 1. Java配置替代YAML
- 使用`@Configuration`类进行Hazelcast配置
- 支持动态配置和条件化配置
- 更好的类型安全和IDE支持

### 2. Consul服务发现
- 自动发现集群成员
- 支持动态扩缩容
- 健康检查和自动注册

### 3. 网络容错机制
- 增强的心跳检测
- 自动重连机制
- 网络分区恢复

### 4. 多区域支持
- 区域感知分区
- 跨区域数据备份
- 故障转移支持

## 配置说明

### 1. Maven依赖

```xml
<!-- Hazelcast -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.3.6</version>
</dependency>

<!-- Hazelcast Spring Boot Starter -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-spring-boot-starter</artifactId>
    <version>5.3.6</version>
</dependency>

<!-- Consul Discovery -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    <version>4.0.4</version>
</dependency>
```

### 2. 应用配置

在`application.yml`中配置Hazelcast参数：

```yaml
app:
  hazelcast:
    cluster:
      name: login-management-cluster
      zone: default                    # 区域标识
      backup-count: 1                  # 同步备份数
      async-backup-count: 1            # 异步备份数
      max-join-seconds: 300            # 最大加入时间
      auto-rejoin:
        enabled: true                  # 启用自动重连
        max-attempts: 5                # 最大重连尝试次数
        initial-backoff-seconds: 1     # 初始退避时间
        max-backoff-seconds: 60        # 最大退避时间
        backoff-multiplier: 2.0        # 退避倍数
    
    network:
      port: 5701                       # Hazelcast端口
      port-auto-increment: true        # 端口自动递增
      heartbeat:
        interval-seconds: 5            # 心跳间隔
        timeout-seconds: 60            # 心跳超时
        max-no-heartbeat-seconds: 300  # 最大无心跳时间
      icmp:
        enabled: true                  # 启用ICMP检测
        timeout-seconds: 10            # ICMP超时
        ttl: 255                       # TTL值
        parallel-mode: true            # 并行模式
      fault-tolerance:
        enabled: true                  # 启用网络容错
        heartbeat-check-interval-seconds: 30
        max-consecutive-failures: 3    # 最大连续失败次数
        recovery-window-seconds: 300   # 恢复窗口
        auto-reconnect:
          enabled: true                # 启用自动重连
          max-attempts: 10             # 最大重连尝试
          backoff-seconds: 5           # 重连退避时间
    
    consul:
      enabled: true                    # 启用Consul服务发现
      refresh-interval-seconds: 30     # 刷新间隔
      service-tag: hazelcast           # 服务标签

# Spring Cloud Consul配置
spring:
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        enabled: true
        service-name: ${spring.application.name}
        tags:
          - hazelcast
        health-check-path: /actuator/health
        health-check-interval: 30s
        deregister: true
```

## 核心组件

### 1. HazelcastConfig

主要的Hazelcast配置类，提供：

- **动态配置创建**: 根据配置参数动态创建Hazelcast配置
- **Consul集成**: 自动从Consul获取集群成员
- **网络容错**: 配置心跳和重连参数
- **多区域支持**: 配置区域感知分区

### 2. ConsulDiscoveryConfig

Consul服务发现配置：

- **服务注册**: 自动注册到Consul
- **健康检查**: 提供健康检查端点
- **服务发现**: 从Consul获取其他服务实例

### 3. NetworkFaultToleranceManager

网络容错管理器：

- **健康监控**: 监控集群成员健康状态
- **自动重连**: 检测到网络问题时自动重连
- **故障恢复**: 提供故障恢复机制

## API接口

### 新增的网络容错API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/distributed-queue/network/health` | 获取网络健康报告 |
| POST | `/api/distributed-queue/network/reconnect` | 手动触发重连 |
| POST | `/api/distributed-queue/network/reset` | 重置网络健康状态 |

### 示例请求

```bash
# 获取网络健康报告
curl -X GET http://localhost:8080/api/distributed-queue/network/health

# 手动触发重连
curl -X POST http://localhost:8080/api/distributed-queue/network/reconnect

# 重置健康状态
curl -X POST http://localhost:8080/api/distributed-queue/network/reset
```

## 部署说明

### 1. 单机部署

```bash
# 启动Consul
consul agent -dev

# 启动应用
java -jar login-management-app.jar
```

### 2. 集群部署

```bash
# 启动Consul集群
consul agent -server -bootstrap-expect=3 -data-dir=/tmp/consul -node=node1

# 启动应用集群
java -jar login-management-app.jar --server.port=8080 --app.hazelcast.cluster.zone=zone1
java -jar login-management-app.jar --server.port=8081 --app.hazelcast.cluster.zone=zone2
java -jar login-management-app.jar --server.port=8082 --app.hazelcast.cluster.zone=zone3
```

### 3. Docker部署

```dockerfile
FROM openjdk:17-jre-slim
COPY target/login-management-app.jar app.jar
EXPOSE 8080 5701
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  consul:
    image: consul:latest
    ports:
      - "8500:8500"
    command: consul agent -server -bootstrap-expect=1 -ui -client=0.0.0.0

  app1:
    build: .
    ports:
      - "8080:8080"
      - "5701:5701"
    environment:
      - SPRING_CLOUD_CONSUL_HOST=consul
      - APP_HAZELCAST_CLUSTER_ZONE=zone1
    depends_on:
      - consul

  app2:
    build: .
    ports:
      - "8081:8080"
      - "5702:5701"
    environment:
      - SPRING_CLOUD_CONSUL_HOST=consul
      - APP_HAZELCAST_CLUSTER_ZONE=zone2
    depends_on:
      - consul
```

## 网络容错机制

### 1. 心跳检测

- **心跳间隔**: 5秒发送一次心跳
- **心跳超时**: 60秒无响应视为超时
- **最大无心跳**: 300秒后标记为离线

### 2. 自动重连

- **检测机制**: 监控成员健康状态
- **重连策略**: 指数退避重连
- **最大尝试**: 可配置最大重连次数

### 3. 故障恢复

- **恢复窗口**: 避免频繁重连
- **健康检查**: 定期检查集群状态
- **状态重置**: 支持手动重置健康状态

## 监控和运维

### 1. 集群状态监控

```bash
# 查看集群信息
curl -X GET http://localhost:8080/api/distributed-queue/cluster/info

# 查看网络健康
curl -X GET http://localhost:8080/api/distributed-queue/network/health
```

### 2. 日志监控

关键日志信息：

```
INFO  - Hazelcast配置初始化完成
INFO  - 集群配置 - 名称: login-management-cluster, 端口: 5701, 区域: zone1
INFO  - 网络配置 - 心跳间隔: 5s, 心跳超时: 60s, 最大无心跳: 300s
INFO  - 自动重连配置 - 启用: true, 最大尝试: 5, 初始退避: 1s
INFO  - 从Consul获取的集群成员: [192.168.1.100:5701, 192.168.1.101:5701]
```

### 3. 性能调优

- **心跳参数**: 根据网络环境调整心跳间隔
- **重连策略**: 根据业务需求调整重连参数
- **区域配置**: 根据部署环境配置区域

## 故障排除

### 1. Consul连接问题

检查Consul配置：

```yaml
spring:
  cloud:
    consul:
      host: your-consul-host
      port: 8500
      discovery:
        enabled: true
```

### 2. 网络分区问题

启用网络容错：

```yaml
app:
  hazelcast:
    network:
      fault-tolerance:
        enabled: true
        auto-reconnect:
          enabled: true
```

### 3. 区域配置问题

配置区域感知：

```yaml
app:
  hazelcast:
    cluster:
      zone: your-zone-name
```

## 最佳实践

1. **网络配置**: 根据网络环境调整心跳和超时参数
2. **重连策略**: 使用指数退避避免网络风暴
3. **监控告警**: 设置网络健康监控告警
4. **区域规划**: 合理规划区域分布
5. **备份策略**: 配置适当的备份数量

## 注意事项

1. Consul服务发现需要网络连通性
2. 网络容错会增加系统开销
3. 区域配置影响数据分布
4. 重连机制可能影响数据一致性
5. 需要监控网络容错组件的性能 