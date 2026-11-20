package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.QuizAttemptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;
@Mapper public interface QuizAttemptMapper extends BaseMapper<QuizAttemptEntity> {
    @Select("SELECT COUNT(*) FROM quiz_attempt WHERE user_id = #{userId}") long countForUser(Long userId);
    @Select("SELECT COUNT(*) FROM quiz_attempt WHERE user_id = #{userId} AND is_correct = 1") long countCorrectForUser(Long userId);
    @Select("SELECT DATE_FORMAT(DATE(submitted_at), '%Y-%m-%d') AS day, COUNT(*) AS attempts FROM quiz_attempt WHERE user_id = #{userId} AND submitted_at >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) GROUP BY DATE(submitted_at) ORDER BY DATE(submitted_at)")
    List<Map<String, Object>> activityLast7Days(Long userId);
}
