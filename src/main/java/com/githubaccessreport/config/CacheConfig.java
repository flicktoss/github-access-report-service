package com.githubaccessreport.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ACCESS_REPORT_CACHE = "accessReports";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(ACCESS_REPORT_CACHE);
    }
}
