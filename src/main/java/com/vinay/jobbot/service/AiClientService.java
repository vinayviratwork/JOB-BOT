package com.vinay.jobbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiClientService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider:groq}")
    private String provider;

    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqBaseUrl;

    @Value("${ai.groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.model:llama3.2}")
    private String ollamaModel;

    public AiClientService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void logProvider() {
        if ("groq".equalsIgnoreCase(provider)) {
            if (groqApiKey == null || groqApiKey.isBlank()) {
                log.warn("=================================================");
                log.warn("  AI provider = groq but GROQ_API_KEY is not set!");
                log.warn("  Get a FREE key at https://console.groq.com/");
                log.warn("  Set it in .env: GROQ_API_KEY=gsk_xxxx");
                log.warn("=================================================");
            } else {
                log.info("AI provider: Groq | model: {} | key: ...{}",
                        groqModel, groqApiKey.substring(Math.max(0, groqApiKey.length() - 4)));
            }
        } else if ("ollama".equalsIgnoreCase(provider)) {
            log.info("AI provider: Ollama (local) | url: {} | model: {}", ollamaBaseUrl, ollamaModel);
        } else {
            log.warn("Unknown AI provider '{}' — defaulting to groq", provider);
            provider = "groq";
        }
    }

    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return callOllama(systemPrompt, userPrompt, maxTokens);
        }
        return callGroq(systemPrompt, userPrompt, maxTokens);
    }

    private String callGroq(String systemPrompt, String userPrompt, int maxTokens) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException(
                "GROQ_API_KEY is not set. Get a free key at https://console.groq.com/ " +
                "and add it to .env as GROQ_API_KEY=gsk_xxxx");
        }

        Map<String, Object> requestBody = Map.of(
            "model",      groqModel,
            "max_tokens", maxTokens,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
            )
        );

        log.debug("Groq request: model={} maxTokens={}", groqModel, maxTokens);

        String raw = restClient.post()
            .uri(groqBaseUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + groqApiKey)
            .body(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new RuntimeException("Groq API error: HTTP " + resp.getStatusCode());
            })
            .body(String.class);

        return extractOpenAiContent(raw);
    }

    private String callOllama(String systemPrompt, String userPrompt, int maxTokens) {
        String url = ollamaBaseUrl.replaceAll("/$", "") + "/v1/chat/completions";

        Map<String, Object> requestBody = Map.of(
            "model",      ollamaModel,
            "max_tokens", maxTokens,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
            )
        );

        log.debug("Ollama request: model={} url={}", ollamaModel, url);

        String raw = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new RuntimeException(
                    "Ollama error: HTTP " + resp.getStatusCode() +
                    " — is Ollama running? Start with: ollama serve");
            })
            .body(String.class);

        return extractOpenAiContent(raw);
    }

    private String extractOpenAiContent(String raw) {
        try {
            JsonNode root    = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText("");
                log.debug("AI response received, length={}", content.length());
                return content;
            }
            if (root.has("error")) {
                throw new RuntimeException("AI API error: " + root.path("error").path("message").asText(raw));
            }
            throw new RuntimeException("Unexpected AI response format: " + raw);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    public String getProviderName() {
        return provider;
    }

    public String getModelName() {
        return "ollama".equalsIgnoreCase(provider) ? ollamaModel : groqModel;
    }

    public boolean isConfigured() {
        if ("ollama".equalsIgnoreCase(provider)) return true;
        return groqApiKey != null && !groqApiKey.isBlank();
    }
}
