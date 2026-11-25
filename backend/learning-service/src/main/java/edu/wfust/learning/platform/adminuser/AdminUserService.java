package edu.wfust.learning.platform.adminuser;

import edu.wfust.learning.platform.auth.PasswordPolicy;
import edu.wfust.learning.platform.auth.RefreshTokenMapper;
import edu.wfust.learning.platform.entity.SysUserEntity;
import edu.wfust.learning.platform.mapper.AdminUserMapper;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final AdminUserMapper adminUserMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(SysUserMapper sysUserMapper,
                            AdminUserMapper adminUserMapper,
                            RefreshTokenMapper refreshTokenMapper,
                            PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.adminUserMapper = adminUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserPageView listUsers(String keyword, String role, Boolean enabled, Integer page, Integer size) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedRole = normalizeRoleFilter(role);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int offset = safePage * safeSize;

        long total = adminUserMapper.countUsers(normalizedKeyword, normalizedRole, enabled);
        if (total == 0) {
            return new UserPageView(List.of(), total, safePage, safeSize);
        }

        List<AdminUserMapper.UserAccountRow> rows = adminUserMapper.selectUserRows(
                normalizedKeyword, normalizedRole, enabled, safeSize, offset);
        if (rows.isEmpty()) {
            return new UserPageView(List.of(), total, safePage, safeSize);
        }

        List<Long> userIds = rows.stream().map(AdminUserMapper.UserAccountRow::id).toList();
        Map<Long, AdminUserMapper.UserCountStatRow> readingMap = adminUserMapper.selectReadingStatsByUsers(userIds)
                .stream().collect(Collectors.toMap(AdminUserMapper.UserCountStatRow::userId, row -> row));
        Map<Long, AdminUserMapper.UserQuizStatRow> quizMap = adminUserMapper.selectQuizStatsByUsers(userIds)
                .stream().collect(Collectors.toMap(AdminUserMapper.UserQuizStatRow::userId, row -> row));
        Map<Long, AdminUserMapper.UserMasteryStatRow> masteryMap = adminUserMapper.selectMasteryStatsByUsers(userIds)
                .stream().collect(Collectors.toMap(AdminUserMapper.UserMasteryStatRow::userId, row -> row));
        Map<Long, Long> wrongMap = toCountMap(adminUserMapper.selectOpenWrongCountsByUsers(userIds));
        Map<Long, Long> dueReviewMap = toCountMap(adminUserMapper.selectDueReviewCountsByUsers(userIds));
        Map<Long, Long> tokenMap = toCountMap(adminUserMapper.selectActiveTokenCountsByUsers(userIds));

        List<UserSummaryView> items = rows.stream().map(row -> {
            var reading = readingMap.get(row.id());
            var quiz = quizMap.get(row.id());
            var mastery = masteryMap.get(row.id());
            long attempts = quiz == null ? 0L : quiz.totalCount();
            long correct = quiz == null ? 0L : quiz.correctCount();
            int accuracy = attempts > 0 ? (int) Math.round(correct * 100.0 / attempts) : 0;
            double avgMastery = mastery == null ? 0.0 : roundOneDecimal(mastery.averageMastery());
            LocalDateTime lastActiveAt = latestTime(
                    reading == null ? null : reading.lastActiveAt(),
                    quiz == null ? null : quiz.lastActiveAt(),
                    mastery == null ? null : mastery.lastActiveAt());
            return new UserSummaryView(
                    row.id(),
                    row.username(),
                    row.displayName(),
                    row.role(),
                    Boolean.TRUE.equals(row.enabled()),
                    row.createdAt(),
                    reading == null ? 0L : reading.completedSections(),
                    attempts,
                    correct,
                    accuracy,
                    avgMastery,
                    wrongMap.getOrDefault(row.id(), 0L),
                    dueReviewMap.getOrDefault(row.id(), 0L),
                    tokenMap.getOrDefault(row.id(), 0L),
                    lastActiveAt);
        }).toList();

        return new UserPageView(items, total, safePage, safeSize);
    }

    @Transactional
    public UserSummaryView createUser(CreateUserRequest request) {
        String username = request.username().trim();
        String displayName = normalizeDisplayName(request.displayName(), username);
        String role = normalizeStrictRole(request.role());
        PasswordPolicy.validate(request.password());

        if (sysUserMapper.findByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        SysUserEntity entity = new SysUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(request.password()));
        entity.setDisplayName(displayName);
        entity.setRole(role);
        entity.setEnabled(request.enabled() == null || request.enabled());

        try {
            sysUserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        return loadSingleSummary(entity.getId());
    }

    @Transactional
    public UserSummaryView updateUser(Long operatorId, Long targetUserId, UpdateUserRequest request) {
        SysUserEntity existing = requireUser(targetUserId);
        String nextRole = normalizeStrictRole(request.role());
        String nextDisplayName = normalizeDisplayName(request.displayName(), existing.getUsername());

        if (Objects.equals(operatorId, targetUserId) && !"ADMIN".equals(nextRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改当前登录管理员自身的角色");
        }

        boolean roleChanged = !nextRole.equals(existing.getRole());
        existing.setDisplayName(nextDisplayName);
        existing.setRole(nextRole);
        sysUserMapper.updateById(existing);

        if (roleChanged) {
            refreshTokenMapper.revokeAllActiveByUserId(targetUserId, LocalDateTime.now());
        }
        return loadSingleSummary(targetUserId);
    }

    @Transactional
    public UserSummaryView setUserEnabled(Long operatorId, Long targetUserId, boolean enabled) {
        SysUserEntity existing = requireUser(targetUserId);
        if (!enabled && Objects.equals(operatorId, targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前登录的管理员账号");
        }
        existing.setEnabled(enabled);
        sysUserMapper.updateById(existing);
        if (!enabled) {
            refreshTokenMapper.revokeAllActiveByUserId(targetUserId, LocalDateTime.now());
        }
        return loadSingleSummary(targetUserId);
    }

    @Transactional
    public void resetPassword(Long targetUserId, String newPassword) {
        SysUserEntity existing = requireUser(targetUserId);
        PasswordPolicy.validate(newPassword);
        existing.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(existing);
        refreshTokenMapper.revokeAllActiveByUserId(targetUserId, LocalDateTime.now());
    }

    @Transactional
    public int revokeTokens(Long operatorId, Long targetUserId) {
        requireUser(targetUserId);
        if (Objects.equals(operatorId, targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前账号请通过右上角退出登录结束会话");
        }
        return refreshTokenMapper.revokeAllActiveByUserId(targetUserId, LocalDateTime.now());
    }

    /**
     * 按白名单脱敏构造单用户独立教学画像：
     * 仅返回账号状态、学习进度、掌握度、错题待复习、实验分配和助教活跃概况，
     * 严禁查询或返回学生私有笔记、收藏及匿名科研评估量表与评价原文。
     */
    public UserProfileView userProfile(Long targetUserId) {
        AdminUserMapper.UserAccountRow account = adminUserMapper.selectUserAccount(targetUserId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        long totalSections = adminUserMapper.countTotalEnabledSections();
        List<AdminUserMapper.UserCourseProgressRow> courseRows = adminUserMapper.selectUserCourseProgress(targetUserId);
        List<AdminUserMapper.UserKnowledgeMasteryRow> masteryRows = adminUserMapper.selectUserKnowledgeMastery(targetUserId);
        List<AdminUserMapper.UserWrongQuestionRow> wrongRows = adminUserMapper.selectUserWrongQuestions(targetUserId);
        List<AdminUserMapper.UserAssignmentRow> assignmentRows = adminUserMapper.selectUserAssignments(targetUserId);
        List<AdminUserMapper.UserAiSessionRow> aiRows = adminUserMapper.selectUserAiSessions(targetUserId);

        var quizStat = adminUserMapper.selectQuizStatsByUsers(List.of(targetUserId)).stream().findFirst().orElse(null);
        var masteryStat = adminUserMapper.selectMasteryStatsByUsers(List.of(targetUserId)).stream().findFirst().orElse(null);
        long openWrongCount = toCountMap(adminUserMapper.selectOpenWrongCountsByUsers(List.of(targetUserId)))
                .getOrDefault(targetUserId, 0L);
        long dueReviewCount = toCountMap(adminUserMapper.selectDueReviewCountsByUsers(List.of(targetUserId)))
                .getOrDefault(targetUserId, 0L);
        long activeSessionCount = toCountMap(adminUserMapper.selectActiveTokenCountsByUsers(List.of(targetUserId)))
                .getOrDefault(targetUserId, 0L);

        long completedSections = courseRows.stream().mapToLong(AdminUserMapper.UserCourseProgressRow::completedSections).sum();
        long attempts = quizStat == null ? 0L : quizStat.totalCount();
        long correct = quizStat == null ? 0L : quizStat.correctCount();
        int accuracy = attempts > 0 ? (int) Math.round(correct * 100.0 / attempts) : 0;
        double averageMastery = masteryStat == null ? 0.0 : roundOneDecimal(masteryStat.averageMastery());
        long aiQuestionCount = aiRows.stream().mapToLong(row -> row.userMessageCount() == null ? 0L : row.userMessageCount()).sum();

        LocalDateTime lastStudiedAt = latestTime(
                courseRows.stream().map(AdminUserMapper.UserCourseProgressRow::lastStudiedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                quizStat == null ? null : quizStat.lastActiveAt(),
                masteryStat == null ? null : masteryStat.lastActiveAt());

        var accountView = new ProfileAccountView(
                account.id(), account.username(), account.displayName(), account.role(),
                Boolean.TRUE.equals(account.enabled()), account.createdAt(), account.updatedAt(),
                activeSessionCount);

        var summaryView = new ProfileSummaryView(
                completedSections, totalSections, attempts, correct, accuracy, averageMastery,
                openWrongCount, dueReviewCount, aiRows.size(), aiQuestionCount, lastStudiedAt);

        var courses = courseRows.stream().map(row -> new CourseProgressView(
                row.courseId(), row.courseName(), row.completedSections(), row.totalSections(), row.lastStudiedAt())).toList();

        var masteries = masteryRows.stream().map(row -> new KnowledgeMasteryView(
                row.pointId(), row.pointName(), row.courseId(), row.courseName(),
                roundOneDecimal(row.masteryScore()), row.attemptCount(), row.correctCount(), row.lastStudiedAt())).toList();

        var wrongs = wrongRows.stream().map(row -> new WrongQuestionView(
                row.wrongQuestionId(), row.questionId(), row.courseName(), row.pointName(),
                truncateStem(row.stem()), row.wrongCount(), row.status(), row.reviewStreak(),
                row.dueAt(), row.lastWrongAt())).toList();

        var assignments = assignmentRows.stream().map(row -> new AssignmentView(
                row.taskId(), row.taskTitle(), row.groupName(), row.conditionKey(),
                row.status(), row.assignedAt(), row.startedAt(), row.completedAt())).toList();

        var aiActivities = aiRows.stream().map(row -> new AiActivityView(
                row.sessionId(), row.title(), row.userMessageCount() == null ? 0L : row.userMessageCount(), row.updatedAt())).toList();

        return new UserProfileView(accountView, summaryView, courses, masteries, wrongs, assignments, aiActivities);
    }

    private UserSummaryView loadSingleSummary(Long userId) {
        AdminUserMapper.UserAccountRow account = adminUserMapper.selectUserAccount(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        var reading = adminUserMapper.selectReadingStatsByUsers(List.of(userId)).stream().findFirst().orElse(null);
        var quiz = adminUserMapper.selectQuizStatsByUsers(List.of(userId)).stream().findFirst().orElse(null);
        var mastery = adminUserMapper.selectMasteryStatsByUsers(List.of(userId)).stream().findFirst().orElse(null);
        long openWrong = toCountMap(adminUserMapper.selectOpenWrongCountsByUsers(List.of(userId))).getOrDefault(userId, 0L);
        long dueReview = toCountMap(adminUserMapper.selectDueReviewCountsByUsers(List.of(userId))).getOrDefault(userId, 0L);
        long tokens = toCountMap(adminUserMapper.selectActiveTokenCountsByUsers(List.of(userId))).getOrDefault(userId, 0L);

        long attempts = quiz == null ? 0L : quiz.totalCount();
        long correct = quiz == null ? 0L : quiz.correctCount();
        int accuracy = attempts > 0 ? (int) Math.round(correct * 100.0 / attempts) : 0;
        double avgMastery = mastery == null ? 0.0 : roundOneDecimal(mastery.averageMastery());
        LocalDateTime lastActiveAt = latestTime(
                reading == null ? null : reading.lastActiveAt(),
                quiz == null ? null : quiz.lastActiveAt(),
                mastery == null ? null : mastery.lastActiveAt());
        return new UserSummaryView(
                account.id(), account.username(), account.displayName(), account.role(),
                Boolean.TRUE.equals(account.enabled()), account.createdAt(),
                reading == null ? 0L : reading.completedSections(),
                attempts, correct, accuracy, avgMastery, openWrong, dueReview, tokens, lastActiveAt);
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity row = userId == null ? null : sysUserMapper.selectById(userId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return row;
    }

    private static String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank() || "ALL".equalsIgnoreCase(role.trim())) {
            return null;
        }
        return normalizeStrictRole(role);
    }

    private static String normalizeStrictRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!"STUDENT".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户角色必须为 STUDENT 或 ADMIN");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName, String fallbackUsername) {
        if (displayName == null || displayName.isBlank()) {
            return fallbackUsername;
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "显示名称不能超过 80 个字符");
        }
        return trimmed;
    }

    private static String truncateStem(String stem) {
        if (stem == null) return "";
        return stem.length() > 120 ? stem.substring(0, 120) + "..." : stem;
    }

    private static double roundOneDecimal(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 10.0) / 10.0;
    }

    private static Map<Long, Long> toCountMap(List<AdminUserMapper.UserSimpleCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                AdminUserMapper.UserSimpleCountRow::userId,
                row -> row.countValue() == null ? 0L : row.countValue()));
    }

    private static LocalDateTime latestTime(LocalDateTime... times) {
        LocalDateTime latest = null;
        for (LocalDateTime t : times) {
            if (t != null && (latest == null || t.isAfter(latest))) {
                latest = t;
            }
        }
        return latest;
    }

    public record CreateUserRequest(String username, String password, String displayName, String role, Boolean enabled) { }

    public record UpdateUserRequest(String displayName, String role) { }

    public record UserSummaryView(
            Long id, String username, String displayName, String role, boolean enabled, LocalDateTime createdAt,
            long completedSections, long attemptCount, long correctCount, int accuracy, double averageMastery,
            long openWrongCount, long dueReviewCount, long activeTokenCount, LocalDateTime lastActiveAt) { }

    public record UserPageView(List<UserSummaryView> items, long total, int page, int size) { }

    public record ProfileAccountView(
            Long id, String username, String displayName, String role, boolean enabled,
            LocalDateTime createdAt, LocalDateTime updatedAt, long activeSessionCount) { }

    public record ProfileSummaryView(
            long completedSections, long totalSections, long attemptCount, long correctCount, int accuracy,
            double averageMastery, long openWrongCount, long dueReviewCount,
            long aiSessionCount, long aiQuestionCount, LocalDateTime lastStudiedAt) { }

    public record CourseProgressView(Long courseId, String courseName, long completedSections, long totalSections, LocalDateTime lastStudiedAt) { }

    public record KnowledgeMasteryView(Long pointId, String pointName, Long courseId, String courseName,
                                       double masteryScore, int attemptCount, int correctCount, LocalDateTime lastStudiedAt) { }

    public record WrongQuestionView(Long wrongQuestionId, Long questionId, String courseName, String pointName,
                                    String stem, int wrongCount, String status, int reviewStreak,
                                    LocalDateTime dueAt, LocalDateTime lastWrongAt) { }

    public record AssignmentView(Long taskId, String taskTitle, String groupName, String conditionKey,
                                 String status, LocalDateTime assignedAt, LocalDateTime startedAt, LocalDateTime completedAt) { }

    public record AiActivityView(Long sessionId, String title, long userMessageCount, LocalDateTime updatedAt) { }

    public record UserProfileView(
            ProfileAccountView account,
            ProfileSummaryView summary,
            List<CourseProgressView> courses,
            List<KnowledgeMasteryView> knowledgeMastery,
            List<WrongQuestionView> wrongQuestions,
            List<AssignmentView> assignments,
            List<AiActivityView> aiActivity) { }
}
