package com.cityledger.cityledger.controller;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.ComplaintStatus;
import com.cityledger.cityledger.repository.AppUserRepository;
import com.cityledger.cityledger.repository.ComplaintRepository;
import com.cityledger.cityledger.service.AIService;
import com.cityledger.cityledger.service.BlockchainService;
import com.cityledger.cityledger.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ComplaintController {

    private final ComplaintRepository complaintRepository;
    private final AppUserRepository appUserRepository;
    private final BlockchainService blockchainService;
    private final SupabaseStorageService supabaseStorageService;
    private final AIService aiService;

    @GetMapping("/citizen/report")
    public String showReportForm() {
        return "citizen/report";
    }

    @PostMapping("/citizen/report")
    public String submitComplaint(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String location,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) List<MultipartFile> media,
            Authentication authentication) {

        // Resolve citizen from JWT/session
        String email = authentication.getName();
        AppUser citizen = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found in database"));

        // ── Feature 1: AI Category + Severity Assignment (via NVIDIA Llama 3.3) ──
        AIService.AICategorization aiResult = aiService.categorizeComplaint(title, description, location);
        String category = aiResult.category();
        String severity = aiResult.severity();
        String aiReason = aiResult.reason();
        log.info("AI classified: {} / {} — {}", category, severity, aiReason);

        // 1. Save complaint to PostgreSQL
        Complaint newComplaint = Complaint.builder()
                .citizen(citizen)
                .title(title)
                .description(description)
                .location(location)
                .latitude(latitude)
                .longitude(longitude)
                .category(category)
                .severity(severity)
                .aiReason(aiReason)
                .status(ComplaintStatus.FILED)
                .filedAt(LocalDateTime.now())
                .build();

        Complaint saved = complaintRepository.save(newComplaint);
        log.info("Complaint #{} FILED by {} at ({}, {})", saved.getId(), email, latitude, longitude);

        // 2. Upload media to Supabase bucket
        if (media != null && !media.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : media) {
                if (!file.isEmpty()) {
                    String mediaUrl = supabaseStorageService.uploadFile(file, saved.getId());
                    if (mediaUrl != null) urls.add(mediaUrl);
                }
            }
            if (!urls.isEmpty()) {
                String joinedUrls = String.join(",", urls);
                saved.setMediaUrl(joinedUrls);
                log.info("Media uploaded for complaint #{}: {}", saved.getId(), joinedUrls);
            }
        }

        // ── Feature 3: Duplicate Complaint Detector ──
        if (latitude != null && longitude != null) {
            try {
                List<Complaint> nearbyComplaints = complaintRepository.findNearbyOpenComplaints(latitude, longitude, 0.005);
                // Exclude the current complaint from the list
                nearbyComplaints.removeIf(c -> c.getId().equals(saved.getId()));
                if (!nearbyComplaints.isEmpty()) {
                    AIService.DuplicateCheckResult dupResult = aiService.checkForDuplicates(
                            title, description, location, nearbyComplaints.stream().limit(10).toList()
                    );
                    if (dupResult.isDuplicate() && dupResult.duplicateOfId() != null) {
                        saved.setDuplicateOfId(dupResult.duplicateOfId());
                        log.info("Complaint #{} flagged as duplicate of #{}", saved.getId(), dupResult.duplicateOfId());
                    }
                }
            } catch (Exception e) {
                log.warn("Duplicate check skipped: {}", e.getMessage());
            }
        }

        // 3. Generate SHA-256 hash of complaint data
        String complaintHash = blockchainService.generateComplaintHashHex(saved);
        saved.setComplaintHash(complaintHash);

        // 4. File hash on blockchain (Sepolia via Web3j)
        String txHash = blockchainService.fileOnChain(saved);
        saved.setBlockchainHash(txHash);

        // 5. Persist everything back to DB
        complaintRepository.save(saved);
        log.info("Complaint #{} complete — Category: {}, Severity: {}, TX: {}", saved.getId(), category, severity, txHash);

        return "redirect:/citizen/report?success=true&id=" + saved.getId()
                + "&hash=" + txHash
                + "&category=" + URLEncoder.encode(category, StandardCharsets.UTF_8)
                + "&severity=" + severity
                + "&reason=" + URLEncoder.encode(aiReason, StandardCharsets.UTF_8)
                + (saved.getDuplicateOfId() != null ? "&duplicateOf=" + saved.getDuplicateOfId() : "");
    }

    // ── Feature 2: AI Summary for Officer Drawer (AJAX endpoint) ──
    @GetMapping("/api/complaint/summary")
    @ResponseBody
    public Map<String, Object> getComplaintSummary(@RequestParam Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        // Use cached summary if available
        if (complaint.getAiSummary() != null && !complaint.getAiSummary().isBlank()) {
            return Map.of(
                    "summary", complaint.getAiSummary(),
                    "category", complaint.getCategory() != null ? complaint.getCategory() : "N/A",
                    "severity", complaint.getSeverity() != null ? complaint.getSeverity() : "N/A",
                    "aiReason", complaint.getAiReason() != null ? complaint.getAiReason() : ""
            );
        }

        // Generate new summary
        String summary = aiService.generateSummary(complaint);
        complaint.setAiSummary(summary);
        complaintRepository.save(complaint);

        return Map.of(
                "summary", summary,
                "category", complaint.getCategory() != null ? complaint.getCategory() : "N/A",
                "severity", complaint.getSeverity() != null ? complaint.getSeverity() : "N/A",
                "aiReason", complaint.getAiReason() != null ? complaint.getAiReason() : ""
        );
    }
}
