package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TutorHintControllerTest {
    private final TutorHintService service = mock(TutorHintService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TutorHintController(service))
            .setControllerAdvice(new AiExceptionHandler()).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void returnsLayeredHintContractWithoutPromptFields() throws Exception {
        when(service.hint(any())).thenReturn(new TutorHintResponse("问题", 2, "相关证据", true,
                List.of(new TutorHintResponse.HintEvidence("数据流图", "包含", "加工", .9)), true, "GRAPH_EVIDENCE"));
        mvc.perform(post("/api/ai/tutor/hint").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"问题\",\"level\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.hasEvidence").value(true))
                .andExpect(jsonPath("$.evidence[0].subject").value("数据流图"))
                .andExpect(jsonPath("$.nextLevelAvailable").value(true))
                .andExpect(jsonPath("$.prompt").doesNotExist());
    }

    @Test void rejectsInvalidHintLevelBeforeCallingService() throws Exception {
        mvc.perform(post("/api/ai/tutor/hint").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"问题\",\"level\":4}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
