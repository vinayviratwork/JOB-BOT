package com.vinay.jobbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.JobListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchScoringServiceTest {

    @Mock
    private AiClientService aiClientService;

    @Mock
    private JobListingRepository jobListingRepository;

    @Mock
    private ResumeStorageService resumeStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MatchScoringService service;

    @BeforeEach
    void setUp() {
        service = new MatchScoringService(aiClientService, jobListingRepository, objectMapper, resumeStorageService);
        when(resumeStorageService.getResumeText())
                .thenReturn("Java developer with 3 years experience in Spring Boot and MySQL.");
        when(resumeStorageService.hasResume()).thenReturn(true);
    }

    // ── parseScoreResult ──────────────────────────────────────────────────────

    @Test
    void parseScoreResult_plainJson_parsedCorrectly() {
        String raw = "{\"score\": 85, \"reasoning\": \"Strong Spring Boot match.\"}";
        var result = service.parseScoreResult(raw);
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.reasoning()).isEqualTo("Strong Spring Boot match.");
    }

    @Test
    void parseScoreResult_stripsMarkdownFences() {
        String raw = "```json\n{\"score\": 72, \"reasoning\": \"Good fit.\"}\n```";
        var result = service.parseScoreResult(raw);
        assertThat(result.score()).isEqualTo(72);
        assertThat(result.reasoning()).isEqualTo("Good fit.");
    }

    @Test
    void parseScoreResult_scoreClamped_above100() {
        String raw = "{\"score\": 150, \"reasoning\": \"Off chart.\"}";
        var result = service.parseScoreResult(raw);
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void parseScoreResult_scoreClamped_belowZero() {
        String raw = "{\"score\": -5, \"reasoning\": \"No match.\"}";
        var result = service.parseScoreResult(raw);
        assertThat(result.score()).isEqualTo(0);
    }

    @Test
    void parseScoreResult_malformedJson_returnsZeroScore() {
        String raw = "This is not JSON at all";
        var result = service.parseScoreResult(raw);
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.reasoning()).contains("Unable to parse score");
    }

    // ── scoreJob ─────────────────────────────────────────────────────────────

    @Test
    void scoreJob_callsAiAndPersistsResult() {
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"score\": 78, \"reasoning\": \"Solid experience match.\"}");

        JobListing job = buildJob(1L);
        service.scoreJob(job);

        ArgumentCaptor<JobListing> captor = ArgumentCaptor.forClass(JobListing.class);
        verify(jobListingRepository).save(captor.capture());

        JobListing saved = captor.getValue();
        assertThat(saved.getMatchScore()).isEqualTo(78.0);
        assertThat(saved.getMatchReasoning()).isEqualTo("Solid experience match.");
    }

    @Test
    void scoreJob_aiReturnsMarkdownFences_stillParsed() {
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn("```json\n{\"score\": 55, \"reasoning\": \"Partial match.\"}\n```");

        JobListing job = buildJob(2L);
        service.scoreJob(job);

        verify(jobListingRepository).save(argThat(j -> j.getMatchScore() == 55.0));
    }

    // ── scoreAllUnscored ──────────────────────────────────────────────────────

    @Test
    void scoreAllUnscored_scoresEveryUnscoredJob() {
        JobListing j1 = buildJob(10L);
        JobListing j2 = buildJob(11L);
        when(jobListingRepository.findByMatchScoreIsNull()).thenReturn(List.of(j1, j2));
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"score\": 60, \"reasoning\": \"Ok match.\"}");

        int count = service.scoreAllUnscored();

        verify(jobListingRepository, times(2)).save(any(JobListing.class));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void scoreAllUnscored_skipsWhenNoResume() {
        when(resumeStorageService.hasResume()).thenReturn(false);

        int count = service.scoreAllUnscored();

        assertThat(count).isEqualTo(0);
        verify(aiClientService, never()).complete(anyString(), anyString(), anyInt());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JobListing buildJob(Long id) {
        return JobListing.builder()
                .id(id)
                .title("Software Engineer")
                .company("Acme")
                .location("Remote")
                .description("We need a Java developer with Spring Boot skills.")
                .sourceUrl("https://boards.greenhouse.io/acme/jobs/" + id)
                .source("greenhouse")
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
