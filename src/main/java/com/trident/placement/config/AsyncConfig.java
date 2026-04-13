package com.trident.placement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring's @Async support for background tasks.
 *
 * Used by CgpaEligibilityService.assignEligibleStudents() which runs
 * AFTER admin creates a drive — processing potentially hundreds of students
 * without blocking the admin API response.
 *
 * Thread pool configuration:
 *  - Core pool: 2 threads  → always ready for CGPA tasks
 *  - Max pool:  5 threads  → scales up if multiple drives posted quickly
 *  - Queue:    100 tasks   → buffers if all threads are busy
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "cgpaTaskExecutor")
    public Executor cgpaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cgpa-worker-");
        executor.initialize();
        return executor;
    }
}