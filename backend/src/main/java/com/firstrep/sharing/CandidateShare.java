package com.firstrep.sharing;

import com.firstrep.common.Kinds.ShareStatus;
import com.firstrep.company.Company;
import com.firstrep.identity.UserAccount;
import com.firstrep.project.Project;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "candidate_shares")
@Getter
@Setter
@NoArgsConstructor
public class CandidateShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(nullable = false, length = 32)
    private String consentVersion;

    @Column(nullable = false)
    private LocalDateTime consentedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShareStatus status;

    @Column(nullable = false)
    private boolean includeProfile;
    @Column(nullable = false)
    private boolean includeCv;
    @Column(nullable = false)
    private boolean includeSubmission;
    @Column(nullable = false)
    private boolean includeAssessment;
    @Column(nullable = false)
    private boolean includeSkills;
    @Column(nullable = false)
    private boolean includeCertificate;
}
