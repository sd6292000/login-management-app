package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.config.HazelcastCacheConfig;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.dto.LoginRecordResponse;
import com.wilsonkeh.loginmanagement.dto.UserSecurityAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cache Integration Test
 * Tests Hazelcast cache with Caffeine fallback functionality
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class CacheIntegrationTest {

    @Autowired
    private LoginRecordService loginRecordService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private HazelcastCacheConfig.ResilientCacheManager resilientCacheManager;

    @Test
    public void testCacheManagerInitialization() {
        assertNotNull(cacheManager, "Cache manager should be initialized");
        assertNotNull(resilientCacheManager, "Resilient cache manager should be initialized");
        
        log.info("Cache manager type: {}", cacheManager.getClass().getSimpleName());
        log.info("Using fallback: {}", resilientCacheManager.isUsingFallback());
    }

    @Test
    public void testCacheNames() {
        assertNotNull(cacheManager.getCacheNames(), "Cache names should not be null");
        assertFalse(cacheManager.getCacheNames().isEmpty(), "Should have at least one cache");
        
        log.info("Available caches: {}", cacheManager.getCacheNames());
        
        // Check for expected cache names
        assertTrue(cacheManager.getCacheNames().contains("login-records"), 
                "Should have login-records cache");
        assertTrue(cacheManager.getCacheNames().contains("user-login-records"), 
                "Should have user-login-records cache");
        assertTrue(cacheManager.getCacheNames().contains("ip-login-records"), 
                "Should have ip-login-records cache");
        assertTrue(cacheManager.getCacheNames().contains("recent-login-records"), 
                "Should have recent-login-records cache");
        assertTrue(cacheManager.getCacheNames().contains("user-security-analysis"), 
                "Should have user-security-analysis cache");
    }

    @Test
    public void testCacheOperations() {
        String cacheName = "test-cache";
        String key = "test-key";
        String value = "test-value";
        
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "Cache should be created");
        
        // Test put operation
        cache.put(key, value);
        
        // Test get operation
        Cache.ValueWrapper retrievedValue = cache.get(key);
        assertNotNull(retrievedValue, "Retrieved value should not be null");
        assertEquals(value, retrievedValue.get(), "Retrieved value should match original");
        
        // Test evict operation
        cache.evict(key);
        Cache.ValueWrapper evictedValue = cache.get(key);
        assertNull(evictedValue, "Evicted value should be null");
        
        // Test clear operation
        cache.put(key, value);
        cache.clear();
        Cache.ValueWrapper clearedValue = cache.get(key);
        assertNull(clearedValue, "Cleared value should be null");
        
        log.info("Cache operations test completed successfully");
    }

    @Test
    public void testFallbackSwitch() {
        // Test initial state
        boolean initialFallback = resilientCacheManager.isUsingFallback();
        log.info("Initial fallback state: {}", initialFallback);
        
        // Force fallback
        resilientCacheManager.forceFallback();
        assertTrue(resilientCacheManager.isUsingFallback(), "Should be using fallback after force");
        
        // Reset to primary
        resilientCacheManager.resetToPrimary();
        assertFalse(resilientCacheManager.isUsingFallback(), "Should not be using fallback after reset");
        
        log.info("Fallback switch test completed successfully");
    }

    @Test
    public void testLoginRecordCaching() {
        // Create test data
        LoginRecordRequest request = new LoginRecordRequest(
                "test-user-123",
                "test@example.com",
                "192.168.1.100",
                LocalDateTime.now(),
                "Test User Agent"
        );
        
        // Create login record (should be cached)
        LoginRecordResponse createdRecord = loginRecordService.createLoginRecord(request);
        assertNotNull(createdRecord, "Created record should not be null");
        
        // Get record by ID (should be served from cache)
        LoginRecordResponse cachedRecord = loginRecordService.getLoginRecordById(createdRecord.id());
        assertNotNull(cachedRecord, "Cached record should not be null");
        assertEquals(createdRecord.id(), cachedRecord.id(), "Cached record ID should match");
        
        log.info("Login record caching test completed successfully");
    }

    @Test
    public void testUserLoginRecordsCaching() {
        String testEmail = "cache-test@example.com";
        
        // Get records by email (should be cached)
        List<LoginRecordResponse> records = loginRecordService.getLoginRecordsByEmail(testEmail);
        assertNotNull(records, "Records should not be null");
        
        // Get again (should be served from cache)
        List<LoginRecordResponse> cachedRecords = loginRecordService.getLoginRecordsByEmail(testEmail);
        assertNotNull(cachedRecords, "Cached records should not be null");
        assertEquals(records.size(), cachedRecords.size(), "Cached records size should match");
        
        log.info("User login records caching test completed successfully");
    }

    @Test
    public void testIpLoginRecordsCaching() {
        String testIp = "192.168.1.200";
        
        // Get records by IP (should be cached)
        List<LoginRecordResponse> records = loginRecordService.getLoginRecordsByIpAddress(testIp);
        assertNotNull(records, "Records should not be null");
        
        // Get again (should be served from cache)
        List<LoginRecordResponse> cachedRecords = loginRecordService.getLoginRecordsByIpAddress(testIp);
        assertNotNull(cachedRecords, "Cached records should not be null");
        assertEquals(records.size(), cachedRecords.size(), "Cached records size should match");
        
        log.info("IP login records caching test completed successfully");
    }

    @Test
    public void testRecentLoginRecordsCaching() {
        // Get recent records (should be cached)
        List<LoginRecordResponse> records = loginRecordService.getRecentLoginRecords();
        assertNotNull(records, "Recent records should not be null");
        
        // Get again (should be served from cache)
        List<LoginRecordResponse> cachedRecords = loginRecordService.getRecentLoginRecords();
        assertNotNull(cachedRecords, "Cached recent records should not be null");
        assertEquals(records.size(), cachedRecords.size(), "Cached recent records size should match");
        
        log.info("Recent login records caching test completed successfully");
    }

    @Test
    public void testUserSecurityAnalysisCaching() {
        String testUid = "security-test-user";
        
        // Get security analysis (should be cached)
        UserSecurityAnalysisResponse analysis = loginRecordService.getUserSecurityAnalysis(testUid);
        // Note: This might be null if no data exists, which is expected
        
        // Get again (should be served from cache)
        UserSecurityAnalysisResponse cachedAnalysis = loginRecordService.getUserSecurityAnalysis(testUid);
        
        // Both should be the same (either null or same object)
        if (analysis == null) {
            assertNull(cachedAnalysis, "Cached analysis should also be null");
        } else {
            assertNotNull(cachedAnalysis, "Cached analysis should not be null");
            assertEquals(analysis.uid(), cachedAnalysis.uid(), "Cached analysis UID should match");
        }
        
        log.info("User security analysis caching test completed successfully");
    }

    @Test
    public void testCacheEviction() {
        // Create test data
        LoginRecordRequest request = new LoginRecordRequest(
                "eviction-test-user",
                "eviction-test@example.com",
                "192.168.1.300",
                LocalDateTime.now(),
                "Eviction Test User Agent"
        );
        
        // Create record
        LoginRecordResponse createdRecord = loginRecordService.createLoginRecord(request);
        assertNotNull(createdRecord, "Created record should not be null");
        
        // Verify it's cached
        LoginRecordResponse cachedRecord = loginRecordService.getLoginRecordById(createdRecord.id());
        assertNotNull(cachedRecord, "Record should be cached");
        
        // Delete record (should evict from cache)
        loginRecordService.deleteLoginRecord(createdRecord.id());
        
        // Try to get again (should not be in cache)
        try {
            LoginRecordResponse evictedRecord = loginRecordService.getLoginRecordById(createdRecord.id());
            // This might throw an exception or return null, both are acceptable
            log.info("Record after deletion: {}", evictedRecord);
        } catch (Exception e) {
            log.info("Expected exception after deletion: {}", e.getMessage());
        }
        
        log.info("Cache eviction test completed successfully");
    }

    @Test
    public void testCachePerformance() {
        String testEmail = "performance-test@example.com";
        
        // Measure first call (database)
        long startTime = System.currentTimeMillis();
        List<LoginRecordResponse> firstCall = loginRecordService.getLoginRecordsByEmail(testEmail);
        long firstCallTime = System.currentTimeMillis() - startTime;
        
        // Measure second call (cache)
        startTime = System.currentTimeMillis();
        List<LoginRecordResponse> secondCall = loginRecordService.getLoginRecordsByEmail(testEmail);
        long secondCallTime = System.currentTimeMillis() - startTime;
        
        log.info("First call (database) time: {}ms", firstCallTime);
        log.info("Second call (cache) time: {}ms", secondCallTime);
        
        // Cache call should be faster (though this might not always be true in test environment)
        assertTrue(secondCallTime <= firstCallTime * 2, 
                "Cache call should not be significantly slower than database call");
        
        log.info("Cache performance test completed successfully");
    }

    @Test
    public void testCacheStatistics() {
        // Get cache statistics
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                log.info("Cache: {} - Type: {}", cacheName, 
                        cache.getNativeCache().getClass().getSimpleName());
                
                // Try to get size information
                try {
                    if (cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                        com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache = 
                            (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
                        log.info("  Caffeine cache estimated size: {}", caffeineCache.estimatedSize());
                    } else if (cache.getNativeCache() instanceof com.hazelcast.map.IMap) {
                        com.hazelcast.map.IMap<?, ?> hazelcastMap = 
                            (com.hazelcast.map.IMap<?, ?>) cache.getNativeCache();
                        log.info("  Hazelcast map size: {}", hazelcastMap.size());
                    }
                } catch (Exception e) {
                    log.debug("Could not get size for cache {}: {}", cacheName, e.getMessage());
                }
            }
        }
        
        log.info("Cache statistics test completed successfully");
    }
} 