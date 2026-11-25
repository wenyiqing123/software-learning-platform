package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.KnowledgeServiceUnavailableException;
import edu.wfust.learning.ai.security.AiJwtFilter;
import edu.wfust.learning.ai.security.AiJwtService;
import edu.wfust.learning.ai.security.AiSecurityConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeynmanControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private FeynmanEvaluationService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        service = context.getBean(FeynmanEvaluationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void enforcesStudentOnlyAccessAcrossAnonymousAdminAndStudentRequests() throws Exception {
        String validBody = """
                {"knowledgePoint":"需求分析","studentExplanation":"需求分析通过调研业务目标来确定软件需要实现的功能和约束。"}
                """;

        // 未携带凭证 -> 401
        mvc.perform(post("/api/ai/feynman/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        // ADMIN 角色 -> 403
        mvc.perform(post("/api/ai/feynman/evaluate")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);

        // STUDENT 角色 -> 200 且不泄露任何后台系统提示词字段
        when(service.evaluate(any())).thenReturn(new FeynmanEvaluateResponse(
                "需求分析",
                List.of("指出了明确功能需求的目标"),
                List.of(new FeynmanEvaluateResponse.TripleEvidence("需求分析", "输出", "软件需求规格说明书", 0.92)),
                List.of(),
                "需求分析完成后会产出哪份核心文档？",
                List.of(new FeynmanEvaluateResponse.TripleEvidence("需求分析", "输出", "软件需求规格说明书", 0.92))
        ));

        mvc.perform(post("/api/ai/feynman/evaluate")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgePoint").value("需求分析"))
                .andExpect(jsonPath("$.coveredPoints[0]").value("指出了明确功能需求的目标"))
                .andExpect(jsonPath("$.missingTriples[0].object").value("软件需求规格说明书"))
                .andExpect(jsonPath("$.evidenceTriples.length()").value(1))
                .andExpect(jsonPath("$.prompt").doesNotExist())
                .andExpect(jsonPath("$.systemPrompt").doesNotExist());
    }

    @Test
    void returnsBadRequestOnInvalidParamsAndServiceUnavailableWhenGraphOffline() throws Exception {
        mvc.perform(post("/api/ai/feynman/evaluate")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgePoint\":\"需求分析\",\"studentExplanation\":\"太短了\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AI_REQUEST"));

        mvc.perform(post("/api/ai/feynman/evaluate")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgePoint\":\"\",\"studentExplanation\":\"这段话足够十个字符以上但知识点为空\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);

        when(service.evaluate(any())).thenThrow(new KnowledgeServiceUnavailableException("Bridge unavailable", null));
        mvc.perform(post("/api/ai/feynman/evaluate")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgePoint\":\"需求分析\",\"studentExplanation\":\"需求分析用于收集并验证业务各方对系统的要求。\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_UNAVAILABLE"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(AiSecurityConfig.class)
    static class TestConfig {
        @Bean
        AiJwtService aiJwtService() {
            AiJwtService jwt = mock(AiJwtService.class);
            when(jwt.parse("student-token")).thenReturn(
                    Jwts.claims().subject("9").add("role", "STUDENT").build());
            when(jwt.parse("admin-token")).thenReturn(
                    Jwts.claims().subject("1").add("role", "ADMIN").build());
            return jwt;
        }

        @Bean
        AiJwtFilter aiJwtFilter(AiJwtService jwtService) {
            return new AiJwtFilter(jwtService);
        }

        @Bean
        FeynmanEvaluationService feynmanEvaluationService() {
            return mock(FeynmanEvaluationService.class);
        }

        @Bean
        FeynmanController feynmanController(FeynmanEvaluationService service) {
            return new FeynmanController(service);
        }

        @Bean
        AiExceptionHandler aiExceptionHandler() {
            return new AiExceptionHandler();
        }
    }
}
