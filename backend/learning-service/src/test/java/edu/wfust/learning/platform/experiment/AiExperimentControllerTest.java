package edu.wfust.learning.platform.experiment;

import edu.wfust.learning.platform.entity.AiExperimentEntity;
import edu.wfust.learning.platform.experimenttask.ExperimentTaskService;
import edu.wfust.learning.platform.mapper.AiExperimentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AiExperimentControllerTest.Config.class)
@WebAppConfiguration
public class AiExperimentControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @EnableWebSecurity
    static class Config {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        AiExperimentMapper aiExperimentMapper() {
            return mock(AiExperimentMapper.class);
        }

        @Bean
        AiComparisonClient aiComparisonClient() {
            return mock(AiComparisonClient.class);
        }

        @Bean
        ExperimentMetrics experimentMetrics() {
            return mock(ExperimentMetrics.class);
        }

        @Bean
        ExperimentTaskService experimentTaskService() {
            return mock(ExperimentTaskService.class);
        }

        @Bean
        ExperimentExport experimentExport() {
            return mock(ExperimentExport.class);
        }

        @Bean
        AiExperimentController aiExperimentController(AiExperimentMapper mapper, ObjectMapper json,
                                                     AiComparisonClient ai, ExperimentMetrics metrics,
                                                     ExperimentTaskService taskService, ExperimentExport exporter) {
            return new AiExperimentController(mapper, json, ai, metrics, taskService, exporter);
        }
    }

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AiExperimentMapper experimentMapper;

    @Autowired
    private AiComparisonClient comparisonClient;

    @Autowired
    private ExperimentTaskService taskServiceProxy;

    private ExperimentTaskService taskService;

    @Autowired
    private ExperimentMetrics experimentMetrics;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        taskService = org.springframework.test.util.AopTestUtils.getTargetObject(taskServiceProxy);
        reset(experimentMapper, comparisonClient, taskService, experimentMetrics);
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    void studentWithoutTaskIdIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/experiments/run")
                        .with(user("101").roles("STUDENT"))
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "自由输入问题"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(comparisonClient);
    }

    @Test
    void studentIgnoresSpoofedQuestionAndRubricAndDoesNotExposeRubricInResponse() throws Exception {
        // 服务端保存的权威真实任务
        when(taskService.getAssignedRunSpec(101L, 9L)).thenReturn(new ExperimentTaskService.AssignedRunSpec(
                31L, 9L, "服务端权威问题内容", "服务端机密参考答案", List.of("服务端期望实体"), "METHODS", false
        ));
        when(taskService.bindRun(101L, 9L)).thenReturn(31L);

        when(comparisonClient.compare(eq("服务端权威问题内容"), eq(false), anyString())).thenReturn(List.of(
                Map.of("resultKey", "DIRECT", "mode", "DIRECT", "answer", "权威直答")
        ));

        // 模拟 mapper.insert 时赋 ID
        doAnswer(inv -> {
            AiExperimentEntity entity = inv.getArgument(0);
            entity.setId(88L);
            return 1;
        }).when(experimentMapper).insert(any(AiExperimentEntity.class));

        // 模拟 selectById
        AiExperimentEntity saved = new AiExperimentEntity();
        saved.setId(88L);
        saved.setUserId(101L);
        saved.setTaskId(9L);
        saved.setAssignmentId(31L);
        saved.setQuestion("服务端权威问题内容");
        saved.setResultsJson("[{\"mode\":\"DIRECT\",\"resultKey\":\"DIRECT\",\"answer\":\"权威直答\"}]");
        saved.setRatingsJson("{}");
        saved.setEvaluationJson("{\"source\":\"SERVER_AI_SERVICE\",\"experimentType\":\"METHODS\",\"referenceAnswer\":\"服务端机密参考答案\",\"expectedEntities\":[\"服务端期望实体\"],\"metricsByMode\":{}}");
        when(experimentMapper.selectById(88L)).thenReturn(saved);

        // 学生发送带有伪造问题、伪造参考答案与伪造实体的请求
        mvc.perform(post("/api/experiments/run")
                        .with(user("101").roles("STUDENT"))
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 9,
                                  "question": "学生伪造问题",
                                  "referenceAnswer": "学生伪造答案",
                                  "expectedEntities": ["伪造实体"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question").value("服务端权威问题内容"))
                .andExpect(jsonPath("$.evaluation.referenceAnswer").doesNotExist())
                .andExpect(jsonPath("$.evaluation.expectedEntities").doesNotExist());

        // 验证 AI 调用使用了服务端权威问题，而非学生伪造的问题
        verify(comparisonClient).compare(eq("服务端权威问题内容"), eq(false), anyString());
    }

    @Test
    void adminPersonalRunRequiresQuestionAndReturnsRubric() throws Exception {
        // ADMIN 缺少 question 时应抛 400
        mvc.perform(post("/api/experiments/run")
                        .with(user("1").roles("ADMIN"))
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // ADMIN 正常自由提问，提供 referenceAnswer
        when(comparisonClient.compare(eq("管理员自定义实验问题"), eq(false), anyString())).thenReturn(List.of(
                Map.of("resultKey", "DIRECT", "mode", "DIRECT", "answer", "管理员回答")
        ));

        doAnswer(inv -> {
            AiExperimentEntity entity = inv.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(experimentMapper).insert(any(AiExperimentEntity.class));

        AiExperimentEntity saved = new AiExperimentEntity();
        saved.setId(99L);
        saved.setUserId(1L);
        saved.setQuestion("管理员自定义实验问题");
        saved.setResultsJson("[{\"mode\":\"DIRECT\",\"resultKey\":\"DIRECT\",\"answer\":\"管理员回答\"}]");
        saved.setRatingsJson("{}");
        saved.setEvaluationJson("{\"source\":\"SERVER_AI_SERVICE\",\"experimentType\":\"METHODS\",\"referenceAnswer\":\"管理员提供的参考答案\",\"expectedEntities\":[\"实体\"],\"metricsByMode\":{}}");
        when(experimentMapper.selectById(99L)).thenReturn(saved);

        mvc.perform(post("/api/experiments/run")
                        .with(user("1").roles("ADMIN"))
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "管理员自定义实验问题",
                                  "referenceAnswer": "管理员提供的参考答案",
                                  "expectedEntities": ["实体"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question").value("管理员自定义实验问题"))
                .andExpect(jsonPath("$.evaluation.referenceAnswer").value("管理员提供的参考答案"))
                .andExpect(jsonPath("$.evaluation.expectedEntities[0]").value("实体"));
    }

    @Test
    void studentIsForbiddenFromAdminEndpoints() throws Exception {
        // GET /api/experiments
        mvc.perform(get("/api/experiments").with(user("101").roles("STUDENT")))
                .andExpect(status().isForbidden());

        // GET /api/experiments/export
        mvc.perform(get("/api/experiments/export").with(user("101").roles("STUDENT")))
                .andExpect(status().isForbidden());

        // GET /api/experiments/export/json
        mvc.perform(get("/api/experiments/export/json").with(user("101").roles("STUDENT")))
                .andExpect(status().isForbidden());

        // PUT /api/experiments/88/ratings
        mvc.perform(put("/api/experiments/88/ratings")
                        .with(user("101").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratings\":{\"DIRECT\":5}}"))
                .andExpect(status().isForbidden());
    }
}
