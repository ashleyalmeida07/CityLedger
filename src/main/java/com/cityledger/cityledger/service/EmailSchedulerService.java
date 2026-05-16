package com.cityledger.cityledger.service;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Complaint;
import com.cityledger.cityledger.model.ComplaintStatus;
import com.cityledger.cityledger.model.Role;
import com.cityledger.cityledger.repository.AppUserRepository;
import com.cityledger.cityledger.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSchedulerService {

    private final ComplaintRepository complaintRepository;
    private final AppUserRepository userRepository;
    private final EmailService emailService;

    /**
     * Send daily digest to all officers at 8 AM every day
     * Shows summary of pending complaints that need attention
     */
    @Scheduled(cron = "0 0 8 * * *") // Every day at 8:00 AM
    public void sendDailyDigestToOfficers() {
        log.info("Starting daily digest email to officers...");
        
        try {
            // Get all officers
            List<AppUser> officers = userRepository.findByRole(Role.OFFICER);
            if (officers.isEmpty()) {
                log.info("No officers found to send digest");
                return;
            }

            // Get pending complaints (FILED status)
            List<Complaint> pendingComplaints = complaintRepository.findByStatus(ComplaintStatus.FILED);
            
            if (pendingComplaints.isEmpty()) {
                log.info("No pending complaints to report");
                return;
            }

            // Build email content
            String subject = String.format("Daily Digest: %d Pending Issues Require Attention", pendingComplaints.size());
            String emailBody = buildDigestEmailBody(pendingComplaints);

            // Send to all officers
            for (AppUser officer : officers) {
                if (officer.getEmail() != null && !officer.getEmail().isEmpty()) {
                    emailService.sendEmail(officer.getEmail(), subject, emailBody);
                    log.info("Daily digest sent to officer: {}", officer.getEmail());
                }
            }

            log.info("Daily digest sent to {} officers", officers.size());
        } catch (Exception e) {
            log.error("Failed to send daily digest", e);
        }
    }

    /**
     * Send immediate notification to officers when critical issues are reported
     */
    public void sendCriticalAlertToOfficers(Complaint complaint) {
        try {
            List<AppUser> officers = userRepository.findByRole(Role.OFFICER);
            if (officers.isEmpty()) {
                return;
            }

            String subject = String.format("🚨 CRITICAL ISSUE REPORTED - #CL-%d", complaint.getId());
            String emailBody = String.format(
                "URGENT: A critical issue has been reported and requires immediate attention.\n\n" +
                "Report Details:\n" +
                "- ID: #CL-%d\n" +
                "- Title: %s\n" +
                "- Category: %s\n" +
                "- Severity: %s\n" +
                "- Location: %s\n" +
                "- Reported: %s\n\n" +
                "Description:\n%s\n\n" +
                "Please review and assign this issue immediately: https://cityledger.com/officer/complaint/%d\n\n" +
                "CityLedger Alert System",
                complaint.getId(),
                complaint.getTitle(),
                complaint.getCategory(),
                complaint.getSeverity(),
                complaint.getLocation(),
                complaint.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")),
                complaint.getDescription(),
                complaint.getId()
            );

            for (AppUser officer : officers) {
                if (officer.getEmail() != null && !officer.getEmail().isEmpty()) {
                    emailService.sendEmail(officer.getEmail(), subject, emailBody);
                }
            }

            log.info("Critical alert sent to {} officers for complaint #{}", officers.size(), complaint.getId());
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }

    private String buildDigestEmailBody(List<Complaint> complaints) {
        StringBuilder body = new StringBuilder();
        body.append("Good morning,\n\n");
        body.append("Citizens have reported issues that require your attention. Please review and assign them to field workers.\n\n");
        body.append("═══════════════════════════════════════════════════\n");
        body.append(String.format("PENDING ISSUES: %d\n", complaints.size()));
        body.append("═══════════════════════════════════════════════════\n\n");

        // Group by severity
        long criticalCount = complaints.stream().filter(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity())).count();
        long highCount = complaints.stream().filter(c -> "HIGH".equalsIgnoreCase(c.getSeverity())).count();
        long mediumCount = complaints.stream().filter(c -> "MEDIUM".equalsIgnoreCase(c.getSeverity())).count();
        long lowCount = complaints.stream().filter(c -> "LOW".equalsIgnoreCase(c.getSeverity())).count();

        body.append("SEVERITY BREAKDOWN:\n");
        if (criticalCount > 0) body.append(String.format("  🔴 CRITICAL: %d\n", criticalCount));
        if (highCount > 0) body.append(String.format("  🟠 HIGH: %d\n", highCount));
        if (mediumCount > 0) body.append(String.format("  🟡 MEDIUM: %d\n", mediumCount));
        if (lowCount > 0) body.append(String.format("  🟢 LOW: %d\n", lowCount));
        body.append("\n");

        // Group by category
        body.append("CATEGORY BREAKDOWN:\n");
        complaints.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                c -> c.getCategory() != null ? c.getCategory() : "Other",
                java.util.stream.Collectors.counting()
            ))
            .forEach((category, count) -> 
                body.append(String.format("  • %s: %d\n", category, count))
            );
        body.append("\n");

        // List top 5 critical/high priority issues
        List<Complaint> urgentIssues = complaints.stream()
            .filter(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity()) || "HIGH".equalsIgnoreCase(c.getSeverity()))
            .limit(5)
            .toList();

        if (!urgentIssues.isEmpty()) {
            body.append("TOP PRIORITY ISSUES:\n");
            body.append("───────────────────────────────────────────────────\n");
            for (Complaint c : urgentIssues) {
                body.append(String.format(
                    "\n#CL-%d | %s | %s\n" +
                    "  Category: %s\n" +
                    "  Location: %s\n" +
                    "  Reported: %s\n",
                    c.getId(),
                    c.getSeverity(),
                    c.getTitle(),
                    c.getCategory(),
                    c.getLocation(),
                    c.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))
                ));
            }
            body.append("\n");
        }

        body.append("═══════════════════════════════════════════════════\n\n");
        body.append("Please review the queue and assign field workers: https://cityledger.com/officer/queue\n\n");
        body.append("Thank you for keeping our city running smoothly!\n\n");
        body.append("Best regards,\n");
        body.append("CityLedger System");

        return body.toString();
    }
}
