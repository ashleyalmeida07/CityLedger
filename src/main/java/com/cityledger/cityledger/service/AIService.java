package com.cityledger.cityledger.service;

import com.cityledger.cityledger.model.Complaint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AIService {

    @Value("${nvidia.api.key}")
    private String apiKey;

    @Value("${nvidia.api.url:https://integrate.api.nvidia.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${nvidia.api.model:meta/llama-3.3-70b-instruct}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Feature 1: Auto Category + Severity Assignment
     * Sends complaint description to LLM and returns category, severity, and reason.
     */
    public AICategorization categorizeComplaint(String title, String description, String location) {
        String prompt = """
                You are a civic complaint classifier for an Indian municipal corporation system called CityLedger.

                Given the following citizen complaint, respond with EXACTLY this JSON format (no markdown, no extra text):
                {"category": "...", "severity": "...", "reason": "..."}

                CATEGORY must be ONE of: Pothole, Street Lamp, Garbage, Water Leakage, Road Damage, Tree Fall, Noise, Stray Animals
                SEVERITY must be ONE of: LOW, MEDIUM, HIGH, CRITICAL
                REASON must be a single short sentence explaining why.

                Complaint Title: %s
                Complaint Description: %s
                Location: %s

                Respond ONLY with the JSON object.
                """.formatted(title, description, location);

        try {
            String response = callLLM(prompt);
            // Extract JSON from response (handle potential markdown wrapping)
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            String category = node.get("category").asText();
            String severity = node.get("severity").asText().toUpperCase();
            String reason = node.get("reason").asText();

            // Validate category
            List<String> validCategories = List.of(
                    "Pothole", "Street Lamp", "Garbage", "Water Leakage",
                    "Road Damage", "Tree Fall", "Noise", "Stray Animals"
            );
            if (!validCategories.contains(category)) {
                category = "Pothole"; // safe fallback
            }

            // Validate severity
            List<String> validSeverities = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
            if (!validSeverities.contains(severity)) {
                severity = "MEDIUM";
            }

            log.info("AI categorized: {} / {} — {}", category, severity, reason);
            return new AICategorization(category, severity, reason);

        } catch (Exception e) {
            log.error("AI categorization failed, using fallback: {}", e.getMessage());
            return new AICategorization("Pothole", "MEDIUM", "AI classification unavailable — defaulted");
        }
    }

    /**
     * Feature 2: Complaint Summary Generator (for officer drawer)
     */
    public String generateSummary(Complaint complaint) {
        String prompt = """
                You are a concise summarizer for a civic complaint system.

                Summarize the following complaint in 2-3 plain English sentences for a municipal officer.
                Focus on: what the issue is, where it is, and urgency level.

                Title: %s
                Description: %s
                Location: %s
                Category: %s
                Severity: %s

                Respond ONLY with the summary text, no quotes, no labels.
                """.formatted(
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getLocation(),
                complaint.getCategory(),
                complaint.getSeverity()
        );

        try {
            return callLLM(prompt).trim();
        } catch (Exception e) {
            log.error("AI summary failed: {}", e.getMessage());
            return "Summary unavailable. Please read the full description.";
        }
    }

    /**
     * Feature 3: Duplicate Complaint Detector
     * Compares a new complaint against existing nearby complaints.
     */
    public DuplicateCheckResult checkForDuplicates(String newTitle, String newDescription, String newLocation,
                                                    List<Complaint> nearbyComplaints) {
        if (nearbyComplaints == null || nearbyComplaints.isEmpty()) {
            return new DuplicateCheckResult(false, null, null);
        }

        StringBuilder existingList = new StringBuilder();
        for (Complaint c : nearbyComplaints) {
            existingList.append(String.format("ID: %d | Title: %s | Description: %s | Location: %s\n",
                    c.getId(), c.getTitle(), c.getDescription(), c.getLocation()));
        }

        String prompt = """
                You are a duplicate complaint detector for a civic complaint system.

                A new complaint has been filed. Check if it describes the SAME real-world issue as any existing open complaint below.

                NEW COMPLAINT:
                Title: %s
                Description: %s
                Location: %s

                EXISTING OPEN COMPLAINTS (nearby area):
                %s

                If a duplicate is found, respond with EXACTLY this JSON (no markdown):
                {"isDuplicate": true, "duplicateOfId": <ID_NUMBER>, "reason": "..."}

                If no duplicate is found:
                {"isDuplicate": false, "duplicateOfId": null, "reason": "No matching complaint found"}

                Respond ONLY with the JSON object.
                """.formatted(newTitle, newDescription, newLocation, existingList.toString());

        try {
            String response = callLLM(prompt);
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            boolean isDuplicate = node.get("isDuplicate").asBoolean();
            Long duplicateOfId = isDuplicate && node.get("duplicateOfId") != null && !node.get("duplicateOfId").isNull()
                    ? node.get("duplicateOfId").asLong() : null;
            String reason = node.has("reason") ? node.get("reason").asText() : "";

            log.info("Duplicate check: isDuplicate={}, duplicateOfId={}, reason={}", isDuplicate, duplicateOfId, reason);
            return new DuplicateCheckResult(isDuplicate, duplicateOfId, reason);

        } catch (Exception e) {
            log.error("Duplicate check failed: {}", e.getMessage());
            return new DuplicateCheckResult(false, null, null);
        }
    }

    /**
     * Calls NVIDIA API (OpenAI-compatible endpoint) with the given prompt.
     */
    private String callLLM(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful assistant for the CityLedger civic complaint platform."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "max_tokens", 300
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, request, String.class
        );

        JsonNode root = null;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse NVIDIA API response", e);
        }

        return root.get("choices").get(0).get("message").get("content").asText();
    }

    /**
     * Extracts JSON object from a string that may contain markdown code fences.
     */
    private String extractJson(String raw) {
        raw = raw.trim();
        // Remove markdown code fences if present
        if (raw.contains("```")) {
            int start = raw.indexOf("{");
            int end = raw.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return raw.substring(start, end + 1);
            }
        }
        // Already pure JSON
        if (raw.startsWith("{")) {
            return raw;
        }
        // Try to find JSON in the response
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    /**
     * Feature 4: Smart Categorization from structured wizard data.
     * Citizen already picked the category — AI just assigns severity + generates clean summary.
     */
    public StructuredResult categorizeFromStructured(String category, String guidedAnswersJson,
                                                      String extraNote, String location) {
        String prompt = """
                You are a civic complaint analyst for CityLedger, an Indian municipal system.

                A citizen has reported an issue using a guided form. The category is already selected by the citizen.
                Your job is to:
                1. Assign a SEVERITY based on the structured answers
                2. Write a CLEAN 2-3 sentence summary suitable for a municipal officer
                3. Provide a short reason for the severity

                Respond with EXACTLY this JSON (no markdown, no extra text):
                {"severity": "...", "summary": "...", "reason": "..."}

                SEVERITY must be ONE of: LOW, MEDIUM, HIGH, CRITICAL
                SUMMARY must be a professional 2-3 sentence description of the issue for an officer.
                REASON must be a single sentence explaining why you chose that severity.

                Category: %s
                Structured Answers: %s
                Additional Note: %s
                Location: %s

                Respond ONLY with the JSON object.
                """.formatted(category, guidedAnswersJson,
                extraNote != null ? extraNote : "none", location);

        try {
            String response = callLLM(prompt);
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            String severity = node.get("severity").asText().toUpperCase();
            String summary = node.get("summary").asText();
            String reason = node.get("reason").asText();

            List<String> validSeverities = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
            if (!validSeverities.contains(severity)) severity = "MEDIUM";

            log.info("Structured AI: {} / {} — {}", category, severity, reason);
            return new StructuredResult(category, severity, summary, reason);

        } catch (Exception e) {
            log.error("Structured AI categorization failed: {}", e.getMessage());
            return new StructuredResult(category, "MEDIUM",
                    "A " + category.toLowerCase() + " issue has been reported at " + location + ".",
                    "AI classification unavailable — defaulted");
        }
    }

    // ── Result records ──

    public record AICategorization(String category, String severity, String reason) {}
    public record DuplicateCheckResult(boolean isDuplicate, Long duplicateOfId, String reason) {}
    public record StructuredResult(String category, String severity, String summary, String reason) {}
}
