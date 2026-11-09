package com.vinay.jobbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JobBot — AI-Powered Job Application Assistant
 *
 * Personal-use tool that:
 *  - Fetches jobs from public Greenhouse board APIs (no scraping)
 *  - Scores each job against your resume using Claude
 *  - Drafts cover letters for high-match jobs
 *  - Never auto-submits — all submission is a manual human step
 *
 * Run: docker compose up -d && mvn spring-boot:run
 */
@SpringBootApplication
public class JobBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobBotApplication.class, args);
    }
}
