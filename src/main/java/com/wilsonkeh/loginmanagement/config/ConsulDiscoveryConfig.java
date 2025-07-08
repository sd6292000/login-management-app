package com.wilsonkeh.loginmanagement.config;

import com.ecwid.consul.v1.ConsulClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consul服务发现配置
 */
@Slf4j
@Configuration
@EnableDiscoveryClient
public class ConsulDiscoveryConfig {

    @Value("${spring.cloud.consul.host:localhost}")
    private String consulHost;

    @Value("${spring.cloud.consul.port:8500}")
    private int consulPort;

    @Value("${spring.application.name:login-management-app}")
    private String serviceName;

    @Value("${app.hazelcast.consul.service-tag:hazelcast}")
    private String serviceTag;

    @Bean
    public ConsulClient consulClient() {
        ConsulClient client = new ConsulClient(consulHost, consulPort);
        log.info("Consul客户端初始化完成 - 主机: {}, 端口: {}", consulHost, consulPort);
        return client;
    }
} 