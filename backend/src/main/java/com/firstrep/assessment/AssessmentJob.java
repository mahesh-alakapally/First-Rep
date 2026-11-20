package com.firstrep.assessment;

import com.firstrep.common.Kinds.AssessmentStatus;
import com.firstrep.submission.Submission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assessment_jobs")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", unique = true)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssessmentStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
