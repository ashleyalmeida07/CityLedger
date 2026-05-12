package com.cityledger.cityledger.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id", nullable = false)
    private AppUser citizen;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    /** True when an officer/admin replies — shown with 🏛️ Official badge in feed */
    @Builder.Default
    @Column(nullable = false)
    private boolean official = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
