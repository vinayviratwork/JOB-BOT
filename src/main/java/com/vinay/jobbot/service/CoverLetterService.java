package com.vinay.jobbot.service;

import com.vinay.jobbot.entity.Application;
import com.vinay.jobbot.entity.ApplicationStatus;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Generates tailored cover letters using FREE AI (Groq/Ollama).
 *
 * GUARDRAIL: System prompt explicitly instructs AI NEVER to fabricate
 * skills, employers, or experience not present in the candidate's resume.
 * Gaps are addressed honestly — never invented.
 *
 * COST: $0.00 — uses Groq free tier or local Ollama.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CoverLetterService {

    // IMPORTANT: Do NOT weaken or remove the honesty rules below.
    private static final String SYSTEM_PROMPT =
        "You are a professional cover letter writer helping a job seeker.\n" +
        "STRICT RULES you MUST follow:\n" +
        "1. Only use facts present in the provided resume text.\n" +
        "2. Do NOT invent, fabricate, or imply skills, employers, certifications,\n" +
        "   projects, or experience that are not in the resume.\n" +
        "3. If the job asks for something the resume does NOT show, acknowledge\n" +
        "   the gap honestly and mention willingness to learn — never fabricate.\n" +
        "4. Write in warm, professional first-person tone.\n" +
        "5. Keep to 3-4 paragraphs, under 400 words.\n" +
        "6. Return only the letter body — no date, address, or salutation header.";

    private final AiClientService aiClientService;
    private final ApplicationRepository applicationRepository;
    private final MatchScoringService matchScoringService;

    /**
     * Generates a cover letter for a job and saves it as DRAFTED Application.
     * If an Application already exists, it is refreshed with a new draft.
     */
    public Application generateCoverLetter(JobListing job) {
        String resumeText = matchScoringService.getResumeText();
        String userPrompt = buildPrompt(job, resumeText);

        log.info("Generating cover letter for job id={} '{}' using {} (FREE)",
                job.getId(), job.getTitle(), aiClientService.getProviderName());

        String letterText = aiClientService.complete(SYSTEM_PROMPT, userPrompt, 1024);

        Application application = applicationRepository.findByJobId(job.getId())
                .orElseGet(() -> Application.builder()
                        .job(job)
                        .status(ApplicationStatus.DRAFTED)
                        .build());

        application.setGeneratedCoverLetter(letterText);
        application.setStatus(ApplicationStatus.DRAFTED);
        application.setUpdatedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(application);
        log.info("Cover letter saved as Application id={}", saved.getId());
        return saved;
    }

    public boolean applicationExists(Long jobId) {
        return applicationRepository.existsByJobId(jobId);
    }

    private String buildPrompt(JobListing job, String resumeText) {
        return "## My Resume\n" + resumeText +
               "\n\n## Job I Am Applying For\n" +
               "Title: "       + job.getTitle()    + "\n" +
               "Company: "     + job.getCompany()  + "\n" +
               "Location: "    + job.getLocation() + "\n\n" +
               "Job Description:\n" + truncate(job.getDescription(), 3000) +
               "\n\nWrite a tailored cover letter body following your rules.";
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
