package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Single job posting from the Lever public postings API.
 * Endpoint: GET https://api.lever.co/v0/postings/{company}?mode=json
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeverJobDto {

    private String id;
    private String text;           // job title
    private String hostedUrl;      // public apply URL
    private String descriptionPlain;
    private List<String> categories;
    private LeverCategories categoriesObj;
    private List<LeverList> lists;
    private String additional;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverCategories {
        private String location;
        private String team;
        private String department;
        private String commitment;  // Full-time / Part-time / Contract
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeverList {
        private String text;
        private String content;
    }
}
