package edu.wfust.learning.platform.mapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface RecommendationSnapshotMapper {
    @Delete("DELETE FROM recommendation_snapshot WHERE user_id=#{userId}") int deleteForUser(long userId);
    @Insert("INSERT INTO recommendation_snapshot(user_id,knowledge_point_id,recommendation_type,priority_score,reason) VALUES(#{userId},#{pointId},'KNOWLEDGE',#{score},#{reason})")
    int save(@Param("userId")long userId,@Param("pointId")long pointId,@Param("score")double score,@Param("reason")String reason);
}
