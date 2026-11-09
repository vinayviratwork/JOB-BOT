package com.vinay.jobbot.controller;

import com.vinay.jobbot.entity.Application;
import com.vinay.jobbot.entity.ApplicationStatus;
import com.vinay.jobbot.repository.ApplicationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Applications", description = "Manage job applications and their statuses")
public class ApplicationController {

    private final ApplicationRepository applicationRepository;

    @Operation(summary = "List all applications with their current status")
    @GetMapping
    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    @Operation(summary = "Get a single application by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Application> getApplication(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update the status of an application.
     * GUARDRAIL: APPLIED status is a human-only action — records that the user
     * manually submitted externally. This app never auto-submits to any platform.
     */
    @Operation(summary = "Update application status (e.g. APPROVED, APPLIED, REJECTED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'status' field in request body"));
        }

        ApplicationStatus newStatus;
        try {
            newStatus = ApplicationStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + statusStr +
                            ". Valid: DISCOVERED, DRAFTED, APPROVED, APPLIED, RESPONDED, REJECTED"));
        }

        return applicationRepository.findById(id)
                .map(app -> {
                    app.setStatus(newStatus);
                    if (newStatus == ApplicationStatus.APPLIED) {
                        app.setAppliedAt(LocalDateTime.now());
                    }
                    app.setUpdatedAt(LocalDateTime.now());
                    Application saved = applicationRepository.save(app);
                    log.info("Application id={} status -> {}", id, newStatus);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update the generated cover letter text")
    @PatchMapping("/{id}/cover-letter")
    public ResponseEntity<?> updateCoverLetter(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String text = body.get("coverLetter");
        if (text == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'coverLetter' field"));
        }

        return applicationRepository.findById(id)
                .map(app -> {
                    app.setGeneratedCoverLetter(text);
                    app.setUpdatedAt(LocalDateTime.now());
                    return ResponseEntity.ok(applicationRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update the private notes for an application.
     * Expected body: {"notes": "Had a phone screen on Monday..."}
     */
    @Operation(summary = "Update private notes for an application")
    @PatchMapping("/{id}/notes")
    public ResponseEntity<?> updateNotes(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String notes = body.get("notes");
        if (notes == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'notes' field"));
        }

        return applicationRepository.findById(id)
                .map(app -> {
                    app.setNotes(notes);
                    app.setUpdatedAt(LocalDateTime.now());
                    log.info("Notes updated for application id={}", id);
                    return ResponseEntity.ok(applicationRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export all applications as a CSV file.
     * Useful for tracking in spreadsheets or sharing with career coaches.
     */
    @Operation(summary = "Export all applications as CSV")
    @GetMapping("/export.csv")
    public ResponseEntity<String> exportCsv() {
        List<Application> apps = applicationRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Job Title,Company,Location,Source,Match Score,Status,Applied At,Updated At,Notes\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Application app : apps) {
            csv.append(app.getId()).append(",");
            csv.append(csvEscape(app.getJob().getTitle())).append(",");
            csv.append(csvEscape(app.getJob().getCompany())).append(",");
            csv.append(csvEscape(app.getJob().getLocation())).append(",");
            csv.append(csvEscape(app.getJob().getSource())).append(",");
            csv.append(app.getJob().getMatchScore() != null
                    ? app.getJob().getMatchScore().intValue() : "").append(",");
            csv.append(app.getStatus()).append(",");
            csv.append(app.getAppliedAt() != null ? app.getAppliedAt().format(fmt) : "").append(",");
            csv.append(app.getUpdatedAt() != null ? app.getUpdatedAt().format(fmt) : "").append(",");
            csv.append(csvEscape(app.getNotes())).append("\n");
        }

        String filename = "jobbot-applications-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        // Wrap in quotes and escape any existing quotes
        return "\"" + value.replace("\"", "\"\"")
                           .replace("\n", " ")
                           .replace("\r", "") + "\"";
    }
}
