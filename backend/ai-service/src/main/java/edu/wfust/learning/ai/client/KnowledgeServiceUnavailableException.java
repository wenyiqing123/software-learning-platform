package edu.wfust.learning.ai.client;

public class KnowledgeServiceUnavailableException extends RuntimeException {
    public KnowledgeServiceUnavailableException(String message) { super(message); }
    public KnowledgeServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
}
