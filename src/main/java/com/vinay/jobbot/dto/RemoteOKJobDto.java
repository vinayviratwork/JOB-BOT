package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Single job from the RemoteOK public API.
 * Endpoint: GET https://remoteok.com/api
 * Returns a JSON array; the first element is a metadata object (skipped by slug check).
 * No auth required — completely free public API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteOKJobDto {

    private String slug;          // unique id
    private String position;      // job title
    private String company;
    private String url;
    private String description;
    private String location;

    @JsonProperty("company_logo")
    private String companyLogo;

    private List<String> tags;

    private long date;            // epoch seconds

    @JsonProperty("original")
    private boolean original;
}
