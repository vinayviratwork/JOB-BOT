package com.vinay.jobbot.service;

import com.vinay.jobbot.entity.Application;
import com.vinay.jobbot.entity.ApplicationStatus;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.ApplicationRepository;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full job-discovery pipeline:
 *   1. Fetch new jobs from all configured sources (Greenhouse, Lever, Ashby, Workable, RemoteOK).
 *   2. Create a DISCOVERED application record for each new job.
 *   3. Score every unscored job against the resume.
 *   4. Auto-generate a DRAFTED cover letter for every job with score >= threshold.
 *   5. Send email digest if notifications are enabled.
 *
 * GUARDRAIL: This service NEVER submits applications automatically.
 * The APPLIED status is set only by a human action via the REST API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationWorkflowService {

    private static final long RATE_LIMIT_MS = 1_000;

    private final JobFetchService jobFetchService;
    private final MatchScoringService matchScoringService;
    private final CoverLetterService coverLetterService;
    private final JobListingRepository jobListingRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailNotificationService emailNotificationService;

    @Value("${jobbot.match-threshold}")
    private int matchThreshold;

    /**
     * Runs the complete pipeline: fetch -> discover -> score -> draft -> notify.
     *
     * @return a summary string describing what was done
     */
    public String runFullPipeline() {
        log.info("=== Starting JobBot pipeline ===");

        // Step 1: Fetch new jobs from all sources
        int newJobs = jobFetchService.fetchAllCompanies();
        log.info("Pipeline step 1 complete: {} new jobs fetched", newJobs);

        // Step 2: Create DISCOVERED application record for every new job that has none yet
        int discovered = createDiscoveredApplications();
        log.info("Pipeline step 2 complete: {} new DISCOVERED applications created", discovered);

        // Step 3: Score all unscored listings
        int scored = matchScoringService.scoreAllUnscored();
        log.info("Pipeline step 3 complete: {} jobs scored", scored);

        // Step 4: Draft cover letters for high-match jobs without one yet
        List<JobListing> highMatch = jobListingRepository
                .findByMatchScoreGreaterThanEqualOrderByMatchScoreDesc((double) matchThreshold);

        List<JobListing> newlyDrafted = new ArrayList<>();
        int drafted = 0;
        for (JobListing job : highMatch) {
            try {
                if (coverLetterService.applicationExists(job.getId())
                        && applicationRepository.findByJobId(job.getId())
                               .map(a -> a.getStatus() != ApplicationStatus.DISCOVERED)
                               .orElse(false)) {
                    continue; // already has a real draft
                }
                coverLetterService.generateCoverLetter(job);
                newlyDrafted.add(job);
                drafted++;
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during cover letter generation");
                break;
            } catch (Exception e) {
                log.error("Failed to generate cover letter for job id={}: {}", job.getId(), e.getMessage());
            }
        }
        log.info("Pipeline step 4 complete: {} cover letters drafted", drafted);

        // Step 5: Send email digest for newly drafted jobs
        if (!newlyDrafted.isEmpty()) {
            emailNotificationService.sendHighMatchDigest(newlyDrafted);
        }

        log.info("=== Pipeline finished ===");
        return "Pipeline complete: %d new jobs, %d discovered, %d scored, %d cover letters drafted"
                .formatted(newJobs, discovered, scored, drafted);
    }

    /**
     * Creates a DISCOVERED Application record for every JobListing
     * that has no Application record yet.
     * This makes the full lifecycle (DISCOVERED -> DRAFTED -> APPLIED) visible.
     */
    private int createDiscoveredApplications() {
        List<JobListing> all = jobListingRepository.findAll();
        int created = 0;
        for (JobListing job : all) {
            if (!applicationRepository.existsByJobId(job.getId())) {
                Application app = Application.builder()
                        .job(job)
                        .status(ApplicationStatus.DISCOVERED)
                        .updatedAt(LocalDateTime.now())
                        .build();
                applicationRepository.save(app);
                created++;
            }
        }
        return created;
    }

    /**
     * Convenience: generate (or regenerate) a cover letter for a single job.
     * Called from POST /api/jobs/{id}/generate-application.
     */
    public Application generateCoverLetterForJob(JobListing job) {
        Application app = coverLetterService.generateCoverLetter(job);
        // Send single-job alert if notifications enabled
        emailNotificationService.sendSingleJobAlert(job);
        return app;
    }
}
