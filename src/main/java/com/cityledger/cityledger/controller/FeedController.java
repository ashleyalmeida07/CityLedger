package com.cityledger.cityledger.controller;

import com.cityledger.cityledger.model.*;
import com.cityledger.cityledger.repository.*;
import com.cityledger.cityledger.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Controller
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final AppUserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final UpvoteRepository upvoteRepository;
    private final CommentRepository commentRepository;

    // ── Pages ─────────────────────────────────────────────────────────

    @GetMapping("/citizen/feed")
    public String feedPage(@RequestParam(required = false) Double lat,
                           @RequestParam(required = false) Double lon,
                           @RequestParam(defaultValue = "5") int radius,
                           @RequestParam(defaultValue = "latest") String sort,
                           @RequestParam(defaultValue = "all") String category,
                           Authentication auth, Model model) {

        AppUser user = getCurrentUser(auth);
        if (user == null) {
            return "redirect:/login?error=user_not_found";
        }
        
        List<Complaint> feed = feedService.getFeed(lat, lon, radius, sort, category);
        List<Complaint> trending = feedService.getTrending();

        // Build upvote status map for this user
        Map<Long, Boolean> upvotedByMe = new java.util.HashMap<>();
        Map<Long, Long> commentCounts = new java.util.HashMap<>();
        Map<Long, Long> duplicateCounts = new java.util.HashMap<>();
        
        for (Complaint c : feed) {
            upvotedByMe.put(c.getId(), upvoteRepository.existsByComplaintAndCitizen(c, user));
            commentCounts.put(c.getId(), commentRepository.countByComplaint(c));
            
            // Count duplicates for this complaint
            long dupCount = complaintRepository.countByDuplicateOfId(c.getId());
            if (dupCount > 0) {
                duplicateCounts.put(c.getId(), dupCount);
            }
        }

        model.addAttribute("feed", feed);
        model.addAttribute("trending", trending);
        model.addAttribute("upvotedByMe", upvotedByMe);
        model.addAttribute("commentCounts", commentCounts);
        model.addAttribute("duplicateCounts", duplicateCounts);
        model.addAttribute("currentRadius", radius);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentCategory", category);
        model.addAttribute("lat", lat);
        model.addAttribute("lon", lon);
        model.addAttribute("user", user);
        return "citizen/feed";
    }

    @GetMapping("/citizen/feed/{id}")
    public String feedDetail(@PathVariable Long id, Authentication auth, Model model) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));

        AppUser user = getCurrentUser(auth);
        if (user == null) {
            return "redirect:/login?error=user_not_found";
        }
        
        boolean upvotedByMe = upvoteRepository.existsByComplaintAndCitizen(complaint, user);
        List<Comment> comments = feedService.getComments(id);

        model.addAttribute("complaint", complaint);
        model.addAttribute("upvotedByMe", upvotedByMe);
        model.addAttribute("comments", comments);
        model.addAttribute("user", user);
        return "citizen/feed-detail";
    }

    // ── AJAX APIs ─────────────────────────────────────────────────────

    @PostMapping("/api/feed/upvote")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upvote(@RequestParam Long complaintId,
                                                       Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        Map<String, Object> result = feedService.toggleUpvote(complaintId, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/feed/comment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> comment(@RequestParam Long complaintId,
                                                        @RequestParam String text,
                                                        Authentication auth) {
        if (text == null || text.isBlank() || text.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment must be 1–500 chars"));
        }
        AppUser user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        boolean isOfficial = user.getRole() == Role.OFFICER || user.getRole() == Role.ADMIN;
        Comment comment = feedService.addComment(complaintId, user, text, isOfficial);

        return ResponseEntity.ok(Map.of(
                "id", comment.getId(),
                "text", comment.getText(),
                "author", comment.getCitizen().getName(),
                "official", comment.isOfficial(),
                "time", "Just now"
        ));
    }

    /** Returns full complaint detail + comments as JSON for the drawer (AJAX). */
    @GetMapping("/api/feed/detail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDetail(@RequestParam Long id, Authentication auth) {
        AppUser user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        Complaint c = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + id));

        boolean upvotedByMe = upvoteRepository.existsByComplaintAndCitizen(c, user);
        List<Comment> comments = commentRepository.findByComplaintOrderByCreatedAtAsc(c);

        List<Map<String, Object>> commentData = comments.stream().map(cm -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", cm.getId());
            m.put("author", cm.getCitizen().getName());
            m.put("text", cm.getText());
            m.put("official", cm.isOfficial());
            m.put("time", formatTimeAgo(cm.getCreatedAt()));
            return m;
        }).collect(java.util.stream.Collectors.toList());

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", c.getId());
        data.put("title", c.getTitle());
        data.put("description", c.getDescription() != null ? c.getDescription() : "");
        data.put("category", c.getCategory() != null ? c.getCategory() : "General");
        data.put("severity", c.getSeverity() != null ? c.getSeverity() : "LOW");
        data.put("status", c.getStatus().name());
        data.put("location", c.getLocation() != null ? c.getLocation() : "");
        data.put("latitude", c.getLatitude());
        data.put("longitude", c.getLongitude());
        data.put("mediaUrl", c.getMediaUrl() != null ? c.getMediaUrl() : "");
        data.put("aiSummary", c.getAiSummary() != null ? c.getAiSummary() : "");
        data.put("aiReason", c.getAiReason() != null ? c.getAiReason() : "");
        data.put("upvoteCount", c.getUpvoteCount());
        data.put("citizenName", c.getCitizen() != null ? c.getCitizen().getName() : "Anonymous");
        data.put("filedAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
        data.put("blockchainHash", c.getBlockchainHash() != null ? c.getBlockchainHash() : "");
        data.put("duplicateOfId", c.getDuplicateOfId());
        data.put("upvotedByMe", upvotedByMe);
        data.put("comments", commentData);
        return ResponseEntity.ok(data);
    }


    // ── Helper ────────────────────────────────────────────────────────

    private AppUser getCurrentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String formatTimeAgo(LocalDateTime dt) {
        if (dt == null) return "";
        long secs = Duration.between(dt, LocalDateTime.now()).getSeconds();
        if (secs < 60)    return "just now";
        if (secs < 3600)  return (secs / 60)   + "m ago";
        if (secs < 86400) return (secs / 3600)  + "h ago";
        return               (secs / 86400) + "d ago";
    }
}
