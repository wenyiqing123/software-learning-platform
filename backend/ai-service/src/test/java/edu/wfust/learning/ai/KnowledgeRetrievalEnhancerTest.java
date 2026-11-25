package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonEntity;
import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalEnhancerTest {
    @Test
    void expandsBridgeSubEntitiesAndDeprioritizesSubsumedPrefixNoiseForDfdProcessNaming() {
        PythonKnowledgeClient client = mock(PythonKnowledgeClient.class);
        String question = "数据流图中的加工应该如何规范命名？";

        List<PythonTriple> firstTriples = List.of(
                new PythonTriple("数据流", "命名规则", "必须采用名词或名词短语，如用户信息、交易凭证", 0.7110),
                new PythonTriple("数据流", "定义", "数据在系统内传播的路径", 0.5568),
                new PythonTriple("加工", "包含", "加工命名", 0.7908),
                new PythonTriple("数据流图DFD", "绘制注意事项2", "加工的命名", 0.7757),
                new PythonTriple("加工", "定义", "对数据进行处理", 0.6912),
                new PythonTriple("加工", "包含", "加工编号", 0.6797)
        );
        PythonKnowledgeResponse first = new PythonKnowledgeResponse(
                question,
                List.of(
                        new PythonEntity("数据流", "数据流", "dictionary", 1.0),
                        new PythonEntity("加工", "加工", "dictionary", 1.0),
                        new PythonEntity("数据流图DFD", "数据流图DFD", "request", 1.0)
                ),
                firstTriples,
                "first-prompt",
                true,
                "template"
        );

        List<PythonTriple> secondTriples = List.of(
                new PythonTriple("数据流", "命名规则", "必须采用名词或名词短语，如用户信息、交易凭证", 0.7110),
                new PythonTriple("加工", "包含", "加工命名", 0.7908),
                new PythonTriple("数据流图DFD", "绘制注意事项2", "加工的命名", 0.7757),
                new PythonTriple("加工的命名", "规则", "应使用动宾短语描述处理逻辑", 0.7351),
                new PythonTriple("加工命名", "语法结构", "必须采用动词+宾语的动宾短语形式，如校验权限、生成报表", 0.6767),
                new PythonTriple("加工", "定义", "对数据进行处理", 0.6912)
        );
        PythonKnowledgeResponse second = new PythonKnowledgeResponse(
                question,
                first.entities(),
                secondTriples,
                "second-prompt-with-2hop",
                true,
                "template"
        );

        when(client.retrieve(eq(question), eq(List.of("数据流图DFD")), eq(List.of()), eq(List.of())))
                .thenReturn(first);
        when(client.retrieve(eq(question),
                argThat(list -> list.containsAll(List.of("数据流图DFD", "加工命名", "加工的命名")) && !list.contains("加工编号")),
                eq(List.of()), eq(List.of())))
                .thenReturn(second);

        KnowledgeRetrievalEnhancer enhancer = new KnowledgeRetrievalEnhancer(client);
        PythonKnowledgeResponse result = enhancer.retrieve(question, List.of(), List.of(), List.of(), true);

        assertThat(result).isNotNull();
        assertThat(result.prompt()).isEqualTo("second-prompt-with-2hop");
        List<String> top5Subjects = result.triples().stream().limit(5).map(PythonTriple::subject).toList();
        assertThat(top5Subjects).contains("加工的命名", "加工命名", "加工");
        assertThat(top5Subjects).doesNotContain("数据流");
        verify(client, times(2)).retrieve(eq(question), anyList(), anyList(), anyList());
    }

    @Test
    void keepsStandaloneEntityHighPriorityWhenQuestionDirectlyAsksAboutIt() {
        PythonKnowledgeClient client = mock(PythonKnowledgeClient.class);
        String question = "数据流应该如何规范命名？";
        PythonKnowledgeResponse response = new PythonKnowledgeResponse(
                question,
                List.of(new PythonEntity("数据流", "数据流", "dictionary", 1.0)),
                List.of(
                        new PythonTriple("数据流", "定义", "数据在系统内传播的路径", 0.55),
                        new PythonTriple("数据流", "命名规则", "必须采用名词或名词短语", 0.78)
                ),
                "prompt",
                true,
                "template"
        );
        when(client.retrieve(eq(question), eq(List.of()), eq(List.of()))).thenReturn(response);

        KnowledgeRetrievalEnhancer enhancer = new KnowledgeRetrievalEnhancer(client);
        PythonKnowledgeResponse result = enhancer.retrieve(question, List.of(), List.of(), List.of(), false);

        assertThat(result).isNotNull();
        assertThat(result.triples().getFirst().predicate()).isEqualTo("命名规则");
        assertThat(result.triples().getFirst().score()).isEqualTo(0.78);
        verify(client, times(1)).retrieve(eq(question), eq(List.of()), eq(List.of()));
    }
}
