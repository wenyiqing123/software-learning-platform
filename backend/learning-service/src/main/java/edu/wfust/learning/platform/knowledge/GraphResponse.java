package edu.wfust.learning.platform.knowledge;

import java.util.List;

public record GraphResponse(List<GraphNode> nodes, List<GraphEdge> edges, List<?> evidence, boolean available) {
    public GraphResponse {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
    public record GraphNode(String id, String label, String category) { }
    public record GraphEdge(String source, String target, String label, double score) { }
}
