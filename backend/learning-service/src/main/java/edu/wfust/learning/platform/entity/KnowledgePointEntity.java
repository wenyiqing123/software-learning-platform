package edu.wfust.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("knowledge_point")
public class KnowledgePointEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long chapterId;
    private String name;
    private String graphEntity;
    private String description;
    private String prerequisitesJson;
    private Integer sortOrder;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGraphEntity() { return graphEntity; }
    public void setGraphEntity(String graphEntity) { this.graphEntity = graphEntity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPrerequisitesJson() { return prerequisitesJson; }
    public void setPrerequisitesJson(String prerequisitesJson) { this.prerequisitesJson = prerequisitesJson; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
