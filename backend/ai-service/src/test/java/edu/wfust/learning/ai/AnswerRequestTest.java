package edu.wfust.learning.ai;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AnswerRequestTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void existingBodiesStillDefaultToRankedWithEmptyHistory() {
        AnswerRequest request = json.readValue("{\"question\":\"它是什么\"}", AnswerRequest.class);
        assertThat(request.mode()).isEqualTo(QaMode.RANKED);
        assertThat(request.historyMessages()).isEmpty();
        assertThat(request.historyEntities()).isEmpty();
    }

    @Test void followupHistoryIsAcceptedAndListsAreImmutable() {
        AnswerRequest request = json.readValue("{\"question\":\"它是什么\",\"mode\":\"TRIPLES\","
                + "\"historyMessages\":[\"需求分析\"],\"historyEntities\":[\"软件需求规格说明书\"],\"entities\":[\"加工\"]}", AnswerRequest.class);
        assertThat(request.historyMessages()).containsExactly("需求分析");
        assertThat(request.historyEntities()).containsExactly("软件需求规格说明书");
        assertThat(request.entities()).containsExactly("加工");
        assertThatThrownBy(() -> request.historyMessages().add("edit")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.entities().add("edit")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void historyCountsLengthsAndBlankValuesAreBounded() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new AnswerRequest("question", QaMode.RANKED,
                    List.of("prior"), List.of("entity")))).isEmpty();
            for (AnswerRequest request : List.of(
                    new AnswerRequest("question", QaMode.RANKED, Collections.nCopies(11, "prior"), List.of()),
                    new AnswerRequest("question", QaMode.RANKED, List.of("x".repeat(2001)), List.of()),
                    new AnswerRequest("question", QaMode.RANKED, List.of(" "), List.of()),
                    new AnswerRequest("question", QaMode.RANKED, List.of(), Collections.nCopies(21, "entity")),
                    new AnswerRequest("question", QaMode.RANKED, List.of(), List.of("x".repeat(201))),
                    new AnswerRequest("question", QaMode.RANKED, List.of(), List.of(" ")))) {
                assertThat(validator.validate(request)).isNotEmpty();
            }
        }
    }
}
