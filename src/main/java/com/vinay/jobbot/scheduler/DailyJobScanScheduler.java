package com.vinay.jobbot.scheduler;

import com.vinay.jobbot.service.ApplicationWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the full job-discovery pipeline automatically once per day at 09:00.
 * Manual trigger is available via POST /api/jobs/scan-now.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyJobScanScheduler {

    private final ApplicationWorkflowService workflowService;

    /**
     * Cron: every day at 09:00 server time.
     * To change the schedule, update the cron expression here.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void runDailyScan() {
        log.info("DailyJobScanScheduler: starting scheduled scan");
        try {
            String result = workflowService.runFullPipeline();
            log.info("DailyJobScanScheduler: {}", result);
        } catch (Exception e) {
            log.error("DailyJobScanScheduler: pipeline failed — {}", e.getMessage(), e);
        }
    }
}
