package com.firstrep.certificate;

import com.firstrep.assessment.Assessment;
import com.firstrep.common.AppException;
import com.firstrep.common.Ids;
import com.firstrep.enrollment.ProjectEnrollment;
import com.firstrep.identity.StudentProfileRepository;
import com.firstrep.identity.UserAccount;
import com.firstrep.notification.NotificationService;
import com.firstrep.storage.ObjectStorage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateService {
    private final CertificateRepository certificates;
    private final StudentProfileRepository profiles;
    private final ObjectStorage storage;
    private final NotificationService notifications;

    public CertificateService(CertificateRepository certificates, StudentProfileRepository profiles, ObjectStorage storage,
                              NotificationService notifications) {
        this.certificates = certificates;
        this.profiles = profiles;
        this.storage = storage;
        this.notifications = notifications;
    }

    @Transactional
    public Certificate issue(Assessment assessment) {
        ProjectEnrollment enrollment = assessment.getSubmission().getEnrollment();
        UserAccount student = enrollment.getStudent();
        var existing = certificates.findByUserIdAndProjectId(student.getId(), enrollment.getProject().getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        String name = profiles.findByUserId(student.getId()).map(profile -> profile.getDisplayName()).orElse("Student");
        Certificate certificate = new Certificate();
        certificate.setPublicId(Ids.uuid());
        certificate.setVerificationCode(Ids.verificationCode());
        certificate.setUser(student);
        certificate.setProject(enrollment.getProject());
        certificate.setEnrollment(enrollment);
        certificate.setAssessment(assessment);
        certificate.setStudentDisplayName(name);
        certificate.setProjectTitle(enrollment.getProject().getTitle());
        certificate.setCompanyName(enrollment.getProject().getCompany().getName());
        certificate.setIssuedAt(LocalDateTime.now(ZoneOffset.UTC));
        byte[] pdf = pdf(certificate);
        String key = "certificates/" + certificate.getVerificationCode() + ".pdf";
        storage.put(key, pdf, "application/pdf");
        certificate.setPdfObjectKey(key);
        certificates.save(certificate);
        notifications.notify(student, "Certificate ready", "A certificate is available for " + certificate.getProjectTitle() + ".");
        return certificate;
    }

    @Transactional(readOnly = true)
    public List<CertificateView> mine(UserAccount user) {
        return certificates.findByUserIdOrderByIssuedAtDesc(user.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PublicCertificate verify(String code) {
        Certificate certificate = certificates.findByVerificationCode(code).orElseThrow(() -> AppException.notFound("Certificate not found."));
        return new PublicCertificate(certificate.getStudentDisplayName(), certificate.getProjectTitle(), certificate.getCompanyName(),
                certificate.getIssuedAt().toString(), certificate.getVerificationCode(),
                "This confirms completion of the named project and its human-reviewed assessment. It is not a job offer and not proof of general professional ability.");
    }

    @Transactional(readOnly = true)
    public byte[] download(UserAccount user, String certificateId) {
        Certificate certificate = certificates.findByPublicId(certificateId)
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> AppException.notFound("Certificate not found."));
        return storage.get(certificate.getPdfObjectKey());
    }

    private CertificateView toView(Certificate certificate) {
        return new CertificateView(certificate.getPublicId(), certificate.getVerificationCode(), certificate.getProjectTitle(),
                certificate.getCompanyName(), certificate.getIssuedAt().toString());
    }

    private byte[] pdf(Certificate certificate) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 22);
                stream.newLineAtOffset(64, 700);
                stream.showText("FirstRep");
                stream.setFont(PDType1Font.HELVETICA, 14);
                stream.newLineAtOffset(0, -36);
                stream.showText("Project completion certificate");
                stream.newLineAtOffset(0, -40);
                stream.showText(safe(certificate.getStudentDisplayName()));
                stream.newLineAtOffset(0, -24);
                stream.showText(safe(certificate.getProjectTitle()));
                stream.newLineAtOffset(0, -24);
                stream.showText(safe(certificate.getCompanyName()));
                stream.newLineAtOffset(0, -24);
                stream.showText("Issued " + certificate.getIssuedAt().toLocalDate());
                stream.newLineAtOffset(0, -24);
                stream.showText("Code " + certificate.getVerificationCode());
                stream.newLineAtOffset(0, -40);
                stream.setFont(PDType1Font.HELVETICA, 11);
                stream.showText("Confirms this project assessment only. Not a job offer.");
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Certificate PDF failed", ex);
        }
    }

    private static String safe(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^\\p{Print}]", " ");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    public record CertificateView(String id, String verificationCode, String projectTitle, String companyName, String issuedAt) {}
    public record PublicCertificate(String studentDisplayName, String projectTitle, String companyName, String issuedAt,
                                    String verificationCode, String statement) {}
}
