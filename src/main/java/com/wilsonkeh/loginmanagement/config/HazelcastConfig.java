package com.wilsonkeh.loginmanagement.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.cluster.impl.ClusterServiceImpl;
import com.hazelcast.spi.properties.ClusterProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hazelcast配置类
 * 使用Java配置和Consul服务发现，支持多区域备份和网络容错
 */
@Slf4j
@Configuration
public class HazelcastConfig {

    public static final String DEDUPLICATION_MAP_NAME = "login-record-deduplication";
    public static final String TASK_QUEUE_NAME = "login-record-task-queue";
    public static final String CLUSTER_NAME = "login-management-cluster";
    public static final String SERVICE_NAME = "login-management-app";

    @Value("${app.hazelcast.cluster.name:login-management-cluster}")
    private String clusterName;

    @Value("${app.hazelcast.network.port:5701}")
    private int port;

    @Value("${app.hazelcast.network.port-auto-increment:true}")
    private boolean portAutoIncrement;

    @Value("${app.hazelcast.network.heartbeat.interval-seconds:5}")
    private int heartbeatIntervalSeconds;

    @Value("${app.hazelcast.network.heartbeat.timeout-seconds:60}")
    private int heartbeatTimeoutSeconds;

    @Value("${app.hazelcast.network.max-no-heartbeat-seconds:300}")
    private int maxNoHeartbeatSeconds;

    @Value("${app.hazelcast.network.icmp.enabled:true}")
    private boolean icmpEnabled;

    @Value("${app.hazelcast.network.icmp.timeout-seconds:10}")
    private int icmpTimeoutSeconds;

    @Value("${app.hazelcast.network.icmp.ttl:255}")
    private int icmpTtl;

    @Value("${app.hazelcast.network.icmp.parallel-mode:true}")
    private boolean icmpParallelMode;

    @Value("${app.hazelcast.cluster.zone:default}")
    private String zone;

    @Value("${app.hazelcast.cluster.backup-count:1}")
    private int backupCount;

    @Value("${app.hazelcast.cluster.async-backup-count:1}")
    private int asyncBackupCount;

    @Value("${app.hazelcast.cluster.max-join-seconds:300}")
    private int maxJoinSeconds;

    @Value("${app.hazelcast.cluster.auto-rejoin.enabled:true}")
    private boolean autoRejoinEnabled;

    @Value("${app.hazelcast.cluster.auto-rejoin.max-attempts:5}")
    private int autoRejoinMaxAttempts;

    @Value("${app.hazelcast.cluster.auto-rejoin.initial-backoff-seconds:1}")
    private int autoRejoinInitialBackoffSeconds;

    @Value("${app.hazelcast.cluster.auto-rejoin.max-backoff-seconds:60}")
    private int autoRejoinMaxBackoffSeconds;

    @Value("${app.hazelcast.cluster.auto-rejoin.backoff-multiplier:2.0}")
    private double autoRejoinBackoffMultiplier;

    @Value("${app.hazelcast.consul.enabled:true}")
    private boolean consulEnabled;

    @Value("${app.hazelcast.consul.refresh-interval-seconds:30}")
    private int consulRefreshIntervalSeconds;

    @Autowired
    private DiscoveryClient discoveryClient;

    private HazelcastInstance hazelcastInstance;
    private final AtomicBoolean isRejoining = new AtomicBoolean(false);

    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = createHazelcastConfig();
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        
        // 添加成员监听器
        hazelcastInstance.getCluster().addMembershipListener(new CustomMembershipListener());
        
        log.info("Hazelcast实例创建成功 - 集群名称: {}, 端口: {}, 区域: {}", 
                clusterName, port, zone);
        
        return hazelcastInstance;
    }

    private Config createHazelcastConfig() {
        Config config = new Config();
        config.setClusterName(clusterName);
        
        // 网络配置
        NetworkConfig networkConfig = createNetworkConfig();
        config.setNetworkConfig(networkConfig);
        
        // 集群配置
        ClusterConfig clusterConfig = createClusterConfig();
        config.setClusterConfig(clusterConfig);
        
        // 分区组配置（支持多区域）
        PartitionGroupConfig partitionGroupConfig = createPartitionGroupConfig();
        config.setPartitionGroupConfig(partitionGroupConfig);
        
        // 分布式Map配置
        MapConfig deduplicationMapConfig = createDeduplicationMapConfig();
        config.addMapConfig(deduplicationMapConfig);
        
        // 分布式队列配置
        QueueConfig queueConfig = createQueueConfig();
        config.addQueueConfig(queueConfig);
        
        // 系统属性配置
        configureSystemProperties(config);
        
        return config;
    }

    private NetworkConfig createNetworkConfig() {
        NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(portAutoIncrement);
        
        // 加入配置
        JoinConfig joinConfig = new JoinConfig();
        
        if (consulEnabled) {
            // 使用Consul服务发现
            joinConfig.setTcpIpConfig(createConsulTcpIpConfig());
        } else {
            // 使用默认的TCP/IP配置
            joinConfig.setTcpIpConfig(createDefaultTcpIpConfig());
        }
        
        // 禁用多播
        joinConfig.getMulticastConfig().setEnabled(false);
        
        networkConfig.setJoin(joinConfig);
        
        // 心跳配置
        networkConfig.setConnectionTimeoutSeconds(heartbeatTimeoutSeconds);
        
        return networkConfig;
    }

    private TcpIpConfig createConsulTcpIpConfig() {
        TcpIpConfig tcpIpConfig = new TcpIpConfig();
        tcpIpConfig.setEnabled(true);
        
        // 初始成员列表（从Consul获取）
        List<String> members = getConsulMembers();
        for (String member : members) {
            tcpIpConfig.addMember(member);
        }
        
        log.info("从Consul获取的集群成员: {}", members);
        return tcpIpConfig;
    }

    private TcpIpConfig createDefaultTcpIpConfig() {
        TcpIpConfig tcpIpConfig = new TcpIpConfig();
        tcpIpConfig.setEnabled(true);
        tcpIpConfig.addMember("127.0.0.1:5701");
        tcpIpConfig.addMember("127.0.0.1:5702");
        tcpIpConfig.addMember("127.0.0.1:5703");
        return tcpIpConfig;
    }

    private ClusterConfig createClusterConfig() {
        ClusterConfig clusterConfig = new ClusterConfig();
        
        // 自动重新加入配置
        if (autoRejoinEnabled) {
            clusterConfig.setAutoRejoinEnabled(true);
        }
        
        return clusterConfig;
    }

    private PartitionGroupConfig createPartitionGroupConfig() {
        PartitionGroupConfig partitionGroupConfig = new PartitionGroupConfig();
        partitionGroupConfig.setEnabled(true);
        partitionGroupConfig.setGroupType(PartitionGroupConfig.MemberGroupType.ZONE_AWARE);
        
        // 配置区域感知
        MemberGroupConfig memberGroupConfig = new MemberGroupConfig();
        memberGroupConfig.addInterface("zone-" + zone);
        partitionGroupConfig.addMemberGroupConfig(memberGroupConfig);
        
        return partitionGroupConfig;
    }

    private MapConfig createDeduplicationMapConfig() {
        MapConfig mapConfig = new MapConfig(DEDUPLICATION_MAP_NAME);
        mapConfig.setTimeToLiveSeconds(3600);
        mapConfig.setMaxIdleSeconds(1800);
        mapConfig.setBackupCount(backupCount);
        mapConfig.setAsyncBackupCount(asyncBackupCount);
        
        // 驱逐配置
        EvictionConfig evictionConfig = new EvictionConfig();
        evictionConfig.setEvictionPolicy(EvictionPolicy.LRU);
        evictionConfig.setMaxSizePolicy(MaxSizePolicy.PER_NODE);
        evictionConfig.setSize(10000);
        mapConfig.setEvictionConfig(evictionConfig);
        
        // 统计配置
        mapConfig.setStatisticsEnabled(true);
        
        return mapConfig;
    }

    private QueueConfig createQueueConfig() {
        QueueConfig queueConfig = new QueueConfig(TASK_QUEUE_NAME);
        queueConfig.setMaxSize(10000);
        queueConfig.setBackupCount(backupCount);
        queueConfig.setAsyncBackupCount(asyncBackupCount);
        queueConfig.setStatisticsEnabled(true);
        return queueConfig;
    }

    private void configureSystemProperties(Config config) {
        // 心跳配置
        config.setProperty(ClusterProperty.HEARTBEAT_INTERVAL_SECONDS.getName(), 
                String.valueOf(heartbeatIntervalSeconds));
        config.setProperty(ClusterProperty.MAX_NO_HEARTBEAT_SECONDS.getName(), 
                String.valueOf(maxNoHeartbeatSeconds));
        
        // ICMP配置
        config.setProperty(ClusterProperty.ICMP_ENABLED.getName(), 
                String.valueOf(icmpEnabled));
        config.setProperty(ClusterProperty.ICMP_TIMEOUT_SECONDS.getName(), 
                String.valueOf(icmpTimeoutSeconds));
        config.setProperty(ClusterProperty.ICMP_TTL.getName(), 
                String.valueOf(icmpTtl));
        config.setProperty(ClusterProperty.ICMP_PARALLEL_MODE.getName(), 
                String.valueOf(icmpParallelMode));
        
        // 加入超时配置
        config.setProperty(ClusterProperty.MAX_JOIN_SECONDS.getName(), 
                String.valueOf(maxJoinSeconds));
    }

    private List<String> getConsulMembers() {
        try {
            return discoveryClient.getInstances(SERVICE_NAME)
                    .stream()
                    .map(serviceInstance -> serviceInstance.getHost() + ":" + 
                            (serviceInstance.getPort() + 1000)) // Hazelcast端口通常是应用端口+1000
                    .toList();
        } catch (Exception e) {
            log.warn("从Consul获取成员列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 定时刷新Consul成员列表
     */
    @Scheduled(fixedDelayString = "${app.hazelcast.consul.refresh-interval-seconds:30}000")
    public void refreshConsulMembers() {
        if (!consulEnabled || hazelcastInstance == null) {
            return;
        }

        try {
            List<String> newMembers = getConsulMembers();
            if (!newMembers.isEmpty()) {
                log.debug("刷新Consul成员列表: {}", newMembers);
                // 这里可以添加逻辑来动态更新集群成员
            }
        } catch (Exception e) {
            log.error("刷新Consul成员列表失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 自定义成员监听器
     */
    private class CustomMembershipListener implements MembershipListener {
        
        @Override
        public void memberAdded(MembershipEvent membershipEvent) {
            log.info("成员加入集群: {} - {}", 
                    membershipEvent.getMember().getAddress(), 
                    membershipEvent.getMember().getUuid());
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            log.warn("成员离开集群: {} - {}", 
                    membershipEvent.getMember().getAddress(), 
                    membershipEvent.getMember().getUuid());
            
            // 检查是否是当前节点被移除
            if (membershipEvent.getMember().getUuid().equals(
                    hazelcastInstance.getCluster().getLocalMember().getUuid())) {
                handleSelfRemoval();
            }
        }

        private void handleSelfRemoval() {
            if (isRejoining.compareAndSet(false, true)) {
                log.warn("检测到当前节点被移除，尝试重新加入集群");
                
                // 在后台线程中处理重新加入
                new Thread(() -> {
                    try {
                        rejoinCluster();
                    } finally {
                        isRejoining.set(false);
                    }
                }, "hazelcast-rejoin-thread").start();
            }
        }
    }

    /**
     * 重新加入集群
     */
    private void rejoinCluster() {
        int attempts = 0;
        int backoffSeconds = autoRejoinInitialBackoffSeconds;
        
        while (attempts < autoRejoinMaxAttempts) {
            attempts++;
            log.info("尝试重新加入集群，第{}次尝试", attempts);
            
            try {
                // 等待一段时间后重试
                Thread.sleep(backoffSeconds * 1000L);
                
                // 检查集群状态
                if (hazelcastInstance != null && 
                    hazelcastInstance.getLifecycleService().isRunning()) {
                    
                    // 尝试重新连接
                    if (hazelcastInstance.getCluster().getMembers().size() > 1) {
                        log.info("成功重新加入集群");
                        return;
                    }
                }
                
                // 指数退避
                backoffSeconds = Math.min(
                    (int) (backoffSeconds * autoRejoinBackoffMultiplier),
                    autoRejoinMaxBackoffSeconds
                );
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("重新加入集群被中断");
                break;
            } catch (Exception e) {
                log.error("重新加入集群失败，第{}次尝试: {}", attempts, e.getMessage());
            }
        }
        
        log.error("重新加入集群失败，已达到最大尝试次数: {}", autoRejoinMaxAttempts);
    }

    @PostConstruct
    public void init() {
        log.info("Hazelcast配置初始化完成");
        log.info("集群配置 - 名称: {}, 端口: {}, 区域: {}, 备份数: {}", 
                clusterName, port, zone, backupCount);
        log.info("网络配置 - 心跳间隔: {}s, 心跳超时: {}s, 最大无心跳: {}s", 
                heartbeatIntervalSeconds, heartbeatTimeoutSeconds, maxNoHeartbeatSeconds);
        log.info("自动重连配置 - 启用: {}, 最大尝试: {}, 初始退避: {}s", 
                autoRejoinEnabled, autoRejoinMaxAttempts, autoRejoinInitialBackoffSeconds);
    }

    @PreDestroy
    public void destroy() {
        if (hazelcastInstance != null) {
            log.info("正在关闭Hazelcast实例");
            hazelcastInstance.shutdown();
        }
    }
} 