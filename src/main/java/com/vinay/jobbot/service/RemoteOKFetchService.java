package com.vinay.jobbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinay.jobbot.dto.RemoteOKJobDto;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches remote job listings from the RemoteOK public API.
 *
 * API: GET https://remoteok.com/api
 * - Completely free, no auth required.
 * - Returns a JSON array; first element is a metadata object (must be skipped).
 * - Contains hundreds of remote tech jobs updated in near real-time.
 * - Optionally filter by tag: https://remoteok.com/api?tags=java,backend
 *
 * This is a single global feed — no "company slug" needed.
 * Configure keyword filters in application.yml under jobbot.remoteok.tags
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RemoteOKFetchService {

    private static final String REMOTEOK_API  = "https://remoteok.com/api";
    private static final String REMOTEOK_TAGS = "https://remoteok.com/api?tags={tags}";

    private final RestClient restClient;
    private final JobListingRepository jobListingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fetches all remote jobs from RemoteOK.
     * Pass a comma-separated tag string to filter (e.g. "java,backend,spring").
     * Pass null or blank to fetch the full feed.
     *
     * @param tags comma-separated skill/role tags (optional)
     * @return number of new jobs saved
     */
    public int fetch(String tags) {
        log.info("RemoteOK: fetching jobs{}", tags != null && !tags.isBlank() ? " with tags=" + tags : "");

        String url = (tags != null && !tags.isBlank())
                ? REMOTEOK_TAGS.replace("{tags}", tags)
                : REMOTEOK_API;

        String raw = restClient.get()
                .uri(url)
                .header("User-Agent", "JobBot/1.0 (personal job search tool)")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new RuntimeException("RemoteOK API error: " + resp.getStatusCode());
                })
                .body(String.class);

        if (raw == null || raw.isBlank()) return 0;

        List<RemoteOKJobDto> jobs = parseJobs(raw);
        log.info("RemoteOK: parsed {} job objects", jobs.size());

        int saved = 0;
        for (RemoteOKJobDto dto : jobs) {
            // Skip entries with no slug or URL (metadata rows)
            if (dto.getSlug() == null || dto.getUrl() == null) continue;

            String jobUrl = dto.getUrl().startsWith("http")
                    ? dto.getUrl()
                    : "https://remoteok.com" + dto.getUrl();

            if (jobListingRepository.findBySourceUrl(jobUrl).isPresent()) continue;

            String title    = dto.getPosition() != null ? dto.getPosition() : "Unknown Role";
            String company  = dto.getCompany()  != null ? dto.getCompany()  : "Unknown Company";
            String location = dto.getLocation() != null && !dto.getLocation().isBlank()
                    ? dto.getLocation() : "Remote";
            String description = buildDescription(dto);

            JobListing listing = JobListing.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .description(description)
                    .sourceUrl(jobUrl)
                    .source("remoteok")
                    .fetchedAt(LocalDateTime.now())
                    .build();

            jobListingRepository.save(listing);
            saved++;
        }
        log.info("RemoteOK: saved {} new jobs", saved);
        return saved;
    }

    /**
     * Parses the RemoteOK array response.
     * The first element is always a legal/metadata object with no "slug" — we skip it via the null check above.
     */
    private List<RemoteOKJobDto> parseJobs(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("RemoteOK: failed to parse response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildDescription(RemoteOKJobDto dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getDescription() != null) sb.append(dto.getDescription()).append("\n\n");
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", dto.getTags()));
        }
        return sb.toString().trim();
    }
}
