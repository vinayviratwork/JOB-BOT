package com.vinay.jobbot.service;

import com.vinay.jobbot.dto.WorkableJobDto;
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
 * Fetches job listings from the Workable public jobs API.
 *
 * API: GET https://{subdomain}.workable.com/api/v1/jobs
 * - No authentication required for published public listings.
 * - Supports pagination via the next_page field.
 * - Max 100 jobs per page.
 *
 * Companies using Workable: Thousands of SMBs and mid-size companies globally.
 * The subdomain is usually the company name: e.g. "acme" → acme.workable.com
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkableFetchService {

    private static final String WORKABLE_API =
            "https://{subdomain}.workable.com/api/v1/jobs";

    private static final long RATE_LIMIT_MS = 1_000;
    private static final int  MAX_PAGES = 10; // safety cap

    private final RestClient restClient;
    private final JobListingRepository jobListingRepository;

    /**
     * Fetches all jobs for a list of Workable subdomains.
     */
    public int fetchAll(List<String> subdomains) {
        int total = 0;
        for (String subdomain : subdomains) {
            try {
                int newJobs = fetchFromWorkable(subdomain);
                total += newJobs;
                log.info("Workable: fetched {} new jobs from '{}'", newJobs, subdomain);
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Workable: error fetching '{}': {}", subdomain, e.getMessage());
            }
        }
        return total;
    }

    /**
     * Fetches all pages of jobs for a single Workable subdomain.
     */
    public int fetchFromWorkable(String subdomain) {
        log.debug("Workable: fetching jobs for subdomain '{}'", subdomain);

        int saved = 0;
        String url = WORKABLE_API.replace("{subdomain}", subdomain);
        int page = 0;

        while (url != null && page < MAX_PAGES) {
            WorkableJobDto.WorkableBoardResponse response;
            try {
                response = restClient.get()
                        .uri(url)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            throw new RuntimeException(
                                    "Workable API error for '" + subdomain + "': " + resp.getStatusCode());
                        })
                        .body(WorkableJobDto.WorkableBoardResponse.class);
            } catch (Exception e) {
                log.error("Workable: request failed for '{}': {}", subdomain, e.getMessage());
                break;
            }

            if (response == null || response.getJobs() == null) break;

            for (WorkableJobDto dto : response.getJobs()) {
                String jobUrl = dto.getUrl();
                if (jobUrl == null || jobListingRepository.findBySourceUrl(jobUrl).isPresent()) continue;

                String location = buildLocation(dto.getLocation());
                String description = buildDescription(dto);

                JobListing listing = JobListing.builder()
                        .title(dto.getTitle())
                        .company(subdomain)
                        .location(location)
                        .description(description)
                        .sourceUrl(jobUrl)
                        .source("workable")
                        .fetchedAt(LocalDateTime.now())
                        .build();

                jobListingRepository.save(listing);
                saved++;
            }

            // Advance to next page
            url = response.getNext_page();
            page++;

            if (url != null) {
                try { Thread.sleep(RATE_LIMIT_MS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }
        return saved;
    }

    private String buildLocation(WorkableJobDto.WorkableLocation loc) {
        if (loc == null) return "Unknown";
        if (loc.getLocationStr() != null && !loc.getLocationStr().isBlank()) return loc.getLocationStr();
        if (loc.isRemote()) return "Remote";
        StringBuilder sb = new StringBuilder();
        if (loc.getCity() != null)    sb.append(loc.getCity()).append(", ");
        if (loc.getRegion() != null)  sb.append(loc.getRegion()).append(", ");
        if (loc.getCountry() != null) sb.append(loc.getCountry());
        return sb.toString().replaceAll(", $", "").isBlank() ? "Unknown" : sb.toString().replaceAll(", $", "");
    }

    private String buildDescription(WorkableJobDto dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getDescription() != null)  sb.append(dto.getDescription()).append("\n\n");
        if (dto.getRequirements() != null) sb.append("## Requirements\n").append(dto.getRequirements()).append("\n\n");
        if (dto.getBenefits() != null)     sb.append("## Benefits\n").append(dto.getBenefits());
        return sb.toString().trim();
    }
}
