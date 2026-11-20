package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
@Mapper public interface KnowledgeMasteryMapper extends BaseMapper<KnowledgeMasteryEntity> {
    @Select("SELECT COALESCE(AVG(mastery_score), 0) FROM knowledge_mastery WHERE user_id = #{userId}") double averageForUser(Long userId);
    @Insert("INSERT INTO knowledge_mastery (user_id, knowledge_point_id, mastery_score, attempt_count, correct_count, last_studied_at) " +
            "VALUES (#{userId}, #{pointId}, CASE WHEN #{correct} THEN 100.00 ELSE 0.00 END, 1, CASE WHEN #{correct} THEN 1 ELSE 0 END, CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE attempt_count = attempt_count + 1, " +
            "correct_count = correct_count + CASE WHEN #{correct} THEN 1 ELSE 0 END, " +
            "mastery_score = ROUND(correct_count * 100.0 / attempt_count, 2), last_studied_at = CURRENT_TIMESTAMP(6)")
    int recordAttempt(@Param("userId") Long userId, @Param("pointId") Long pointId, @Param("correct") boolean correct);
}
