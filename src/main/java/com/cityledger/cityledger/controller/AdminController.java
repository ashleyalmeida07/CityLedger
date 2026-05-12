package com.cityledger.cityledger.controller;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.ComplaintStatus;
import com.cityledger.cityledger.model.Role;
import com.cityledger.cityledger.repository.AppUserRepository;
import com.cityledger.cityledger.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(Model model) {
        List<AppUser> users = userRepository.findAll();
        List<Complaint> complaints = complaintRepository.findAll();
        
        long totalComplaints = complaints.size();
        long resolvedComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();
        long pendingComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.FILED).count();
        long inProgressComplaints = complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();

        model.addAttribute("users", users);
        model.addAttribute("roles", Role.values());
        
        model.addAttribute("complaints", complaints);
        model.addAttribute("totalComplaints", totalComplaints);
        model.addAttribute("resolvedComplaints", resolvedComplaints);
        model.addAttribute("pendingComplaints", pendingComplaints);
        model.addAttribute("inProgressComplaints", inProgressComplaints);
        
        return "admin/dashboard";
    }

    @PostMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateUserRole(@PathVariable Long id, @RequestParam Role role) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        user.setRole(role);
        userRepository.save(user);
        return "redirect:/admin/dashboard?success=true";
    }
}
