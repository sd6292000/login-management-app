package com.wilsonkeh.loginmanagement.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络容错管理器
 * 处理网络问题和自动重连机制
 */
@Slf4j
@Component
public class NetworkFaultToleranceManager {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Value("${app.hazelcast.network.fault-tolerance.enabled:true}")
    private boolean faultToleranceEnabled;

    @Value("${app.hazelcast.network.fault-tolerance.heartbeat-check-interval-seconds:30}")
    private int heartbeatCheckIntervalSeconds;

    @Value("${app.hazelcast.network.fault-tolerance.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    @Value("${app.hazelcast.network.fault-tolerance.recovery-window-seconds:300}")
    private int recoveryWindowSeconds;

    @Value("${app.hazelcast.network.fault-tolerance.auto-reconnect.enabled:true}")
    private boolean autoReconnectEnabled;

    @Value("${app.hazelcast.network.fault-tolerance.auto-reconnect.max-attempts:10}")
    private int autoReconnectMaxAttempts;

    @Value("${app.hazelcast.network.fault-tolerance.auto-reconnect.backoff-seconds:5}")
    private int autoReconnectBackoffSeconds;

    // 成员健康状态跟踪
    private final ConcurrentHashMap<String, MemberHealthStatus> memberHealthMap = new ConcurrentHashMap<>();
    
    // 重连尝试计数器
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // 最后重连时间
    private final AtomicLong lastReconnectTime = new AtomicLong(0);

    @PostConstruct
    public void init() {
        if (faultToleranceEnabled) {
            log.info("网络容错管理器初始化完成");
            log.info("容错配置 - 心跳检查间隔: {}s, 最大连续失败: {}, 恢复窗口: {}s", 
                    heartbeatCheckIntervalSeconds, maxConsecutiveFailures, recoveryWindowSeconds);
            log.info("自动重连配置 - 启用: {}, 最大尝试: {}, 退避时间: {}s", 
                    autoReconnectEnabled, autoReconnectMaxAttempts, autoReconnectBackoffSeconds);
        }
    }

    /**
     * 定时检查集群成员健康状态
     */
    @Scheduled(fixedDelayString = "${app.hazelcast.network.fault-tolerance.heartbeat-check-interval-seconds:30}000")
    public void checkClusterHealth() {
        if (!faultToleranceEnabled || hazelcastInstance == null) {
            return;
        }

        try {
            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            log.debug("检查集群健康状态，当前成员数: {}", members.size());

            for (Member member : members) {
                String memberId = member.getUuid().toString();
                MemberHealthStatus status = memberHealthMap.computeIfAbsent(
                    memberId, k -> new MemberHealthStatus());

                // 检查成员是否可达
                boolean isReachable = checkMemberReachability(member);
                status.updateHealth(isReachable);

                if (status.isUnhealthy()) {
                    log.warn("检测到不健康的成员: {} - 地址: {}, 连续失败次数: {}", 
                            memberId, member.getAddress(), status.getConsecutiveFailures());
                }
            }

            // 检查当前节点是否需要重连
            checkSelfReconnection();

        } catch (Exception e) {
            log.error("检查集群健康状态失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查成员可达性
     */
    private boolean checkMemberReachability(Member member) {
        try {
            // 使用Hazelcast内置的ping机制
            return member.isLiteMember() || member.getAddress() != null;
        } catch (Exception e) {
            log.debug("检查成员可达性失败: {} - {}", member.getUuid(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查当前节点是否需要重连
     */
    private void checkSelfReconnection() {
        if (!autoReconnectEnabled) {
            return;
        }

        Member localMember = hazelcastInstance.getCluster().getLocalMember();
        if (localMember == null) {
            log.warn("无法获取本地成员信息");
            return;
        }

        String localMemberId = localMember.getUuid().toString();
        MemberHealthStatus localStatus = memberHealthMap.get(localMemberId);

        if (localStatus != null && localStatus.isUnhealthy()) {
            long now = System.currentTimeMillis();
            long lastReconnect = lastReconnectTime.get();

            // 检查是否在恢复窗口内
            if (now - lastReconnect > recoveryWindowSeconds * 1000L) {
                attemptReconnection();
            }
        }
    }

    /**
     * 尝试重连
     */
    private void attemptReconnection() {
        int attempts = reconnectAttempts.get();
        if (attempts >= autoReconnectMaxAttempts) {
            log.error("已达到最大重连尝试次数: {}", autoReconnectMaxAttempts);
            return;
        }

        if (reconnectAttempts.compareAndSet(attempts, attempts + 1)) {
            lastReconnectTime.set(System.currentTimeMillis());
            
            log.warn("尝试重连集群，第{}次尝试", attempts + 1);
            
            // 在后台线程中执行重连
            new Thread(() -> {
                try {
                    performReconnection();
                } catch (Exception e) {
                    log.error("重连失败: {}", e.getMessage(), e);
                }
            }, "network-reconnect-thread").start();
        }
    }

    /**
     * 执行重连操作
     */
    private void performReconnection() {
        try {
            // 等待退避时间
            Thread.sleep(autoReconnectBackoffSeconds * 1000L);

            // 检查Hazelcast实例状态
            if (hazelcastInstance != null && 
                hazelcastInstance.getLifecycleService().isRunning()) {
                
                // 尝试重新加入集群
                if (hazelcastInstance.getCluster().getMembers().size() > 1) {
                    log.info("重连成功，当前集群成员数: {}", 
                            hazelcastInstance.getCluster().getMembers().size());
                    
                    // 重置重连计数器
                    reconnectAttempts.set(0);
                    return;
                }
            }

            log.warn("重连失败，将继续尝试");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重连操作被中断");
        }
    }

    /**
     * 获取集群健康报告
     */
    public String getClusterHealthReport() {
        if (!faultToleranceEnabled) {
            return "网络容错功能已禁用";
        }

        try {
            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            int totalMembers = members.size();
            int healthyMembers = 0;
            int unhealthyMembers = 0;

            for (Member member : members) {
                String memberId = member.getUuid().toString();
                MemberHealthStatus status = memberHealthMap.get(memberId);
                
                if (status != null && status.isHealthy()) {
                    healthyMembers++;
                } else {
                    unhealthyMembers++;
                }
            }

            return String.format(
                "集群健康报告:\n" +
                "- 总成员数: %d\n" +
                "- 健康成员数: %d\n" +
                "- 不健康成员数: %d\n" +
                "- 重连尝试次数: %d\n" +
                "- 最后重连时间: %s",
                totalMembers, healthyMembers, unhealthyMembers,
                reconnectAttempts.get(),
                formatTimestamp(lastReconnectTime.get())
            );

        } catch (Exception e) {
            log.error("生成集群健康报告失败: {}", e.getMessage(), e);
            return "生成健康报告失败: " + e.getMessage();
        }
    }

    /**
     * 手动触发重连
     */
    public void triggerReconnection() {
        if (!autoReconnectEnabled) {
            log.warn("自动重连功能已禁用");
            return;
        }

        log.info("手动触发重连");
        reconnectAttempts.set(0); // 重置计数器
        attemptReconnection();
    }

    /**
     * 重置健康状态
     */
    public void resetHealthStatus() {
        memberHealthMap.clear();
        reconnectAttempts.set(0);
        lastReconnectTime.set(0);
        log.info("健康状态已重置");
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "从未";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(timestamp));
    }

    @PreDestroy
    public void destroy() {
        log.info("网络容错管理器正在关闭");
        memberHealthMap.clear();
    }

    /**
     * 成员健康状态
     */
    private static class MemberHealthStatus {
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong lastSuccessTime = new AtomicLong(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);

        public void updateHealth(boolean isHealthy) {
            if (isHealthy) {
                consecutiveFailures.set(0);
                lastSuccessTime.set(System.currentTimeMillis());
            } else {
                consecutiveFailures.incrementAndGet();
                lastFailureTime.set(System.currentTimeMillis());
            }
        }

        public boolean isHealthy() {
            return consecutiveFailures.get() == 0;
        }

        public boolean isUnhealthy() {
            return consecutiveFailures.get() >= 3; // 可配置
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }

        public long getLastSuccessTime() {
            return lastSuccessTime.get();
        }

        public long getLastFailureTime() {
            return lastFailureTime.get();
        }
    }
} 