package edu.wfust.learning.platform.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("experiment_assignment")
public class ExperimentAssignmentEntity {
    @TableId(type=IdType.AUTO) private Long id; private Long taskId; private Long groupId; private Long userId; private String status;
    private LocalDateTime assignedAt; private LocalDateTime startedAt; private LocalDateTime completedAt;
    public Long getId(){return id;} public void setId(Long value){id=value;} public Long getTaskId(){return taskId;} public void setTaskId(Long value){taskId=value;}
    public Long getGroupId(){return groupId;} public void setGroupId(Long value){groupId=value;} public Long getUserId(){return userId;} public void setUserId(Long value){userId=value;}
    public String getStatus(){return status;} public void setStatus(String value){status=value;} public LocalDateTime getAssignedAt(){return assignedAt;} public void setAssignedAt(LocalDateTime value){assignedAt=value;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime value){startedAt=value;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime value){completedAt=value;}
}
