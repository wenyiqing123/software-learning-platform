package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminContentAuditMapper {

    @Select("""
        <script>
        SELECT kp.id AS knowledgePointId,
               kp.course_id AS courseId,
               c.name AS courseName,
               kp.chapter_id AS chapterId,
               ch.name AS chapterName,
               kp.name AS knowledgePointName,
               kp.graph_entity AS graphEntity,
               kp.description AS description,
               kp.enabled AS enabled,
               kp.sort_order AS sortOrder,
               COALESCE(sec.section_cnt, 0) AS sectionCount,
               COALESCE(qq.question_cnt, 0) AS questionCount,
               COALESCE(diag.diag_cnt, 0) AS diagnosisCount,
               COALESCE(ml.published_cnt, 0) AS publishedMicroLessonCount,
               COALESCE(ml.draft_cnt, 0) AS draftMicroLessonCount,
               COALESCE(fc.review_cnt, 0) AS flashcardReviewCount
        FROM knowledge_point kp
        JOIN course c ON c.id = kp.course_id
        LEFT JOIN course_chapter ch ON ch.id = kp.chapter_id
        LEFT JOIN (
            SELECT knowledge_point_id, COUNT(*) AS section_cnt
            FROM course_study_section
            GROUP BY knowledge_point_id
        ) sec ON sec.knowledge_point_id = kp.id
        LEFT JOIN (
            SELECT knowledge_point_id, COUNT(*) AS question_cnt
            FROM quiz_question
            WHERE enabled = TRUE AND knowledge_point_id IS NOT NULL
            GROUP BY knowledge_point_id
        ) qq ON qq.knowledge_point_id = kp.id
        LEFT JOIN (
            SELECT q.knowledge_point_id AS kp_id, COUNT(d.id) AS diag_cnt
            FROM quiz_option_diagnosis d
            JOIN quiz_question q ON q.id = d.question_id
            WHERE d.enabled = TRUE AND q.enabled = TRUE AND q.knowledge_point_id IS NOT NULL
            GROUP BY q.knowledge_point_id
        ) diag ON diag.kp_id = kp.id
        LEFT JOIN (
            SELECT s.knowledge_point_id AS kp_id,
                   SUM(CASE WHEN m.status = 'PUBLISHED' THEN 1 ELSE 0 END) AS published_cnt,
                   SUM(CASE WHEN m.status = 'DRAFT' THEN 1 ELSE 0 END) AS draft_cnt
            FROM micro_lesson_draft m
            JOIN course_study_section s ON s.id = m.section_id
            GROUP BY s.knowledge_point_id
        ) ml ON ml.kp_id = kp.id
        LEFT JOIN (
            SELECT knowledge_point_id, COUNT(*) AS review_cnt
            FROM flashcard_review_state
            GROUP BY knowledge_point_id
        ) fc ON fc.knowledge_point_id = kp.id
        <where>
          <if test="courseId != null">kp.course_id = #{courseId}</if>
        </where>
        ORDER BY kp.course_id ASC, COALESCE(ch.sort_order, 9999) ASC, kp.sort_order ASC, kp.id ASC
        </script>
        """)
    List<AuditRow> findCoverageRows(@Param("courseId") Long courseId);

    record AuditRow(
        Long knowledgePointId,
        Long courseId,
        String courseName,
        Long chapterId,
        String chapterName,
        String knowledgePointName,
        String graphEntity,
        String description,
        Boolean enabled,
        Integer sortOrder,
        Long sectionCount,
        Long questionCount,
        Long diagnosisCount,
        Long publishedMicroLessonCount,
        Long draftMicroLessonCount,
        Long flashcardReviewCount
    ) { }
}
