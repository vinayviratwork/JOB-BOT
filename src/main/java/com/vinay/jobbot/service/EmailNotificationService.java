package com.vinay.jobbot.service;

import com.vinay.jobbot.entity.JobListing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Sends email notifications when new high-match jobs are found.
 *
 * Setup (Gmail, free):
 *  1. Enable 2FA on your Google account
 *  2. myaccount.google.com/apppasswords → create App Password for Mail
 *  3. Set in .env:
 *       NOTIFY_ENABLED=true
 *       NOTIFY_EMAIL=your@email.com
 *       MAIL_USERNAME=your@gmail.com
 *       MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx   (16-char app password)
 *
 * If NOTIFY_ENABLED=false (default), all methods are no-ops — safe to leave unconfigured.
 */
@Service
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;  // null when mail is not configured

    @Value("${notification.enabled:false}")
    private boolean enabled;

    @Value("${notification.to-email:}")
    private String toEmail;

    @Value("${notification.from-email:}")
    private String fromEmail;

    public EmailNotificationService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender.orElse(null);
    }

    /**
     * Sends a digest email listing all newly discovered high-match jobs.
     * Called by ApplicationWorkflowService after the pipeline completes.
     *
     * @param highMatchJobs list of jobs with score >= match-threshold that are newly drafted
     */
    public void sendHighMatchDigest(List<JobListing> highMatchJobs) {
        if (!enabled || toEmail.isBlank() || highMatchJobs.isEmpty() || mailSender == null) {
            if (enabled && !toEmail.isBlank()) {
                log.debug("Email digest skipped — no new high-match jobs this run");
            }
            return;
        }

        try {
            String subject = String.format("JobBot: %d new high-match job%s found!",
                    highMatchJobs.size(), highMatchJobs.size() == 1 ? "" : "s");

            StringBuilder body = new StringBuilder();
            body.append("Hi,\n\n");
            body.append("JobBot found ").append(highMatchJobs.size())
                .append(" new high-match job(s) for you:\n\n");
            body.append("─".repeat(50)).append("\n\n");

            for (JobListing job : highMatchJobs) {
                body.append("Title   : ").append(job.getTitle()).append("\n");
                body.append("Company : ").append(job.getCompany()).append("\n");
                body.append("Location: ").append(job.getLocation()).append("\n");
                body.append("Score   : ").append(
                        job.getMatchScore() != null ? job.getMatchScore().intValue() + "/100" : "N/A"
                ).append("\n");
                body.append("Source  : ").append(job.getSource()).append("\n");
                body.append("Link    : ").append(job.getSourceUrl()).append("\n");
                if (job.getMatchReasoning() != null && !job.getMatchReasoning().isBlank()) {
                    body.append("Why     : ").append(job.getMatchReasoning()).append("\n");
                }
                body.append("\n");
            }

            body.append("─".repeat(50)).append("\n\n");
            body.append("Open your dashboard to review and copy cover letters:\n");
            body.append("http://localhost:8080\n\n");
            body.append("── JobBot (personal job search assistant) ──\n");
            body.append("This app never auto-submits. All applications are reviewed by you.\n");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail.isBlank() ? toEmail : fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body.toString());

            mailSender.send(message);
            log.info("Email digest sent to {} with {} high-match jobs", toEmail, highMatchJobs.size());

        } catch (Exception e) {
            // Email failure should never crash the main pipeline
            log.warn("Failed to send email notification: {} — job processing continues normally",
                    e.getMessage());
        }
    }

    /**
     * Sends a single alert when a specific job gets a cover letter drafted.
     */
    public void sendSingleJobAlert(JobListing job) {
        if (!enabled || toEmail.isBlank() || mailSender == null) return;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail.isBlank() ? toEmail : fromEmail);
            message.setTo(toEmail);
            message.setSubject("JobBot: Cover letter drafted for " + job.getTitle()
                    + " at " + job.getCompany());
            message.setText(
                "A cover letter has been drafted for:\n\n" +
                "Title   : " + job.getTitle() + "\n" +
                "Company : " + job.getCompany() + "\n" +
                "Score   : " + (job.getMatchScore() != null
                        ? job.getMatchScore().intValue() + "/100" : "N/A") + "\n" +
                "Link    : " + job.getSourceUrl() + "\n\n" +
                "Review it at: http://localhost:8080\n"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Single job alert failed: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled && !toEmail.isBlank() && mailSender != null;
    }
}
