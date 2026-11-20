package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EvaluationMapper {
    @Insert("INSERT INTO learning_evaluation_session(user_id,condition_key,task_key,consent_version,consented_at) VALUES(#{userId},#{conditionKey},#{taskKey},#{consentVersion},#{consentedAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertSession(SessionRow row);
    @Select("SELECT id,user_id AS userId,condition_key AS conditionKey,task_key AS taskKey,consent_version AS consentVersion,consented_at AS consentedAt,started_at AS startedAt,completed_at AS completedAt,elapsed_seconds AS elapsedSeconds,psychological_effort AS psychologicalEffort,feedback FROM learning_evaluation_session WHERE user_id=#{userId} AND id=#{id}")
    SessionRow findSession(@Param("userId") long userId,@Param("id") long id);
    @Select("SELECT id,user_id AS userId,condition_key AS conditionKey,task_key AS taskKey,consent_version AS consentVersion,consented_at AS consentedAt,started_at AS startedAt,completed_at AS completedAt,elapsed_seconds AS elapsedSeconds,psychological_effort AS psychologicalEffort,feedback FROM learning_evaluation_session WHERE user_id=#{userId} ORDER BY created_at DESC LIMIT 50")
    List<SessionRow> listOwned(@Param("userId") long userId);
    @Insert("INSERT INTO learning_evaluation_measurement(session_id,user_id,phase,score,responses_json) VALUES(#{sessionId},#{userId},#{phase},#{score},#{responsesJson}) ON DUPLICATE KEY UPDATE score=VALUES(score),responses_json=VALUES(responses_json),measured_at=CURRENT_TIMESTAMP(6)")
    int upsertMeasurement(@Param("userId") long userId,@Param("sessionId") long sessionId,@Param("phase") String phase,@Param("score") double score,@Param("responsesJson") String responsesJson,@Param("measuredAt") LocalDateTime measuredAt);
    @Update("UPDATE learning_evaluation_session SET completed_at=#{completedAt},elapsed_seconds=#{elapsedSeconds},psychological_effort=#{effort},feedback=#{feedback} WHERE user_id=#{userId} AND id=#{id}")
    int complete(@Param("userId") long userId,@Param("id") long id,@Param("elapsedSeconds") int elapsedSeconds,@Param("effort") int effort,@Param("feedback") String feedback,@Param("completedAt") LocalDateTime completedAt);
    @Select("""
        SELECT s.condition_key AS conditionKey,COUNT(*) AS sampleCount,
               AVG(pre.score) AS meanPreScore,AVG(post.score) AS meanPostScore,
               AVG(post.score-pre.score) AS meanGain,AVG(s.psychological_effort) AS meanEffort,
               AVG(s.elapsed_seconds) AS meanElapsedSeconds,COUNT(s.feedback) AS feedbackCount
        FROM learning_evaluation_session s
        LEFT JOIN learning_evaluation_measurement pre ON pre.session_id=s.id AND pre.phase='PRE'
        LEFT JOIN learning_evaluation_measurement post ON post.session_id=s.id AND post.phase='POST'
        WHERE s.completed_at IS NOT NULL AND (#{conditionKey} IS NULL OR s.condition_key=#{conditionKey})
        GROUP BY s.condition_key ORDER BY s.condition_key
        """)
    List<SummaryRow> summary(@Param("conditionKey") String conditionKey);

    class SessionRow {
        private Long id,userId; private String conditionKey,taskKey,consentVersion,feedback; private LocalDateTime consentedAt,startedAt,completedAt; private Integer elapsedSeconds,psychologicalEffort;
        public SessionRow() { }
        public SessionRow(Long id,Long userId,String conditionKey,String taskKey,String consentVersion,LocalDateTime consentedAt,LocalDateTime startedAt,LocalDateTime completedAt,Integer elapsedSeconds,Integer psychologicalEffort,String feedback){this.id=id;this.userId=userId;this.conditionKey=conditionKey;this.taskKey=taskKey;this.consentVersion=consentVersion;this.consentedAt=consentedAt;this.startedAt=startedAt;this.completedAt=completedAt;this.elapsedSeconds=elapsedSeconds;this.psychologicalEffort=psychologicalEffort;this.feedback=feedback;}
        public Long getId(){return id;} public void setId(Long value){id=value;} public Long getUserId(){return userId;} public String getConditionKey(){return conditionKey;} public String getTaskKey(){return taskKey;} public String getConsentVersion(){return consentVersion;} public LocalDateTime getConsentedAt(){return consentedAt;} public LocalDateTime getStartedAt(){return startedAt;} public LocalDateTime getCompletedAt(){return completedAt;} public Integer getElapsedSeconds(){return elapsedSeconds;} public Integer getPsychologicalEffort(){return psychologicalEffort;} public String getFeedback(){return feedback;}
    }
    record SummaryRow(String conditionKey,Long sampleCount,Double meanPreScore,Double meanPostScore,Double meanGain,Double meanEffort,Double meanElapsedSeconds,Long feedbackCount) { }
}
