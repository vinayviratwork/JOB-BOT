package com.vinay.jobbot.controller;

import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import com.vinay.jobbot.service.ApplicationWorkflowService;
import com.vinay.jobbot.service.MatchScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Jobs", description = "Job listing discovery and scanning")
public class JobController {

    private final JobListingRepository jobListingRepository;
    private final ApplicationWorkflowService workflowService;
    private final MatchScoringService matchScoringService;

    @Operation(summary = "List all discovered job listings")
    @GetMapping
    public List<JobListing> getAllJobs() {
        return jobListingRepository.findAll();
    }

    @Operation(summary = "Get high-match jobs sorted by score descending")
    @GetMapping("/matches")
    public List<JobListing> getMatches(
            @RequestParam(defaultValue = "70") double minScore) {
        return jobListingRepository
                .findByMatchScoreGreaterThanEqualOrderByMatchScoreDesc(minScore);
    }

    @Operation(summary = "Manually trigger the full fetch + score + draft pipeline")
    @PostMapping("/scan-now")
    public ResponseEntity<Map<String, String>> scanNow() {
        log.info("Manual scan triggered via POST /api/jobs/scan-now");
        String result = workflowService.runFullPipeline();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @Operation(summary = "Generate (or regenerate) a cover letter for a specific job")
    @PostMapping("/{id}/generate-application")
    public ResponseEntity<?> generateApplication(@PathVariable Long id) {
        return jobListingRepository.findById(id)
                .map(job -> ResponseEntity.ok(workflowService.generateCoverLetterForJob(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Re-score a single job against the current resume.
     * Useful after uploading a new resume — re-runs only this one job, not the full pipeline.
     */
    @Operation(summary = "Re-score a single job against current resume")
    @PostMapping("/{id}/rescore")
    public ResponseEntity<?> rescoreJob(@PathVariable Long id) {
        return jobListingRepository.findById(id)
                .map(job -> {
                    log.info("Manual rescore triggered for job id={} '{}'", id, job.getTitle());
                    matchScoringService.scoreJob(job);
                    // Return the refreshed job
                    return ResponseEntity.ok(jobListingRepository.findById(id).orElse(job));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
