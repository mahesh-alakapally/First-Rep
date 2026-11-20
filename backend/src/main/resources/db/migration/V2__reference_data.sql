INSERT INTO roles (name, scope) VALUES
  ('STUDENT', 'PLATFORM'),
  ('ORG_ADMIN', 'COMPANY'),
  ('RECRUITER', 'COMPANY'),
  ('REVIEWER', 'COMPANY'),
  ('PLATFORM_ADMIN', 'PLATFORM');

INSERT INTO permissions (code) VALUES
  ('PROJECT_CREATE'),
  ('PROJECT_EDIT_OWN_COMPANY'),
  ('PROJECT_SUBMIT'),
  ('SUBMISSION_REVIEW_ASSIGNED'),
  ('CANDIDATE_EVIDENCE_VIEW_CONSENTED'),
  ('USER_MANAGE'),
  ('PROJECT_MODERATE'),
  ('SUBSCRIPTION_MANAGE'),
  ('REPORT_VIEW'),
  ('COMPANY_MANAGE'),
  ('PROFILE_MANAGE'),
  ('BILLING_MANAGE_SELF'),
  ('CERTIFICATE_VIEW_OWN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'STUDENT' AND p.code IN ('PROJECT_SUBMIT', 'PROFILE_MANAGE', 'BILLING_MANAGE_SELF', 'CERTIFICATE_VIEW_OWN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'ORG_ADMIN' AND p.code IN (
  'PROJECT_CREATE', 'PROJECT_EDIT_OWN_COMPANY', 'SUBMISSION_REVIEW_ASSIGNED',
  'CANDIDATE_EVIDENCE_VIEW_CONSENTED', 'COMPANY_MANAGE'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'RECRUITER' AND p.code IN ('CANDIDATE_EVIDENCE_VIEW_CONSENTED');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'REVIEWER' AND p.code IN ('SUBMISSION_REVIEW_ASSIGNED');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'PLATFORM_ADMIN' AND p.code IN (
  'USER_MANAGE', 'PROJECT_MODERATE', 'SUBSCRIPTION_MANAGE', 'REPORT_VIEW', 'COMPANY_MANAGE'
);

INSERT INTO plans (code, name, billing_interval, price_cents, currency, active) VALUES
  ('PREMIUM_MONTHLY', 'Premium monthly', 'MONTH', 1900, 'EUR', 1),
  ('PREMIUM_ANNUAL', 'Premium annual', 'YEAR', 19900, 'EUR', 1);

INSERT INTO skills (name, slug) VALUES
  ('Java', 'java'),
  ('SQL', 'sql'),
  ('Python', 'python'),
  ('Data visualisation', 'data-visualisation'),
  ('REST APIs', 'rest-apis'),
  ('Testing', 'testing'),
  ('Communication', 'communication');
