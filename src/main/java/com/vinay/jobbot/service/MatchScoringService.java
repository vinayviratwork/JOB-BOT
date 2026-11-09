package com.vinay.jobbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scores each JobListing against the user's resume using FREE AI (Groq/Ollama).
 * Returns a score 0-100 and a 2-3 sentence reasoning string.
 * Rate-limited to 1 request/second between scorings.
 */
@Service
@Slf4j
public class MatchScoringService {

    private static final long RATE_LIMIT_MS = 1_000;

    private static final String SYSTEM_PROMPT =
        "You are an expert technical recruiter and career coach. " +
        "Evaluate how well a candidate's resume matches a job description. " +
        "Respond ONLY with a strict JSON object — no markdown, no extra text: " +
        "{\"score\": <integer 0-100>, \"reasoning\": \"<2-3 sentence honest explanation>\"} " +
        "Be honest about gaps. Score 70+ means strong match.";

    private final AiClientService aiClientService;
    private final JobListingRepository jobListingRepository;
    private final ObjectMapper objectMapper;
    private final ResumeStorageService resumeStorageService;

    public MatchScoringService(AiClientService aiClientService,
                               JobListingRepository jobListingRepository,
                               ObjectMapper objectMapper,
                               ResumeStorageService resumeStorageService) {
        this.aiClientService      = aiClientService;
        this.jobListingRepository = jobListingRepository;
        this.objectMapper         = objectMapper;
        this.resumeStorageService = resumeStorageService;
    }

    /**
     * Scores all unscored JobListings in the database.
     * @return count of listings scored
     */
    public int scoreAllUnscored() {
        if (!resumeStorageService.hasResume()) {
            log.warn("Scoring skipped: no resume loaded. Upload via POST /api/resume/upload");
            return 0;
        }

        List<JobListing> unscored = jobListingRepository.findByMatchScoreIsNull();
        log.info("Scoring {} unscored job listings using {} (FREE)",
                unscored.size(), aiClientService.getProviderName());

        int count = 0;
        for (JobListing job : unscored) {
            try {
                scoreJob(job);
                count++;
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during scoring");
                break;
            } catch (Exception e) {
                log.error("Failed to score job id={} '{}': {}", job.getId(), job.getTitle(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * Scores a single JobListing and persists the result.
     */
    public void scoreJob(JobListing job) {
        String userPrompt = buildScoringPrompt(job);
        String raw = aiClientService.complete(SYSTEM_PROMPT, userPrompt, 512);

        ScoreResult result = parseScoreResult(raw);
        job.setMatchScore((double) result.score());
        job.setMatchReasoning(result.reasoning());
        jobListingRepository.save(job);

        log.info("Scored job id={} '{}' score={}", job.getId(), job.getTitle(), result.score());
    }

    private String buildScoringPrompt(JobListing job) {
        return "## Resume\n" + resumeStorageService.getResumeText() +
               "\n\n## Job Listing\n" +
               "Title: "    + job.getTitle()    + "\n" +
               "Company: "  + job.getCompany()  + "\n" +
               "Location: " + job.getLocation() + "\n" +
               "Source: "   + job.getSource()   + "\n\n" +
               "Description:\n" + truncate(job.getDescription(), 4000) +
               "\n\nScore this match.";
    }

    ScoreResult parseScoreResult(String raw) {
        try {
            String cleaned = raw.trim()
                    .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            JsonNode node = objectMapper.readTree(cleaned);
            int score = Math.max(0, Math.min(100, node.path("score").asInt(0)));
            String reasoning = node.path("reasoning").asText("");
            return new ScoreResult(score, reasoning);
        } catch (Exception e) {
            log.warn("Could not parse score JSON: {}", raw);
            return new ScoreResult(0, "Unable to parse score: " + raw);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    public String getResumeText() {
        return resumeStorageService.getResumeText();
    }

    public record ScoreResult(int score, String reasoning) {}
}
