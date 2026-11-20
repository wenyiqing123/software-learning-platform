package edu.wfust.learning.platform.practice;

import edu.wfust.learning.platform.mapper.AdaptivePracticeMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdaptivePracticeServiceTest {
    private final AdaptivePracticeMapper mapper=mock(AdaptivePracticeMapper.class);
    private final AdaptivePracticeService service=new AdaptivePracticeService(mapper,JsonMapper.builder().build());
    @Test void selectsDueWrongAndWeakQuestionsWithReasons(){
        var due=new AdaptivePracticeMapper.Candidate();due.setQuestionId(2L);due.setStem("复习题");due.setKnowledgePointId(1L);due.setKnowledgePointName("数据流图");due.setMastery(20.0);due.setWrongCount(2);due.setDue(true);due.setDifficulty(3);
        var weak=new AdaptivePracticeMapper.Candidate();weak.setQuestionId(1L);weak.setStem("薄弱题");weak.setKnowledgePointId(1L);weak.setKnowledgePointName("数据流图");weak.setMastery(10.0);weak.setWrongCount(0);weak.setDue(false);weak.setDifficulty(2);
        when(mapper.candidates(7L,null,30)).thenReturn(List.of(weak,due));
        var result=service.recommend(7,null,10);
        assertThat(result).extracting(AdaptivePracticeService.Item::questionId).containsExactly(2L,1L);
        assertThat(result.get(0).reason()).contains("复习时间");
    }
    @Test void rejectsUnsafeLimit(){assertThatThrownBy(()->service.recommend(7,null,31)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
}
