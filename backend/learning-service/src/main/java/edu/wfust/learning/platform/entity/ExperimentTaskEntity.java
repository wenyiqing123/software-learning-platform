package edu.wfust.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("experiment_task")
public class ExperimentTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String title; private String description; private String experimentType; private String question;
    private String referenceAnswer; private String expectedEntitiesJson; private Boolean includeAblations; private String status;
    private Long createdBy; private LocalDateTime createdAt; private LocalDateTime startsAt; private LocalDateTime endsAt;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public String getTitle(){return title;} public void setTitle(String value){title=value;}
    public String getDescription(){return description;} public void setDescription(String value){description=value;}
    public String getExperimentType(){return experimentType;} public void setExperimentType(String value){experimentType=value;}
    public String getQuestion(){return question;} public void setQuestion(String value){question=value;}
    public String getReferenceAnswer(){return referenceAnswer;} public void setReferenceAnswer(String value){referenceAnswer=value;}
    public String getExpectedEntitiesJson(){return expectedEntitiesJson;} public void setExpectedEntitiesJson(String value){expectedEntitiesJson=value;}
    public Boolean getIncludeAblations(){return includeAblations;} public void setIncludeAblations(Boolean value){includeAblations=value;}
    public String getStatus(){return status;} public void setStatus(String value){status=value;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long value){createdBy=value;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime value){createdAt=value;}
    public LocalDateTime getStartsAt(){return startsAt;} public void setStartsAt(LocalDateTime value){startsAt=value;}
    public LocalDateTime getEndsAt(){return endsAt;} public void setEndsAt(LocalDateTime value){endsAt=value;}
}
