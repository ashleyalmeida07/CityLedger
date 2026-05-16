package com.cityledger.cityledger;

import com.cityledger.cityledger.dto.ComplaintWithReportCount;
import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.ComplaintStatus;
import com.cityledger.cityledger.model.Role;
import com.cityledger.cityledger.repository.AppUserRepository;
import com.cityledger.cityledger.repository.ComplaintRepository;
import com.cityledger.cityledger.service.AIService;
import com.cityledger.cityledger.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final AIService aiService;
    private final BlockchainService blockchainService;
    private final com.cityledger.cityledger.service.EmailService emailService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "how-it-works";
    }

    @GetMapping("/ai-features")
    public String aiFeatures() {
        return "ai-features";
    }


    @GetMapping("/dashboard")
    public String dashboardRedirect(Authentication authentication) {
        if (authentication == null) return "redirect:/login";
        String email = authentication.getName();
        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/";
        return switch (user.getRole()) {
            case ADMIN -> "redirect:/admin/dashboard";
            case OFFICER -> "redirect:/officer/dashboard";
            case FIELD_WORKER -> "redirect:/field-worker/dashboard";
            case CITIZEN -> "redirect:/citizen/dashboard";
        };
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    // ══════════════════════════════════════════════════
    //  CITIZEN PAGES
    // ══════════════════════════════════════════════════

    @GetMapping("/citizen/dashboard")
    public String citizenDashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser citizen = userRepository.findByEmail(email).orElse(null);
        if (citizen != null) {
            List<Complaint> myComplaints = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen);
            long openCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
            long inProgressCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
            long resolvedCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
            
            // Category breakdown
            java.util.Map<String, Long> categoryBreakdown = myComplaints.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> c.getCategory() != null ? c.getCategory() : "Other",
                    java.util.stream.Collectors.counting()
                ));
            
            // Severity breakdown
            java.util.Map<String, Long> severityBreakdown = myComplaints.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> c.getSeverity() != null ? c.getSeverity() : "LOW",
                    java.util.stream.Collectors.counting()
                ));
            
            model.addAttribute("totalComplaints", myComplaints.size());
            model.addAttribute("openComplaints", openCount);
            model.addAttribute("inProgressComplaints", inProgressCount);
            model.addAttribute("resolvedComplaints", resolvedCount);
            model.addAttribute("recentComplaints", myComplaints.stream().limit(5).toList());
            model.addAttribute("categoryBreakdown", categoryBreakdown);
            model.addAttribute("severityBreakdown", severityBreakdown);
        }
        model.addAttribute("user", principal);
        return "citizen/dashboard";
    }

    @GetMapping("/citizen/reports")
    public String citizenReports(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser citizen = userRepository.findByEmail(email).orElse(null);
        if (citizen != null) {
            List<Complaint> myComplaints = complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen);
            
            // Calculate statistics
            long openCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
            long inProgressCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
            long resolvedCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
            
            model.addAttribute("complaints", myComplaints);
            model.addAttribute("totalComplaints", myComplaints.size());
            model.addAttribute("openComplaints", openCount);
            model.addAttribute("inProgressComplaints", inProgressCount);
            model.addAttribute("resolvedComplaints", resolvedCount);
        }
        model.addAttribute("user", principal);
        return "citizen/reports";
    }

    @GetMapping("/citizen/complaints")
    public String citizenComplaintsRedirect() {
        return "redirect:/citizen/reports";
    }

    @GetMapping("/citizen/track")
    public String citizenTrackRedirect(@RequestParam(required = false) Long id) {
        if (id != null) {
            return "redirect:/citizen/reports?id=" + id;
        }
        return "redirect:/citizen/reports";
    }

    @GetMapping("/citizen/map")
    public String citizenMap(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        
        // Only pass complaints that have valid coordinates
        List<Complaint> allComplaints = complaintRepository.findAll();
        List<Complaint> complaintsWithCoords = allComplaints.stream()
            .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
            .collect(java.util.stream.Collectors.toList());
        
        model.addAttribute("complaints", complaintsWithCoords);
        model.addAttribute("user", principal);
        return "citizen/map";
    }

    @GetMapping("/api/citizen/complaint/{id}")
    @ResponseBody
    public ResponseEntity<?> getComplaintDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        
        String email = principal.getAttribute("email");
        AppUser citizen = userRepository.findByEmail(email).orElse(null);
        if (citizen == null) return ResponseEntity.status(401).build();
        
        Complaint complaint = complaintRepository.findById(id).orElse(null);
        if (complaint == null) return ResponseEntity.notFound().build();
        
        // Verify the complaint belongs to this citizen
        if (!complaint.getCitizen().getId().equals(citizen.getId())) {
            return ResponseEntity.status(403).build();
        }
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", complaint.getId());
        response.put("title", complaint.getTitle());
        response.put("description", complaint.getDescription());
        response.put("category", complaint.getCategory());
        response.put("severity", complaint.getSeverity());
        response.put("status", complaint.getStatus().name());
        response.put("location", complaint.getLocation());
        response.put("latitude", complaint.getLatitude());
        response.put("longitude", complaint.getLongitude());
        response.put("mediaUrl", complaint.getMediaUrl());
        response.put("createdAt", complaint.getCreatedAt());
        response.put("updatedAt", complaint.getUpdatedAt());
        response.put("filedAt", complaint.getFiledAt());
        response.put("blockchainHash", complaint.getBlockchainHash());
        response.put("aiSummary", complaint.getAiSummary());
        response.put("aiReason", complaint.getAiReason());
        response.put("upvoteCount", complaint.getUpvoteCount());
        response.put("assignedWorker", complaint.getAssignedWorker() != null ? 
            java.util.Map.of("id", complaint.getAssignedWorker().getId(), 
                           "name", complaint.getAssignedWorker().getName()) : null);
        
        return ResponseEntity.ok(response);
    }

    private record ComplaintCard(
            Long id,
            String title,
            String category,
            String location,
            String severity,
            String status,
            String mediaUrl,
            LocalDateTime createdAt,
            int upvoteCount
    ) {
        private static ComplaintCard from(Complaint complaint) {
            String title = StringUtils.hasText(complaint.getTitle()) ? complaint.getTitle().trim() : "Untitled Issue";
            String category = StringUtils.hasText(complaint.getCategory()) ? complaint.getCategory().trim() : "General";
            String location = StringUtils.hasText(complaint.getLocation()) ? complaint.getLocation().trim() : "Location unknown";
            String severity = StringUtils.hasText(complaint.getSeverity())
                    ? complaint.getSeverity().trim().toUpperCase()
                    : "LOW";
            String status = complaint.getStatus() != null ? complaint.getStatus().name() : "FILED";
            String mediaUrl = StringUtils.hasText(complaint.getMediaUrl())
                    && !"null".equalsIgnoreCase(complaint.getMediaUrl().trim())
                    ? complaint.getMediaUrl().trim()
                    : null;

            return new ComplaintCard(
                    complaint.getId(),
                    title,
                    category,
                    location,
                    severity,
                    status,
                    mediaUrl,
                    complaint.getCreatedAt(),
                    complaint.getUpvoteCount()
            );
        }
    }

    // ══════════════════════════════════════════════════
    //  OFFICER PAGES
    // ══════════════════════════════════════════════════

    @GetMapping("/officer/dashboard")
    public String officerDashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        List<Complaint> allComplaints = complaintRepository.findAll();
        long openCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long inProgressCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
        long resolvedCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
        List<AppUser> fieldWorkers = userRepository.findByRole(Role.FIELD_WORKER);
        model.addAttribute("totalComplaints", (long) allComplaints.size());
        model.addAttribute("pendingComplaints", openCount);
        model.addAttribute("inProgressComplaints", inProgressCount);
        model.addAttribute("resolvedComplaints", resolvedCount);
        model.addAttribute("activeFieldWorkers", (long) fieldWorkers.size());
        model.addAttribute("user", principal);
        return "officer/dashboard";
    }

    @GetMapping("/officer/queue")
    public String officerQueue(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        List<Complaint> allComplaints = complaintRepository.findAllByOrderByCreatedAtDesc();

        // Get all original complaints (duplicateOfId is null)
        List<Complaint> originalComplaints = allComplaints.stream()
                .filter(c -> c.getDuplicateOfId() == null)
                .toList();

        // Map each original complaint to include report count (original + duplicates)
        List<ComplaintWithReportCount> complaintQueue = originalComplaints.stream()
                .map(original -> {
                    long duplicateCount = allComplaints.stream()
                            .filter(c -> original.getId().equals(c.getDuplicateOfId()))
                            .count();
                    long totalReporters = 1 + duplicateCount; // Original reporter + duplicates
                    return new ComplaintWithReportCount(original, totalReporters);
                })
                .collect(Collectors.toList());

        model.addAttribute("complaints", complaintQueue);
        model.addAttribute("fieldWorkers", userRepository.findByRole(Role.FIELD_WORKER));
        model.addAttribute("user", principal);
        return "officer/queue";
    }

    @GetMapping("/officer/complaint/{id}")
    public String officerComplaintDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        
        // Verify blockchain integrity
        BlockchainService.VerificationResult verification = blockchainService.verifyComplaintDetailed(complaint);
        
        model.addAttribute("complaint", complaint);
        model.addAttribute("fieldWorkers", userRepository.findByRole(Role.FIELD_WORKER));
        model.addAttribute("verification", verification);
        model.addAttribute("user", principal);
        return "officer/complaint-detail";
    }

    @PostMapping("/officer/queue/assign")
    public String assignComplaint(@RequestParam Long complaintId, @RequestParam Long workerId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        AppUser worker = userRepository.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found"));
        complaint.setAssignedWorker(worker);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaintRepository.save(complaint);
        
        emailService.sendEmail(worker.getEmail(), "New Task Assigned", "A new task '" + complaint.getTitle() + "' has been assigned to you. Please check your dashboard.");
        
        return "redirect:/officer/queue?assigned=true";
    }

    @GetMapping("/officer/alerts")
    public String officerAlerts(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        List<Complaint> allComplaints = complaintRepository.findAllByOrderByCreatedAtDesc();
        List<Complaint> criticalAlerts = allComplaints.stream()
                .filter(c -> "HIGH".equalsIgnoreCase(c.getSeverity()) || "CRITICAL".equalsIgnoreCase(c.getSeverity()))
                .limit(20)
                .toList();
        model.addAttribute("alerts", criticalAlerts);
        model.addAttribute("user", principal);
        return "officer/alerts";
    }

    // ══════════════════════════════════════════════════
    //  FIELD WORKER PAGES
    // ══════════════════════════════════════════════════

    @GetMapping("/field-worker/dashboard")
    public String fieldWorkerDashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser worker = userRepository.findByEmail(email).orElse(null);
        if (worker != null) {
            List<Complaint> myTasks = complaintRepository.findByAssignedWorker(worker);
            long pendingCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS || c.getStatus() == ComplaintStatus.FILED).count();
            long completedCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
            
            // Category breakdown
            java.util.Map<String, Long> categoryBreakdown = myTasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> c.getCategory() != null ? c.getCategory() : "Other",
                    java.util.stream.Collectors.counting()
                ));
            
            // Severity breakdown
            java.util.Map<String, Long> severityBreakdown = myTasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> c.getSeverity() != null ? c.getSeverity() : "N/A",
                    java.util.stream.Collectors.counting()
                ));
            
            // AI Score statistics (for completed tasks)
            long tasksWithScore = myTasks.stream()
                .filter(c -> c.getCompletionScore() != null)
                .count();
            double avgScore = myTasks.stream()
                .filter(c -> c.getCompletionScore() != null)
                .mapToInt(Complaint::getCompletionScore)
                .average()
                .orElse(0.0);
            long approvedTasks = myTasks.stream()
                .filter(c -> c.getCompletionScore() != null && c.getCompletionScore() >= 70)
                .count();
            
            model.addAttribute("totalTasks", (long) myTasks.size());
            model.addAttribute("pendingTasks", pendingCount);
            model.addAttribute("completedTasks", completedCount);
            model.addAttribute("categoryBreakdown", categoryBreakdown);
            model.addAttribute("severityBreakdown", severityBreakdown);
            model.addAttribute("tasksWithScore", tasksWithScore);
            model.addAttribute("avgScore", Math.round(avgScore));
            model.addAttribute("approvedTasks", approvedTasks);
        } else {
            model.addAttribute("totalTasks", 0L);
            model.addAttribute("pendingTasks", 0L);
            model.addAttribute("completedTasks", 0L);
            model.addAttribute("categoryBreakdown", new java.util.HashMap<>());
            model.addAttribute("severityBreakdown", new java.util.HashMap<>());
            model.addAttribute("tasksWithScore", 0L);
            model.addAttribute("avgScore", 0);
            model.addAttribute("approvedTasks", 0L);
        }
        model.addAttribute("user", principal);
        return "field-worker/dashboard";
    }

    @GetMapping("/field-worker/tasks")
    public String fieldWorkerTasks(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser worker = userRepository.findByEmail(email).orElse(null);
        if (worker != null) {
            model.addAttribute("tasks", complaintRepository.findByAssignedWorker(worker));
        }
        model.addAttribute("user", principal);
        return "field-worker/tasks";
    }

    @GetMapping("/field-worker/map")
    public String fieldWorkerMap(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser worker = userRepository.findByEmail(email).orElse(null);
        if (worker != null) {
            List<Complaint> myTasks = complaintRepository.findByAssignedWorker(worker);
            
            // Only pass tasks with valid coordinates
            List<Complaint> tasksWithCoords = myTasks.stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .collect(java.util.stream.Collectors.toList());
            
            // Calculate stats
            long pendingCount = myTasks.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.FILED)
                .count();
            long inProgressCount = myTasks.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS)
                .count();
            long completedCount = myTasks.stream()
                .filter(c -> c.getStatus() == ComplaintStatus.RESOLVED)
                .count();
            
            model.addAttribute("tasks", tasksWithCoords);
            model.addAttribute("pendingTasks", pendingCount);
            model.addAttribute("inProgressTasks", inProgressCount);
            model.addAttribute("completedTasks", completedCount);
        } else {
            model.addAttribute("tasks", new java.util.ArrayList<>());
            model.addAttribute("pendingTasks", 0L);
            model.addAttribute("inProgressTasks", 0L);
            model.addAttribute("completedTasks", 0L);
        }
        model.addAttribute("user", principal);
        return "field-worker/map";
    }

    @GetMapping("/field-worker/task/{id}")
    public String fieldWorkerTaskDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        Complaint task = complaintRepository.findById(id).orElse(null);
        model.addAttribute("task", task);
        model.addAttribute("user", principal);
        return "field-worker/task-detail";
    }

    @PostMapping("/field-worker/task/{id}/status")
    public String updateTaskStatus(@PathVariable Long id, @RequestParam String newStatus,
                                   @RequestParam(required = false) MultipartFile completionPhoto) {
        Complaint task = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        // If marking as RESOLVED, require completion photo and run AI comparison
        if ("RESOLVED".equals(newStatus)) {
            if (completionPhoto != null && !completionPhoto.isEmpty()) {
                try {
                    // Upload completion photo (you can integrate with your storage service)
                    String completionPhotoUrl = uploadCompletionPhoto(completionPhoto, id);
                    task.setCompletionPhotoUrl(completionPhotoUrl);
                    
                    // Run AI comparison between before and after images
                    if (task.getMediaUrl() != null && !task.getMediaUrl().isEmpty()) {
                        String beforeImageUrl = task.getMediaUrl().split(",")[0]; // Get first image
                        
                        boolean isCheat = false;
                        try {
                            org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
                            byte[] beforeBytes = rest.getForObject(beforeImageUrl, byte[].class);
                            byte[] afterBytes = completionPhoto.getBytes();
                            if (java.util.Arrays.equals(beforeBytes, afterBytes)) {
                                isCheat = true;
                            }
                        } catch (Exception ex) {
                            log.warn("Could not download before image for cheat detection", ex);
                        }
                        
                        if (isCheat) {
                            task.setCompletionScore(0);
                            task.setCompletionAssessment("Fraudulent submission detected. The completion photo uploaded is identical to the original complaint photo. Please upload a real photo showing the completed work.");
                            task.setCompletionObservations("Exact same image uploaded as the original complaint. Work cannot be verified.");
                            log.warn("Field worker uploaded identical image for task {}", id);
                        } else {
                            AIService.ImageComparisonResult comparison = aiService.compareBeforeAfterImages(
                                beforeImageUrl,
                                completionPhotoUrl,
                                task.getCategory(),
                                task.getDescription()
                            );
                            
                            task.setCompletionScore(comparison.score());
                            task.setCompletionAssessment(comparison.assessment());
                            task.setCompletionObservations(comparison.observations());
                            
                            log.info("AI Completion Score for task {}: {}/100 - {}", 
                                    id, comparison.score(), comparison.recommendation());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process completion photo: {}", e.getMessage());
                }
            }
        }
        
        switch (newStatus) {
            case "IN_PROGRESS" -> task.setStatus(ComplaintStatus.IN_PROGRESS);
            case "RESOLVED" -> {
                task.setStatus(ComplaintStatus.RESOLVED);
                if (task.getCitizen() != null && task.getCitizen().getEmail() != null) {
                    emailService.sendEmail(task.getCitizen().getEmail(), "Your Issue Has Been Resolved", "Good news! Your reported issue '" + task.getTitle() + "' has been resolved by our field worker.");
                }
            }
        }
        complaintRepository.save(task);
        return "redirect:/field-worker/task/" + id + "?updated=true";
    }
    
    private String uploadCompletionPhoto(MultipartFile file, Long taskId) {
        // For now, return a placeholder URL
        // In production, integrate with your storage service (Supabase, S3, etc.)
        return "/uploads/completion_" + taskId + "_" + System.currentTimeMillis() + ".jpg";
    }

    // ══════════════════════════════════════════════════
    //  OTHER
    // ══════════════════════════════════════════════════

    @GetMapping("/settings")
    public String settingsPage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("user", principal);
        return "settings";
    }
}
