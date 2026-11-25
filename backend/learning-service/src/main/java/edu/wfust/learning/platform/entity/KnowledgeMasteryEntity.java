package edu.wfust.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("knowledge_mastery")
public class KnowledgeMasteryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long knowledgePointId;
    private java.math.BigDecimal masteryScore;
    private java.math.BigDecimal peakMastery;
    private Integer attemptCount;
    private Integer correctCount;
    private java.time.LocalDateTime lastStudiedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    public java.math.BigDecimal getMasteryScore() { return masteryScore; }
    public void setMasteryScore(java.math.BigDecimal masteryScore) { this.masteryScore = masteryScore; }
    public java.math.BigDecimal getPeakMastery() { return peakMastery; }
    public void setPeakMastery(java.math.BigDecimal peakMastery) { this.peakMastery = peakMastery; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public java.time.LocalDateTime getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(java.time.LocalDateTime lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
}
