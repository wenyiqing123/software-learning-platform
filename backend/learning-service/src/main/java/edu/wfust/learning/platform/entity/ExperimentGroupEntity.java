package edu.wfust.learning.platform.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("experiment_group")
public class ExperimentGroupEntity {
    @TableId(type=IdType.AUTO) private Long id; private Long taskId; private String name; private String conditionKey; private Integer sortOrder; private String taskOrder;
    public Long getId(){return id;} public void setId(Long value){id=value;} public Long getTaskId(){return taskId;} public void setTaskId(Long value){taskId=value;}
    public String getName(){return name;} public void setName(String value){name=value;} public String getConditionKey(){return conditionKey;} public void setConditionKey(String value){conditionKey=value;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer value){sortOrder=value;}
    public String getTaskOrder(){return taskOrder;} public void setTaskOrder(String value){taskOrder=value;}
}
