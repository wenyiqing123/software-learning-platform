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
        SELECT a.id,a.task_id AS taskId,a.group_id AS groupId,a.status,a.assigned_at AS assignedAt,a.started_at AS startedAt,a.completed_at AS completedAt,
               t.title,t.description,t.experiment_type AS experimentType,t.question,t.status AS taskStatus,
               g.name AS groupName,g.condition_key AS conditionKey,g.task_order AS taskOrder
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

    @Select("""
        SELECT a.id, a.user_id AS userId, a.task_id AS taskId, a.group_id AS groupId, a.status AS assignmentStatus,
               t.status AS taskStatus, g.task_order AS taskOrder, a.started_at AS startedAt
        FROM experiment_assignment a
        JOIN experiment_task t ON t.id=a.task_id
        JOIN experiment_group g ON g.id=a.group_id
        WHERE a.id=#{assignmentId} AND a.user_id=#{userId}
        LIMIT 1
        """)
    AssignmentDetail findDetailForUser(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    @Select("""
        SELECT a.id, a.task_id AS taskId, a.group_id AS groupId, a.user_id AS userId,
               u.username, u.display_name AS displayName,
               g.name AS groupName, g.condition_key AS conditionKey, g.task_order AS taskOrder,
               a.status, a.assigned_at AS assignedAt, a.started_at AS startedAt, a.completed_at AS completedAt,
               (SELECT COUNT(*) FROM ai_experiment_run r WHERE r.assignment_id=a.id) AS runCount
        FROM experiment_assignment a
        JOIN sys_user u ON u.id=a.user_id
        JOIN experiment_group g ON g.id=a.group_id
        WHERE a.task_id=#{taskId}
        ORDER BY a.id DESC
        """)
    List<AdminAssignmentRow> findTaskAssignmentsForAdmin(@Param("taskId") Long taskId);

    record AssignmentRow(Long id,Long taskId,Long groupId,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,String title,String description,String experimentType,String question,String taskStatus,String groupName,String conditionKey,String taskOrder) { }
    record ExportRow(Long id,Long taskId,Long groupId,Long userId,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,String title,String taskStatus,String groupName,String conditionKey,Long runCount) { }
    record AssignmentDetail(Long id,Long userId,Long taskId,Long groupId,String assignmentStatus,String taskStatus,String taskOrder,LocalDateTime startedAt) { }
    record AdminAssignmentRow(Long id,Long taskId,Long groupId,Long userId,String username,String displayName,String groupName,String conditionKey,String taskOrder,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,Long runCount) { }
}
