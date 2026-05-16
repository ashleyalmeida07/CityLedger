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
    private final com.cityledger.cityledger.service.EmailService emailService;

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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String guidedAnswers,
            @RequestParam(required = false) String extraNote,
            Authentication authentication) {

        // Resolve citizen
        String email = authentication.getName();
        AppUser citizen = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found in database"));

        // ── AI Classification ──
        String finalCategory;
        String severity;
        String aiReason;
        String aiSummary = null;

        if (category != null && !category.isBlank() && guidedAnswers != null && !guidedAnswers.isBlank()) {
            // Wizard mode: citizen picked category + structured answers
            AIService.StructuredResult result = aiService.categorizeFromStructured(
                    category, guidedAnswers, extraNote, location);
            finalCategory = result.category();
            severity = result.severity();
            aiReason = result.reason();
            aiSummary = result.summary();
            log.info("Wizard AI: {} / {} — {}", finalCategory, severity, aiReason);
        } else {
            // Legacy mode: free-text submission
            AIService.AICategorization aiResult = aiService.categorizeComplaint(title, description, location);
            finalCategory = aiResult.category();
            severity = aiResult.severity();
            aiReason = aiResult.reason();
            log.info("Legacy AI: {} / {} — {}", finalCategory, severity, aiReason);
        }

        // Build complaint
        Complaint newComplaint = Complaint.builder()
                .citizen(citizen)
                .title(title)
                .description(description)
                .location(location)
                .latitude(latitude)
                .longitude(longitude)
                .category(finalCategory)
                .severity(severity)
                .aiReason(aiReason)
                .aiSummary(aiSummary)
                .guidedAnswers(guidedAnswers)
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
        log.info("Complaint #{} complete — Category: {}, Severity: {}, TX: {}", saved.getId(), finalCategory, severity, txHash);

        // 6. Send confirmation email to citizen
        try {
            String emailSubject = "Report Submitted Successfully - CityLedger #" + saved.getId();
            String emailBody = String.format(
                "Dear %s,\n\n" +
                "Thank you for reporting an issue to CityLedger. Your report has been successfully submitted and recorded on the blockchain.\n\n" +
                "Report Details:\n" +
                "- Report ID: #CL-%d\n" +
                "- Category: %s\n" +
                "- Severity: %s\n" +
                "- Location: %s\n" +
                "- Status: Filed\n\n" +
                "What happens next?\n" +
                "1. Our AI system has analyzed your report\n" +
                "2. An officer will review and assign it to a field worker\n" +
                "3. The field worker will address the issue\n" +
                "4. You'll receive an email when the issue is resolved\n\n" +
                "Action will be taken immediately. You can track your report at: %s/citizen/reports\n\n" +
                "Blockchain Transaction: %s\n\n" +
                "Thank you for helping make our city better!\n\n" +
                "Best regards,\n" +
                "CityLedger Team",
                citizen.getName(),
                saved.getId(),
                finalCategory,
                severity,
                location,
                "https://cityledger.com", // Replace with actual domain
                txHash != null ? "https://sepolia.etherscan.io/tx/" + txHash : "Processing..."
            );
            emailService.sendEmail(citizen.getEmail(), emailSubject, emailBody);
            log.info("Confirmation email sent to citizen: {}", citizen.getEmail());
        } catch (Exception e) {
            log.error("Failed to send confirmation email to citizen", e);
        }

        return "redirect:/citizen/report?success=true&id=" + saved.getId()
                + "&hash=" + (txHash != null ? txHash : "")
                + "&category=" + (finalCategory != null ? URLEncoder.encode(finalCategory, StandardCharsets.UTF_8) : "")
                + "&severity=" + (severity != null ? severity : "")
                + "&reason=" + (aiReason != null ? URLEncoder.encode(aiReason, StandardCharsets.UTF_8) : "")
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
