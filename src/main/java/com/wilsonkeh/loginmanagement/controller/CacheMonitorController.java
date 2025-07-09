package com.wilsonkeh.loginmanagement.controller;

import com.wilsonkeh.loginmanagement.config.HazelcastCacheConfig;
import com.wilsonkeh.loginmanagement.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache Monitor Controller
 * Provides endpoints to monitor and manage cache status
 */
@Slf4j
@RestController
@RequestMapping("/api/cache")
public class CacheMonitorController {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private HazelcastCacheConfig.ResilientCacheManager resilientCacheManager;

    /**
     * Get cache status information
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Check if using fallback
            boolean usingFallback = resilientCacheManager.isUsingFallback();
            status.put("usingFallback", usingFallback);
            status.put("cacheType", usingFallback ? "Caffeine (Fallback)" : "Hazelcast (Primary)");
            
            // Get cache names
            status.put("cacheNames", cacheManager.getCacheNames());
            
            // Get cache statistics
            Map<String, Object> cacheStats = new HashMap<>();
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("name", cache.getName());
                    stats.put("nativeCache", cache.getNativeCache().getClass().getSimpleName());
                    
                    // Try to get size if available
                    try {
                        if (cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                            com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache = 
                                (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
                            stats.put("estimatedSize", caffeineCache.estimatedSize());
                        } else if (cache.getNativeCache() instanceof com.hazelcast.map.IMap) {
                            com.hazelcast.map.IMap<?, ?> hazelcastMap = 
                                (com.hazelcast.map.IMap<?, ?>) cache.getNativeCache();
                            stats.put("size", hazelcastMap.size());
                        }
                    } catch (Exception e) {
                        log.debug("Could not get size for cache {}: {}", cacheName, e.getMessage());
                    }
                    
                    cacheStats.put(cacheName, stats);
                }
            }
            status.put("cacheStatistics", cacheStats);
            
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cache status retrieved successfully", status));
        } catch (Exception e) {
            log.error("Failed to get cache status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get cache status: " + e.getMessage(), null));
        }
    }

    /**
     * Reset to use primary cache manager
     */
    @PostMapping("/reset-to-primary")
    public ResponseEntity<ApiResponse<String>> resetToPrimary() {
        try {
            resilientCacheManager.resetToPrimary();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Reset to primary cache manager", "Now using Hazelcast cache"));
        } catch (Exception e) {
            log.error("Failed to reset to primary cache: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to reset to primary cache: " + e.getMessage(), null));
        }
    }

    /**
     * Force use of fallback cache manager
     */
    @PostMapping("/force-fallback")
    public ResponseEntity<ApiResponse<String>> forceFallback() {
        try {
            resilientCacheManager.forceFallback();
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Forced to use fallback cache", "Now using Caffeine cache"));
        } catch (Exception e) {
            log.error("Failed to force fallback cache: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to force fallback cache: " + e.getMessage(), null));
        }
    }

    /**
     * Clear specific cache
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<ApiResponse<String>> clearCache(@PathVariable String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cache cleared successfully", "Cache '" + cacheName + "' has been cleared"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("ERROR", "Cache not found", "Cache '" + cacheName + "' does not exist"));
            }
        } catch (Exception e) {
            log.error("Failed to clear cache '{}': {}", cacheName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to clear cache: " + e.getMessage(), null));
        }
    }

    /**
     * Clear all caches
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<ApiResponse<String>> clearAllCaches() {
        try {
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "All caches cleared successfully", "All caches have been cleared"));
        } catch (Exception e) {
            log.error("Failed to clear all caches: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to clear all caches: " + e.getMessage(), null));
        }
    }

    /**
     * Get cache value by key
     */
    @GetMapping("/{cacheName}/{key}")
    public ResponseEntity<ApiResponse<Object>> getCacheValue(@PathVariable String cacheName, @PathVariable String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper value = cache.get(key);
                if (value != null) {
                    return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cache value retrieved successfully", value.get()));
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<>("ERROR", "Cache value not found", "No value found for key '" + key + "' in cache '" + cacheName + "'"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("ERROR", "Cache not found", "Cache '" + cacheName + "' does not exist"));
            }
        } catch (Exception e) {
            log.error("Failed to get cache value for key '{}' in cache '{}': {}", key, cacheName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to get cache value: " + e.getMessage(), null));
        }
    }

    /**
     * Put value into cache
     */
    @PutMapping("/{cacheName}/{key}")
    public ResponseEntity<ApiResponse<String>> putCacheValue(@PathVariable String cacheName, 
                                                           @PathVariable String key, 
                                                           @RequestBody Object value) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
                return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cache value stored successfully", "Value stored for key '" + key + "' in cache '" + cacheName + "'"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("ERROR", "Cache not found", "Cache '" + cacheName + "' does not exist"));
            }
        } catch (Exception e) {
            log.error("Failed to put cache value for key '{}' in cache '{}': {}", key, cacheName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to put cache value: " + e.getMessage(), null));
        }
    }

    /**
     * Evict value from cache
     */
    @DeleteMapping("/{cacheName}/{key}")
    public ResponseEntity<ApiResponse<String>> evictCacheValue(@PathVariable String cacheName, @PathVariable String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Cache value evicted successfully", "Value evicted for key '" + key + "' from cache '" + cacheName + "'"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>("ERROR", "Cache not found", "Cache '" + cacheName + "' does not exist"));
            }
        } catch (Exception e) {
            log.error("Failed to evict cache value for key '{}' from cache '{}': {}", key, cacheName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("ERROR", "Failed to evict cache value: " + e.getMessage(), null));
        }
    }
} 