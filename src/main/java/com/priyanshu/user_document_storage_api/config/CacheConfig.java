package com.priyanshu.user_document_storage_api.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

    public static final String FILE_LIST_CACHE = "fileList";
    public static final String PRESIGNED_DOWNLOAD_URL_CACHE = "presignedDownloadUrl";

    @Bean
    @ConfigurationProperties(prefix = "cache.file-list")
    public CacheProperties fileListCacheProperties() {
        return new CacheProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "cache.presigned-download-url")
    public CacheProperties presignedDownloadUrlCacheProperties() {
        return new CacheProperties();
    }

    @Bean
    public CacheManager cacheManager(
            CacheProperties fileListCacheProperties,
            CacheProperties presignedDownloadUrlCacheProperties) {

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(FILE_LIST_CACHE, PRESIGNED_DOWNLOAD_URL_CACHE));
        manager.registerCustomCache(FILE_LIST_CACHE,
                buildCache(fileListCacheProperties));
        manager.registerCustomCache(PRESIGNED_DOWNLOAD_URL_CACHE,
                buildCache(presignedDownloadUrlCacheProperties));
        return manager;
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> buildCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .expireAfterWrite(props.getTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(props.getMaxSize())
                .recordStats()
                .build();
    }

    /**
     * Holds per-cache tunables sourced from application properties.
     * <p>
     * Example properties:
     * <pre>
     *   cache.file-list.ttl-seconds=60
     *   cache.file-list.max-size=500
     * </pre>
     */
    public static class CacheProperties {
        private long ttlSeconds = 60;
        private long maxSize = 500;

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
    }
}
