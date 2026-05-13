package com.cityledger.cityledger;

import com.cityledger.cityledger.dto.ComplaintWithReportCount;
import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.ComplaintStatus;
import com.cityledger.cityledger.model.Role;
import com.cityledger.cityledger.repository.AppUserRepository;
import com.cityledger.cityledger.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;

    @GetMapping("/")
    public String home() {
        return "index";
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
            model.addAttribute("totalComplaints", myComplaints.size());
            model.addAttribute("openComplaints", openCount);
            model.addAttribute("inProgressComplaints", inProgressCount);
            model.addAttribute("resolvedComplaints", resolvedCount);
            model.addAttribute("recentComplaints", myComplaints);
        }
        model.addAttribute("user", principal);
        return "citizen/dashboard";
    }

    @GetMapping("/citizen/complaints")
    public String citizenComplaints(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        String email = principal.getAttribute("email");
        AppUser citizen = userRepository.findByEmail(email).orElse(null);
        if (citizen != null) {
            model.addAttribute("complaints", complaintRepository.findByCitizenOrderByCreatedAtDesc(citizen));
        }
        model.addAttribute("user", principal);
        return "citizen/complaints";
    }

    @GetMapping("/citizen/track")
    public String citizenTrack(@RequestParam(required = false) Long id, @AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        if (id != null) {
            Complaint complaint = complaintRepository.findById(id).orElse(null);
            model.addAttribute("complaint", complaint);
        }
        model.addAttribute("user", principal);
        return "citizen/track";
    }

    @GetMapping("/citizen/map")
    public String citizenMap(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("complaints", complaintRepository.findAll());
        model.addAttribute("user", principal);
        return "citizen/map";
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
        model.addAttribute("complaint", complaint);
        model.addAttribute("fieldWorkers", userRepository.findByRole(Role.FIELD_WORKER));
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
            model.addAttribute("totalTasks", (long) myTasks.size());
            model.addAttribute("pendingTasks", pendingCount);
            model.addAttribute("completedTasks", completedCount);
        } else {
            model.addAttribute("totalTasks", 0L);
            model.addAttribute("pendingTasks", 0L);
            model.addAttribute("completedTasks", 0L);
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

    @GetMapping("/field-worker/task/{id}")
    public String fieldWorkerTaskDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/login";
        Complaint task = complaintRepository.findById(id).orElse(null);
        model.addAttribute("task", task);
        model.addAttribute("user", principal);
        return "field-worker/task-detail";
    }

    @PostMapping("/field-worker/task/{id}/status")
    public String updateTaskStatus(@PathVariable Long id, @RequestParam String newStatus) {
        Complaint task = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        switch (newStatus) {
            case "IN_PROGRESS" -> task.setStatus(ComplaintStatus.IN_PROGRESS);
            case "RESOLVED" -> task.setStatus(ComplaintStatus.RESOLVED);
        }
        complaintRepository.save(task);
        return "redirect:/field-worker/task/" + id + "?updated=true";
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
