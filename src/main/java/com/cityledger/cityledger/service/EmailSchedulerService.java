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
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyDigestToOfficers() {
        log.info("Starting daily digest email to officers...");
        try {
            List<AppUser> officers = userRepository.findByRole(Role.OFFICER);
            if (officers.isEmpty()) {
                log.info("No officers found to send digest");
                return;
            }

            List<Complaint> pendingComplaints = complaintRepository.findByStatus(ComplaintStatus.FILED);
            if (pendingComplaints.isEmpty()) {
                log.info("No pending complaints to report");
                return;
            }

            long critical = pendingComplaints.stream().filter(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity())).count();
            long high     = pendingComplaints.stream().filter(c -> "HIGH".equalsIgnoreCase(c.getSeverity())).count();
            long medium   = pendingComplaints.stream().filter(c -> "MEDIUM".equalsIgnoreCase(c.getSeverity())).count();
            long low      = pendingComplaints.stream().filter(c -> "LOW".equalsIgnoreCase(c.getSeverity())).count();

            // Build priority issue rows (top 10 critical/high)
            List<Complaint> urgentIssues = pendingComplaints.stream()
                .filter(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity()) || "HIGH".equalsIgnoreCase(c.getSeverity()))
                .limit(10)
                .toList();

            StringBuilder rowsHtml = new StringBuilder();
            for (int i = 0; i < urgentIssues.size(); i++) {
                Complaint c = urgentIssues.get(i);
                rowsHtml.append(EmailTemplates.digestIssueRow(
                    c.getId(), c.getTitle(), c.getSeverity(), c.getLocation(), i % 2 == 0
                ));
            }

            String subject = String.format("\uD83D\uDCCB Daily Digest: %d Pending Issues — CityLedger", pendingComplaints.size());
            String html = EmailTemplates.dailyDigest(
                pendingComplaints.size(), critical, high, medium, low, rowsHtml.toString()
            );

            for (AppUser officer : officers) {
                if (officer.getEmail() != null && !officer.getEmail().isEmpty()) {
                    emailService.sendHtmlEmail(officer.getEmail(), subject, html);
                    log.info("Daily digest sent to officer: {}", officer.getEmail());
                }
            }
            log.info("Daily digest sent to {} officers", officers.size());
        } catch (Exception e) {
            log.error("Failed to send daily digest", e);
        }
    }

    /**
     * Send immediate HTML notification to officers when critical issues are reported
     */
    public void sendCriticalAlertToOfficers(Complaint complaint) {
        try {
            List<AppUser> officers = userRepository.findByRole(Role.OFFICER);
            if (officers.isEmpty()) return;

            String reportedAt = complaint.getCreatedAt() != null
                ? complaint.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))
                : "—";

            String subject = String.format("\uD83D\uDEA8 CRITICAL ISSUE #CL-%d — Immediate Action Required", complaint.getId());
            String html = EmailTemplates.criticalAlert(
                complaint.getId(),
                complaint.getTitle(),
                complaint.getCategory(),
                complaint.getSeverity(),
                complaint.getLocation(),
                reportedAt,
                complaint.getDescription()
            );

            for (AppUser officer : officers) {
                if (officer.getEmail() != null && !officer.getEmail().isEmpty()) {
                    emailService.sendHtmlEmail(officer.getEmail(), subject, html);
                }
            }
            log.info("Critical alert sent to {} officers for complaint #{}", officers.size(), complaint.getId());
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }
}
