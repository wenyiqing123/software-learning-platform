package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeachingDashboardMapper {
    @Select("SELECT default_days FROM teaching_dashboard_config WHERE course_id=#{courseId} AND enabled=TRUE")
    Integer defaultDays(@Param("courseId") Long courseId);

    @Select("""
        SELECT
          (SELECT COUNT(DISTINCT a.user_id) FROM quiz_attempt a JOIN sys_user ua ON ua.id=a.user_id AND ua.role='STUDENT' JOIN quiz_question q ON q.id=a.question_id JOIN course c2 ON c2.id=q.course_id AND c2.enabled=TRUE WHERE a.submitted_at>=#{start} AND a.submitted_at<#{end} AND (#{courseId} IS NULL OR c2.id=#{courseId})) AS activeLearners,
          (SELECT COUNT(*) FROM course_study_section s JOIN course c3 ON c3.id=s.course_id AND c3.enabled=TRUE WHERE #{courseId} IS NULL OR c3.id=#{courseId}) AS totalSections,
          (SELECT COUNT(*) FROM course_study_progress p JOIN sys_user up ON up.id=p.user_id AND up.role='STUDENT' JOIN course_study_section s2 ON s2.id=p.section_id JOIN course c4 ON c4.id=s2.course_id AND c4.enabled=TRUE WHERE p.completed=TRUE AND p.updated_at>=#{start} AND p.updated_at<#{end} AND (#{courseId} IS NULL OR c4.id=#{courseId})) AS completedReads,
          (SELECT COUNT(*) FROM quiz_attempt a2 JOIN sys_user ua2 ON ua2.id=a2.user_id AND ua2.role='STUDENT' JOIN quiz_question q2 ON q2.id=a2.question_id JOIN course c5 ON c5.id=q2.course_id AND c5.enabled=TRUE WHERE a2.submitted_at>=#{start} AND a2.submitted_at<#{end} AND (#{courseId} IS NULL OR c5.id=#{courseId})) AS attempts,
          (SELECT COALESCE(SUM(CASE WHEN a3.is_correct=TRUE THEN 1 ELSE 0 END),0) FROM quiz_attempt a3 JOIN sys_user ua3 ON ua3.id=a3.user_id AND ua3.role='STUDENT' JOIN quiz_question q3 ON q3.id=a3.question_id JOIN course c6 ON c6.id=q3.course_id AND c6.enabled=TRUE WHERE a3.submitted_at>=#{start} AND a3.submitted_at<#{end} AND (#{courseId} IS NULL OR c6.id=#{courseId})) AS correct,
          (SELECT COUNT(*) FROM wrong_question w JOIN sys_user uw ON uw.id=w.user_id AND uw.role='STUDENT' JOIN quiz_question q4 ON q4.id=w.question_id JOIN course c7 ON c7.id=q4.course_id AND c7.enabled=TRUE WHERE w.status<>'MASTERED' AND (#{courseId} IS NULL OR c7.id=#{courseId})) AS openWrong,
          (SELECT COALESCE(AVG(m.mastery_score),0) FROM knowledge_mastery m JOIN sys_user um ON um.id=m.user_id AND um.role='STUDENT' JOIN knowledge_point kp ON kp.id=m.knowledge_point_id JOIN course c8 ON c8.id=kp.course_id AND c8.enabled=TRUE WHERE (#{courseId} IS NULL OR c8.id=#{courseId})) AS averageMastery
        """)
    Overview overview(@Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT DATE(a.submitted_at) AS day,COUNT(DISTINCT a.user_id) AS activeLearners,COUNT(*) AS attempts,
               COALESCE(SUM(CASE WHEN a.is_correct=TRUE THEN 1 ELSE 0 END),0) AS correct
        FROM quiz_attempt a JOIN sys_user u ON u.id=a.user_id AND u.role='STUDENT' JOIN quiz_question q ON q.id=a.question_id JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        WHERE a.submitted_at>=#{start} AND a.submitted_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY DATE(a.submitted_at) ORDER BY day
        """)
    List<DailyPractice> dailyPractice(@Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT DATE(p.updated_at) AS day,COUNT(*) AS completedReads
        FROM course_study_progress p JOIN sys_user u ON u.id=p.user_id AND u.role='STUDENT' JOIN course_study_section s ON s.id=p.section_id JOIN course c ON c.id=s.course_id AND c.enabled=TRUE
        WHERE p.completed=TRUE AND p.updated_at>=#{start} AND p.updated_at<#{end} AND (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY DATE(p.updated_at) ORDER BY day
        """)
    List<DailyReading> dailyReading(@Param("courseId") Long courseId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
        SELECT kp.id AS pointId,kp.name AS pointName,COALESCE(AVG(m.mastery_score),0) AS averageMastery,
               COALESCE(SUM(m.attempt_count),0) AS attempts,COUNT(DISTINCT m.user_id) AS learners
        FROM knowledge_point kp JOIN course c ON c.id=kp.course_id AND c.enabled=TRUE
        LEFT JOIN (SELECT m.* FROM knowledge_mastery m JOIN sys_user u ON u.id=m.user_id AND u.role='STUDENT') m ON m.knowledge_point_id=kp.id
        WHERE kp.enabled=TRUE AND (#{courseId} IS NULL OR c.id=#{courseId})
        GROUP BY kp.id,kp.name,kp.sort_order,c.id ORDER BY averageMastery ASC,attempts ASC,kp.id ASC LIMIT 10
        """)
    List<WeakPoint> weakPoints(@Param("courseId") Long courseId);

    record Overview(Long activeLearners,Long totalSections,Long completedReads,Long attempts,Long correct,Long openWrong,Double averageMastery) { }
    record DailyPractice(LocalDate day,Long activeLearners,Long attempts,Long correct) { }
    record DailyReading(LocalDate day,Long completedReads) { }
    record WeakPoint(Long pointId,String pointName,Double averageMastery,Long attempts,Long learners) { }
}

