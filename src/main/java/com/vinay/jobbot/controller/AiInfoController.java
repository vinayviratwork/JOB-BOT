package com.vinay.jobbot.controller;

import com.vinay.jobbot.service.AiClientService;
import com.vinay.jobbot.service.EmailNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/info")
@RequiredArgsConstructor
@Tag(name = "Info", description = "App and AI provider information")
public class AiInfoController {

    private final AiClientService aiClientService;
    private final EmailNotificationService emailNotificationService;

    @Operation(summary = "Get current AI provider info")
    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> getAiInfo() {
        return ResponseEntity.ok(Map.of(
            "provider",      aiClientService.getProviderName(),
            "model",         aiClientService.getModelName(),
            "configured",    aiClientService.isConfigured(),
            "cost",          "FREE",
            "notifyEnabled", emailNotificationService.isEnabled(),
            "message",       aiClientService.isConfigured()
                ? aiClientService.getProviderName().toUpperCase() + " is ready — $0.00 cost"
                : "AI not configured. Set GROQ_API_KEY in .env"
        ));
    }
}
