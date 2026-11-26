package edu.wfust.learning.platform.security;

import edu.wfust.learning.platform.experiment.*;
import edu.wfust.learning.platform.mapper.AiExperimentMapper;
import edu.wfust.learning.platform.experimenttask.ExperimentTaskService;
import edu.wfust.learning.platform.flashcard.FlashcardController;
import edu.wfust.learning.platform.flashcard.FlashcardService;
import edu.wfust.learning.platform.microcourse.MicroLessonService;
import edu.wfust.learning.platform.microcourse.PublishedMicroLessonController;
import edu.wfust.learning.platform.practice.AdaptivePracticeController;
import edu.wfust.learning.platform.practice.AdaptivePracticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(RoleBoundaryMvcTest.Config.class)
@WebAppConfiguration
public class RoleBoundaryMvcTest {

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
        AdaptivePracticeService adaptivePracticeService() {
            return mock(AdaptivePracticeService.class);
        }

        @Bean
        MicroLessonService microLessonService() {
            return mock(MicroLessonService.class);
        }

        @Bean
        AiExperimentController aiExperimentController(AiExperimentMapper mapper, ObjectMapper json,
                                                     AiComparisonClient ai, ExperimentMetrics metrics,
                                                     ExperimentTaskService taskService, ExperimentExport exporter) {
            return new AiExperimentController(mapper, json, ai, metrics, taskService, exporter);
        }

        @Bean
        AdaptivePracticeController adaptivePracticeController(AdaptivePracticeService service) {
            return new AdaptivePracticeController(service);
        }

        @Bean
        PublishedMicroLessonController publishedMicroLessonController(MicroLessonService service) {
            return new PublishedMicroLessonController(service);
        }

        @Bean
        FlashcardService flashcardService() {
            return mock(FlashcardService.class);
        }

        @Bean
        FlashcardController flashcardController(FlashcardService service) {
            return new FlashcardController(service);
        }
    }

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AiExperimentMapper experimentMapper;

    @Autowired
    private AdaptivePracticeService adaptivePracticeServiceProxy;

    @Autowired
    private MicroLessonService microLessonServiceProxy;

    @Autowired
    private FlashcardService flashcardServiceProxy;

    private AdaptivePracticeService adaptivePracticeService;
    private MicroLessonService microLessonService;
    private FlashcardService flashcardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        adaptivePracticeService = AopTestUtils.getUltimateTargetObject(adaptivePracticeServiceProxy);
        microLessonService = AopTestUtils.getUltimateTargetObject(microLessonServiceProxy);
        flashcardService = AopTestUtils.getUltimateTargetObject(flashcardServiceProxy);
        reset(experimentMapper, adaptivePracticeService, microLessonService, flashcardService);
    }

    @Test
    void adminCanReadExperimentsHistory() throws Exception {
        doReturn(List.of()).when(experimentMapper).selectList(any());
        mockMvc.perform(get("/api/experiments").with(user("3").roles("ADMIN")))
                .andExpect(status().isOk());
        verify(experimentMapper).selectList(any());
    }

    @Test
    void studentCannotReadExperimentsHistory() throws Exception {
        mockMvc.perform(get("/api/experiments").with(user("1").roles("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(experimentMapper);
    }

    @Test
    void studentCanReadAdaptivePractice() throws Exception {
        doReturn(List.of()).when(adaptivePracticeService).recommend(eq(1L), any(), anyInt());
        mockMvc.perform(get("/api/adaptive-practice").with(user("1").roles("STUDENT")))
                .andExpect(status().isOk());
        verify(adaptivePracticeService).recommend(eq(1L), any(), anyInt());
    }

    @Test
    void adminCannotReadAdaptivePractice() throws Exception {
        mockMvc.perform(get("/api/adaptive-practice").with(user("3").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adaptivePracticeService);
    }

    @Test
    void studentCanReadPublishedMicroLesson() throws Exception {
        var draft = mock(MicroLessonService.DraftView.class);
        doReturn(draft).when(microLessonService).published(1L, 1L);
        mockMvc.perform(get("/api/courses/1/study/sections/1/micro-lesson").with(user("1").roles("STUDENT")))
                .andExpect(status().isOk());
        verify(microLessonService).published(1L, 1L);
    }

    @Test
    void adminCannotReadPublishedMicroLesson() throws Exception {
        mockMvc.perform(get("/api/courses/1/study/sections/1/micro-lesson").with(user("3").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(microLessonService);
    }

    @Test
    void studentCanAccessFlashcardsAndAdminIsForbidden() throws Exception {
        doReturn(List.of()).when(flashcardService).list(1L, null, false);
        mockMvc.perform(get("/api/flashcards").with(user("1").roles("STUDENT")))
                .andExpect(status().isOk());
        verify(flashcardService).list(1L, null, false);

        mockMvc.perform(get("/api/flashcards").with(user("3").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
