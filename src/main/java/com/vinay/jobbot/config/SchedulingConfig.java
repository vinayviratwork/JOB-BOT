package com.vinay.jobbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled annotation processing.
 * The actual cron job lives in DailyJobScanScheduler.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
