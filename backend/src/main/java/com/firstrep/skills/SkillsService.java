package com.firstrep.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.common.AppException;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AttemptStatus;
import com.firstrep.common.Kinds.QuestionType;
import com.firstrep.enrollment.EnrollmentRepository;
import com.firstrep.identity.UserAccount;
import com.firstrep.project.ProjectRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillsService {
    private final SkillsRoundRepository rounds;
    private final SkillsQuestionRepository questions;
    private final SkillsAttemptRepository attempts;
    private final SkillsAnswerRepository answers;
    private final ProjectRepository projects;
    private final EnrollmentRepository enrollments;
    private final ObjectMapper objectMapper;

    public SkillsService(SkillsRoundRepository rounds, SkillsQuestionRepository questions, SkillsAttemptRepository attempts,
                         SkillsAnswerRepository answers, ProjectRepository projects, EnrollmentRepository enrollments, ObjectMapper objectMapper) {
        this.rounds = rounds;
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.projects = projects;
        this.enrollments = enrollments;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RoundView roundForProject(UserAccount student, String projectId) {
        var project = projects.findByPublicId(projectId).orElseThrow(() -> AppException.notFound("Project not found."));
        enrollments.findByProjectIdAndStudentId(project.getId(), student.getId()).orElseThrow(() -> AppException.notFound("Enroll before the skills round."));
        SkillsRound round = rounds.findByProjectId(project.getId()).orElseThrow(() -> AppException.notFound("This project has no skills round."));
        List<QuestionView> views = questions.findByRoundIdOrderBySortOrderAsc(round.getId()).stream()
                .map(question -> new QuestionView(question.getPublicId(), question.getPrompt(), question.getQuestionType().name(), read(question.getOptionsJson())))
                .toList();
        var latest = attempts.findFirstByRoundIdAndStudentIdOrderByStartedAtDesc(round.getId(), student.getId()).orElse(null);
        return new RoundView(round.getPublicId(), round.getTitle(), round.getInstructions(), round.getPassingScore(), views,
                latest == null ? null : new AttemptView(latest.getPublicId(), latest.getStatus().name(), latest.getScore(), latest.getMaxScore(), latest.getPassed()));
    }

    @Transactional
    public AttemptView start(UserAccount student, String roundId) {
        SkillsRound round = rounds.findByPublicId(roundId).orElseThrow(() -> AppException.notFound("Skills round not found."));
        enrollments.findByProjectIdAndStudentId(round.getProject().getId(), student.getId()).orElseThrow(() -> AppException.notFound("Skills round not found."));
        var open = attempts.findFirstByRoundIdAndStudentIdAndStatusOrderByStartedAtDesc(round.getId(), student.getId(), AttemptStatus.IN_PROGRESS);
        if (open.isPresent()) {
            return new AttemptView(open.get().getPublicId(), open.get().getStatus().name(), null, null, null);
        }
        SkillsAttempt attempt = new SkillsAttempt();
        attempt.setPublicId(Ids.uuid());
        attempt.setRound(round);
        attempt.setStudent(student);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        attempts.save(attempt);
        return new AttemptView(attempt.getPublicId(), attempt.getStatus().name(), null, null, null);
    }

    @Transactional
    public AttemptView submit(UserAccount student, String attemptId, List<AnswerWrite> writes) {
        SkillsAttempt attempt = attempts.findByPublicId(attemptId).filter(item -> item.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> AppException.notFound("Attempt not found."));
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            return new AttemptView(attempt.getPublicId(), attempt.getStatus().name(), attempt.getScore(), attempt.getMaxScore(), attempt.getPassed());
        }
        int score = 0;
        int max = 0;
        for (SkillsQuestion question : questions.findByRoundIdOrderBySortOrderAsc(attempt.getRound().getId())) {
            String answer = writes == null ? "" : writes.stream().filter(item -> question.getPublicId().equals(item.questionId())).map(AnswerWrite::answer).findFirst().orElse("");
            SkillsAnswer stored = new SkillsAnswer();
            stored.setAttempt(attempt);
            stored.setQuestion(question);
            stored.setAnswerText(answer == null ? "" : answer.trim());
            if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                max += question.getMaxScore();
                int awarded = question.getCorrectOption() != null && question.getCorrectOption().equalsIgnoreCase(stored.getAnswerText()) ? question.getMaxScore() : 0;
                stored.setScore(awarded);
                score += awarded;
            }
            answers.save(stored);
        }
        attempt.setScore(score);
        attempt.setMaxScore(max);
        attempt.setPassed(score >= attempt.getRound().getPassingScore());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        return new AttemptView(attempt.getPublicId(), attempt.getStatus().name(), score, max, attempt.getPassed());
    }

    public SkillsAttempt latest(UserAccount student, Long projectId) {
        return rounds.findByProjectId(projectId)
                .flatMap(round -> attempts.findFirstByRoundIdAndStudentIdOrderByStartedAtDesc(round.getId(), student.getId()))
                .orElse(null);
    }

    private List<String> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    public record QuestionView(String id, String prompt, String questionType, List<String> options) {}
    public record RoundView(String id, String title, String instructions, int passingScore, List<QuestionView> questions, AttemptView latestAttempt) {}
    public record AttemptView(String id, String status, Integer score, Integer maxScore, Boolean passed) {}
    public record AnswerWrite(String questionId, String answer) {}
    public record SubmitAttempt(List<AnswerWrite> answers) {}
}
