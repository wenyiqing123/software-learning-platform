package edu.wfust.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.QuestionBankVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuestionBankVersionMapper extends BaseMapper<QuestionBankVersionEntity> {
    @Select("SELECT * FROM question_bank_version WHERE course_id=#{courseId} AND status='PUBLISHED' AND id<>#{exceptId} ORDER BY id DESC")
    List<QuestionBankVersionEntity> findPublishedByCourse(@Param("courseId") Long courseId, @Param("exceptId") Long exceptId);

    @Select("SELECT COUNT(*) FROM quiz_question WHERE version_id=#{versionId}")
    long countQuestions(@Param("versionId") Long versionId);

    @Update("UPDATE question_bank_version SET status='ARCHIVED' WHERE course_id=#{courseId} AND status='PUBLISHED' AND id<>#{exceptId}")
    int archivePublished(@Param("courseId") Long courseId, @Param("exceptId") Long exceptId);

    @Update("UPDATE question_bank_version SET status='PUBLISHED', published_at=#{publishedAt} WHERE id=#{id}")
    int markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);
}
