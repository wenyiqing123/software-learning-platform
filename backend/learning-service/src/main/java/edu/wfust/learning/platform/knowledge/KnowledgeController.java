package edu.wfust.learning.platform.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class KnowledgeController {
    private final PythonGraphClient graphClient;
    public KnowledgeController(PythonGraphClient graphClient) { this.graphClient = graphClient; }

    @GetMapping("/graph")
    public GraphResponse graph(@RequestParam @NotBlank @Size(max = 160) String entity) {
        var result = graphClient.retrieve(entity.trim(), 200);
        var nodes = new LinkedHashMap<String, GraphResponse.GraphNode>();
        var edges = result.triples() == null ? List.<PythonGraphClient.Triple>of() : result.triples().stream()
                .filter(t -> t.subject() != null && !t.subject().isBlank() && t.object() != null && !t.object().isBlank() && t.predicate() != null && !t.predicate().isBlank()).toList();
        var unique = new LinkedHashMap<List<String>, GraphResponse.GraphEdge>();
        for (var triple : edges) {
            nodes.putIfAbsent(triple.subject(), new GraphResponse.GraphNode(triple.subject(), triple.subject(), "knowledge"));
            nodes.putIfAbsent(triple.object(), new GraphResponse.GraphNode(triple.object(), triple.object(), "knowledge"));
            List<String> key = List.of(triple.subject(), triple.predicate(), triple.object());
            var edge = new GraphResponse.GraphEdge(triple.subject(), triple.object(), triple.predicate(), Double.isFinite(triple.score()) ? triple.score() : 0);
            unique.merge(key, edge, (oldValue,newValue) -> oldValue.score() >= newValue.score() ? oldValue : newValue);
        }
        List<GraphResponse.GraphEdge> graphEdges = List.copyOf(unique.values());
        return new GraphResponse(List.copyOf(nodes.values()), graphEdges, edges, true);
    }
}
