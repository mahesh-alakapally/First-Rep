package com.firstrep.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assessment_criterion_results")
@Getter
@Setter
@NoArgsConstructor
public class CriterionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @Column(nullable = false, length = 36)
    private String criterionPublicId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int maxScore;

    @Column(nullable = false, columnDefinition = "text")
    private String evidence;

    @Column(nullable = false, columnDefinition = "text")
    private String strengths;

    @Column(nullable = false, columnDefinition = "text")
    private String gaps;

    @Column(nullable = false, columnDefinition = "text")
    private String feedback;

    @Column(nullable = false, length = 32)
    private String confidence;

    @Column(length = 500)
    private String safetyFlags;
}
