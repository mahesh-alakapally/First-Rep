import { Route, Routes } from "react-router-dom";
import { Shell } from "./shell";
import { AboutPage, CataloguePage, LegalPage, PilotPage, PricingPage, ProjectPage, VerifyPage } from "./pages/public";
import { AcceptInvitePage, ForgotPage, LoginPage, RegisterPage, ResetPage, VerifyEmailPage } from "./pages/auth";
import { AccountPage, HomePage, NoticesPage } from "./pages/home";
import { BillingPage, CertificatesPage, ProfilePage, SharesPage, StudentHome, WorkspacePage } from "./pages/student";
import { CandidatePackage, CandidatesPage, CompanyHome, CompanyProjects, ProjectEditor, ReviewDetail, ReviewQueue, TeamPage } from "./pages/company";
import { AdminHome, AppealsPage, AuditPage, ModerationDetail, ModerationPage, PeoplePage } from "./pages/admin";

const privacy = `FirstRep stores the account, profile, submissions, reviews, and billing records needed to run a project.
You can download that data from Account, and you can ask for the personal fields to be anonymised.
Payment records and the audit trail are kept. A revoked share stops the company from opening the package.
The local demo uses a mock assessment and a billing simulator. Do not put production secrets in this stack.`;

const terms = `A certificate records that a human reviewer approved a project against its rubric.
It is not a job offer, a qualification from an institution, or a statement that a model ranked you against other people.
Companies see evidence only when you share it. Free enrolment covers two projects. Further projects need an active subscription.`;

export function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route path="/" element={<AboutPage />} />
        <Route path="/pricing" element={<PricingPage />} />
        <Route path="/projects" element={<CataloguePage />} />
        <Route path="/projects/:projectId" element={<ProjectPage />} />
        <Route path="/verify" element={<VerifyPage />} />
        <Route path="/privacy" element={<LegalPage title="Privacy" children={privacy} />} />
        <Route path="/terms" element={<LegalPage title="Terms" children={terms} />} />
        <Route path="/pilot" element={<PilotPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPage />} />
        <Route path="/reset-password" element={<ResetPage />} />
        <Route path="/invitations/accept" element={<AcceptInvitePage />} />
        <Route path="/home" element={<HomePage />} />
        <Route path="/account" element={<AccountPage />} />
        <Route path="/notifications" element={<NoticesPage />} />
        <Route path="/student" element={<StudentHome />} />
        <Route path="/student/enrollments/:enrollmentId" element={<WorkspacePage />} />
        <Route path="/student/certificates" element={<CertificatesPage />} />
        <Route path="/student/billing" element={<BillingPage />} />
        <Route path="/student/profile" element={<ProfilePage />} />
        <Route path="/student/shares" element={<SharesPage />} />
        <Route path="/company/:companyId" element={<CompanyHome />} />
        <Route path="/company/:companyId/projects" element={<CompanyProjects />} />
        <Route path="/company/:companyId/projects/new" element={<ProjectEditor mode="new" />} />
        <Route path="/company/:companyId/projects/:projectId" element={<ProjectEditor mode="edit" />} />
        <Route path="/company/:companyId/review" element={<ReviewQueue />} />
        <Route path="/company/:companyId/review/:submissionId" element={<ReviewDetail />} />
        <Route path="/company/:companyId/candidates" element={<CandidatesPage />} />
        <Route path="/company/:companyId/candidates/:shareId" element={<CandidatePackage />} />
        <Route path="/company/:companyId/team" element={<TeamPage />} />
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/projects" element={<ModerationPage />} />
        <Route path="/admin/projects/:projectId" element={<ModerationDetail />} />
        <Route path="/admin/users" element={<PeoplePage />} />
        <Route path="/admin/appeals" element={<AppealsPage />} />
        <Route path="/admin/audit" element={<AuditPage />} />
      </Route>
    </Routes>
  );
}
