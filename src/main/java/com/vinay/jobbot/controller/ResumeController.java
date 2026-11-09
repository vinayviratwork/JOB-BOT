package com.vinay.jobbot.controller;

import com.vinay.jobbot.service.ResumeStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Resume", description = "Upload and manage your resume for AI matching")
public class ResumeController {

    private final ResumeStorageService resumeStorageService;

    @Operation(summary = "Get current resume status")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getResumeStatus() {
        boolean hasResume = resumeStorageService.hasResume();
        String  text      = resumeStorageService.getResumeText();
        return ResponseEntity.ok(Map.of(
                "loaded",    hasResume,
                "fileName",  resumeStorageService.getResumeFileName(),
                "charCount", text != null ? text.length() : 0,
                "preview",   text != null && text.length() > 200
                                 ? text.substring(0, 200) + "…"
                                 : (text != null ? text : "")
        ));
    }

    /**
     * Upload a resume as PDF or plain text (.txt).
     * Accepts multipart/form-data with field name "file".
     *
     * The extracted text is immediately available for scoring new jobs.
     * Previously scored jobs can be re-scored via POST /api/jobs/scan-now.
     */
    @Operation(summary = "Upload resume (PDF or .txt) — replaces current resume")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Uploaded file is empty"));
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        boolean isPdf = filename.toLowerCase().endsWith(".pdf");
        boolean isTxt = filename.toLowerCase().endsWith(".txt");

        if (!isPdf && !isTxt) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF and .txt files are supported. Got: " + filename));
        }

        try {
            int chars = resumeStorageService.storeAndExtract(file);
            log.info("Resume uploaded: '{}', {} chars extracted", filename, chars);

            String advice = "";
            if (chars < 500) {
                advice = " WARNING: Only " + chars + " characters extracted — your PDF may be partially " +
                         "scanned. For best results, re-export from Word/Google Docs or use a .txt file.";
            }

            return ResponseEntity.ok(Map.of(
                    "success",   true,
                    "fileName",  filename,
                    "charCount", chars,
                    "message",   "Resume uploaded and extracted successfully." + advice +
                                 " Run 'Scan Now' to re-score jobs with the new resume."
            ));
        } catch (IllegalArgumentException e) {
            // Includes the detailed scanned-PDF explanation
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", e.getMessage(),
                            "hint",  "If your PDF is scanned, open it in Word or Google Docs, " +
                                     "then File → Download as PDF (this creates a text-based PDF). " +
                                     "Or simply paste your resume text into a .txt file."
                    ));
        } catch (Exception e) {
            log.error("Resume upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process resume: " + e.getMessage()));
        }
    }
}
