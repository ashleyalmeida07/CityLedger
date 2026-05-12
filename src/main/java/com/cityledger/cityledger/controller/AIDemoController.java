package com.cityledger.cityledger.controller;

import com.cityledger.cityledger.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIDemoController {

    private final AIService aiService;

    /**
     * Public demo endpoint — lets anyone test the AI categorizer from the /ai-features page.
     * Rate limiting via frontend (button disabled during request).
     */
    @PostMapping("/demo")
    public ResponseEntity<Map<String, String>> demo(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Untitled complaint");
        String description = body.getOrDefault("description", "");
        String location = body.getOrDefault("location", "Unknown location");

        if (description.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Description is required"));
        }

        try {
            AIService.AICategorization result = aiService.categorizeComplaint(title, description, location);
            return ResponseEntity.ok(Map.of(
                    "category", result.category(),
                    "severity", result.severity(),
                    "reason",   result.reason()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "AI service unavailable: " + e.getMessage()));
        }
    }
}
