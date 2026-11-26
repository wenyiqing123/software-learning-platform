package edu.wfust.learning.platform.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FlashcardMapper {
    @Select({
        "<script>",
        "SELECT kp.id AS knowledgePointId, kp.course_id AS courseId, c.name AS courseName,",
        "       ch.name AS chapterName, sec.id AS sectionId, sec.title AS sectionTitle,",
        "       sec.objectives_json AS objectivesJson, kp.name AS name, kp.graph_entity AS graphEntity,",
        "       kp.description AS summary, COALESCE(km.mastery_score, 0) AS masteryScore,",
        "       fr.recall_rating AS recallRating, COALESCE(fr.review_count, 0) AS reviewCount,",
        "       fr.interval_minutes AS intervalMinutes, fr.next_due_at AS nextDueAt, fr.last_reviewed_at AS lastReviewedAt",
        "FROM knowledge_point kp",
        "JOIN course c ON c.id = kp.course_id AND c.enabled = TRUE",
        "LEFT JOIN course_chapter ch ON ch.id = kp.chapter_id",
        "LEFT JOIN course_study_section sec ON sec.knowledge_point_id = kp.id",
        "LEFT JOIN knowledge_mastery km ON km.knowledge_point_id = kp.id AND km.user_id = #{userId}",
        "LEFT JOIN flashcard_review_state fr ON fr.knowledge_point_id = kp.id AND fr.user_id = #{userId}",
        "WHERE kp.enabled = TRUE",
        "<if test='courseId != null'> AND kp.course_id = #{courseId}</if>",
        "<if test='knowledgePointId != null'> AND kp.id = #{knowledgePointId}</if>",
        "ORDER BY CASE WHEN fr.next_due_at IS NULL OR fr.next_due_at &lt;= #{now} THEN 0 ELSE 1 END,",
        "         COALESCE(fr.next_due_at, #{now}) ASC, kp.sort_order ASC, kp.id ASC",
        "</script>"
    })
    List<FlashcardRow> findCards(
        @Param("userId") long userId,
        @Param("courseId") Long courseId,
        @Param("knowledgePointId") Long knowledgePointId,
        @Param("now") LocalDateTime now
    );

    @Select("""
        SELECT COUNT(*)
        FROM knowledge_point kp
        JOIN course c ON c.id = kp.course_id AND c.enabled = TRUE
        WHERE kp.id = #{knowledgePointId} AND kp.enabled = TRUE
        """)
    int existsEnabledPoint(@Param("knowledgePointId") long knowledgePointId);

    @Update("""
        UPDATE flashcard_review_state
        SET recall_rating = #{recallRating},
            review_count = review_count + 1,
            interval_minutes = #{intervalMinutes},
            next_due_at = #{nextDueAt},
            last_reviewed_at = #{reviewedAt},
            updated_at = #{reviewedAt}
        WHERE user_id = #{userId} AND knowledge_point_id = #{knowledgePointId}
        """)
    int updateState(
        @Param("userId") long userId,
        @Param("knowledgePointId") long knowledgePointId,
        @Param("recallRating") int recallRating,
        @Param("intervalMinutes") int intervalMinutes,
        @Param("nextDueAt") LocalDateTime nextDueAt,
        @Param("reviewedAt") LocalDateTime reviewedAt
    );

    @Insert("""
        INSERT INTO flashcard_review_state(
            user_id, knowledge_point_id, recall_rating, review_count, interval_minutes, next_due_at, last_reviewed_at
        )
        VALUES(
            #{userId}, #{knowledgePointId}, #{recallRating}, 1, #{intervalMinutes}, #{nextDueAt}, #{reviewedAt}
        )
        """)
    int insertState(
        @Param("userId") long userId,
        @Param("knowledgePointId") long knowledgePointId,
        @Param("recallRating") int recallRating,
        @Param("intervalMinutes") int intervalMinutes,
        @Param("nextDueAt") LocalDateTime nextDueAt,
        @Param("reviewedAt") LocalDateTime reviewedAt
    );

    record FlashcardRow(
        Long knowledgePointId,
        Long courseId,
        String courseName,
        String chapterName,
        Long sectionId,
        String sectionTitle,
        String objectivesJson,
        String name,
        String graphEntity,
        String summary,
        BigDecimal masteryScore,
        Integer recallRating,
        Integer reviewCount,
        Integer intervalMinutes,
        LocalDateTime nextDueAt,
        LocalDateTime lastReviewedAt
    ) { }
}
