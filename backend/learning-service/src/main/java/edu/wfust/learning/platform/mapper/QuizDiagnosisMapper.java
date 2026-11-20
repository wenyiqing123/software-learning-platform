package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuizDiagnosisMapper {
    @Select({
        "<script>",
        "SELECT d.option_key AS optionKey, d.knowledge_point_id AS knowledgePointId, kp.name AS knowledgePointName,",
        "d.rationale AS rationale, s.id AS sectionId, s.title AS sectionTitle,",
        "(SELECT MIN(q2.id) FROM quiz_question q2 WHERE q2.knowledge_point_id=d.knowledge_point_id AND q2.enabled=TRUE AND q2.id&lt;&gt;d.question_id) AS practiceQuestionId",
        "FROM quiz_option_diagnosis d JOIN knowledge_point kp ON kp.id=d.knowledge_point_id AND kp.enabled=TRUE",
        "LEFT JOIN course_study_section s ON s.knowledge_point_id=d.knowledge_point_id",
        "WHERE d.question_id=#{questionId} AND d.enabled=TRUE AND d.option_key IN",
        "<foreach collection='optionKeys' item='key' open='(' separator=',' close=')'>#{key}</foreach>",
        "ORDER BY d.option_key, d.id",
        "</script>"
    })
    List<DiagnosisRow> matching(@Param("questionId") Long questionId, @Param("optionKeys") List<String> optionKeys);

    class DiagnosisRow {
        private String optionKey, knowledgePointName, rationale, sectionTitle;
        private Long knowledgePointId, sectionId, practiceQuestionId;
        public String getOptionKey() { return optionKey; }
        public void setOptionKey(String value) { optionKey = value; }
        public Long getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(Long value) { knowledgePointId = value; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String value) { knowledgePointName = value; }
        public String getRationale() { return rationale; }
        public void setRationale(String value) { rationale = value; }
        public Long getSectionId() { return sectionId; }
        public void setSectionId(Long value) { sectionId = value; }
        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String value) { sectionTitle = value; }
        public Long getPracticeQuestionId() { return practiceQuestionId; }
        public void setPracticeQuestionId(Long value) { practiceQuestionId = value; }
    }
}
