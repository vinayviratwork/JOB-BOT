package com.vinay.jobbot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_listing", uniqueConstraints = {
        @UniqueConstraint(columnNames = "source_url")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", nullable = false, unique = true)
    private String sourceUrl;

    /** e.g. "greenhouse" */
    private String source;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "match_reasoning", columnDefinition = "TEXT")
    private String matchReasoning;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
