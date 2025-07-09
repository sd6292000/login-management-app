package com.wilsonkeh.loginmanagement.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Hazelcast Cache Configuration with Caffeine Fallback
 * Provides resilient caching with automatic fallback to local cache when Hazelcast is unavailable
 */
@Slf4j
@Configuration
@EnableCaching
public class HazelcastCacheConfig {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Value("${app.cache.hazelcast.enabled:true}")
    private boolean hazelcastCacheEnabled;

    @Value("${app.cache.fallback.enabled:true}")
    private boolean fallbackCacheEnabled;

    @Value("${app.cache.fallback.max-size:1000}")
    private int fallbackMaxSize;

    @Value("${app.cache.fallback.expire-after-write-seconds:300}")
    private int fallbackExpireAfterWriteSeconds;

    @Value("${app.cache.fallback.expire-after-access-seconds:600}")
    private int fallbackExpireAfterAccessSeconds;

    /**
     * Primary Hazelcast Cache Manager
     */
    @Bean
    @Primary
    public CacheManager hazelcastCacheManager() {
        if (!hazelcastCacheEnabled) {
            log.warn("Hazelcast cache is disabled, using fallback cache only");
            return fallbackCacheManager();
        }

        try {
            HazelcastCacheManager cacheManager = new HazelcastCacheManager(hazelcastInstance);
            log.info("Hazelcast cache manager initialized successfully");
            return cacheManager;
        } catch (Exception e) {
            log.error("Failed to initialize Hazelcast cache manager: {}", e.getMessage(), e);
            if (fallbackCacheEnabled) {
                log.info("Falling back to Caffeine cache manager");
                return fallbackCacheManager();
            }
            throw e;
        }
    }

    /**
     * Fallback Caffeine Cache Manager
     */
    @Bean
    public CacheManager fallbackCacheManager() {
        if (!fallbackCacheEnabled) {
            log.warn("Fallback cache is disabled");
            return null;
        }

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(fallbackMaxSize)
                .expireAfterWrite(fallbackExpireAfterWriteSeconds, TimeUnit.SECONDS)
                .expireAfterAccess(fallbackExpireAfterAccessSeconds, TimeUnit.SECONDS)
                .recordStats());

        log.info("Caffeine fallback cache manager initialized with max size: {}, expire after write: {}s, expire after access: {}s",
                fallbackMaxSize, fallbackExpireAfterWriteSeconds, fallbackExpireAfterAccessSeconds);
        return cacheManager;
    }

    /**
     * Resilient Cache Manager that automatically falls back to Caffeine when Hazelcast fails
     */
    @Bean
    public CacheManager resilientCacheManager() {
        return new ResilientCacheManager(hazelcastCacheManager(), fallbackCacheManager());
    }

    /**
     * Resilient Cache Manager Implementation
     */
    public static class ResilientCacheManager implements CacheManager {

        private final CacheManager primaryCacheManager;
        private final CacheManager fallbackCacheManager;
        private volatile boolean useFallback = false;

        public ResilientCacheManager(CacheManager primaryCacheManager, CacheManager fallbackCacheManager) {
            this.primaryCacheManager = primaryCacheManager;
            this.fallbackCacheManager = fallbackCacheManager;
        }

        @Override
        public Cache getCache(String name) {
            if (useFallback || primaryCacheManager == null) {
                return fallbackCacheManager != null ? fallbackCacheManager.getCache(name) : null;
            }

            try {
                Cache cache = primaryCacheManager.getCache(name);
                if (cache == null) {
                    log.warn("Cache '{}' not found in primary cache manager, using fallback", name);
                    return fallbackCacheManager != null ? fallbackCacheManager.getCache(name) : null;
                }
                return new ResilientCache(cache, fallbackCacheManager != null ? fallbackCacheManager.getCache(name) : null);
            } catch (Exception e) {
                log.error("Error accessing primary cache '{}': {}", name, e.getMessage());
                useFallback = true;
                return fallbackCacheManager != null ? fallbackCacheManager.getCache(name) : null;
            }
        }

        @Override
        public java.util.Collection<String> getCacheNames() {
            try {
                if (useFallback || primaryCacheManager == null) {
                    return fallbackCacheManager != null ? fallbackCacheManager.getCacheNames() : java.util.Collections.emptyList();
                }
                return primaryCacheManager.getCacheNames();
            } catch (Exception e) {
                log.error("Error getting cache names from primary cache manager: {}", e.getMessage());
                useFallback = true;
                return fallbackCacheManager != null ? fallbackCacheManager.getCacheNames() : java.util.Collections.emptyList();
            }
        }

        /**
         * Reset to use primary cache manager
         */
        public void resetToPrimary() {
            useFallback = false;
            log.info("Reset to use primary cache manager");
        }

        /**
         * Force use of fallback cache manager
         */
        public void forceFallback() {
            useFallback = true;
            log.info("Forced to use fallback cache manager");
        }

        /**
         * Check if currently using fallback
         */
        public boolean isUsingFallback() {
            return useFallback;
        }
    }

    /**
     * Resilient Cache Implementation
     */
    public static class ResilientCache implements Cache {

        private final Cache primaryCache;
        private final Cache fallbackCache;
        private volatile boolean useFallback = false;

        public ResilientCache(Cache primaryCache, Cache fallbackCache) {
            this.primaryCache = primaryCache;
            this.fallbackCache = fallbackCache;
        }

        @Override
        public String getName() {
            return primaryCache.getName();
        }

        @Override
        public Object getNativeCache() {
            return useFallback ? fallbackCache.getNativeCache() : primaryCache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            if (useFallback || primaryCache == null) {
                return fallbackCache != null ? fallbackCache.get(key) : null;
            }

            try {
                ValueWrapper value = primaryCache.get(key);
                if (value != null && fallbackCache != null) {
                    // Update fallback cache with value from primary cache
                    fallbackCache.put(key, value.get());
                }
                return value;
            } catch (Exception e) {
                log.error("Error getting value from primary cache for key '{}': {}", key, e.getMessage());
                useFallback = true;
                return fallbackCache != null ? fallbackCache.get(key) : null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (useFallback || primaryCache == null) {
                return fallbackCache != null ? fallbackCache.get(key, type) : null;
            }

            try {
                T value = primaryCache.get(key, type);
                if (value != null && fallbackCache != null) {
                    // Update fallback cache with value from primary cache
                    fallbackCache.put(key, value);
                }
                return value;
            } catch (Exception e) {
                log.error("Error getting value from primary cache for key '{}': {}", key, e.getMessage());
                useFallback = true;
                return fallbackCache != null ? fallbackCache.get(key, type) : null;
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (useFallback || primaryCache == null) {
                if (fallbackCache != null) {
                    fallbackCache.put(key, value);
                }
                return;
            }

            try {
                primaryCache.put(key, value);
                // Also update fallback cache
                if (fallbackCache != null) {
                    fallbackCache.put(key, value);
                }
            } catch (Exception e) {
                log.error("Error putting value to primary cache for key '{}': {}", key, e.getMessage());
                useFallback = true;
                if (fallbackCache != null) {
                    fallbackCache.put(key, value);
                }
            }
        }

        @Override
        public void evict(Object key) {
            if (useFallback || primaryCache == null) {
                if (fallbackCache != null) {
                    fallbackCache.evict(key);
                }
                return;
            }

            try {
                primaryCache.evict(key);
                // Also evict from fallback cache
                if (fallbackCache != null) {
                    fallbackCache.evict(key);
                }
            } catch (Exception e) {
                log.error("Error evicting value from primary cache for key '{}': {}", key, e.getMessage());
                useFallback = true;
                if (fallbackCache != null) {
                    fallbackCache.evict(key);
                }
            }
        }

        @Override
        public void clear() {
            if (useFallback || primaryCache == null) {
                if (fallbackCache != null) {
                    fallbackCache.clear();
                }
                return;
            }

            try {
                primaryCache.clear();
                // Also clear fallback cache
                if (fallbackCache != null) {
                    fallbackCache.clear();
                }
            } catch (Exception e) {
                log.error("Error clearing primary cache: {}", e.getMessage());
                useFallback = true;
                if (fallbackCache != null) {
                    fallbackCache.clear();
                }
            }
        }

        /**
         * Reset to use primary cache
         */
        public void resetToPrimary() {
            useFallback = false;
        }

        /**
         * Force use of fallback cache
         */
        public void forceFallback() {
            useFallback = true;
        }

        /**
         * Check if currently using fallback
         */
        public boolean isUsingFallback() {
            return useFallback;
        }
    }
} 