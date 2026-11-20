package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningReportMapper {
    @Select("""
        SELECT COUNT(s.id) AS totalSections,
               COALESCE(SUM(CASE WHEN p.completed=TRUE THEN 1 ELSE 0 END),0) AS completedSections,
               COALESCE(SUM(CASE WHEN p.completed=TRUE AND p.updated_at>=#{start} AND p.updated_at<#{end} THEN 1 ELSE 0 END),0) AS completedInPeriod
        FROM course_study_section s JOIN course c ON c.id=s.course_id AND c.enabled=TRUE
        LEFT JOIN course_study_progress p ON p.section_id=s.id AND p.user_id=#{userId}
        WHERE (#{courseId} IS NULL OR c.id=#{courseId})
        """)
    ReadingSummary reading(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT ch.id AS chapterId,ch.name AS chapterName,COUNT(s.id) AS totalSections,
               COALESCE(SUM(CASE WHEN p.completed=TRUE THEN 1 ELSE 0 END),0) AS completedSections,
               COALESCE(SUM(CASE WHEN p.completed=TRUE AND p.updated_at>=#{start} AND p.updated_at<#{end} THEN 1 ELSE 0 END),0) AS completedInPeriod
        FROM course_chapter ch JOIN course c ON c.id=ch.course_id AND c.enabled=TRUE
        LEFT JOIN course_study_section s ON s.chapter_id=ch.id
        LEFT JOIN course_study_progress p ON p.section_id=s.id AND p.user_id=#{userId}
        WHERE (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY ch.id,ch.name,ch.sort_order ORDER BY c.id,ch.sort_order,ch.id
        """)
    List<ChapterSummary> chapters(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT COUNT(*) AS attempts,COALESCE(SUM(CASE WHEN a.is_correct=TRUE THEN 1 ELSE 0 END),0) AS correct
        FROM quiz_attempt a JOIN quiz_question q ON q.id=a.question_id JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        WHERE a.user_id=#{userId} AND a.submitted_at>=#{start} AND a.submitted_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        """)
    PracticeSummary practice(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT COUNT(*) AS reviews,COALESCE(SUM(CASE WHEN l.is_correct=TRUE THEN 1 ELSE 0 END),0) AS correct
        FROM spaced_review_log l JOIN wrong_question w ON w.id=l.wrong_question_id
        JOIN quiz_question q ON q.id=w.question_id JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        WHERE l.user_id=#{userId} AND l.reviewed_at>=#{start} AND l.reviewed_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        """)
    ReviewSummary reviews(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT COUNT(t.id) AS totalTasks,COALESCE(SUM(CASE WHEN t.state='COMPLETED' THEN 1 ELSE 0 END),0) AS completedTasks,
               COALESCE(SUM(t.minutes),0) AS plannedMinutes,COALESCE(SUM(CASE WHEN t.state='COMPLETED' THEN t.minutes ELSE 0 END),0) AS completedMinutes
        FROM daily_study_plan p JOIN daily_study_task t ON t.plan_id=p.id
        WHERE p.user_id=#{userId} AND p.plan_date>=#{fromDate} AND p.plan_date<#{toDate}
        """)
    PlanSummary plans(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Select("""
        SELECT DATE(a.submitted_at) AS day,COUNT(*) AS attempts,COALESCE(SUM(CASE WHEN a.is_correct=TRUE THEN 1 ELSE 0 END),0) AS correct
        FROM quiz_attempt a JOIN quiz_question q ON q.id=a.question_id JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        WHERE a.user_id=#{userId} AND a.submitted_at>=#{start} AND a.submitted_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY DATE(a.submitted_at) ORDER BY day
        """)
    List<DailyPractice> dailyPractice(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT DATE(p.updated_at) AS day,COUNT(*) AS completed
        FROM course_study_progress p JOIN course_study_section s ON s.id=p.section_id JOIN course c ON c.id=s.course_id AND c.enabled=TRUE
        WHERE p.user_id=#{userId} AND p.completed=TRUE AND p.updated_at>=#{start} AND p.updated_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY DATE(p.updated_at) ORDER BY day
        """)
    List<DailyReading> dailyReading(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT kp.id AS pointId,kp.name AS pointName,COALESCE(m.mastery_score,0) AS mastery,
               COALESCE(m.attempt_count,0) AS attempts,COALESCE(m.correct_count,0) AS correct,
               COALESCE((SELECT COUNT(*) FROM spaced_review_schedule sr JOIN wrong_question w ON w.id=sr.wrong_question_id AND w.status<>'MASTERED' JOIN quiz_question q2 ON q2.id=w.question_id WHERE sr.user_id=#{userId} AND sr.state='ACTIVE' AND sr.due_at<=CURRENT_TIMESTAMP(6) AND q2.knowledge_point_id=kp.id),0) AS dueReviewCount
        FROM knowledge_point kp JOIN course c ON c.id=kp.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_mastery m ON m.knowledge_point_id=kp.id AND m.user_id=#{userId}
        WHERE kp.enabled=TRUE AND (#{courseId} IS NULL OR c.id=#{courseId})
        ORDER BY c.id,kp.sort_order,kp.id
        """)
    List<KnowledgeSummary> knowledge(@Param("userId") Long userId, @Param("courseId") Long courseId);

    record ReadingSummary(Long totalSections, Long completedSections, Long completedInPeriod) { }
    record ChapterSummary(Long chapterId, String chapterName, Long totalSections, Long completedSections, Long completedInPeriod) { }
    record PracticeSummary(Long attempts, Long correct) { }
    record ReviewSummary(Long reviews, Long correct) { }
    record PlanSummary(Long totalTasks, Long completedTasks, Long plannedMinutes, Long completedMinutes) { }
    record DailyPractice(LocalDate day, Long attempts, Long correct) { }
    record DailyReading(LocalDate day, Long completed) { }
    record KnowledgeSummary(Long pointId, String pointName, Double mastery, Long attempts, Long correct, Long dueReviewCount) { }
}
