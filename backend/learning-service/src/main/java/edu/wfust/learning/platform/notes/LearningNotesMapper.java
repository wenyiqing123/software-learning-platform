package edu.wfust.learning.platform.notes;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LearningNotesMapper {
    @Select({
        "<script>",
        "SELECT id,user_id AS userId,section_id AS sectionId,knowledge_point_id AS knowledgePointId,title,content,tags_json AS tagsJson,created_at AS createdAt,updated_at AS updatedAt",
        "FROM learning_note WHERE user_id=#{userId}",
        "<if test='sectionId != null'> AND section_id=#{sectionId}</if>",
        "<if test='knowledgePointId != null'> AND knowledge_point_id=#{knowledgePointId}</if>",
        "ORDER BY updated_at DESC,id DESC LIMIT 100",
        "</script>"
    })
    List<NoteRow> list(@Param("userId") Long userId, @Param("sectionId") Long sectionId, @Param("knowledgePointId") Long knowledgePointId);

    @Select("SELECT id,user_id AS userId,section_id AS sectionId,knowledge_point_id AS knowledgePointId,title,content,tags_json AS tagsJson,created_at AS createdAt,updated_at AS updatedAt FROM learning_note WHERE id=#{id} AND user_id=#{userId}")
    NoteRow owned(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("INSERT INTO learning_note(user_id,section_id,knowledge_point_id,title,content,tags_json) VALUES(#{userId},#{sectionId},#{knowledgePointId},#{title},#{content},#{tagsJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteRow row);

    @Update("UPDATE learning_note SET section_id=#{sectionId},knowledge_point_id=#{knowledgePointId},title=#{title},content=#{content},tags_json=#{tagsJson},updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{userId}")
    int update(NoteRow row);

    @Delete("DELETE FROM learning_note WHERE id=#{id} AND user_id=#{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM course_study_section WHERE id=#{id}")
    int sectionExists(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM knowledge_point WHERE id=#{id} AND enabled=TRUE")
    int pointExists(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM learning_section_bookmark WHERE user_id=#{userId} AND section_id=#{targetId}")
    int sectionBookmarked(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Insert("INSERT INTO learning_section_bookmark(user_id,section_id) VALUES(#{userId},#{targetId})")
    int addSectionBookmark(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Delete("DELETE FROM learning_section_bookmark WHERE user_id=#{userId} AND section_id=#{targetId}")
    int removeSectionBookmark(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Select("SELECT COUNT(*) FROM learning_point_bookmark WHERE user_id=#{userId} AND knowledge_point_id=#{targetId}")
    int pointBookmarked(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Insert("INSERT INTO learning_point_bookmark(user_id,knowledge_point_id) VALUES(#{userId},#{targetId})")
    int addPointBookmark(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Delete("DELETE FROM learning_point_bookmark WHERE user_id=#{userId} AND knowledge_point_id=#{targetId}")
    int removePointBookmark(@Param("userId") Long userId, @Param("targetId") Long targetId);

    class NoteRow {
        private Long id,userId,sectionId,knowledgePointId;
        private String title,content,tagsJson;
        private java.time.LocalDateTime createdAt,updatedAt;
        public Long getId(){return id;} public void setId(Long value){id=value;}
        public Long getUserId(){return userId;} public void setUserId(Long value){userId=value;}
        public Long getSectionId(){return sectionId;} public void setSectionId(Long value){sectionId=value;}
        public Long getKnowledgePointId(){return knowledgePointId;} public void setKnowledgePointId(Long value){knowledgePointId=value;}
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public String getContent(){return content;} public void setContent(String value){content=value;}
        public String getTagsJson(){return tagsJson;} public void setTagsJson(String value){tagsJson=value;}
        public java.time.LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(java.time.LocalDateTime value){createdAt=value;}
        public java.time.LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(java.time.LocalDateTime value){updatedAt=value;}
    }
}
