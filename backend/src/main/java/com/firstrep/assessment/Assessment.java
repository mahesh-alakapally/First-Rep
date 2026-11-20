package com.firstrep.assessment;

import com.firstrep.common.Kinds.AssessmentStatus;
import com.firstrep.project.RubricVersion;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", unique = true)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_version_id")
    private RubricVersion rubricVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssessmentStatus status;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 120)
    private String modelVersion;

    @Column(nullable = false, length = 40)
    private String promptVersion;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime releasedAt;
}
