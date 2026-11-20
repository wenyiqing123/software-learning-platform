package edu.wfust.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("micro_lesson_draft")
public class MicroLessonDraftEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long sectionId;
    private String title;
    private String status;
    private String summary;
    private String objectivesJson;
    private String triplesJson;
    private String commonErrorsJson;
    private String selfTestJson;
    private String prerequisitesJson;
    private String generatedText;
    private String evidenceJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getObjectivesJson() { return objectivesJson; }
    public void setObjectivesJson(String objectivesJson) { this.objectivesJson = objectivesJson; }
    public String getTriplesJson() { return triplesJson; }
    public void setTriplesJson(String triplesJson) { this.triplesJson = triplesJson; }
    public String getCommonErrorsJson() { return commonErrorsJson; }
    public void setCommonErrorsJson(String commonErrorsJson) { this.commonErrorsJson = commonErrorsJson; }
    public String getSelfTestJson() { return selfTestJson; }
    public void setSelfTestJson(String selfTestJson) { this.selfTestJson = selfTestJson; }
    public String getPrerequisitesJson() { return prerequisitesJson; }
    public void setPrerequisitesJson(String prerequisitesJson) { this.prerequisitesJson = prerequisitesJson; }
    public String getGeneratedText() { return generatedText; }
    public void setGeneratedText(String generatedText) { this.generatedText = generatedText; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
