package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Single job posting from the Ashby public job board API.
 * Endpoint: POST https://api.ashbyhq.com/posting-api/job-board/{company}
 * Body: {"includeCompensation": false}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AshbyJobDto {

    private String id;
    private String title;
    private String locationName;
    private String employmentType;
    private String departmentName;
    private String descriptionHtml;
    private String descriptionPlain;
    private String jobUrl;
    private boolean isListed;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AshbyBoardResponse {
        private List<AshbyJobDto> jobs;
        private String organizationName;
    }
}
