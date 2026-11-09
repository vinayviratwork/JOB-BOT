package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Single job posting from the Workable public jobs API.
 * Endpoint: GET https://{subdomain}.workable.com/api/v1/jobs
 * (No auth required for public listings)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkableJobDto {

    private String id;
    private String title;
    private String url;           // public job URL
    private String shortcode;
    private String description;
    private String requirements;
    private String benefits;

    @JsonProperty("full_title")
    private String fullTitle;

    private WorkableLocation location;
    private String department;

    @JsonProperty("employment_type")
    private String employmentType;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkableLocation {
        private String city;
        private String region;
        private String country;

        @JsonProperty("location_str")
        private String locationStr;

        private boolean remote;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkableBoardResponse {
        private List<WorkableJobDto> jobs;
        private String next_page;
    }
}
