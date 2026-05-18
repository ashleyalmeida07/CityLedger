package com.cityledger.cityledger.controller;

import com.cityledger.cityledger.dto.ComplaintWithReportCount;
import com.cityledger.cityledger.model.*;
import com.cityledger.cityledger.repository.*;
import com.cityledger.cityledger.service.AIService;
import com.cityledger.cityledger.service.BlockchainService;
import com.cityledger.cityledger.service.FeedService;
import com.cityledger.cityledger.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ApiRestController {

    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final CommentRepository commentRepository;
    private final UpvoteRepository upvoteRepository;
    private final FeedService feedService;
    private final AIService aiService;
    private final BlockchainService blockchainService;
    private final SupabaseStorageService supabaseStorageService;
    private final com.cityledger.cityledger.service.EmailService emailService;

    private AppUser getCurrentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String formatTimeAgo(LocalDateTime dt) {
        if (dt == null) return "";
        long secs = Duration.between(dt, LocalDateTime.now()).getSeconds();
        if (secs < 60) return "just now";
        if (secs < 3600) return (secs / 60) + "m ago";
        if (secs < 86400) return (secs / 3600) + "h ago";
        return (secs / 86400) + "d ago";
    }

    private Map<String, Object> complaintToMap(Complaint c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("title", c.getTitle() != null ? c.getTitle() : "Untitled");
        m.put("description", c.getDescription() != null ? c.getDescription() : "");
        m.put("location", c.getLocation() != null ? c.getLocation() : "");
        m.put("latitude", c.getLatitude());
        m.put("longitude", c.getLongitude());
        m.put("category", c.getCategory() != null ? c.getCategory() : "General");
        m.put("severity", c.getSeverity() != null ? c.getSeverity() : "LOW");
        m.put("status", c.getStatus() != null ? c.getStatus().name() : "FILED");
        m.put("filedAt", c.getFiledAt() != null ? c.getFiledAt().toString() : "");
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
        m.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
        m.put("mediaUrl", c.getMediaUrl() != null ? c.getMediaUrl() : "");
        m.put("blockchainHash", c.getBlockchainHash() != null ? c.getBlockchainHash() : "");
        m.put("complaintHash", c.getComplaintHash() != null ? c.getComplaintHash() : "");
        m.put("aiReason", c.getAiReason() != null ? c.getAiReason() : "");
        m.put("aiSummary", c.getAiSummary() != null ? c.getAiSummary() : "");
        m.put("upvoteCount", c.getUpvoteCount());
        m.put("duplicateOfId", c.getDuplicateOfId());
        m.put("completionPhotoUrl", c.getCompletionPhotoUrl() != null ? c.getCompletionPhotoUrl() : "");
        m.put("completionScore", c.getCompletionScore());
        m.put("completionAssessment", c.getCompletionAssessment() != null ? c.getCompletionAssessment() : "");
        m.put("completionObservations", c.getCompletionObservations() != null ? c.getCompletionObservations() : "");
        m.put("guidedAnswers", c.getGuidedAnswers() != null ? c.getGuidedAnswers() : "");
        if (c.getCitizen() != null) {
            m.put("citizenId", c.getCitizen().getId());
            m.put("citizenName", c.getCitizen().getName() != null ? c.getCitizen().getName() : "Anonymous");
        }
        if (c.getAssignedWorker() != null) {
            m.put("assignedWorkerId", c.getAssignedWorker().getId());
            m.put("assignedWorkerName", c.getAssignedWorker().getName());
        }
        return m;
    }

    private Map<String, Object> userToMap(AppUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("name", u.getName() != null ? u.getName() : "");
        m.put("role", u.getRole() != null ? u.getRole().name() : "CITIZEN");
        m.put("pictureUrl", u.getPictureUrl() != null ? u.getPictureUrl() : "");
        return m;
    }

    // ══════════════════════════════════════════════════
    // AUTH
    // ══════════════════════════════════════════════════

    @GetMapping("/auth/me")
    public ResponseEntity<?> getAuthMe(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        return ResponseEntity.ok(userToMap(user));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<?> signup(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false, defaultValue = "CITIZEN") String role) {
        
        // Check if user already exists
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", "Email already exists"
            ));
        }

        // Create new user
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password); // Note: Should be encoded by UserService if you have one
        user.setEnabled(true);
        
        // Set role
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            user.setRole(Role.CITIZEN);
        }
        
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Registration successful");
        response.put("user", userToMap(user));
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════
    // CITIZEN ENDPOINTS
    // ══════════════════════════════════════════════════

    @GetMapping("/citizen/dashboard")
    public ResponseEntity<?> citizenDashboard(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> myComplaints = complaintRepository.findByCitizenOrderByCreatedAtDesc(user);
        long openCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long inProgressCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
        long assignedCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.ASSIGNED).count();
        long resolvedCount = myComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();

        Map<String, Long> categoryBreakdown = myComplaints.stream()
            .collect(Collectors.groupingBy(c -> c.getCategory() != null ? c.getCategory() : "Other", Collectors.counting()));

        Map<String, Long> severityBreakdown = myComplaints.stream()
            .collect(Collectors.groupingBy(c -> c.getSeverity() != null ? c.getSeverity() : "LOW", Collectors.counting()));

        List<Map<String, Object>> recentComplaints = myComplaints.stream().limit(5).map(this::complaintToMap).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("user", userToMap(user));
        response.put("totalComplaints", myComplaints.size());
        response.put("openComplaints", openCount);
        response.put("inProgressComplaints", inProgressCount);
        response.put("assignedComplaints", assignedCount);
        response.put("resolvedComplaints", resolvedCount);
        response.put("categoryBreakdown", categoryBreakdown);
        response.put("severityBreakdown", severityBreakdown);
        response.put("recentComplaints", recentComplaints);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/citizen/reports")
    public ResponseEntity<?> citizenReports(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> myComplaints = complaintRepository.findByCitizenOrderByCreatedAtDesc(user);
        List<Map<String, Object>> complaints = myComplaints.stream().map(this::complaintToMap).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("complaints", complaints);
        response.put("totalComplaints", complaints.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/citizen/report")
    public ResponseEntity<?> submitComplaint(
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

        AppUser citizen = getCurrentUser(authentication);
        if (citizen == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        String finalCategory;
        String severity;
        String aiReason;
        String aiSummary = null;

        if (category != null && !category.isBlank() && guidedAnswers != null && !guidedAnswers.isBlank()) {
            AIService.StructuredResult result = aiService.categorizeFromStructured(category, guidedAnswers, extraNote, location);
            finalCategory = result.category();
            severity = result.severity();
            aiReason = result.reason();
            aiSummary = result.summary();
        } else {
            AIService.AICategorization aiResult = aiService.categorizeComplaint(title, description, location);
            finalCategory = aiResult.category();
            severity = aiResult.severity();
            aiReason = aiResult.reason();
        }

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

        if (media != null && !media.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : media) {
                if (!file.isEmpty()) {
                    String mediaUrl = supabaseStorageService.uploadFile(file, saved.getId());
                    if (mediaUrl != null) urls.add(mediaUrl);
                }
            }
            if (!urls.isEmpty()) {
                saved.setMediaUrl(String.join(",", urls));
            }
        }

        if (latitude != null && longitude != null) {
            try {
                List<Complaint> nearbyComplaints = complaintRepository.findNearbyOpenComplaints(latitude, longitude, 0.005);
                nearbyComplaints.removeIf(c -> c.getId().equals(saved.getId()));
                if (!nearbyComplaints.isEmpty()) {
                    AIService.DuplicateCheckResult dupResult = aiService.checkForDuplicates(title, description, location, nearbyComplaints.stream().limit(10).toList());
                    if (dupResult.isDuplicate() && dupResult.duplicateOfId() != null) {
                        saved.setDuplicateOfId(dupResult.duplicateOfId());
                    }
                }
            } catch (Exception e) {
                log.warn("Duplicate check skipped: {}", e.getMessage());
            }
        }

        String complaintHash = blockchainService.generateComplaintHashHex(saved);
        saved.setComplaintHash(complaintHash);
        String txHash = blockchainService.fileOnChain(saved);
        saved.setBlockchainHash(txHash);
        complaintRepository.save(saved);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("complaint", complaintToMap(saved));
        response.put("message", "Complaint submitted successfully");
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════
    // FEED ENDPOINTS
    // ══════════════════════════════════════════════════

    @GetMapping("/citizen/feed")
    public ResponseEntity<?> getFeed(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "5") int radius,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "all") String category,
            Authentication auth) {

        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> feed = feedService.getFeed(lat, lon, radius, sort, category);
        List<Complaint> trending = feedService.getTrending();

        List<Map<String, Object>> feedData = new ArrayList<>();
        for (Complaint c : feed) {
            Map<String, Object> item = complaintToMap(c);
            item.put("upvotedByMe", upvoteRepository.existsByComplaintAndCitizen(c, user));
            item.put("commentCount", commentRepository.countByComplaint(c));
            item.put("timeAgo", formatTimeAgo(c.getCreatedAt()));
            long dupCount = complaintRepository.countByDuplicateOfId(c.getId());
            if (dupCount > 0) item.put("duplicateCount", dupCount);
            feedData.add(item);
        }

        List<Map<String, Object>> trendingData = trending.stream().map(this::complaintToMap).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("feed", feedData);
        response.put("trending", trendingData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/citizen/map")
    public ResponseEntity<?> citizenMap(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> allComplaints = complaintRepository.findAll();
        List<Map<String, Object>> complaintsWithCoords = allComplaints.stream()
            .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
            .map(this::complaintToMap)
            .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("complaints", complaintsWithCoords);
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════
    // OFFICER ENDPOINTS
    // ══════════════════════════════════════════════════

    @GetMapping("/officer/dashboard")
    public ResponseEntity<?> officerDashboard(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> allComplaints = complaintRepository.findAll();
        long openCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long assignedCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.ASSIGNED).count();
        long inProgressCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
        long resolvedCount = allComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
        List<AppUser> fieldWorkers = userRepository.findByRole(Role.FIELD_WORKER);

        Map<String, Long> categoryBreakdown = allComplaints.stream()
            .collect(Collectors.groupingBy(c -> c.getCategory() != null ? c.getCategory() : "Other", Collectors.counting()));

        Map<String, Long> severityBreakdown = allComplaints.stream()
            .collect(Collectors.groupingBy(c -> c.getSeverity() != null ? c.getSeverity() : "LOW", Collectors.counting()));

        Map<String, Object> response = new HashMap<>();
        response.put("user", userToMap(user));
        response.put("totalComplaints", (long) allComplaints.size());
        response.put("pendingComplaints", openCount);
        response.put("assignedComplaints", assignedCount);
        response.put("inProgressComplaints", inProgressCount);
        response.put("resolvedComplaints", resolvedCount);
        response.put("activeFieldWorkers", (long) fieldWorkers.size());
        response.put("categoryBreakdown", categoryBreakdown);
        response.put("severityBreakdown", severityBreakdown);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/officer/queue")
    public ResponseEntity<?> officerQueue(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> allComplaints = complaintRepository.findAllByOrderByCreatedAtDesc();
        List<Complaint> originalComplaints = allComplaints.stream()
                .filter(c -> c.getDuplicateOfId() == null)
                .toList();

        List<Map<String, Object>> complaintQueue = originalComplaints.stream()
                .map(original -> {
                    long duplicateCount = allComplaints.stream()
                            .filter(c -> original.getId().equals(c.getDuplicateOfId()))
                            .count();
                    Map<String, Object> m = complaintToMap(original);
                    m.put("reportCount", 1 + duplicateCount);
                    return m;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> fieldWorkers = userRepository.findByRole(Role.FIELD_WORKER)
                .stream().map(this::userToMap).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("complaints", complaintQueue);
        response.put("fieldWorkers", fieldWorkers);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/officer/complaint/{id}")
    public ResponseEntity<?> officerComplaintDetail(@PathVariable Long id, Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));

        BlockchainService.VerificationResult verification = blockchainService.verifyComplaintDetailed(complaint);

        Map<String, Object> response = new HashMap<>();
        response.put("complaint", complaintToMap(complaint));
        response.put("verification", Map.of(
                "isValid", verification.isValid(),
                "storedHash", verification.getStoredHash() != null ? verification.getStoredHash() : "",
                "currentHash", verification.getCurrentHash() != null ? verification.getCurrentHash() : "",
                "blockchainTxHash", verification.getBlockchainTxHash() != null ? verification.getBlockchainTxHash() : "",
                "message", verification.getMessage() != null ? verification.getMessage() : ""
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/officer/queue/assign")
    public ResponseEntity<?> assignComplaint(@RequestParam Long complaintId, @RequestParam Long workerId, Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        AppUser worker = userRepository.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found"));

        complaint.setAssignedWorker(worker);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaintRepository.save(complaint);

        emailService.sendEmail(worker.getEmail(), "New Task Assigned",
                "A new task '" + complaint.getTitle() + "' has been assigned to you. Please check your dashboard.");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Complaint assigned successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/officer/alerts")
    public ResponseEntity<?> officerAlerts(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> allComplaints = complaintRepository.findAllByOrderByCreatedAtDesc();
        List<Complaint> criticalAlerts = allComplaints.stream()
                .filter(c -> "HIGH".equalsIgnoreCase(c.getSeverity()) || "CRITICAL".equalsIgnoreCase(c.getSeverity()))
                .limit(20)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("alerts", criticalAlerts.stream().map(this::complaintToMap).collect(Collectors.toList()));
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════
    // FIELD WORKER ENDPOINTS
    // ══════════════════════════════════════════════════

    @GetMapping("/field-worker/dashboard")
    public ResponseEntity<?> fieldWorkerDashboard(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> myTasks = complaintRepository.findByAssignedWorker(user);
        long pendingCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS || c.getStatus() == ComplaintStatus.FILED).count();
        long completedCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();

        Map<String, Long> categoryBreakdown = myTasks.stream()
            .collect(Collectors.groupingBy(c -> c.getCategory() != null ? c.getCategory() : "Other", Collectors.counting()));

        Map<String, Long> severityBreakdown = myTasks.stream()
            .collect(Collectors.groupingBy(c -> c.getSeverity() != null ? c.getSeverity() : "N/A", Collectors.counting()));

        long tasksWithScore = myTasks.stream().filter(c -> c.getCompletionScore() != null).count();
        double avgScore = myTasks.stream()
            .filter(c -> c.getCompletionScore() != null)
            .mapToInt(Complaint::getCompletionScore)
            .average().orElse(0.0);
        long approvedTasks = myTasks.stream()
            .filter(c -> c.getCompletionScore() != null && c.getCompletionScore() >= 70).count();

        Map<String, Object> response = new HashMap<>();
        response.put("user", userToMap(user));
        response.put("totalTasks", (long) myTasks.size());
        response.put("pendingTasks", pendingCount);
        response.put("completedTasks", completedCount);
        response.put("categoryBreakdown", categoryBreakdown);
        response.put("severityBreakdown", severityBreakdown);
        response.put("tasksWithScore", tasksWithScore);
        response.put("avgScore", Math.round(avgScore));
        response.put("approvedTasks", approvedTasks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/field-worker/tasks")
    public ResponseEntity<?> fieldWorkerTasks(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> myTasks = complaintRepository.findByAssignedWorker(user);
        Map<String, Object> response = new HashMap<>();
        response.put("tasks", myTasks.stream().map(this::complaintToMap).collect(Collectors.toList()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/field-worker/task/{id}")
    public ResponseEntity<?> fieldWorkerTaskDetail(@PathVariable Long id, Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Complaint task = complaintRepository.findById(id).orElse(null);
        if (task == null) return ResponseEntity.notFound().build();

        Map<String, Object> response = new HashMap<>();
        response.put("task", complaintToMap(task));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/field-worker/task/{id}/status")
    public ResponseEntity<?> updateTaskStatus(
            @PathVariable Long id,
            @RequestParam String newStatus,
            @RequestParam(required = false) MultipartFile completionPhoto,
            Authentication auth) {

        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Complaint task = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        if ("RESOLVED".equals(newStatus)) {
            if (completionPhoto != null && !completionPhoto.isEmpty()) {
                try {
                    String completionPhotoUrl = supabaseStorageService.uploadFile(completionPhoto, id);
                    task.setCompletionPhotoUrl(completionPhotoUrl);

                    if (task.getMediaUrl() != null && !task.getMediaUrl().isEmpty()) {
                        String beforeImageUrl = task.getMediaUrl().split(",")[0];
                        AIService.ImageComparisonResult comparison = aiService.compareBeforeAfterImages(
                            beforeImageUrl, completionPhotoUrl, task.getCategory(), task.getDescription());
                        task.setCompletionScore(comparison.score());
                        task.setCompletionAssessment(comparison.assessment());
                        task.setCompletionObservations(comparison.observations());
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
                    emailService.sendEmail(task.getCitizen().getEmail(), "Your Issue Has Been Resolved",
                            "Good news! Your reported issue '" + task.getTitle() + "' has been resolved.");
                }
            }
        }
        complaintRepository.save(task);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("task", complaintToMap(task));
        response.put("message", "Status updated successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/field-worker/map")
    public ResponseEntity<?> fieldWorkerMap(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Complaint> myTasks = complaintRepository.findByAssignedWorker(user);
        List<Complaint> tasksWithCoords = myTasks.stream()
            .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
            .collect(Collectors.toList());

        long pendingCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long inProgressCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
        long completedCount = myTasks.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();

        Map<String, Object> response = new HashMap<>();
        response.put("tasks", tasksWithCoords.stream().map(this::complaintToMap).collect(Collectors.toList()));
        response.put("pendingTasks", pendingCount);
        response.put("inProgressTasks", inProgressCount);
        response.put("completedTasks", completedCount);
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════
    // ADMIN ENDPOINTS
    // ══════════════════════════════════════════════════

    @GetMapping("/admin/dashboard")
    public ResponseEntity<?> adminDashboard(Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<AppUser> users = userRepository.findAll();
        List<Complaint> complaints = complaintRepository.findAll();

        long totalComplaints = complaints.size();
        long resolvedComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
        long pendingComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long inProgressComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();

        Map<String, Object> response = new HashMap<>();
        response.put("user", userToMap(user));
        response.put("users", users.stream().map(this::userToMap).collect(Collectors.toList()));
        response.put("complaints", complaints.stream().map(this::complaintToMap).collect(Collectors.toList()));
        response.put("totalComplaints", totalComplaints);
        response.put("resolvedComplaints", resolvedComplaints);
        response.put("pendingComplaints", pendingComplaints);
        response.put("inProgressComplaints", inProgressComplaints);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestParam String role, Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        AppUser targetUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        try {
            targetUser.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role));
        }
        userRepository.save(targetUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Role updated successfully");
        response.put("user", userToMap(targetUser));
        return ResponseEntity.ok(response);
    }
}
