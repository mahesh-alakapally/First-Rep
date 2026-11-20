export type Me = {
  id: string;
  email: string;
  status: string;
  platformRoles: string[];
  memberships: { companyId: string; companyName: string; roles: string[] }[];
  displayName: string | null;
};

export type ProjectSummary = {
  id: string;
  companyId: string;
  companyName: string;
  title: string;
  summary: string;
  roleTag: string;
  difficulty: string;
  effortHours: number;
  deadline: string | null;
  recruitmentLinked: boolean;
  status: string;
  skills: string[];
};

export type ProjectDetail = {
  summary: ProjectSummary;
  description: string;
  eligibility: string | null;
  certificateConditions: string | null;
  moderationNote: string | null;
  deliverables: { title: string; description: string; required: boolean }[];
  rubric: { id: string; title: string; description: string; maxScore: number }[];
  skillsQuestions: { id: string; prompt: string; questionType: string; options: string[]; correctOption: string | null }[];
  enrollmentId: string | null;
};

export type Page<T> = { items: T[]; page: number; size: number; total: number };

export type Enrollment = {
  id: string;
  projectId: string;
  projectTitle: string;
  companyName: string;
  status: string;
  accessGrantType: string;
  enrolledAt: string;
};

export type Submission = {
  id: string;
  enrollmentId: string;
  revision: number;
  status: string;
  notes: string | null;
  submittedAt: string | null;
  jobStatus: string | null;
  assets: { id: string; filename: string; contentType: string; sizeBytes: number; scanStatus: string }[];
  links: { id: string; url: string; kind: string }[];
  assessment: {
    id: string;
    status: string;
    summary: string | null;
    provider: string | null;
    modelVersion: string | null;
    promptVersion: string | null;
    releasedAt: string | null;
    criteria: {
      criterionId: string;
      score: number;
      maxScore: number;
      evidence: string;
      strengths: string;
      gaps: string;
      feedback: string;
      confidence: string;
      safetyFlags: string;
    }[];
    reviews: { decision: string; notes: string; createdAt: string }[];
  } | null;
};

export type Entitlements = {
  freeAllowance: number;
  freeRemaining: number;
  freeUsed: number;
  premiumActive: boolean;
  subscription: {
    id: string;
    planCode: string;
    status: string;
    provider: string;
    currentPeriodEnd: string;
    cancelAtPeriodEnd: boolean;
  } | null;
};

export type Plan = { code: string; name: string; interval: string; priceCents: number; currency: string };

export type Profile = {
  displayName: string;
  headline: string | null;
  education: string | null;
  targetRoles: string | null;
  bio: string | null;
  portfolioUrl: string | null;
  githubUrl: string | null;
  cvFilename: string | null;
  shareProfileInPackages: boolean;
  skillNames: string[];
};
