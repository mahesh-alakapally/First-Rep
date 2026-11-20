package com.firstrep.common;

public final class Kinds {
    private Kinds() {}

    public enum AccountStatus { PENDING_VERIFICATION, ACTIVE, SUSPENDED, DELETED }
    public enum TokenPurpose { VERIFY_EMAIL, PASSWORD_RESET }
    public enum CompanyStatus { ACTIVE, SUSPENDED }
    public enum MembershipStatus { INVITED, ACTIVE, REVOKED }
    public enum ProjectStatus { DRAFT, PENDING_MODERATION, PUBLISHED, PAUSED, CLOSED, ARCHIVED }
    public enum Difficulty { INTRODUCTORY, INTERMEDIATE, ADVANCED }
    public enum RubricStatus { DRAFT, PUBLISHED, SUPERSEDED }
    public enum SubscriptionStatus { INCOMPLETE, ACTIVE, PAST_DUE, CANCEL_AT_PERIOD_END, CANCELED, EXPIRED }
    public enum CheckoutStatus { PENDING, COMPLETED, EXPIRED }
    public enum EnrollmentStatus {
        ENROLLED, IN_PROGRESS, SUBMITTED, REVIEW_PENDING, APPROVED, CHANGES_REQUESTED, REJECTED, COMPLETED, CANCELLED
    }
    public enum AccessGrantType { FREE_CREDIT, PREMIUM }
    public enum SlotStatus { AVAILABLE, CONSUMED }
    public enum SubmissionStatus { DRAFT, SUBMITTED }
    public enum ScanStatus { PENDING, CLEAN, REJECTED }
    public enum LinkKind { GITHUB, LIVE }
    public enum OutboxStatus { PENDING, PUBLISHED, FAILED }
    public enum AssessmentStatus {
        QUEUED, PROCESSING, AI_DRAFT_READY, HUMAN_REVIEW, RELEASED, RETRYING, DEAD_LETTER, MANUAL_REVIEW_REQUIRED
    }
    public enum ReviewDecision { APPROVE, REQUEST_CHANGES, REJECT }
    public enum AppealStatus { OPEN, UPHELD, DENIED }
    public enum QuestionType { MULTIPLE_CHOICE, SHORT_ANSWER }
    public enum AttemptStatus { IN_PROGRESS, SUBMITTED }
    public enum ShareStatus { PENDING, ACTIVE, EXPIRED, REVOKED }
    public enum DataRequestType { EXPORT, DELETION }
    public enum DataRequestStatus { OPEN, COMPLETED }

    public static final String CONSENT_VERSION = "2026-11-07";
}
