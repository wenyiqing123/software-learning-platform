package edu.wfust.learning.platform.knowledge;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class KnowledgeControllerTest {
    @Test void graphCollapsesRepeatedRelationsAndIgnoresMalformedTriples(){
        var python=mock(PythonGraphClient.class);
        when(python.retrieve("UML",200)).thenReturn(new PythonGraphClient.PythonGraphResponse("UML",List.of(),List.of(
            new PythonGraphClient.Triple("UML","包含","类图",.8),new PythonGraphClient.Triple("UML","包含","类图",.9),new PythonGraphClient.Triple(null,"坏数据","类图",0)),"prompt",true,"original"));
        var graph=new KnowledgeController(python).graph("UML");
        assertThat(graph.nodes()).hasSize(2);assertThat(graph.edges()).singleElement().satisfies(edge->assertThat(edge.score()).isEqualTo(.9));
        assertThat(graph.evidence()).hasSize(2);
    }
}
