package edu.wfust.learning.platform.tutor;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TutorSessionService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public TutorSessionService(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }
    public record SessionView(long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record MessageView(long id, String role, String content, String mode, Map<String,Object> metadata, LocalDateTime createdAt) { }
    public record MessageInput(@NotBlank @Pattern(regexp="USER|ASSISTANT") String role,
                               @NotBlank @Size(max=20000) String content,
                               @Pattern(regexp="DIRECT|TRIPLES|RANKED|COMPARE|TUTOR_HINT_[1-3]") String mode, Map<String,Object> metadata) { }
    public List<SessionView> list(long userId) {
        return db.query("SELECT * FROM ai_session WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT 50",
            (rs,n) -> new SessionView(rs.getLong("id"),rs.getString("title"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime()),userId);
    }
    @Transactional public SessionView create(long userId, String title) {
        String name = title == null || title.isBlank() ? "新的学习会话" : title.strip();
        if(name.length()>120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"会话标题过长");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update(connection -> { var ps=connection.prepareStatement("INSERT INTO ai_session(user_id,title) VALUES(?,?)",new String[]{"id"}); ps.setLong(1,userId);ps.setString(2,name);return ps; },key);
        return owned(userId,Objects.requireNonNull(key.getKey()).longValue());
    }
    @Transactional public SessionView rename(long userId, long id, String title) {
        owned(userId, id);
        String name = title == null ? "" : title.strip();
        if (name.isBlank() || name.length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话标题长度必须为 1-120 个字符");
        db.update("UPDATE ai_session SET title=? WHERE id=? AND user_id=?", name, id, userId);
        return owned(userId, id);
    }
    public List<MessageView> messages(long userId,long id) {
        owned(userId,id);
        return db.query("SELECT * FROM ai_message WHERE session_id=? ORDER BY id ASC LIMIT 200",
            (rs,n)-> new MessageView(rs.getLong("id"),rs.getString("role"),rs.getString("content"),rs.getString("mode"),read(rs.getString("metadata_json")),rs.getTimestamp("created_at").toLocalDateTime()),id);
    }
    @Transactional public MessageView append(long userId,long id,MessageInput input) {
        owned(userId,id);
        if(input==null || !Set.of("USER","ASSISTANT").contains(input.role()) || input.content()==null || input.content().isBlank() || input.content().length()>20000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"会话消息格式不正确");
        String metadata=json.writeValueAsString(input.metadata()==null?Map.of():input.metadata());
        if(metadata.length()>200000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"会话证据超过存储限制");
        Long count=db.queryForObject("SELECT COUNT(*) FROM ai_message WHERE session_id=?",Long.class,id);
        if(count!=null && count>=200) throw new ResponseStatusException(HttpStatus.CONFLICT,"此会话已达到 200 条消息，请新建会话");
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        db.update(connection->{var ps=connection.prepareStatement("INSERT INTO ai_message(session_id,role,content,mode,metadata_json) VALUES(?,?,?,?,?)",new String[]{"id"});ps.setLong(1,id);ps.setString(2,input.role());ps.setString(3,input.content());ps.setString(4,input.mode());ps.setString(5,metadata);return ps;},key);
        db.update("UPDATE ai_session SET updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,userId);
        return new MessageView(Objects.requireNonNull(key.getKey()).longValue(),input.role(),input.content(),input.mode(),read(metadata),LocalDateTime.now());
    }
    @Transactional public void delete(long userId,long id) { owned(userId,id);db.update("DELETE FROM ai_session WHERE id=? AND user_id=?",id,userId); }
    private SessionView owned(long userId,long id) {
        var rows=db.query("SELECT * FROM ai_session WHERE id=? AND user_id=?",(rs,n)->new SessionView(rs.getLong("id"),rs.getString("title"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime()),id,userId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"会话不存在");
        return rows.getFirst();
    }
    private Map<String,Object> read(String value) { return value==null?Map.of():json.readValue(value,new TypeReference<>(){}); }
}
