package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface StudyPlanMapper {
    @Select("SELECT id,user_id,plan_date,budget_minutes FROM daily_study_plan WHERE user_id=#{userId} AND plan_date=#{date}")
    PlanRow findPlan(@Param("userId") long userId,@Param("date") LocalDate date);
    @Insert("INSERT INTO daily_study_plan(user_id,plan_date,budget_minutes) VALUES(#{userId},#{planDate},#{budgetMinutes}) ON DUPLICATE KEY UPDATE budget_minutes=VALUES(budget_minutes),updated_at=CURRENT_TIMESTAMP(6)")
    int savePlan(PlanRow plan);
    @Select("SELECT id,plan_id AS planId,user_id AS userId,task_type AS type,target_id AS targetId,target_title AS title,target_detail AS detail,minutes,reason,state,sort_order AS sortOrder,course_id AS courseId FROM daily_study_task WHERE user_id=#{userId} AND plan_id=#{planId} ORDER BY sort_order,id")
    List<TaskRow> tasks(@Param("userId") long userId,@Param("planId") long planId);
    @Delete("DELETE FROM daily_study_task WHERE user_id=#{userId} AND plan_id=#{planId} AND state='PENDING'") int deletePending(@Param("userId") long userId,@Param("planId") long planId);
    @Select("SELECT COALESCE(SUM(minutes),0) FROM daily_study_task WHERE user_id=#{userId} AND plan_id=#{planId} AND state='COMPLETED'") int completedMinutes(@Param("userId") long userId,@Param("planId") long planId);
    @Insert("INSERT IGNORE INTO daily_study_task(plan_id,user_id,task_type,target_id,target_title,target_detail,minutes,reason,state,sort_order,course_id) VALUES(#{planId},#{userId},#{type},#{targetId},#{title},#{detail},#{minutes},#{reason},'PENDING',#{sortOrder},#{courseId})") int insertTask(TaskRow task);
    @Select("SELECT id,plan_id AS planId,user_id AS userId,task_type AS type,target_id AS targetId,target_title AS title,target_detail AS detail,minutes,reason,state,sort_order AS sortOrder,course_id AS courseId FROM daily_study_task WHERE user_id=#{userId} AND plan_id=#{planId} AND id=#{taskId}") TaskRow findTask(@Param("userId") long userId,@Param("planId") long planId,@Param("taskId") long taskId);
    @Update("UPDATE daily_study_task SET state=#{state},completed_at=#{completedAt} WHERE id=#{taskId} AND user_id=#{userId} AND plan_id=#{planId} AND state='PENDING'") int updateState(@Param("userId") long userId,@Param("planId") long planId,@Param("taskId") long taskId,@Param("state") String state,@Param("completedAt") LocalDateTime completedAt);
    @Select("SELECT COUNT(*) FROM course_study_progress WHERE user_id=#{userId} AND section_id=#{targetId} AND completed=TRUE") int sectionCompleted(@Param("userId") long userId,@Param("targetId") long targetId);
    @Select("SELECT COUNT(*) FROM spaced_review_log WHERE user_id=#{userId} AND wrong_question_id=#{targetId} AND reviewed_at>=#{start} AND reviewed_at<#{end} AND is_correct=1") int hasCorrectReview(@Param("userId") long userId,@Param("targetId") long targetId,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    @Select("SELECT COUNT(*) FROM quiz_attempt WHERE user_id=#{userId} AND question_id=#{targetId} AND submitted_at>=#{start} AND submitted_at<#{end} AND is_correct=1") int hasCorrectAttempt(@Param("userId") long userId,@Param("targetId") long targetId,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    @Select("""
        SELECT s.wrong_question_id AS targetId,q.stem AS title,COALESCE(kp.name,'错题复习') AS detail,c.id AS courseId
        FROM spaced_review_schedule s JOIN wrong_question w ON w.id=s.wrong_question_id AND w.status<>'MASTERED'
        JOIN quiz_question q ON q.id=w.question_id AND q.enabled=TRUE JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_point kp ON kp.id=q.knowledge_point_id
        WHERE s.user_id=#{userId} AND s.state='ACTIVE' AND s.due_at<#{nextDay} ORDER BY s.due_at,s.id
        """)
    List<ReviewCandidate> reviewCandidates(@Param("userId") long userId,@Param("nextDay") LocalDateTime nextDay);
    @Select("""
        SELECT s.id AS targetId,s.title AS title,CONCAT(c.name,' · ',ch.name) AS detail,
               NULL AS dueAt,NULL AS wrongCount,c.id AS courseId,NULL AS mastery
        FROM course_study_section s JOIN course c ON c.id=s.course_id AND c.enabled=TRUE
        JOIN course_chapter ch ON ch.id=s.chapter_id AND ch.course_id=c.id
        LEFT JOIN course_study_progress p ON p.section_id=s.id AND p.user_id=#{userId}
        WHERE COALESCE(p.completed,FALSE)=FALSE ORDER BY c.id,ch.sort_order,s.sort_order,s.id
        """)
    List<ReadingCandidate> readingCandidates(@Param("userId") long userId);
    @Select("""
        SELECT q.id AS targetId,q.stem AS title,COALESCE(kp.name,'课程练习') AS detail,c.id AS courseId,
               COALESCE(m.mastery_score,0) AS mastery
        FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.enabled=TRUE
        LEFT JOIN knowledge_point kp ON kp.id=q.knowledge_point_id
        LEFT JOIN knowledge_mastery m ON m.knowledge_point_id=q.knowledge_point_id AND m.user_id=#{userId}
        WHERE q.enabled=TRUE AND COALESCE(m.mastery_score,0)<60
          AND NOT EXISTS (SELECT 1 FROM wrong_question w WHERE w.user_id=#{userId} AND w.question_id=q.id AND w.status<>'MASTERED')
        ORDER BY COALESCE(m.mastery_score,0),q.difficulty DESC,q.id
        """)
    List<PracticeCandidate> practiceCandidates(@Param("userId") long userId);
    record PlanRow(Long id,Long userId,LocalDate planDate,Integer budgetMinutes) { }
    record TaskRow(Long id,Long planId,Long userId,String type,Long targetId,String title,String detail,Integer minutes,String reason,String state,Integer sortOrder,Long courseId) { }
    class ReviewCandidate {
        private Long targetId,courseId; private String title,detail;
        public Long getTargetId(){return targetId;} public void setTargetId(Long value){targetId=value;}
        public Long getCourseId(){return courseId;} public void setCourseId(Long value){courseId=value;}
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public String getDetail(){return detail;} public void setDetail(String value){detail=value;}
    }
    class ReadingCandidate {
        private Long targetId,courseId; private String title,detail;
        public Long getTargetId(){return targetId;} public void setTargetId(Long value){targetId=value;}
        public Long getCourseId(){return courseId;} public void setCourseId(Long value){courseId=value;}
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public String getDetail(){return detail;} public void setDetail(String value){detail=value;}
    }
    class PracticeCandidate {
        private Long targetId,courseId; private String title,detail; private Double mastery;
        public Long getTargetId(){return targetId;} public void setTargetId(Long value){targetId=value;}
        public Long getCourseId(){return courseId;} public void setCourseId(Long value){courseId=value;}
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public String getDetail(){return detail;} public void setDetail(String value){detail=value;}
        public Double getMastery(){return mastery;} public void setMastery(Double value){mastery=value;}
    }
}

