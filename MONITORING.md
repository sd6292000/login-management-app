# 队列监控文档

## 概述

本系统使用 Micrometer 和 Prometheus 进行队列监控，支持与 Grafana 集成，提供实时的队列性能指标。

## 监控指标

### 计数器指标 (Counters)

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `queue_tasks_enqueued_total` | 入队任务总数 | `queue`, `application` |
| `queue_tasks_deduplicated_total` | 去重任务总数 | `queue`, `application` |
| `queue_tasks_processed_total` | 处理任务总数 | `queue`, `application` |
| `queue_tasks_failed_total` | 失败任务总数 | `queue`, `application` |
| `queue_batches_processed_total` | 批处理总数 | `queue`, `application` |

### 计时器指标 (Timers)

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `queue_task_processing_time_seconds` | 单个任务处理时间 | `queue`, `application` |
| `queue_batch_processing_time_seconds` | 批处理时间 | `queue`, `application` |

### 仪表指标 (Gauges)

| 指标名称 | 描述 | 标签 |
|---------|------|------|
| `queue_size` | 当前队列大小 | `queue`, `application` |
| `queue_capacity` | 队列容量 | `queue`, `application` |

## 配置

### 应用配置

```yaml
# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active:default}
```

### 队列配置

```yaml
app:
  queue:
    queues:
      login-record-queue:
        enableMetrics: true  # 启用监控
        maxQueueSize: 10000  # 队列容量
```

## 访问端点

### Prometheus 指标端点
- URL: `http://localhost:8080/actuator/prometheus`
- 格式: Prometheus 文本格式
- 用途: Prometheus 抓取指标

### 健康检查端点
- URL: `http://localhost:8080/actuator/health`
- 格式: JSON
- 用途: 应用健康状态检查

### 指标端点
- URL: `http://localhost:8080/actuator/metrics`
- 格式: JSON
- 用途: 查看所有可用指标

## Grafana 集成

### 1. 安装 Grafana
```bash
# Docker 安装
docker run -d -p 3000:3000 grafana/grafana
```

### 2. 配置 Prometheus 数据源
1. 登录 Grafana (http://localhost:3000)
2. 添加数据源 -> Prometheus
3. URL: `http://localhost:9090` (Prometheus 地址)

### 3. 导入仪表板
1. 导入 -> 上传 JSON 文件
2. 选择 `grafana-dashboard.json`
3. 选择 Prometheus 数据源

## Prometheus 查询示例

### 队列大小
```promql
queue_size{application="login-management-app"}
```

### 任务入队速率
```promql
rate(queue_tasks_enqueued_total{application="login-management-app"}[5m])
```

### 任务处理速率
```promql
rate(queue_tasks_processed_total{application="login-management-app"}[5m])
```

### 平均任务处理时间
```promql
rate(queue_task_processing_time_seconds_sum{application="login-management-app"}[5m]) / 
rate(queue_task_processing_time_seconds_count{application="login-management-app"}[5m])
```

### 队列利用率
```promql
queue_size{application="login-management-app"} / 
queue_capacity{application="login-management-app"}
```

## 告警规则

### 队列满告警
```yaml
groups:
  - name: queue_alerts
    rules:
      - alert: QueueFull
        expr: queue_size / queue_capacity > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Queue {{ $labels.queue }} is nearly full"
          description: "Queue {{ $labels.queue }} is {{ $value | humanizePercentage }} full"
```

### 任务处理失败告警
```yaml
      - alert: HighTaskFailureRate
        expr: rate(queue_tasks_failed_total[5m]) / rate(queue_tasks_processed_total[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High task failure rate in queue {{ $labels.queue }}"
          description: "{{ $value | humanizePercentage }} of tasks are failing"
```

## 性能优化建议

### 1. 队列大小监控
- 设置队列大小告警阈值 (80%)
- 监控队列增长趋势
- 及时调整队列容量

### 2. 处理时间监控
- 监控平均处理时间
- 设置处理时间告警
- 优化批处理大小

### 3. 失败率监控
- 监控任务失败率
- 设置失败率告警
- 分析失败原因

### 4. 去重效果监控
- 监控去重率
- 优化去重策略
- 减少重复任务

## 故障排查

### 1. 队列积压
- 检查队列大小指标
- 分析处理时间趋势
- 调整批处理参数

### 2. 高失败率
- 检查任务失败指标
- 查看错误日志
- 验证任务处理器

### 3. 性能下降
- 监控处理时间
- 检查系统资源
- 优化队列配置

## 扩展监控

### 自定义指标
```java
// 添加自定义指标
Counter customCounter = Counter.builder("custom.metric")
    .tag("queue", queueName)
    .description("Custom metric description")
    .register(meterRegistry);
```

### 业务指标
- 任务类型分布
- 用户活跃度
- 系统负载

### 基础设施监控
- CPU 使用率
- 内存使用率
- 磁盘 I/O
- 网络流量 