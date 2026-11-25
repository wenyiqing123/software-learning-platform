package edu.wfust.learning.platform.tutor;

import edu.wfust.learning.platform.config.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TutorSessionControllerTest {
    private final TutorSessionService sessions = mock(TutorSessionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TutorSessionController(sessions))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    private final UsernamePasswordAuthenticationToken studentAuth =
            new UsernamePasswordAuthenticationToken("11", "n/a", List.of());

    @Test
    void acceptsLayeredStepHintModesWhenPersistingAssistantMessages() throws Exception {
        for (String mode : List.of("TUTOR_HINT_1", "TUTOR_HINT_2", "TUTOR_HINT_3")) {
            when(sessions.append(eq(11L), eq(7L), any())).thenReturn(
                    new TutorSessionService.MessageView(100L, "ASSISTANT", "提示内容", mode, Map.of(), LocalDateTime.now()));
            mvc.perform(post("/api/tutor/sessions/7/messages")
                            .principal(studentAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ASSISTANT\",\"content\":\"提示内容\",\"mode\":\"" + mode + "\",\"metadata\":{}}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mode").value(mode));
        }
        verify(sessions, times(3)).append(eq(11L), eq(7L), any());
    }

    @Test
    void rejectsUnsupportedModeBeforeCallingService() throws Exception {
        mvc.perform(post("/api/tutor/sessions/7/messages")
                        .principal(studentAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"content\":\"提示内容\",\"mode\":\"UNKNOWN_MODE\",\"metadata\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(sessions);
    }
}
