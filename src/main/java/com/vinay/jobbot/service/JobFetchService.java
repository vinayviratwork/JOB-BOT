package com.vinay.jobbot.service;

import com.vinay.jobbot.dto.GreenhouseBoardResponse;
import com.vinay.jobbot.dto.GreenhouseJobDto;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobFetchService {

    private static final String GREENHOUSE_API =
            "https://boards-api.greenhouse.io/v1/boards/{boardToken}/jobs?content=true";
    private static final long RATE_LIMIT_MS = 1_000;

    private final RestClient restClient;
    private final JobListingRepository jobListingRepository;
    private final LeverFetchService leverFetchService;
    private final AshbyFetchService ashbyFetchService;
    private final WorkableFetchService workableFetchService;
    private final RemoteOKFetchService remoteOKFetchService;

    @Value("${jobbot.companies-file}")
    private Resource companiesFile;

    @Value("${jobbot.remoteok.tags:}")
    private String remoteOkTags;

    private final List<String> greenhouseTokens = new ArrayList<>();
    private final List<String> leverSlugs       = new ArrayList<>();
    private final List<String> ashbySlugs       = new ArrayList<>();
    private final List<String> workableSlugs    = new ArrayList<>();
    private boolean fetchRemoteOK               = false;

    @PostConstruct
    public void loadConfig() {
        try {
            String content = companiesFile.getContentAsString(StandardCharsets.UTF_8);
            Arrays.stream(content.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .forEach(this::parseLine);

            log.info("Job sources loaded — Greenhouse:{}, Lever:{}, Ashby:{}, Workable:{}, RemoteOK:{}",
                    greenhouseTokens.size(), leverSlugs.size(), ashbySlugs.size(),
                    workableSlugs.size(), fetchRemoteOK);
        } catch (IOException e) {
            log.error("Failed to load target-companies.txt: {}", e.getMessage());
        }
    }

    private void parseLine(String line) {
        if (line.startsWith("lever:"))      { leverSlugs.add(line.substring(6).trim()); return; }
        if (line.startsWith("ashby:"))      { ashbySlugs.add(line.substring(6).trim()); return; }
        if (line.startsWith("workable:"))   { workableSlugs.add(line.substring(9).trim()); return; }
        if (line.equalsIgnoreCase("remoteok")) { fetchRemoteOK = true; return; }
        if (line.startsWith("greenhouse:")) { greenhouseTokens.add(line.substring(11).trim()); return; }
        greenhouseTokens.add(line);
    }

    public int fetchAllCompanies() {
        int total = 0;

        if (!greenhouseTokens.isEmpty()) {
            log.info("Fetching from Greenhouse ({} boards)...", greenhouseTokens.size());
            for (String token : greenhouseTokens) {
                try {
                    total += fetchFromGreenhouse(token);
                    Thread.sleep(RATE_LIMIT_MS);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                  catch (Exception e) { log.error("Greenhouse error '{}': {}", token, e.getMessage()); }
            }
        }

        if (!leverSlugs.isEmpty()) {
            log.info("Fetching from Lever ({} companies)...", leverSlugs.size());
            total += leverFetchService.fetchAll(leverSlugs);
        }

        if (!ashbySlugs.isEmpty()) {
            log.info("Fetching from Ashby ({} companies)...", ashbySlugs.size());
            total += ashbyFetchService.fetchAll(ashbySlugs);
        }

        if (!workableSlugs.isEmpty()) {
            log.info("Fetching from Workable ({} companies)...", workableSlugs.size());
            total += workableFetchService.fetchAll(workableSlugs);
        }

        if (fetchRemoteOK) {
            log.info("Fetching from RemoteOK...");
            total += remoteOKFetchService.fetch(remoteOkTags);
        }

        log.info("All sources fetched. Total new jobs: {}", total);
        return total;
    }

    public int fetchFromGreenhouse(String boardToken) {
        log.debug("Greenhouse: fetching board '{}'", boardToken);

        GreenhouseBoardResponse response = restClient.get()
                .uri(GREENHOUSE_API, boardToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new RuntimeException(
                            "Greenhouse API error for '" + boardToken + "': " + resp.getStatusCode());
                })
                .body(GreenhouseBoardResponse.class);

        if (response == null || response.getJobs() == null) {
            log.warn("No jobs returned for Greenhouse board '{}'", boardToken);
            return 0;
        }

        int saved = 0;
        for (GreenhouseJobDto dto : response.getJobs()) {
            String url = dto.getAbsoluteUrl();
            if (url == null || jobListingRepository.findBySourceUrl(url).isPresent()) continue;

            JobListing listing = JobListing.builder()
                    .title(dto.getTitle())
                    .company(boardToken)
                    .location(dto.getLocation() != null ? dto.getLocation().getName() : "Unknown")
                    .description(dto.getContent())
                    .sourceUrl(url)
                    .source("greenhouse")
                    .fetchedAt(LocalDateTime.now())
                    .build();

            jobListingRepository.save(listing);
            saved++;
        }
        return saved;
    }

    public List<String> getGreenhouseTokens() { return greenhouseTokens; }
    public List<String> getLeverSlugs()        { return leverSlugs; }
    public List<String> getAshbySlugs()        { return ashbySlugs; }
    public List<String> getWorkableSlugs()     { return workableSlugs; }
    public boolean      isFetchRemoteOK()      { return fetchRemoteOK; }
}
