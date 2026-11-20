package edu.wfust.learning.aitests;

import edu.wfust.learning.ai.security.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiSecurityConfigTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); }

    @Test void errorDispatchPreservesServiceUnavailableInsteadOfBecomingUnauthorized() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)).andExpect(status().isServiceUnavailable());
    }
    @Test void directRequestsToErrorAreStillAuthenticated() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }
    @Test void normalAiRequestsRemainAuthenticated() throws Exception {
        mvc.perform(get("/api/ai/guarded")).andExpect(status().isUnauthorized());
    }
    @Test void matrixHealthIsIntentionallyPublicForLocalStartupChecks() throws Exception {
        mvc.perform(get("/api/ai/matrix-health")).andExpect(status().isOk());
    }

    @org.springframework.boot.test.context.TestConfiguration @EnableWebSecurity @EnableWebMvc @Import(AiSecurityConfig.class)
    static class TestConfiguration {
        @Bean AiJwtFilter jwtFilter() { return new AiJwtFilter(mock(AiJwtService.class)); }
        @Bean ErrorEndpoint endpoint() { return new ErrorEndpoint(); }
    }

    @RestController static class ErrorEndpoint {
        @GetMapping("/error") ResponseEntity<Void> error(HttpServletRequest request) {
            Integer code = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
            return ResponseEntity.status(code == null ? 500 : code).build();
        }
        @GetMapping("/api/ai/guarded") String guarded() { return "guarded"; }
        @GetMapping("/api/ai/matrix-health") String matrixHealth() { return "ok"; }
    }
}
