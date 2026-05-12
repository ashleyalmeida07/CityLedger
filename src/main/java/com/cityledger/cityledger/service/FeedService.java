package com.cityledger.cityledger.service;

import com.cityledger.cityledger.model.*;
import com.cityledger.cityledger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private final ComplaintRepository complaintRepository;
    private final UpvoteRepository upvoteRepository;
    private final CommentRepository commentRepository;
    private final BlockchainService blockchainService;

    // Degrees per km (approx)
    private static final double DEG_PER_KM = 1.0 / 111.0;

    // ── Feed ──────────────────────────────────────────────────────────

    public List<Complaint> getFeed(Double lat, Double lon, int radiusKm, String sortBy, String category) {
        List<Complaint> complaints;

        if (lat != null && lon != null) {
            double delta = radiusKm * DEG_PER_KM;
            if ("upvotes".equals(sortBy)) {
                complaints = complaintRepository.findFeedByBoundingBoxSortedByUpvotes(
                        lat - delta, lat + delta, lon - delta, lon + delta);
            } else if ("critical".equals(sortBy)) {
                complaints = complaintRepository.findFeedByBoundingBox(
                        lat - delta, lat + delta, lon - delta, lon + delta);
                complaints = complaints.stream()
                        .sorted(Comparator.comparing(c -> severityOrder(c.getSeverity())))
                        .collect(Collectors.toList());
            } else {
                complaints = complaintRepository.findFeedByBoundingBox(
                        lat - delta, lat + delta, lon - delta, lon + delta);
            }
            // Refine with Haversine
            complaints = complaints.stream()
                    .filter(c -> c.getLatitude() != null && c.getLongitude() != null
                            && haversine(lat, lon, c.getLatitude(), c.getLongitude()) <= radiusKm)
                    .collect(Collectors.toList());
        } else {
            // No GPS — show all recent/top
            complaints = "upvotes".equals(sortBy)
                    ? complaintRepository.findTop50ByOrderByUpvoteCountDescCreatedAtDesc()
                    : complaintRepository.findTop50ByOrderByCreatedAtDesc();
        }

        // Category filter
        if (category != null && !category.isBlank() && !category.equals("all")) {
            complaints = complaints.stream()
                    .filter(c -> category.equalsIgnoreCase(c.getCategory()))
                    .collect(Collectors.toList());
        }

        return complaints;
    }

    public List<Complaint> getTrending() {
        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        List<Complaint> trending = complaintRepository.findTrendingThisWeek(weekAgo);
        return trending.stream().limit(3).collect(Collectors.toList());
    }

    // ── Upvote ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> toggleUpvote(Long complaintId, AppUser citizen) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        boolean alreadyUpvoted = upvoteRepository.existsByComplaintAndCitizen(complaint, citizen);
        boolean nowUpvoted;

        if (alreadyUpvoted) {
            upvoteRepository.findByComplaintAndCitizen(complaint, citizen)
                    .ifPresent(upvoteRepository::delete);
            complaint.setUpvoteCount(Math.max(0, complaint.getUpvoteCount() - 1));
            nowUpvoted = false;
        } else {
            upvoteRepository.save(Upvote.builder().complaint(complaint).citizen(citizen).build());
            complaint.setUpvoteCount(complaint.getUpvoteCount() + 1);
            nowUpvoted = true;
            checkAndEscalate(complaint);
        }

        complaintRepository.save(complaint);

        return Map.of(
                "upvoted", nowUpvoted,
                "count", complaint.getUpvoteCount()
        );
    }

    /** Auto-escalation: 10+ → HIGH, 25+ → CRITICAL, logs on-chain */
    private void checkAndEscalate(Complaint complaint) {
        int votes = complaint.getUpvoteCount();
        String oldSeverity = complaint.getSeverity();
        String newSeverity = null;

        if (votes >= 25 && !"CRITICAL".equals(oldSeverity)) {
            newSeverity = "CRITICAL";
        } else if (votes >= 10 && "LOW".equals(oldSeverity) || votes >= 10 && "MEDIUM".equals(oldSeverity)) {
            if (!"CRITICAL".equals(oldSeverity) && !"HIGH".equals(oldSeverity)) {
                newSeverity = "HIGH";
            }
        }

        if (newSeverity != null) {
            complaint.setSeverity(newSeverity);
            log.info("CityFeed auto-escalated complaint #{} from {} → {} ({} upvotes)",
                    complaint.getId(), oldSeverity, newSeverity, votes);

            // Log escalation on blockchain using the existing fileOnChain method
            try {
                String txHash = blockchainService.fileOnChain(complaint);
                log.info("Blockchain escalation logged for complaint #{} — TX: {}", complaint.getId(), txHash);
            } catch (Exception e) {
                log.warn("Blockchain escalation log failed (non-critical): {}", e.getMessage());
            }
        }
    }

    // ── Comments ──────────────────────────────────────────────────────

    @Transactional
    public Comment addComment(Long complaintId, AppUser citizen, String text, boolean official) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        return commentRepository.save(Comment.builder()
                .complaint(complaint)
                .citizen(citizen)
                .text(text.trim())
                .official(official)
                .build());
    }

    public List<Comment> getComments(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        return commentRepository.findByComplaintOrderByCreatedAtAsc(complaint);
    }

    public long getCommentCount(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .map(c -> commentRepository.countByComplaint(c))
                .orElse(0L);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private int severityOrder(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 0;
            case "HIGH"     -> 1;
            case "MEDIUM"   -> 2;
            default         -> 3;
        };
    }
}
