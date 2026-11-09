package com.vinay.jobbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinay.jobbot.dto.LeverJobDto;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fetches job listings from the Lever public postings API.
 *
 * API: GET https://api.lever.co/v0/postings/{company}?mode=json
 * - No authentication required — fully public.
 * - Returns a JSON array of posting objects.
 * - Rate-limited to 1 req/sec between companies.
 *
 * Companies using Lever: Shopify, Netflix, GitHub, Atlassian, Duolingo, etc.
 * Find a company's Lever slug from: https://jobs.lever.co/{slug}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeverFetchService {

    private static final String LEVER_API =
            "https://api.lever.co/v0/postings/{company}?mode=json";

    private static final long RATE_LIMIT_MS = 1_000;

    private final RestClient restClient;
    private final JobListingRepository jobListingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fetches all jobs for a list of Lever company slugs.
     *
     * @param companySlugs list of Lever company slugs (e.g. ["shopify", "netflix"])
     * @return total new jobs saved
     */
    public int fetchAll(List<String> companySlugs) {
        int total = 0;
        for (String slug : companySlugs) {
            try {
                int newJobs = fetchFromLever(slug);
                total += newJobs;
                log.info("Lever: fetched {} new jobs from '{}'", newJobs, slug);
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Lever: error fetching '{}': {}", slug, e.getMessage());
            }
        }
        return total;
    }

    /**
     * Fetches jobs for a single Lever company slug.
     */
    public int fetchFromLever(String companySlug) {
        log.debug("Lever: fetching jobs for '{}'", companySlug);

        // Lever returns a raw JSON array, so we read as String and parse manually
        String raw = restClient.get()
                .uri(LEVER_API, companySlug)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new RuntimeException(
                            "Lever API error for '" + companySlug + "': " + resp.getStatusCode());
                })
                .body(String.class);

        if (raw == null || raw.isBlank()) return 0;

        List<LeverJobDto> jobs;
        try {
            jobs = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Lever: failed to parse response for '{}': {}", companySlug, e.getMessage());
            return 0;
        }

        int saved = 0;
        for (LeverJobDto dto : jobs) {
            String url = dto.getHostedUrl();
            if (url == null || jobListingRepository.findBySourceUrl(url).isPresent()) continue;

            String location = dto.getCategoriesObj() != null
                    ? dto.getCategoriesObj().getLocation() : "Remote / Unknown";

            String description = buildDescription(dto);

            JobListing listing = JobListing.builder()
                    .title(dto.getText())
                    .company(companySlug)
                    .location(location != null ? location : "Unknown")
                    .description(description)
                    .sourceUrl(url)
                    .source("lever")
                    .fetchedAt(LocalDateTime.now())
                    .build();

            jobListingRepository.save(listing);
            saved++;
        }
        return saved;
    }

    private String buildDescription(LeverJobDto dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getDescriptionPlain() != null) {
            sb.append(dto.getDescriptionPlain()).append("\n\n");
        }
        if (dto.getLists() != null) {
            for (LeverJobDto.LeverList list : dto.getLists()) {
                if (list.getText() != null) sb.append("## ").append(list.getText()).append("\n");
                if (list.getContent() != null) sb.append(list.getContent()).append("\n\n");
            }
        }
        if (dto.getAdditional() != null && !dto.getAdditional().isBlank()) {
            sb.append("## Additional\n").append(dto.getAdditional());
        }
        return sb.toString();
    }
}
