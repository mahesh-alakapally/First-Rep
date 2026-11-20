CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  account_status VARCHAR(32) NOT NULL,
  email_verified_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_public_id (public_id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE email_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_email_tokens_hash (token_hash),
  CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  family_id VARCHAR(36) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_hash (token_hash),
  KEY idx_refresh_family (family_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB;

CREATE TABLE permissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id),
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB;

CREATE TABLE skills (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  slug VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skills_name (name),
  UNIQUE KEY uk_skills_slug (slug)
) ENGINE=InnoDB;

CREATE TABLE student_profiles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  headline VARCHAR(240) NULL,
  education VARCHAR(500) NULL,
  target_roles VARCHAR(500) NULL,
  bio TEXT NULL,
  portfolio_url VARCHAR(500) NULL,
  github_url VARCHAR(500) NULL,
  cv_object_key VARCHAR(500) NULL,
  cv_filename VARCHAR(255) NULL,
  share_profile_in_packages TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_user (user_id),
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE student_skills (
  profile_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  PRIMARY KEY (profile_id, skill_id),
  CONSTRAINT fk_student_skill_profile FOREIGN KEY (profile_id) REFERENCES student_profiles (id),
  CONSTRAINT fk_student_skill_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB;

CREATE TABLE companies (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(200) NOT NULL,
  description TEXT NULL,
  location VARCHAR(200) NULL,
  website VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_companies_public_id (public_id),
  UNIQUE KEY uk_companies_slug (slug)
) ENGINE=InnoDB;

CREATE TABLE company_memberships (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_membership (company_id, user_id),
  KEY idx_membership_user (user_id),
  CONSTRAINT fk_membership_company FOREIGN KEY (company_id) REFERENCES companies (id),
  CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE company_membership_roles (
  membership_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (membership_id, role_id),
  CONSTRAINT fk_cmr_membership FOREIGN KEY (membership_id) REFERENCES company_memberships (id),
  CONSTRAINT fk_cmr_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB;

CREATE TABLE company_invitations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  company_id BIGINT NOT NULL,
  email VARCHAR(255) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  accepted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invitation_public (public_id),
  KEY idx_invitation_hash (token_hash),
  CONSTRAINT fk_invitation_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB;

CREATE TABLE projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  company_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(600) NOT NULL,
  description TEXT NOT NULL,
  role_tag VARCHAR(120) NOT NULL,
  difficulty VARCHAR(32) NOT NULL,
  effort_hours INT NOT NULL,
  deadline DATETIME(6) NULL,
  eligibility TEXT NULL,
  certificate_conditions TEXT NULL,
  recruitment_linked TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  moderation_note TEXT NULL,
  published_at DATETIME(6) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  archived_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_projects_public_id (public_id),
  KEY idx_projects_status_deadline (status, deadline),
  KEY idx_projects_company (company_id, status),
  CONSTRAINT fk_projects_company FOREIGN KEY (company_id) REFERENCES companies (id),
  CONSTRAINT fk_projects_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE project_skills (
  project_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  PRIMARY KEY (project_id, skill_id),
  KEY idx_project_skills_skill (skill_id),
  CONSTRAINT fk_ps_project FOREIGN KEY (project_id) REFERENCES projects (id),
  CONSTRAINT fk_ps_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB;

CREATE TABLE project_deliverables (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  required_item TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_deliverable_project (project_id),
  CONSTRAINT fk_deliverable_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB;

CREATE TABLE rubric_versions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rubric_version (project_id, version_number),
  CONSTRAINT fk_rubric_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB;

CREATE TABLE rubric_criteria (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  rubric_version_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  max_score INT NOT NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_criterion_public (public_id),
  KEY idx_criterion_rubric (rubric_version_id),
  CONSTRAINT fk_criterion_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
) ENGINE=InnoDB;

CREATE TABLE plans (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  billing_interval VARCHAR(16) NOT NULL,
  price_cents INT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plans_code (code)
) ENGINE=InnoDB;

CREATE TABLE subscriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_customer_id VARCHAR(160) NULL,
  provider_subscription_id VARCHAR(160) NULL,
  current_period_start DATETIME(6) NULL,
  current_period_end DATETIME(6) NULL,
  cancel_at_period_end TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subscriptions_public (public_id),
  KEY idx_subscriptions_user_status (user_id, status),
  CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB;

CREATE TABLE payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  subscription_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  amount_cents INT NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_event_id VARCHAR(160) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_public (public_id),
  KEY idx_payments_user (user_id),
  CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
  CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE payment_webhook_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider VARCHAR(32) NOT NULL,
  event_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  processed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_webhook_event (provider, event_id)
) ENGINE=InnoDB;

CREATE TABLE checkout_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_session_id VARCHAR(160) NULL,
  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkout_public (public_id),
  KEY idx_checkout_user (user_id),
  CONSTRAINT fk_checkout_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_checkout_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB;

CREATE TABLE project_enrollments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  project_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  access_grant_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  enrolled_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_public (public_id),
  UNIQUE KEY uk_enrollment_pair (project_id, student_user_id),
  KEY idx_enrollment_student (student_user_id, status),
  CONSTRAINT fk_enrollment_project FOREIGN KEY (project_id) REFERENCES projects (id),
  CONSTRAINT fk_enrollment_student FOREIGN KEY (student_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE free_allowance_slots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_user_id BIGINT NOT NULL,
  slot_index INT NOT NULL,
  enrollment_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_free_slot (student_user_id, slot_index),
  UNIQUE KEY uk_free_slot_enrollment (enrollment_id),
  CONSTRAINT fk_free_slot_user FOREIGN KEY (student_user_id) REFERENCES users (id),
  CONSTRAINT fk_free_slot_enrollment FOREIGN KEY (enrollment_id) REFERENCES project_enrollments (id)
) ENGINE=InnoDB;

CREATE TABLE submissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  enrollment_id BIGINT NOT NULL,
  revision_number INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  notes TEXT NULL,
  rubric_version_id BIGINT NULL,
  submitted_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_submission_public (public_id),
  UNIQUE KEY uk_submission_revision (enrollment_id, revision_number),
  KEY idx_submission_status (status),
  CONSTRAINT fk_submission_enrollment FOREIGN KEY (enrollment_id) REFERENCES project_enrollments (id),
  CONSTRAINT fk_submission_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
) ENGINE=InnoDB;

CREATE TABLE submission_assets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  submission_id BIGINT NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  scan_status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_asset_public (public_id),
  KEY idx_asset_submission (submission_id),
  CONSTRAINT fk_asset_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
) ENGINE=InnoDB;

CREATE TABLE submission_links (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  submission_id BIGINT NOT NULL,
  url VARCHAR(1000) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_link_public (public_id),
  KEY idx_link_submission (submission_id),
  CONSTRAINT fk_link_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
) ENGINE=InnoDB;

CREATE TABLE outbox_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_status (status, id)
) ENGINE=InnoDB;

CREATE TABLE assessment_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  submission_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  idempotency_key VARCHAR(80) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_public (public_id),
  UNIQUE KEY uk_job_submission (submission_id),
  UNIQUE KEY uk_job_idem (idempotency_key),
  KEY idx_job_status (status),
  CONSTRAINT fk_job_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
) ENGINE=InnoDB;

CREATE TABLE assessments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  submission_id BIGINT NOT NULL,
  rubric_version_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model_version VARCHAR(120) NOT NULL,
  prompt_version VARCHAR(40) NOT NULL,
  summary TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  released_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_assessment_public (public_id),
  UNIQUE KEY uk_assessment_submission (submission_id),
  KEY idx_assessment_status (status),
  CONSTRAINT fk_assessment_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
  CONSTRAINT fk_assessment_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id)
) ENGINE=InnoDB;

CREATE TABLE assessment_criterion_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  assessment_id BIGINT NOT NULL,
  criterion_public_id VARCHAR(36) NOT NULL,
  score INT NOT NULL,
  max_score INT NOT NULL,
  evidence TEXT NOT NULL,
  strengths TEXT NOT NULL,
  gaps TEXT NOT NULL,
  feedback TEXT NOT NULL,
  confidence VARCHAR(32) NOT NULL,
  safety_flags VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_result_assessment (assessment_id),
  CONSTRAINT fk_result_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id)
) ENGINE=InnoDB;

CREATE TABLE human_reviews (
  id BIGINT NOT NULL AUTO_INCREMENT,
  assessment_id BIGINT NOT NULL,
  reviewer_user_id BIGINT NOT NULL,
  decision VARCHAR(32) NOT NULL,
  notes TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_review_assessment (assessment_id),
  CONSTRAINT fk_review_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id),
  CONSTRAINT fk_review_user FOREIGN KEY (reviewer_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE appeals (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  assessment_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  resolution_note TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_appeal_public (public_id),
  KEY idx_appeal_status (status),
  CONSTRAINT fk_appeal_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id),
  CONSTRAINT fk_appeal_student FOREIGN KEY (student_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE skills_rounds (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  instructions TEXT NULL,
  passing_score INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_public (public_id),
  UNIQUE KEY uk_round_project (project_id),
  CONSTRAINT fk_round_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB;

CREATE TABLE skills_round_questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  round_id BIGINT NOT NULL,
  prompt TEXT NOT NULL,
  question_type VARCHAR(32) NOT NULL,
  options_json TEXT NULL,
  correct_option VARCHAR(8) NULL,
  max_score INT NOT NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_public (public_id),
  KEY idx_question_round (round_id),
  CONSTRAINT fk_question_round FOREIGN KEY (round_id) REFERENCES skills_rounds (id)
) ENGINE=InnoDB;

CREATE TABLE skills_round_attempts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  round_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  score INT NULL,
  max_score INT NULL,
  passed TINYINT NULL,
  started_at DATETIME(6) NOT NULL,
  submitted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attempt_public (public_id),
  KEY idx_attempt_student (student_user_id, round_id),
  CONSTRAINT fk_attempt_round FOREIGN KEY (round_id) REFERENCES skills_rounds (id),
  CONSTRAINT fk_attempt_student FOREIGN KEY (student_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE skills_round_answers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  attempt_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_text TEXT NOT NULL,
  score INT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_answer_once (attempt_id, question_id),
  CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES skills_round_attempts (id),
  CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES skills_round_questions (id)
) ENGINE=InnoDB;

CREATE TABLE certificates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  verification_code VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  enrollment_id BIGINT NOT NULL,
  assessment_id BIGINT NOT NULL,
  student_display_name VARCHAR(160) NOT NULL,
  project_title VARCHAR(200) NOT NULL,
  company_name VARCHAR(200) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  pdf_object_key VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_certificate_public (public_id),
  UNIQUE KEY uk_certificate_code (verification_code),
  UNIQUE KEY uk_certificate_once (user_id, project_id),
  CONSTRAINT fk_certificate_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_certificate_project FOREIGN KEY (project_id) REFERENCES projects (id),
  CONSTRAINT fk_certificate_enrollment FOREIGN KEY (enrollment_id) REFERENCES project_enrollments (id),
  CONSTRAINT fk_certificate_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id)
) ENGINE=InnoDB;

CREATE TABLE candidate_shares (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  submission_id BIGINT NULL,
  purpose VARCHAR(500) NOT NULL,
  consent_version VARCHAR(32) NOT NULL,
  consented_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  status VARCHAR(32) NOT NULL,
  include_profile TINYINT NOT NULL,
  include_cv TINYINT NOT NULL,
  include_submission TINYINT NOT NULL,
  include_assessment TINYINT NOT NULL,
  include_skills TINYINT NOT NULL,
  include_certificate TINYINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_share_public (public_id),
  KEY idx_share_company_status (company_id, status, expires_at),
  KEY idx_share_user (user_id, status),
  CONSTRAINT fk_share_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_share_company FOREIGN KEY (company_id) REFERENCES companies (id),
  CONSTRAINT fk_share_project FOREIGN KEY (project_id) REFERENCES projects (id),
  CONSTRAINT fk_share_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)
) ENGINE=InnoDB;

CREATE TABLE evidence_package_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  share_id BIGINT NOT NULL,
  item_type VARCHAR(40) NOT NULL,
  item_ref VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_evidence_share (share_id),
  CONSTRAINT fk_evidence_share FOREIGN KEY (share_id) REFERENCES candidate_shares (id)
) ENGINE=InnoDB;

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_public (public_id),
  KEY idx_notification_user (user_id, created_at),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  action VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(80) NULL,
  company_id BIGINT NULL,
  detail VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_company (company_id, created_at),
  KEY idx_audit_action (action),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE data_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  request_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_data_request_public (public_id),
  KEY idx_data_request_user (user_id),
  CONSTRAINT fk_data_request_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE pilot_interest (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  name VARCHAR(160) NOT NULL,
  email VARCHAR(255) NOT NULL,
  organisation VARCHAR(200) NOT NULL,
  message VARCHAR(2000) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_interest_public (public_id)
) ENGINE=InnoDB;
