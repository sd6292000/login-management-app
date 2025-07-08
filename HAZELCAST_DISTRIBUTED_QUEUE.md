# Hazelcast分布式队列集成

## 概述

本项目已集成Hazelcast分布式队列，支持跨机器的任务去重和处理。通过Hazelcast集群，多个应用实例可以共享同一个队列，避免重复处理相同的任务。

## 功能特性

- **分布式去重**: 使用Hazelcast分布式Map实现跨机器的任务去重
- **分布式队列**: 使用Hazelcast分布式队列存储任务
- **自动故障转移**: 支持集群节点故障自动转移
- **可配置**: 支持通过配置文件调整各种参数
- **监控支持**: 提供完整的队列状态监控API

## 配置说明

### 1. Maven依赖

已在`pom.xml`中添加Hazelcast依赖：

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
```

### 2. 应用配置

在`application.yml`中配置分布式队列参数：

```yaml
app:
  queue:
    distributed:
      enabled: true                    # 启用分布式队列
      max-size: 10000                 # 最大队列大小
      deduplication:
        enabled: true                 # 启用去重
        ttl-seconds: 3600            # 去重记录TTL
        max-idle-seconds: 1800       # 最大空闲时间
      processor:
        enabled: true                # 启用处理器
        threads: 2                   # 处理线程数
        poll-timeout: 5              # 轮询超时时间
```

### 3. Hazelcast配置

Hazelcast配置文件位于`src/main/resources/hazelcast.yml`：

```yaml
hazelcast:
  cluster-name: login-management-cluster
  
  network:
    port: 5701
    port-auto-increment: true
    join:
      tcp-ip:
        enabled: true
        member-list:
          - 127.0.0.1:5701
          - 127.0.0.1:5702
          - 127.0.0.1:5703
```

## 核心组件

### 1. DistributedDeduplicatingPriorityBlockingQueue

分布式去重优先级阻塞队列，主要功能：

- 使用Hazelcast分布式Map进行去重
- 使用Hazelcast分布式队列存储任务
- 支持优先级排序
- 提供完整的统计信息

### 2. DistributedTaskQueueManager

分布式任务队列管理器，提供：

- 队列生命周期管理
- 集群健康检查
- 统计信息收集
- 过期记录清理

### 3. DistributedLoginRecordTaskProcessor

分布式登录记录任务处理器，功能包括：

- 多线程任务处理
- 自动重试机制
- 定时状态检查
- 优雅关闭

## API接口

### 分布式队列监控API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/distributed-queue/status` | 获取队列状态 |
| GET | `/api/distributed-queue/processor/status` | 获取处理器状态 |
| POST | `/api/distributed-queue/processor/start` | 启动处理器 |
| POST | `/api/distributed-queue/processor/stop` | 停止处理器 |
| POST | `/api/distributed-queue/cleanup` | 清理过期记录 |
| GET | `/api/distributed-queue/cluster/info` | 获取集群信息 |
| GET | `/api/distributed-queue/stats` | 获取统计信息 |
| DELETE | `/api/distributed-queue/clear` | 清空队列 |

### 示例请求

```bash
# 获取队列状态
curl -X GET http://localhost:8080/api/distributed-queue/status

# 获取集群信息
curl -X GET http://localhost:8080/api/distributed-queue/cluster/info

# 启动处理器
curl -X POST http://localhost:8080/api/distributed-queue/processor/start
```

## 部署说明

### 1. 单机部署

```bash
# 启动单个实例
java -jar login-management-app.jar
```

### 2. 集群部署

```bash
# 启动第一个节点
java -jar login-management-app.jar --server.port=8080

# 启动第二个节点
java -jar login-management-app.jar --server.port=8081

# 启动第三个节点
java -jar login-management-app.jar --server.port=8082
```

### 3. Docker部署

```dockerfile
FROM openjdk:17-jre-slim
COPY target/login-management-app.jar app.jar
EXPOSE 8080 5701
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# 构建镜像
docker build -t login-management-app .

# 启动集群
docker run -d -p 8080:8080 -p 5701:5701 --name node1 login-management-app
docker run -d -p 8081:8080 -p 5702:5701 --name node2 login-management-app
docker run -d -p 8082:8080 -p 5703:5701 --name node3 login-management-app
```

## 监控和运维

### 1. 集群健康检查

```bash
curl -X GET http://localhost:8080/api/distributed-queue/cluster/info
```

### 2. 队列状态监控

```bash
curl -X GET http://localhost:8080/api/distributed-queue/stats
```

### 3. 日志监控

关键日志信息：

```
INFO  - 分布式去重队列初始化完成
INFO  - 分布式任务队列管理器初始化完成
INFO  - 分布式登录记录任务处理器初始化完成
INFO  - 集群信息: [Member [127.0.0.1]:5701]
```

### 4. 性能调优

- **队列大小**: 根据业务量调整`max-size`
- **处理线程数**: 根据CPU核心数调整`threads`
- **去重TTL**: 根据业务需求调整`ttl-seconds`
- **网络配置**: 根据网络环境调整集群成员列表

## 故障排除

### 1. 集群连接问题

检查网络配置和防火墙设置：

```yaml
hazelcast:
  network:
    join:
      tcp-ip:
        member-list:
          - 192.168.1.100:5701  # 使用实际IP地址
          - 192.168.1.101:5701
```

### 2. 序列化问题

确保所有任务类都实现了`IdentifiedDataSerializable`接口：

```java
public class LoginRecordTask implements IdentifiedDataSerializable {
    @Override
    public int getFactoryId() {
        return LoginRecordDataSerializableFactory.FACTORY_ID;
    }
    
    @Override
    public int getClassId() {
        return LoginRecordDataSerializableFactory.LOGIN_RECORD_TASK_TYPE;
    }
}
```

### 3. 内存问题

调整JVM参数：

```bash
java -Xms2g -Xmx4g -jar login-management-app.jar
```

## 最佳实践

1. **集群规模**: 建议3-5个节点，避免过多节点影响性能
2. **网络配置**: 使用专用网络进行集群通信
3. **监控告警**: 设置队列大小和处理延迟的告警
4. **备份策略**: 配置适当的备份数量确保数据安全
5. **版本兼容**: 确保所有节点使用相同版本的Hazelcast

## 注意事项

1. 分布式队列会增加网络开销，需要合理配置
2. 去重功能会消耗内存，需要监控内存使用情况
3. 集群节点故障会影响队列可用性，建议配置备份
4. 序列化性能会影响整体性能，需要优化序列化实现 