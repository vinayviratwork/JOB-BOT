package com.vinay.jobbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * General-purpose RestClient bean used by JobFetchService and AiClientService.
     * Timeout and connection settings are kept at defaults for simplicity;
     * rate limiting is handled in the callers via Thread.sleep.
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .build();
    }
}
