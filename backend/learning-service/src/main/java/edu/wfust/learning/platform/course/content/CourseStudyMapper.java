package edu.wfust.learning.platform.course.content;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CourseStudyMapper {
    @Select("SELECT id, course_id, name, description, sort_order FROM course_chapter WHERE course_id=#{courseId} ORDER BY sort_order,id")
    List<ChapterRow> chapters(@Param("courseId") Long courseId);
    @Select("""
        SELECT s.id, s.course_id, s.chapter_id, s.knowledge_point_id, s.title,
               s.objectives_json, s.content_json, s.resources_json, s.sort_order, kp.name, kp.graph_entity
        FROM course_study_section s
        JOIN knowledge_point kp ON kp.id=s.knowledge_point_id AND kp.course_id=s.course_id AND kp.enabled=TRUE
        JOIN course_chapter ch ON ch.id=s.chapter_id AND ch.course_id=s.course_id
        WHERE s.course_id=#{courseId} ORDER BY s.sort_order,s.id
        """)
    List<SectionRow> sections(@Param("courseId") Long courseId);
    @Select("""
        SELECT s.id, s.course_id, s.chapter_id, s.knowledge_point_id, s.title,
               s.objectives_json, s.content_json, s.resources_json, s.sort_order, kp.name, kp.graph_entity
        FROM course_study_section s
        JOIN knowledge_point kp ON kp.id=s.knowledge_point_id AND kp.course_id=s.course_id AND kp.enabled=TRUE
        JOIN course_chapter ch ON ch.id=s.chapter_id AND ch.course_id=s.course_id
        WHERE s.course_id=#{courseId} AND s.id=#{sectionId}
        """)
    SectionRow section(@Param("courseId") Long courseId, @Param("sectionId") Long sectionId);
    @Select("""
        SELECT p.section_id FROM course_study_progress p
        JOIN course_study_section s ON s.id=p.section_id
        WHERE p.user_id=#{userId} AND s.course_id=#{courseId} AND p.completed=TRUE
        """)
    List<Long> completed(@Param("userId") Long userId, @Param("courseId") Long courseId);
    @Insert("""
        INSERT INTO course_study_progress (user_id, section_id, completed, updated_at)
        VALUES (#{userId}, #{sectionId}, #{completed}, CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE completed=#{completed}, updated_at=CURRENT_TIMESTAMP
        """)
    void saveProgress(@Param("userId") Long userId, @Param("sectionId") Long sectionId, @Param("completed") boolean completed);

    record ChapterRow(Long id, Long courseId, String name, String description, Integer sortOrder) { }
    record SectionRow(Long id, Long courseId, Long chapterId, Long knowledgePointId, String title,
        String objectivesJson, String contentJson, String resourcesJson, Integer sortOrder, String knowledgePointName, String graphEntity) { }
}
