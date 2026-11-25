package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminUserMapper {

    @Select("""
        <script>
        SELECT COUNT(*)
        FROM sys_user u
        <where>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')
              OR LOWER(u.display_name) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
          </if>
          <if test="role != null and role != ''">
            AND u.role = #{role}
          </if>
          <if test="enabled != null">
            AND u.enabled = #{enabled}
          </if>
        </where>
        </script>
        """)
    long countUsers(@Param("keyword") String keyword, @Param("role") String role, @Param("enabled") Boolean enabled);

    @Select("""
        <script>
        SELECT u.id, u.username, u.display_name AS displayName, u.role, u.enabled,
               u.created_at AS createdAt, u.updated_at AS updatedAt
        FROM sys_user u
        <where>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')
              OR LOWER(u.display_name) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
          </if>
          <if test="role != null and role != ''">
            AND u.role = #{role}
          </if>
          <if test="enabled != null">
            AND u.enabled = #{enabled}
          </if>
        </where>
        ORDER BY u.id DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<UserAccountRow> selectUserRows(@Param("keyword") String keyword,
                                        @Param("role") String role,
                                        @Param("enabled") Boolean enabled,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("""
        SELECT u.id, u.username, u.display_name AS displayName, u.role, u.enabled,
               u.created_at AS createdAt, u.updated_at AS updatedAt
        FROM sys_user u
        WHERE u.id = #{userId}
        LIMIT 1
        """)
    UserAccountRow selectUserAccount(@Param("userId") Long userId);

    @Select("""
        <script>
        SELECT p.user_id AS userId, COUNT(*) AS completedSections, MAX(p.updated_at) AS lastActiveAt
        FROM course_study_progress p
        JOIN course_study_section s ON s.id = p.section_id
        JOIN course c ON c.id = s.course_id AND c.enabled = TRUE
        WHERE p.completed = TRUE
          AND p.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY p.user_id
        </script>
        """)
    List<UserCountStatRow> selectReadingStatsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        <script>
        SELECT a.user_id AS userId,
               COUNT(*) AS totalCount,
               COALESCE(SUM(CASE WHEN a.is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correctCount,
               MAX(a.submitted_at) AS lastActiveAt
        FROM quiz_attempt a
        WHERE a.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY a.user_id
        </script>
        """)
    List<UserQuizStatRow> selectQuizStatsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        <script>
        SELECT m.user_id AS userId,
               COALESCE(AVG(m.mastery_score), 0) AS averageMastery,
               MAX(m.last_studied_at) AS lastActiveAt
        FROM knowledge_mastery m
        JOIN knowledge_point kp ON kp.id = m.knowledge_point_id AND kp.enabled = TRUE
        WHERE m.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY m.user_id
        </script>
        """)
    List<UserMasteryStatRow> selectMasteryStatsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        <script>
        SELECT w.user_id AS userId, COUNT(*) AS countValue
        FROM wrong_question w
        WHERE w.status &lt;&gt; 'MASTERED'
          AND w.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY w.user_id
        </script>
        """)
    List<UserSimpleCountRow> selectOpenWrongCountsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        <script>
        SELECT s.user_id AS userId, COUNT(*) AS countValue
        FROM spaced_review_schedule s
        WHERE s.state = 'ACTIVE'
          AND s.due_at &lt;= CURRENT_TIMESTAMP
          AND s.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY s.user_id
        </script>
        """)
    List<UserSimpleCountRow> selectDueReviewCountsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        <script>
        SELECT r.user_id AS userId, COUNT(*) AS countValue
        FROM auth_refresh_token r
        WHERE r.revoked_at IS NULL
          AND r.expires_at &gt; CURRENT_TIMESTAMP
          AND r.user_id IN
          <foreach item="uid" collection="userIds" open="(" separator="," close=")">#{uid}</foreach>
        GROUP BY r.user_id
        </script>
        """)
    List<UserSimpleCountRow> selectActiveTokenCountsByUsers(@Param("userIds") List<Long> userIds);

    @Select("""
        SELECT COUNT(*)
        FROM course_study_section s
        JOIN course c ON c.id = s.course_id AND c.enabled = TRUE
        """)
    long countTotalEnabledSections();

    @Select("""
        SELECT c.id AS courseId,
               c.name AS courseName,
               COUNT(s.id) AS totalSections,
               COALESCE(SUM(CASE WHEN p.completed = TRUE THEN 1 ELSE 0 END), 0) AS completedSections,
               MAX(CASE WHEN p.completed = TRUE THEN p.updated_at ELSE NULL END) AS lastStudiedAt
        FROM course c
        JOIN course_study_section s ON s.course_id = c.id
        LEFT JOIN course_study_progress p ON p.section_id = s.id AND p.user_id = #{userId}
        WHERE c.enabled = TRUE
        GROUP BY c.id, c.name
        ORDER BY c.id ASC
        """)
    List<UserCourseProgressRow> selectUserCourseProgress(@Param("userId") Long userId);

    @Select("""
        SELECT kp.id AS pointId,
               kp.name AS pointName,
               c.id AS courseId,
               c.name AS courseName,
               COALESCE(m.mastery_score, 0) AS masteryScore,
               COALESCE(m.attempt_count, 0) AS attemptCount,
               COALESCE(m.correct_count, 0) AS correctCount,
               m.last_studied_at AS lastStudiedAt
        FROM knowledge_point kp
        JOIN course c ON c.id = kp.course_id AND c.enabled = TRUE
        LEFT JOIN knowledge_mastery m ON m.knowledge_point_id = kp.id AND m.user_id = #{userId}
        WHERE kp.enabled = TRUE
        ORDER BY COALESCE(m.mastery_score, 0) ASC, kp.id ASC
        """)
    List<UserKnowledgeMasteryRow> selectUserKnowledgeMastery(@Param("userId") Long userId);

    @Select("""
        SELECT w.id AS wrongQuestionId,
               q.id AS questionId,
               c.name AS courseName,
               COALESCE(kp.name, '未关联知识点') AS pointName,
               q.stem AS stem,
               w.wrong_count AS wrongCount,
               w.status AS status,
               COALESCE(w.review_streak, 0) AS reviewStreak,
               sr.due_at AS dueAt,
               w.last_wrong_at AS lastWrongAt
        FROM wrong_question w
        JOIN quiz_question q ON q.id = w.question_id
        JOIN course c ON c.id = q.course_id
        LEFT JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
        LEFT JOIN spaced_review_schedule sr ON sr.wrong_question_id = w.id AND sr.state = 'ACTIVE'
        WHERE w.user_id = #{userId}
          AND w.status <> 'MASTERED'
        ORDER BY w.last_wrong_at DESC
        LIMIT 30
        """)
    List<UserWrongQuestionRow> selectUserWrongQuestions(@Param("userId") Long userId);

    @Select("""
        SELECT a.task_id AS taskId,
               t.title AS taskTitle,
               g.name AS groupName,
               g.condition_key AS conditionKey,
               a.status AS status,
               a.assigned_at AS assignedAt,
               a.started_at AS startedAt,
               a.completed_at AS completedAt
        FROM experiment_assignment a
        JOIN experiment_task t ON t.id = a.task_id
        JOIN experiment_group g ON g.id = a.group_id
        WHERE a.user_id = #{userId}
        ORDER BY a.assigned_at DESC
        """)
    List<UserAssignmentRow> selectUserAssignments(@Param("userId") Long userId);

    @Select("""
        SELECT s.id AS sessionId,
               s.title AS title,
               SUM(CASE WHEN m.role = 'USER' THEN 1 ELSE 0 END) AS userMessageCount,
               s.updated_at AS updatedAt
        FROM ai_session s
        LEFT JOIN ai_message m ON m.session_id = s.id
        WHERE s.user_id = #{userId}
        GROUP BY s.id, s.title, s.updated_at
        ORDER BY s.updated_at DESC
        LIMIT 20
        """)
    List<UserAiSessionRow> selectUserAiSessions(@Param("userId") Long userId);

    record UserAccountRow(Long id, String username, String displayName, String role, Boolean enabled,
                          LocalDateTime createdAt, LocalDateTime updatedAt) { }

    record UserCountStatRow(Long userId, Long completedSections, LocalDateTime lastActiveAt) { }

    record UserQuizStatRow(Long userId, Long totalCount, Long correctCount, LocalDateTime lastActiveAt) { }

    record UserMasteryStatRow(Long userId, Double averageMastery, LocalDateTime lastActiveAt) { }

    record UserSimpleCountRow(Long userId, Long countValue) { }

    record UserCourseProgressRow(Long courseId, String courseName, Long totalSections, Long completedSections,
                                 LocalDateTime lastStudiedAt) { }

    record UserKnowledgeMasteryRow(Long pointId, String pointName, Long courseId, String courseName,
                                   Double masteryScore, Integer attemptCount, Integer correctCount,
                                   LocalDateTime lastStudiedAt) { }

    record UserWrongQuestionRow(Long wrongQuestionId, Long questionId, String courseName, String pointName,
                                String stem, Integer wrongCount, String status, Integer reviewStreak,
                                LocalDateTime dueAt, LocalDateTime lastWrongAt) { }

    record UserAssignmentRow(Long taskId, String taskTitle, String groupName, String conditionKey,
                             String status, LocalDateTime assignedAt, LocalDateTime startedAt,
                             LocalDateTime completedAt) { }

    record UserAiSessionRow(Long sessionId, String title, Long userMessageCount, LocalDateTime updatedAt) { }
}
