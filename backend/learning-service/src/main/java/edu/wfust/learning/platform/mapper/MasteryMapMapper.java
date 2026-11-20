package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MasteryMapMapper {
    @Select("""
        SELECT q.knowledge_point_id AS pointId, COUNT(*) AS dueCount
        FROM spaced_review_schedule sr
        JOIN wrong_question w ON w.id=sr.wrong_question_id AND w.status<>'MASTERED'
        JOIN quiz_question q ON q.id=w.question_id
        WHERE sr.user_id=#{userId} AND sr.state='ACTIVE' AND sr.due_at<=CURRENT_TIMESTAMP(6)
        GROUP BY q.knowledge_point_id
        """)
    List<DueCount> dueCounts(@Param("userId") Long userId);

    class DueCount {
        private Long pointId;
        private Long dueCount;
        public Long getPointId() { return pointId; }
        public void setPointId(Long value) { pointId = value; }
        public Long getDueCount() { return dueCount; }
        public void setDueCount(Long value) { dueCount = value; }
    }
}
