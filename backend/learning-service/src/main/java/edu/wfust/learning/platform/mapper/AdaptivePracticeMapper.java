package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AdaptivePracticeMapper {
    @Select("""
        SELECT q.id AS questionId,q.stem AS stem,q.difficulty AS difficulty,
               q.knowledge_point_id AS knowledgePointId,kp.name AS knowledgePointName,
               kp.prerequisites_json AS prerequisitesJson,COALESCE(m.mastery_score,0) AS mastery,
               COALESCE(w.wrong_count,0) AS wrongCount,
               CASE WHEN EXISTS(SELECT 1 FROM spaced_review_schedule sr WHERE sr.user_id=#{userId} AND sr.wrong_question_id=w.id AND sr.state='ACTIVE' AND sr.due_at<=CURRENT_TIMESTAMP(6)) THEN TRUE ELSE FALSE END AS due
        FROM quiz_question q
        JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_point kp ON kp.id=q.knowledge_point_id
        LEFT JOIN knowledge_mastery m ON m.user_id=#{userId} AND m.knowledge_point_id=q.knowledge_point_id
        LEFT JOIN wrong_question w ON w.user_id=#{userId} AND w.question_id=q.id AND w.status<>'MASTERED'
        WHERE q.enabled=TRUE AND (#{courseId} IS NULL OR q.course_id=#{courseId})
        ORDER BY COALESCE(m.mastery_score,0),COALESCE(w.wrong_count,0) DESC,q.difficulty DESC,q.id
        LIMIT #{limit}
        """)
    List<Candidate> candidates(@Param("userId") long userId,@Param("courseId") Long courseId,@Param("limit") int limit);
    class Candidate {
        private Long questionId,knowledgePointId; private String stem,knowledgePointName,prerequisitesJson; private Integer difficulty,wrongCount; private Double mastery; private Boolean due;
        public Long getQuestionId(){return questionId;} public void setQuestionId(Long value){questionId=value;}
        public Long getKnowledgePointId(){return knowledgePointId;} public void setKnowledgePointId(Long value){knowledgePointId=value;}
        public String getStem(){return stem;} public void setStem(String value){stem=value;}
        public String getKnowledgePointName(){return knowledgePointName;} public void setKnowledgePointName(String value){knowledgePointName=value;}
        public String getPrerequisitesJson(){return prerequisitesJson;} public void setPrerequisitesJson(String value){prerequisitesJson=value;}
        public Integer getDifficulty(){return difficulty;} public void setDifficulty(Integer value){difficulty=value;}
        public Integer getWrongCount(){return wrongCount;} public void setWrongCount(Integer value){wrongCount=value;}
        public Double getMastery(){return mastery;} public void setMastery(Double value){mastery=value;}
        public Boolean getDue(){return due;} public void setDue(Boolean value){due=value;}
    }
}
