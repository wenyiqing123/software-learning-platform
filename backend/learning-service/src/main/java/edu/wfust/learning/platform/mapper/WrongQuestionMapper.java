package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.WrongQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;
@Mapper public interface WrongQuestionMapper extends BaseMapper<WrongQuestionEntity> {
    @Select("SELECT COUNT(*) FROM wrong_question WHERE user_id = #{userId} AND status <> 'MASTERED'") long countOpenForUser(Long userId);
    @Select("SELECT q.knowledge_point_id AS knowledgePointId, SUM(w.wrong_count) AS wrongCount FROM wrong_question w JOIN quiz_question q ON q.id = w.question_id WHERE w.user_id = #{userId} AND w.status <> 'MASTERED' AND q.knowledge_point_id IS NOT NULL GROUP BY q.knowledge_point_id")
    List<Map<String, Object>> countByKnowledgePoint(Long userId);
}
