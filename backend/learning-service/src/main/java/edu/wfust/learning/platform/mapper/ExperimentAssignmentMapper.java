package edu.wfust.learning.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper public interface ExperimentAssignmentMapper extends BaseMapper<ExperimentAssignmentEntity> {
    @Select("SELECT * FROM experiment_assignment WHERE user_id=#{userId} AND task_id=#{taskId} LIMIT 1")
    ExperimentAssignmentEntity findForUserAndTask(@Param("userId") Long userId,@Param("taskId") Long taskId);
    @Update("UPDATE experiment_assignment SET status='IN_PROGRESS', started_at=#{at} WHERE id=#{id} AND status='ASSIGNED'")
    int start(@Param("id") Long id,@Param("at") LocalDateTime at);
    @Update("UPDATE experiment_assignment SET status='COMPLETED', completed_at=#{at} WHERE id=#{id} AND user_id=#{userId} AND status='IN_PROGRESS'")
    int complete(@Param("id") Long id,@Param("userId") Long userId,@Param("at") LocalDateTime at);
    @Insert("INSERT IGNORE INTO experiment_assignment(task_id,group_id,user_id,status) VALUES(#{taskId},#{groupId},#{userId},'ASSIGNED')")
    int assign(@Param("taskId") Long taskId,@Param("groupId") Long groupId,@Param("userId") Long userId);
    @Select("""
        SELECT a.id,a.task_id AS taskId,a.group_id AS groupId,a.user_id AS userId,a.status,a.assigned_at AS assignedAt,a.started_at AS startedAt,a.completed_at AS completedAt,
               t.title,t.description,t.experiment_type AS experimentType,t.question,t.reference_answer AS referenceAnswer,t.status AS taskStatus,
               g.name AS groupName,g.condition_key AS conditionKey
        FROM experiment_assignment a JOIN experiment_task t ON t.id=a.task_id JOIN experiment_group g ON g.id=a.group_id
        WHERE a.user_id=#{userId} ORDER BY a.assigned_at DESC
        """)
    List<AssignmentRow> findForUser(@Param("userId") Long userId);
    @Select("""
        SELECT a.id,a.task_id AS taskId,a.group_id AS groupId,a.user_id AS userId,a.status,a.assigned_at AS assignedAt,a.started_at AS startedAt,a.completed_at AS completedAt,
               t.title,t.status AS taskStatus,g.name AS groupName,g.condition_key AS conditionKey,
               (SELECT COUNT(*) FROM ai_experiment_run r WHERE r.assignment_id=a.id) AS runCount
        FROM experiment_assignment a JOIN experiment_task t ON t.id=a.task_id JOIN experiment_group g ON g.id=a.group_id
        WHERE #{taskId} IS NULL OR a.task_id=#{taskId} ORDER BY a.id
        """)
    List<ExportRow> exportRows(@Param("taskId") Long taskId);
    record AssignmentRow(Long id,Long taskId,Long groupId,Long userId,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,String title,String description,String experimentType,String question,String referenceAnswer,String taskStatus,String groupName,String conditionKey) { }
    record ExportRow(Long id,Long taskId,Long groupId,Long userId,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,String title,String taskStatus,String groupName,String conditionKey,Long runCount) { }
}
