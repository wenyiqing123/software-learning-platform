package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuizQuestionMapper extends BaseMapper<QuizQuestionEntity> {
    @Update("UPDATE quiz_question SET enabled=FALSE WHERE version_id=#{versionId}")
    int disableVersionQuestions(@Param("versionId") Long versionId);

    @Update("UPDATE quiz_question SET enabled=TRUE WHERE version_id=#{versionId}")
    int enableVersionQuestions(@Param("versionId") Long versionId);
}
