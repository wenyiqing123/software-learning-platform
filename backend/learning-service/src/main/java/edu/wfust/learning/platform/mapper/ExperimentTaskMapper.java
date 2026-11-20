package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.ExperimentTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper public interface ExperimentTaskMapper extends BaseMapper<ExperimentTaskEntity> {
    @Select("SELECT COUNT(*) FROM experiment_group WHERE task_id=#{taskId}") long countGroups(@Param("taskId") Long taskId);
    @Select("SELECT COUNT(*) FROM experiment_assignment WHERE task_id=#{taskId}") long countAssignments(@Param("taskId") Long taskId);
}
