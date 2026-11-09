package com.vinay.jobbot.service;

import com.vinay.jobbot.entity.Application;
import com.vinay.jobbot.entity.ApplicationStatus;
import com.vinay.jobbot.entity.JobListing;
import com.vinay.jobbot.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverLetterServiceTest {

    @Mock
    private AiClientService aiClientService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchScoringService matchScoringService;

    @InjectMocks
    private CoverLetterService coverLetterService;

    private static final String FAKE_RESUME =
            "Jane Smith — Java developer, Spring Boot, 4 years experience.";

    private static final String FAKE_COVER_LETTER =
            "Dear Hiring Manager,\n\nI am excited to apply for the Software Engineer role.\n\n[body]";

    @BeforeEach
    void setUp() {
        when(matchScoringService.getResumeText()).thenReturn(FAKE_RESUME);
        when(aiClientService.getProviderName()).thenReturn("groq");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void generateCoverLetter_newApplication_createdWithDraftedStatus() {
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn(FAKE_COVER_LETTER);
        when(applicationRepository.findByJobId(anyLong()))
                .thenReturn(Optional.empty());
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        JobListing job = buildJob(1L);
        Application result = coverLetterService.generateCoverLetter(job);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.DRAFTED);
        assertThat(result.getGeneratedCoverLetter()).isEqualTo(FAKE_COVER_LETTER);
        assertThat(result.getJob()).isEqualTo(job);
    }

    @Test
    void generateCoverLetter_existingApplication_updatesInPlace() {
        Application existing = Application.builder()
                .id(99L)
                .job(buildJob(2L))
                .status(ApplicationStatus.APPROVED)
                .generatedCoverLetter("Old letter")
                .updatedAt(LocalDateTime.now())
                .build();

        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn(FAKE_COVER_LETTER);
        when(applicationRepository.findByJobId(2L))
                .thenReturn(Optional.of(existing));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Application result = coverLetterService.generateCoverLetter(buildJob(2L));

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getGeneratedCoverLetter()).isEqualTo(FAKE_COVER_LETTER);
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.DRAFTED);
    }

    @Test
    void generateCoverLetter_promptIncludesResumeAndJobTitle() {
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn(FAKE_COVER_LETTER);
        when(applicationRepository.findByJobId(anyLong()))
                .thenReturn(Optional.empty());
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        coverLetterService.generateCoverLetter(buildJob(3L));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).complete(anyString(), userPromptCaptor.capture(), anyInt());

        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains(FAKE_RESUME);
        assertThat(prompt).contains("Software Engineer");
        assertThat(prompt).contains("Acme");
    }

    @Test
    void generateCoverLetter_systemPromptContainsAntiHallucinationGuardrail() {
        when(aiClientService.complete(anyString(), anyString(), anyInt()))
                .thenReturn(FAKE_COVER_LETTER);
        when(applicationRepository.findByJobId(anyLong()))
                .thenReturn(Optional.empty());
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        coverLetterService.generateCoverLetter(buildJob(4L));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).complete(systemPromptCaptor.capture(), anyString(), anyInt());

        String systemPrompt = systemPromptCaptor.getValue();
        assertThat(systemPrompt.toLowerCase()).contains("do not");
        assertThat(systemPrompt.toLowerCase()).contains("fabricat");
    }

    // ── applicationExists ─────────────────────────────────────────────────────

    @Test
    void applicationExists_returnsTrueWhenFound() {
        when(applicationRepository.existsByJobId(5L)).thenReturn(true);
        assertThat(coverLetterService.applicationExists(5L)).isTrue();
    }

    @Test
    void applicationExists_returnsFalseWhenNotFound() {
        when(applicationRepository.existsByJobId(6L)).thenReturn(false);
        assertThat(coverLetterService.applicationExists(6L)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JobListing buildJob(Long id) {
        return JobListing.builder()
                .id(id)
                .title("Software Engineer")
                .company("Acme")
                .location("Remote")
                .description("Build great things with Java and Spring Boot.")
                .sourceUrl("https://boards.greenhouse.io/acme/jobs/" + id)
                .source("greenhouse")
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
