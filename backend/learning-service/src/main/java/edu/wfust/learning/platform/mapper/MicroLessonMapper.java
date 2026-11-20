package edu.wfust.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.MicroLessonDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MicroLessonMapper extends BaseMapper<MicroLessonDraftEntity> {
    @Select("""
        SELECT q.stem, q.explanation, COALESCE(SUM(w.wrong_count),0) AS wrongCount
        FROM wrong_question w JOIN quiz_question q ON q.id=w.question_id
        WHERE q.knowledge_point_id=#{pointId} AND q.enabled=TRUE
        GROUP BY q.id,q.stem,q.explanation ORDER BY wrongCount DESC,q.id LIMIT 5
        """)
    List<CommonErrorRow> commonErrors(@Param("pointId") Long pointId);

    @Select("SELECT * FROM micro_lesson_draft WHERE course_id=#{courseId} AND section_id=#{sectionId} AND status='PUBLISHED' ORDER BY id DESC LIMIT 1")
    MicroLessonDraftEntity published(@Param("courseId") Long courseId, @Param("sectionId") Long sectionId);

    @Select("SELECT * FROM micro_lesson_draft WHERE course_id=#{courseId} AND section_id=#{sectionId} AND status='PUBLISHED' AND id<>#{exceptId} ORDER BY id DESC")
    List<MicroLessonDraftEntity> findPublished(@Param("courseId") Long courseId, @Param("sectionId") Long sectionId, @Param("exceptId") Long exceptId);

    @Update("UPDATE micro_lesson_draft SET status='ARCHIVED' WHERE course_id=#{courseId} AND section_id=#{sectionId} AND status='PUBLISHED' AND id<>#{exceptId}")
    int archivePublished(@Param("courseId") Long courseId, @Param("sectionId") Long sectionId, @Param("exceptId") Long exceptId);

    @Update("UPDATE micro_lesson_draft SET status='PUBLISHED', published_at=#{publishedAt}, updated_at=#{publishedAt} WHERE id=#{id}")
    int markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);

    record CommonErrorRow(String stem, String explanation, Long wrongCount) { }
}
