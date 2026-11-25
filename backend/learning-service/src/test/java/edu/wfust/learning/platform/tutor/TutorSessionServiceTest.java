package edu.wfust.learning.platform.tutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TutorSessionServiceTest {
    TutorSessionService service;
    @BeforeEach void setup() {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:sessions" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE ai_session(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,title VARCHAR(120),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE ai_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,session_id BIGINT,role VARCHAR(20),content CLOB,mode VARCHAR(30),metadata_json CLOB,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(session_id) REFERENCES ai_session(id) ON DELETE CASCADE)");
        service = new TutorSessionService(db, new ObjectMapper());
    }
    @Test void sessionsAndMessagesAreIsolatedBetweenUsers() {
        var s = service.create(11, "需求分析");
        service.append(11, s.id(), new TutorSessionService.MessageInput("USER", "什么是需求分析？", "DIRECT", Map.of()));
        assertThat(service.list(11)).hasSize(1);
        assertThat(service.list(22)).isEmpty();
        assertThat(service.messages(11, s.id())).singleElement().extracting(TutorSessionService.MessageView::content).isEqualTo("什么是需求分析？");
        assertThatThrownBy(() -> service.messages(22, s.id())).isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.append(22, s.id(), new TutorSessionService.MessageInput("ASSISTANT", "伪造", "DIRECT", Map.of()))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void deletingOneSessionPreservesOtherSessions() {
        var a = service.create(11, "A"); var b = service.create(11, "B");
        service.append(11, a.id(), new TutorSessionService.MessageInput("USER", "测试", "DIRECT", Map.of()));
        service.delete(11, a.id());
        assertThat(service.list(11)).extracting(TutorSessionService.SessionView::id).containsExactly(b.id());
        assertThatThrownBy(() -> service.messages(11, a.id())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void renamingSessionUpdatesOnlyOwnedSession() {
        var session = service.create(11, "需求分析");

        var renamed = service.rename(11, session.id(), "数据流图复习");

        assertThat(renamed.title()).isEqualTo("数据流图复习");
        assertThat(service.list(11)).singleElement()
            .extracting(TutorSessionService.SessionView::title)
            .isEqualTo("数据流图复习");
    }

    @Test void renamingSessionRejectsBlankTitle() {
        var session = service.create(11, "需求分析");

        assertThatThrownBy(() -> service.rename(11, session.id(), "  "))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test void renamingAnotherUsersSessionReturnsNotFound() {
        var session = service.create(11, "需求分析");

        assertThatThrownBy(() -> service.rename(22, session.id(), "其他会话"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test void persistsAndReloadsLayeredStepHintMessagesWithMetadata() {
        var session = service.create(11, "加工命名");
        var metadata = Map.<String, Object>of("hint", Map.of("level", 1, "content", "先定位加工实体", "nextLevelAvailable", true));

        service.append(11, session.id(), new TutorSessionService.MessageInput("USER", "加工如何规范命名？", "RANKED", Map.of()));
        service.append(11, session.id(), new TutorSessionService.MessageInput("ASSISTANT", "第1级学习提示：先定位加工实体", "TUTOR_HINT_1", metadata));
        service.append(11, session.id(), new TutorSessionService.MessageInput("ASSISTANT", "第2级学习提示：证据", "TUTOR_HINT_2", metadata));
        service.append(11, session.id(), new TutorSessionService.MessageInput("ASSISTANT", "第3级学习提示：动宾短语", "TUTOR_HINT_3", metadata));

        var loaded = service.messages(11, session.id());
        assertThat(loaded).hasSize(4);
        assertThat(loaded).extracting(TutorSessionService.MessageView::mode)
                .containsExactly("RANKED", "TUTOR_HINT_1", "TUTOR_HINT_2", "TUTOR_HINT_3");
        assertThat(loaded.get(1).metadata()).containsKey("hint");
    }
}
