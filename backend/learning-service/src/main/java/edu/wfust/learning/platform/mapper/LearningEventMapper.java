package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper public interface LearningEventMapper extends BaseMapper<LearningEventEntity> {
    @Select("SELECT * FROM learning_event WHERE event_id = #{eventId} LIMIT 1 FOR UPDATE") LearningEventEntity findByEventId(String eventId);
    @Select("SELECT * FROM learning_event WHERE published_at IS NULL ORDER BY id ASC LIMIT 100") List<LearningEventEntity> findPending();
}
