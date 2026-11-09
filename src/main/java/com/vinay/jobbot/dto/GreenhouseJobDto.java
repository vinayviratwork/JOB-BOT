package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single job from the Greenhouse public board API.
 * API endpoint: GET https://boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GreenhouseJobDto {

    private Long id;

    private String title;

    @JsonProperty("absolute_url")
    private String absoluteUrl;

    private String content;           // HTML job description (content=true)

    private LocationDto location;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationDto {
        private String name;
    }
}
