package com.cityledger.cityledger.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id", nullable = false)
    private AppUser citizen; // The person who reported the issue

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private String location; // Address or coordinate string

    private Double latitude;  // GPS latitude from auto-capture
    private Double longitude; // GPS longitude from auto-capture

    private String category; // AI-assigned: Pothole, Street Lamp, Garbage, Water Leakage, etc.

    private String severity; // AI-assigned: LOW, MEDIUM, HIGH, CRITICAL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ComplaintStatus status = ComplaintStatus.FILED;

    @Column(updatable = false)
    private LocalDateTime filedAt; // Exact timestamp when citizen submitted

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private AppUser assignedWorker; // Field worker assigned to fix it

    // For blockchain integration (Hash of the complaint data stored on IPFS/Ethereum)
    private String blockchainHash;

    // SHA-256 hash of complaint data (used for on-chain verification)
    private String complaintHash;

    // Supabase public URLs for uploaded media (photo/video), comma-separated
    @Column(columnDefinition = "TEXT")
    private String mediaUrl;

    // AI-generated reason for category/severity assignment
    @Column(columnDefinition = "TEXT")
    private String aiReason;

    // If AI detects this as a duplicate, stores the original complaint ID
    private Long duplicateOfId;

    // AI-generated summary for officers
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    // Structured answers from the guided wizard (JSON)
    @Column(columnDefinition = "TEXT")
    private String guidedAnswers;

    // Community upvote count — drives auto-escalation via CityFeed
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int upvoteCount = 0;

    // Field worker completion photo URL
    @Column(columnDefinition = "TEXT")
    private String completionPhotoUrl;

    // AI quality score for completion (0-100)
    private Integer completionScore;

    // AI assessment of the completion work
    @Column(columnDefinition = "TEXT")
    private String completionAssessment;

    // AI observations about the work
    @Column(columnDefinition = "TEXT")
    private String completionObservations;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // DB record creation time

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
