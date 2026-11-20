package edu.wfust.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("learning_event")
public class LearningEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long userId;
    private String eventType;
    private String payloadJson;
    private java.time.LocalDateTime publishedAt;
    private java.time.LocalDateTime processedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public java.time.LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(java.time.LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public java.time.LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(java.time.LocalDateTime processedAt) { this.processedAt = processedAt; }
}
