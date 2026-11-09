package com.vinay.jobbot.service;

import com.vinay.jobbot.dto.AshbyJobDto;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fetches job listings from the Ashby public job board API.
 *
 * API: POST https://api.ashbyhq.com/posting-api/job-board/{company}
 * Body: {} (empty JSON object, no auth required)
 * Returns: {"jobs": [...], "organizationName": "..."}
 *
 * Companies using Ashby: Vercel, Retool, Rippling, Linear, Figma (some boards), etc.
 * Find a company's Ashby slug from: https://jobs.ashbyhq.com/{slug}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AshbyFetchService {

    private static final String ASHBY_API =
            "https://api.ashbyhq.com/posting-api/job-board/{company}";

    private static final long RATE_LIMIT_MS = 1_000;

    private final RestClient restClient;
    private final JobListingRepository jobListingRepository;

    /**
     * Fetches all jobs for a list of Ashby company slugs.
     */
    public int fetchAll(List<String> companySlugs) {
        int total = 0;
        for (String slug : companySlugs) {
            try {
                int newJobs = fetchFromAshby(slug);
                total += newJobs;
                log.info("Ashby: fetched {} new jobs from '{}'", newJobs, slug);
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Ashby: error fetching '{}': {}", slug, e.getMessage());
            }
        }
        return total;
    }

    /**
     * Fetches jobs for a single Ashby company slug.
     */
    public int fetchFromAshby(String companySlug) {
        log.debug("Ashby: fetching jobs for '{}'", companySlug);

        AshbyJobDto.AshbyBoardResponse response = restClient.post()
                .uri(ASHBY_API, companySlug)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())   // empty body required by Ashby API
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new RuntimeException(
                            "Ashby API error for '" + companySlug + "': " + resp.getStatusCode());
                })
                .body(AshbyJobDto.AshbyBoardResponse.class);

        if (response == null || response.getJobs() == null) return 0;

        // Use organizationName if available, otherwise fall back to slug
        String companyName = response.getOrganizationName() != null
                ? response.getOrganizationName() : companySlug;

        int saved = 0;
        for (AshbyJobDto dto : response.getJobs()) {
            if (!dto.isListed()) continue;  // skip unlisted jobs

            String url = dto.getJobUrl();
            if (url == null || jobListingRepository.findBySourceUrl(url).isPresent()) continue;

            String description = dto.getDescriptionPlain() != null
                    ? dto.getDescriptionPlain()
                    : stripHtml(dto.getDescriptionHtml());

            JobListing listing = JobListing.builder()
                    .title(dto.getTitle())
                    .company(companyName)
                    .location(dto.getLocationName() != null ? dto.getLocationName() : "Unknown")
                    .description(description)
                    .sourceUrl(url)
                    .source("ashby")
                    .fetchedAt(LocalDateTime.now())
                    .build();

            jobListingRepository.save(listing);
            saved++;
        }
        return saved;
    }

    /** Naive HTML tag stripper — description fallback when plain text isn't provided */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("\\s{2,}", " ")
                   .trim();
    }
}
