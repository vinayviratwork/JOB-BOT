package com.vinay.jobbot.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Manages resume storage and text extraction.
 *
 * Supports two input modes:
 *  1. Plain-text file (my-resume.txt) — loaded at startup from classpath
 *  2. PDF upload via POST /api/resume/upload — extracted with Apache PDFBox,
 *     persisted to the configured upload directory, and cached in memory.
 *
 * The in-memory resumeText is what MatchScoringService and CoverLetterService use.
 * An uploaded PDF or text file overrides the classpath default for the lifetime
 * of the running process (and persists across restarts via the upload dir).
 */
@Service
@Slf4j
public class ResumeStorageService {

    @Value("${jobbot.resume-file}")
    private Resource defaultResumeResource;

    @Value("${jobbot.resume-upload-dir:${user.home}/.jobbot/resume}")
    private String uploadDir;

    private volatile String resumeText = "";
    private volatile String resumeFileName = "my-resume.txt";

    @PostConstruct
    public void init() {
        // 1. Check if a previously uploaded file exists in the upload dir
        Path uploadPath = Paths.get(uploadDir);
        if (Files.exists(uploadPath)) {
            Path latestPdf = findLatestFile(uploadPath, ".pdf");
            Path latestTxt = findLatestFile(uploadPath, ".txt");

            Path toLoad = pickNewer(latestPdf, latestTxt);
            if (toLoad != null) {
                try {
                    loadFromPath(toLoad);
                    log.info("Resume loaded from upload dir: {}", toLoad);
                    return;
                } catch (IOException e) {
                    log.warn("Could not load persisted resume from {}: {}", toLoad, e.getMessage());
                }
            }
        }

        // 2. Fall back to classpath default
        loadFromClasspath();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Accepts a multipart file (PDF or plain text), extracts text,
     * saves to disk, and updates the in-memory cache used for scoring.
     *
     * @param file uploaded resume file (PDF or .txt)
     * @return number of characters extracted
     */
    public int storeAndExtract(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "resume";

        boolean isPdf = originalName.toLowerCase().endsWith(".pdf")
                || "application/pdf".equals(file.getContentType());

        // Ensure upload directory exists
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        // Persist raw file
        String saveName = isPdf ? "resume.pdf" : "resume.txt";
        Path savePath = dir.resolve(saveName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, savePath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Resume file saved to {}", savePath);

        // Extract text
        String extracted;
        if (isPdf) {
            extracted = extractFromPdf(savePath);
        } else {
            extracted = Files.readString(savePath, StandardCharsets.UTF_8);
        }

        if (extracted == null || extracted.isBlank()) {
            if (isPdf) {
                throw new IllegalArgumentException(
                    "No text could be extracted from this PDF. " +
                    "This usually means it is a SCANNED IMAGE (photo of a document), not a text-based PDF. " +
                    "To fix this: open your resume in Word/Google Docs → File → Download as PDF, " +
                    "or copy-paste the resume text into a .txt file and upload that instead.");
            }
            throw new IllegalArgumentException(
                    "The uploaded file appears to be empty. Please check the file and try again.");
        }

        // Warn if text looks like a scanned PDF (very few words per page)
        long pageCount = extracted.chars().filter(c -> c == '\f').count() + 1;
        long wordCount = extracted.split("\\s+").length;
        if (isPdf && pageCount > 0 && (wordCount / pageCount) < 20) {
            log.warn("Resume PDF may be partially scanned: only {} words across {} pages. " +
                     "Consider re-exporting from Word/Google Docs.", wordCount, pageCount);
        }

        this.resumeText     = extracted;
        this.resumeFileName = originalName;
        log.info("Resume updated from upload: {} chars extracted from '{}'",
                extracted.length(), originalName);

        return extracted.length();
    }

    /** Returns the current in-memory resume text used for AI scoring. */
    public String getResumeText() {
        return resumeText;
    }

    /** Returns the filename of the currently active resume. */
    public String getResumeFileName() {
        return resumeFileName;
    }

    /** Returns true if a non-empty resume is loaded. */
    public boolean hasResume() {
        return resumeText != null && !resumeText.isBlank()
                && !resumeText.contains("PASTE YOUR RESUME HERE");
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private void loadFromClasspath() {
        try {
            String text = defaultResumeResource.getContentAsString(StandardCharsets.UTF_8);
            this.resumeText     = text;
            this.resumeFileName = "my-resume.txt (classpath default)";
            if (hasResume()) {
                log.info("Resume loaded from classpath: {} chars", text.length());
            } else {
                log.warn("my-resume.txt contains placeholder text — please upload your real resume " +
                         "via POST /api/resume/upload or edit src/main/resources/my-resume.txt");
            }
        } catch (IOException e) {
            log.error("Could not load default resume file: {}", e.getMessage());
        }
    }

    private void loadFromPath(Path path) throws IOException {
        String name = path.getFileName().toString();
        if (name.toLowerCase().endsWith(".pdf")) {
            this.resumeText = extractFromPdf(path);
        } else {
            this.resumeText = Files.readString(path, StandardCharsets.UTF_8);
        }
        this.resumeFileName = name;
    }

    private String extractFromPdf(Path pdfPath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            log.debug("PDFBox extracted {} chars from {}", text.length(), pdfPath.getFileName());
            return text;
        }
    }

    private Path findLatestFile(Path dir, String extension) {
        try {
            return Files.list(dir)
                    .filter(p -> p.toString().toLowerCase().endsWith(extension))
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a)
                                    .compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) { return 0; }
                    })
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Path pickNewer(Path a, Path b) {
        if (a == null) return b;
        if (b == null) return a;
        try {
            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)) >= 0 ? a : b;
        } catch (IOException e) {
            return a;
        }
    }
}
