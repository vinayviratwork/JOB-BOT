package com.vinay.jobbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Top-level response envelope from the Greenhouse jobs board API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GreenhouseBoardResponse {

    private List<GreenhouseJobDto> jobs;
}
