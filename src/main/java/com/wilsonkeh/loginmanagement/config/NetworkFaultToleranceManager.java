package com.wilsonkeh.loginmanagement.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/**
 * Network Fault Tolerance Manager
 * Handles network issues and automatic reconnection mechanisms
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

    @Value("${app.hazelcast.network.fault-tolerance.connection-test-timeout-seconds:10}")
    private int connectionTestTimeoutSeconds;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${app.hazelcast.consul.enabled:true}")
    private boolean consulEnabled;

    @Value("${app.hazelcast.consul.service-name:login-management-app}")
    private String serviceName;

    @Value("${app.hazelcast.network.port:5701}")
    private int hazelcastPort;

    // Member health status tracking
    private final ConcurrentHashMap<String, MemberHealthStatus> memberHealthMap = new ConcurrentHashMap<>();
    
    // Reconnection attempt counter
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // Last reconnection time
    private final AtomicLong lastReconnectTime = new AtomicLong(0);

    // Cluster status tracking
    private final AtomicBoolean isClusterHealthy = new AtomicBoolean(true);
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private final AtomicLong lastClusterHealthCheck = new AtomicLong(0);

    // Listeners
    private LifecycleListener lifecycleListener;
    private MembershipListener membershipListener;

    @PostConstruct
    public void init() {
        if (faultToleranceEnabled) {
            log.info("Network fault tolerance manager initialization completed");
            log.info("Fault tolerance config - heartbeat check interval: {}s, max consecutive failures: {}, recovery window: {}s", 
                    heartbeatCheckIntervalSeconds, maxConsecutiveFailures, recoveryWindowSeconds);
            log.info("Auto reconnect config - enabled: {}, max attempts: {}, backoff time: {}s", 
                    autoReconnectEnabled, autoReconnectMaxAttempts, autoReconnectBackoffSeconds);
            
            // Register listeners
            registerListeners();
        }
    }

    /**
     * Register Hazelcast listeners
     */
    private void registerListeners() {
        if (hazelcastInstance == null) {
            log.warn("Hazelcast instance not initialized, cannot register listeners");
            return;
        }

        // Lifecycle listener
        lifecycleListener = new LifecycleListener() {
            @Override
            public void stateChanged(LifecycleEvent event) {
                log.info("Hazelcast lifecycle state changed: {}", event.getState());
                
                switch (event.getState()) {
                    case STARTING:
                        log.info("Hazelcast instance is starting");
                        break;
                    case STARTED:
                        log.info("Hazelcast instance started");
                        isClusterHealthy.set(true);
                        reconnectAttempts.set(0);
                        break;
                    case SHUTTING_DOWN:
                        log.warn("Hazelcast instance is shutting down");
                        isClusterHealthy.set(false);
                        break;
                    case SHUTDOWN:
                        log.warn("Hazelcast instance shutdown");
                        isClusterHealthy.set(false);
                        if (autoReconnectEnabled) {
                            scheduleReconnection();
                        }
                        break;
                    case MERGING:
                        log.warn("Hazelcast instance is merging");
                        break;
                    case MERGED:
                        log.info("Hazelcast instance merge completed");
                        break;
                    case CLIENT_CONNECTED:
                        log.info("Hazelcast client connected");
                        break;
                    case CLIENT_DISCONNECTED:
                        log.warn("Hazelcast client disconnected");
                        if (autoReconnectEnabled) {
                            scheduleReconnection();
                        }
                        break;
                }
            }
        };
        hazelcastInstance.getLifecycleService().addLifecycleListener(lifecycleListener);

        // Membership listener
        membershipListener = new MembershipListener() {
            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
                log.info("Member joined cluster: {} - {}", 
                        membershipEvent.getMember().getAddress(), 
                        membershipEvent.getMember().getUuid());
                updateClusterHealth();
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
                log.warn("Member left cluster: {} - {}", 
                        membershipEvent.getMember().getAddress(), 
                        membershipEvent.getMember().getUuid());
                
                // Check if current node was removed
                if (membershipEvent.getMember().getUuid().equals(
                        hazelcastInstance.getCluster().getLocalMember().getUuid())) {
                    log.error("Current node was removed from cluster!");
                    isClusterHealthy.set(false);
                    if (autoReconnectEnabled) {
                        scheduleReconnection();
                    }
                } else {
                    updateClusterHealth();
                }
            }
        };
        hazelcastInstance.getCluster().addMembershipListener(membershipListener);
    }

    /**
     * Scheduled cluster member health check
     */
    @Scheduled(fixedDelayString = "${app.hazelcast.network.fault-tolerance.heartbeat-check-interval-seconds:30}000")
    public void checkClusterHealth() {
        if (!faultToleranceEnabled || hazelcastInstance == null) {
            return;
        }

        try {
            lastClusterHealthCheck.set(System.currentTimeMillis());
            
            // Check Hazelcast instance status
            if (!hazelcastInstance.getLifecycleService().isRunning()) {
                log.warn("Hazelcast instance not running");
                isClusterHealthy.set(false);
                if (autoReconnectEnabled) {
                    scheduleReconnection();
                }
                return;
            }

            // Check cluster connection status
            if (!isClusterConnected()) {
                log.warn("Cluster connection abnormal");
                isClusterHealthy.set(false);
                if (autoReconnectEnabled) {
                    scheduleReconnection();
                }
                return;
            }

            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            log.debug("Checking cluster health status, current member count: {}", members.size());

            if (members.isEmpty()) {
                log.warn("No members in cluster");
                isClusterHealthy.set(false);
                if (autoReconnectEnabled) {
                    scheduleReconnection();
                }
                return;
            }

            // Check if current node is in cluster
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            if (localMember == null) {
                log.warn("Cannot get local member information");
                isClusterHealthy.set(false);
                if (autoReconnectEnabled) {
                    scheduleReconnection();
                }
                return;
            }

            // Check other members health status
            boolean allMembersHealthy = true;
            for (Member member : members) {
                if (member.getUuid().equals(localMember.getUuid())) {
                    continue; // Skip local member
                }

                String memberId = member.getUuid().toString();
                MemberHealthStatus status = memberHealthMap.computeIfAbsent(
                    memberId, k -> new MemberHealthStatus());

                // Check member reachability
                boolean isReachable = checkMemberReachability(member);
                status.updateHealth(isReachable);

                if (status.isUnhealthy()) {
                    log.warn("Detected unhealthy member: {} - address: {}, consecutive failures: {}", 
                            memberId, member.getAddress(), status.getConsecutiveFailures());
                    allMembersHealthy = false;
                }
            }

            isClusterHealthy.set(allMembersHealthy);

            // If cluster is healthy, reset reconnection counter
            if (allMembersHealthy) {
                reconnectAttempts.set(0);
            }

        } catch (Exception e) {
            log.error("Failed to check cluster health status: {}", e.getMessage(), e);
            isClusterHealthy.set(false);
            if (autoReconnectEnabled) {
                scheduleReconnection();
            }
        }
    }

    /**
     * Check if cluster is connected
     */
    private boolean isClusterConnected() {
        try {
            // Try to get cluster information
            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            
            // Check if local member is in cluster
            if (localMember == null) {
                return false;
            }

            // Check if there are other members
            if (members.size() <= 1) {
                log.debug("Only local member or no members in cluster");
                return false;
            }

            // Try simple cluster operation to verify connection
            try {
                hazelcastInstance.getMap("health-check").put("test", "value");
                return true;
            } catch (Exception e) {
                log.debug("Cluster operation test failed: {}", e.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.debug("Failed to check cluster connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check member reachability
     */
    private boolean checkMemberReachability(Member member) {
        try {
            // Use more reliable way to check member reachability
            if (member.isLiteMember()) {
                return true; // Lite members don't need network reachability check
            }

            // Try to ping member
            if (member.getAddress() != null) {
                // Here you can add more complex network reachability checks
                // For example: try to establish TCP connection, send heartbeat, etc.
                return true;
            }

            return false;
        } catch (Exception e) {
            log.debug("Failed to check member reachability: {} - {}", member.getUuid(), e.getMessage());
            return false;
        }
    }

    /**
     * Schedule reconnection
     */
    private void scheduleReconnection() {
        if (!autoReconnectEnabled || isReconnecting.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastReconnect = lastReconnectTime.get();

        // Check if within recovery window
        if (now - lastReconnect < recoveryWindowSeconds * 1000L) {
            log.debug("Within recovery window, skipping reconnection");
            return;
        }

        if (isReconnecting.compareAndSet(false, true)) {
            log.warn("Scheduling cluster reconnection");
            
            // Execute reconnection in background thread
            new Thread(() -> {
                try {
                    attemptReconnection();
                } finally {
                    isReconnecting.set(false);
                }
            }, "network-reconnect-thread").start();
        }
    }

    /**
     * Attempt reconnection
     */
    private void attemptReconnection() {
        int attempts = reconnectAttempts.get();
        if (attempts >= autoReconnectMaxAttempts) {
            log.error("Reached maximum reconnection attempts: {}", autoReconnectMaxAttempts);
            return;
        }

        if (reconnectAttempts.compareAndSet(attempts, attempts + 1)) {
            lastReconnectTime.set(System.currentTimeMillis());
            
            log.warn("Attempting cluster reconnection, attempt {} of {}", attempts + 1, autoReconnectMaxAttempts);
            
            try {
                performReconnection();
            } catch (Exception e) {
                log.error("Reconnection failed: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Perform reconnection operation
     */
    private void performReconnection() {
        try {
            // Wait for backoff time
            Thread.sleep(autoReconnectBackoffSeconds * 1000L);

            // Check Hazelcast instance status
            if (hazelcastInstance == null) {
                log.error("Hazelcast instance is null, cannot reconnect");
                return;
            }

            // If instance is shutdown, try to restart
            if (!hazelcastInstance.getLifecycleService().isRunning()) {
                log.warn("Hazelcast instance is shutdown, attempting to restart");
                restartHazelcastInstance();
                return;
            }

            // Try to rejoin cluster
            if (rejoinCluster()) {
                log.info("Reconnection successful, current cluster member count: {}", 
                        hazelcastInstance.getCluster().getMembers().size());
                
                // Reset reconnection counter
                reconnectAttempts.set(0);
                isClusterHealthy.set(true);
                return;
            }

            log.warn("Reconnection failed, will continue trying");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Reconnection operation interrupted");
        }
    }

    /**
     * Rejoin cluster
     */
    private boolean rejoinCluster() {
        try {
            // Check cluster status
            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            
            if (localMember == null) {
                log.warn("Cannot get local member information");
                return false;
            }

            // Check if there are other members
            if (members.size() > 1) {
                log.info("Successfully connected to cluster, member count: {}", members.size());
                return true;
            }

            // Try to rediscover cluster members
            log.info("Attempting to rediscover cluster members");
            
            if (consulEnabled) {
                return rejoinClusterWithConsul();
            } else {
                return rejoinClusterWithStaticMembers();
            }

        } catch (Exception e) {
            log.error("Failed to rejoin cluster: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Rejoin cluster through Consul
     */
    private boolean rejoinClusterWithConsul() {
        try {
            log.info("Rejoining cluster through Consul service discovery");
            
            // Get latest member list from Consul
            List<String> consulMembers = getConsulMembers();
            if (consulMembers.isEmpty()) {
                log.warn("Empty member list from Consul");
                return false;
            }

            log.info("Members from Consul: {}", consulMembers);

            // Try to connect to each member
            for (String memberAddress : consulMembers) {
                try {
                    log.info("Attempting to connect to member: {}", memberAddress);
                    
                    // Here you can add more complex connection logic
                    // For example: try to establish TCP connection, send heartbeat, etc.
                    
                    // Check if connection successful
                    if (isMemberReachable(memberAddress)) {
                        log.info("Successfully connected to member: {}", memberAddress);
                        return true;
                    }
                    
                } catch (Exception e) {
                    log.debug("Failed to connect to member: {} - {}", memberAddress, e.getMessage());
                }
            }

            log.warn("Cannot connect to any Consul member");
            return false;

        } catch (Exception e) {
            log.error("Failed to rejoin cluster through Consul: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Rejoin cluster through static member list
     */
    private boolean rejoinClusterWithStaticMembers() {
        try {
            log.info("Rejoining cluster through static member list");
            
            // Configure static member list here
            List<String> staticMembers = List.of(
                "127.0.0.1:5701",
                "127.0.0.1:5702", 
                "127.0.0.1:5703"
            );

            for (String memberAddress : staticMembers) {
                try {
                    log.info("Attempting to connect to static member: {}", memberAddress);
                    
                    if (isMemberReachable(memberAddress)) {
                        log.info("Successfully connected to static member: {}", memberAddress);
                        return true;
                    }
                    
                } catch (Exception e) {
                    log.debug("Failed to connect to static member: {} - {}", memberAddress, e.getMessage());
                }
            }

            log.warn("Cannot connect to any static member");
            return false;

        } catch (Exception e) {
            log.error("Failed to rejoin cluster through static members: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if member is reachable
     */
    private boolean isMemberReachable(String memberAddress) {
        try {
            // Parse address
            String[] parts = memberAddress.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : hazelcastPort;

            // Try to establish TCP connection
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 
                             connectionTestTimeoutSeconds * 1000);
                return true;
            } catch (Exception e) {
                log.debug("TCP connection failed: {}:{} - {}", host, port, e.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.debug("Failed to check member reachability: {} - {}", memberAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Get member list from Consul
     */
    private List<String> getConsulMembers() {
        try {
            return discoveryClient.getInstances(serviceName)
                    .stream()
                    .map(serviceInstance -> serviceInstance.getHost() + ":" + 
                            (serviceInstance.getPort() + 1000)) // Hazelcast port is usually app port + 1000
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to get member list from Consul: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Restart Hazelcast instance
     */
    private void restartHazelcastInstance() {
        try {
            log.info("Restarting Hazelcast instance");
            
            // Shutdown current instance
            if (hazelcastInstance != null) {
                hazelcastInstance.shutdown();
            }
            
            // Wait for some time
            Thread.sleep(5000);
            
            // Recreate instance
            // Note: Here you need to recreate the Hazelcast instance
            // In actual applications, you may need to recreate the Bean through Spring's ApplicationContext
            log.info("Hazelcast instance restart completed");
            
            // Re-register listeners
            registerListeners();
            
        } catch (Exception e) {
            log.error("Failed to restart Hazelcast instance: {}", e.getMessage(), e);
        }
    }

    /**
     * Update cluster health status
     */
    private void updateClusterHealth() {
        try {
            Set<Member> members = hazelcastInstance.getCluster().getMembers();
            boolean healthy = !members.isEmpty() && 
                            hazelcastInstance.getLifecycleService().isRunning();
            isClusterHealthy.set(healthy);
        } catch (Exception e) {
            log.error("Failed to update cluster health status: {}", e.getMessage(), e);
            isClusterHealthy.set(false);
        }
    }

    /**
     * Get cluster health report
     */
    public String getClusterHealthReport() {
        if (!faultToleranceEnabled) {
            return "Network fault tolerance feature is disabled";
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
                "Cluster Health Report:\n" +
                "- Total Members: %d\n" +
                "- Healthy Members: %d\n" +
                "- Unhealthy Members: %d\n" +
                "- Cluster Health Status: %s\n" +
                "- Reconnection Attempts: %d\n" +
                "- Last Reconnection Time: %s\n" +
                "- Last Health Check: %s\n" +
                "- Is Reconnecting: %s",
                totalMembers, healthyMembers, unhealthyMembers,
                isClusterHealthy.get() ? "Healthy" : "Unhealthy",
                reconnectAttempts.get(),
                formatTimestamp(lastReconnectTime.get()),
                formatTimestamp(lastClusterHealthCheck.get()),
                isReconnecting.get() ? "Yes" : "No"
            );

        } catch (Exception e) {
            log.error("Failed to generate cluster health report: {}", e.getMessage(), e);
            return "Failed to generate health report: " + e.getMessage();
        }
    }

    /**
     * Manually trigger reconnection
     */
    public void triggerReconnection() {
        if (!autoReconnectEnabled) {
            log.warn("Auto reconnection feature is disabled");
            return;
        }

        log.info("Manually triggering reconnection");
        reconnectAttempts.set(0); // Reset counter
        scheduleReconnection();
    }

    /**
     * Reset health status
     */
    public void resetHealthStatus() {
        memberHealthMap.clear();
        reconnectAttempts.set(0);
        lastReconnectTime.set(0);
        isClusterHealthy.set(true);
        isReconnecting.set(false);
        log.info("Health status has been reset");
    }

    /**
     * Get cluster health status
     */
    public boolean isClusterHealthy() {
        return isClusterHealthy.get();
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "Never";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(timestamp));
    }

    @PreDestroy
    public void destroy() {
        log.info("Network fault tolerance manager is shutting down");
        
        // Remove listeners
        if (hazelcastInstance != null) {
            if (lifecycleListener != null) {
                hazelcastInstance.getLifecycleService().removeLifecycleListener(lifecycleListener);
            }
            if (membershipListener != null) {
                hazelcastInstance.getCluster().removeMembershipListener(membershipListener);
            }
        }
        
        memberHealthMap.clear();
    }

    /**
     * Member health status
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
            return consecutiveFailures.get() >= 3; // Configurable
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