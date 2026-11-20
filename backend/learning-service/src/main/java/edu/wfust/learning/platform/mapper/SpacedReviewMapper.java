package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@org.apache.ibatis.annotations.Mapper
public interface SpacedReviewMapper {
    @Insert("""
        INSERT IGNORE INTO spaced_review_schedule(user_id,wrong_question_id,due_at,last_attempt_id)
        SELECT w.user_id,w.id,w.last_wrong_at,(SELECT MAX(a.id) FROM quiz_attempt a WHERE a.user_id=w.user_id AND a.question_id=w.question_id)
        FROM wrong_question w WHERE w.user_id=#{userId} AND w.status<>'MASTERED'
        """) int synchronizeOpenWrongQuestions(@Param("userId") long userId);
    @Update("""
        UPDATE spaced_review_schedule s JOIN wrong_question w ON w.id=s.wrong_question_id AND w.user_id=s.user_id
        SET s.state='ACTIVE',s.interval_minutes=0,s.due_at=w.last_wrong_at,s.last_reviewed_at=NULL,
            s.last_attempt_id=(SELECT MAX(a.id) FROM quiz_attempt a WHERE a.user_id=w.user_id AND a.question_id=w.question_id),w.review_streak=0
        WHERE s.user_id=#{userId} AND s.state='MASTERED' AND w.status<>'MASTERED'
        """) int reopenNewlyUnmastered(@Param("userId") long userId);
    @Update("""
        UPDATE spaced_review_schedule s JOIN wrong_question w ON w.id=s.wrong_question_id AND w.user_id=s.user_id
        SET s.state='ACTIVE',s.interval_minutes=0,s.due_at=w.last_wrong_at,
            s.last_attempt_id=(SELECT MAX(a.id) FROM quiz_attempt a WHERE a.user_id=w.user_id AND a.question_id=w.question_id),w.review_streak=0
        WHERE s.user_id=#{userId} AND s.state<>'PAUSED' AND w.status<>'MASTERED'
          AND (SELECT MAX(a.id) FROM quiz_attempt a WHERE a.user_id=w.user_id AND a.question_id=w.question_id)>COALESCE(s.last_attempt_id,0)
          AND w.last_wrong_at>=COALESCE(s.last_reviewed_at,'1970-01-01 00:00:00')
        """) int resetAfterNewAttempt(@Param("userId") long userId);

    String CARDS="""
        SELECT s.id AS scheduleId,s.wrong_question_id AS wrongQuestionId,s.due_at AS dueAt,s.interval_minutes AS intervalMinutes,
               w.wrong_count AS wrongCount,q.id AS questionId,q.stem AS question,kp.name AS knowledgePoint,
               q.question_type AS questionType,q.options_json AS optionsJson,sec.id AS sectionId,q.course_id AS courseId
        FROM spaced_review_schedule s
        JOIN wrong_question w ON w.id=s.wrong_question_id AND w.user_id=s.user_id AND w.status<>'MASTERED'
        JOIN quiz_question q ON q.id=w.question_id AND q.enabled=TRUE JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_point kp ON kp.id=q.knowledge_point_id
        LEFT JOIN course_study_section sec ON sec.knowledge_point_id=q.knowledge_point_id AND sec.course_id=q.course_id
        """;
    @Select(CARDS+" WHERE s.user_id=#{userId} AND s.state='ACTIVE' AND s.due_at<=#{now} ORDER BY s.due_at,s.id")
    List<ReviewCandidate> due(@Param("userId") long userId,@Param("now") LocalDateTime now);
    @Select(CARDS+" WHERE s.user_id=#{userId} AND s.state='PAUSED' ORDER BY s.due_at,s.id")
    List<ReviewCandidate> paused(@Param("userId") long userId);
    @Select("SELECT * FROM spaced_review_schedule WHERE user_id=#{userId} AND wrong_question_id=#{wrongId} FOR UPDATE")
    ReviewSchedule findOwnedForUpdate(@Param("userId") long userId,@Param("wrongId") long wrongId);
    @Select("SELECT wrong_question_id,recall_rating,response_json,request_json FROM spaced_review_log WHERE user_id=#{userId} AND request_key=#{requestKey}")
    Map<String,Object> findRequest(@Param("userId") long userId,@Param("requestKey") String requestKey);
    @Insert("""
        INSERT INTO spaced_review_log(user_id,wrong_question_id,request_key,attempt_id,recall_rating,is_correct,interval_minutes,reviewed_at,next_due_at,response_json,request_json)
        VALUES(#{userId},#{wrongId},#{requestKey},#{attemptId},#{rating},#{correct},#{interval},#{reviewedAt},#{nextDue},#{responseJson},#{requestJson})
        """)
    int insertLog(@Param("userId") long userId,@Param("wrongId") long wrongId,@Param("requestKey") String requestKey,
                  @Param("attemptId") long attemptId,@Param("rating") int rating,@Param("correct") boolean correct,
                  @Param("interval") int interval,@Param("reviewedAt") LocalDateTime reviewedAt,@Param("nextDue") LocalDateTime nextDue,
                  @Param("responseJson") String responseJson,@Param("requestJson") String requestJson);
    @Update("UPDATE spaced_review_schedule SET state=#{state},interval_minutes=#{interval},due_at=#{dueAt},last_reviewed_at=#{reviewedAt},last_attempt_id=#{attemptId} WHERE user_id=#{userId} AND wrong_question_id=#{wrongId}")
    int updateAfterReview(@Param("userId") long userId,@Param("wrongId") long wrongId,@Param("state") String state,
                          @Param("interval") int interval,@Param("dueAt") LocalDateTime dueAt,@Param("reviewedAt") LocalDateTime reviewedAt,@Param("attemptId") long attemptId);
    @Update("UPDATE spaced_review_schedule SET state=#{state},interval_minutes=IF(#{state}='ACTIVE',0,interval_minutes),due_at=IF(#{state}='ACTIVE',#{now},due_at) WHERE user_id=#{userId} AND wrong_question_id=#{wrongId} AND state IN ('ACTIVE','PAUSED','MASTERED')")
    int setState(@Param("userId") long userId,@Param("wrongId") long wrongId,@Param("state") String state,@Param("now") LocalDateTime now);
    @Select("""
        SELECT s.wrong_question_id AS targetId,q.stem AS title,COALESCE(kp.name,'错题复习') AS detail,s.due_at AS dueAt,w.wrong_count AS wrongCount,c.id AS courseId
        FROM spaced_review_schedule s JOIN wrong_question w ON w.id=s.wrong_question_id AND w.status<>'MASTERED'
        JOIN quiz_question q ON q.id=w.question_id AND q.enabled=TRUE JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_point kp ON kp.id=q.knowledge_point_id
        WHERE s.user_id=#{userId} AND s.state='ACTIVE' AND s.due_at<#{nextDay} ORDER BY s.due_at,s.id
        """) List<ReviewCandidate> reviewCandidates(@Param("userId") long userId,@Param("nextDay") LocalDateTime nextDay);
    record ReviewSchedule(Long id,Long userId,Long wrongQuestionId,String state,Integer intervalMinutes,LocalDateTime dueAt,LocalDateTime lastReviewedAt) { }
    record ReviewCandidate(Long scheduleId,Long wrongQuestionId,LocalDateTime dueAt,Integer intervalMinutes,Integer wrongCount,
                           Long questionId,String question,String knowledgePoint,String questionType,String optionsJson,Long sectionId,Long courseId) { }
}
