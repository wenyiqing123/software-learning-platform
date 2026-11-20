package edu.wfust.learning.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
public final class AiModelClient {
    private final ModelSettings settings;
    private final ChatModel model;

    @Autowired
    public AiModelClient(ModelSettings settings) {
        this(settings, settings.configured() ? OpenAiChatModel.builder().options(settings.options()).build() : null);
    }
    public AiModelClient(ModelSettings settings, ChatModel model) {
        this.settings = settings;
        this.model = model;
    }
    public ModelSettings settings() { return settings; }
    public void requireConfigured() { settings.requireConfigured(); }
    private Prompt prompt(String text) {
        return prompt(text, null);
    }
    private Prompt prompt(String text, String systemMessage) {
        return prompt(text, systemMessage, settings.options());
    }
    private Prompt prompt(String text, String systemMessage, org.springframework.ai.openai.OpenAiChatOptions options) {
        // A UserMessage preserves Python's braces, newlines and whitespace without template evaluation.
        List<Message> messages = new ArrayList<>();
        if (systemMessage != null && !systemMessage.isBlank()) messages.add(new SystemMessage(systemMessage));
        messages.add(new UserMessage(text));
        return new Prompt(messages, options);
    }
    public ChatResponse call(String text) {
        return call(text, null);
    }
    public ChatResponse call(String text, String systemMessage) {
        requireConfigured();
        try {
            ChatResponse response = model.call(prompt(text, systemMessage));
            if (response == null || response.getResult() == null) throw AiFailure.model(null);
            return response;
        } catch (AiFailure failure) { throw failure; }
        catch (RuntimeException exception) { throw AiFailure.model(exception); }
    }
    /** Allows an internal long-form request to raise only its own completion budget. */
    public ChatResponse call(String text, String systemMessage, int maxTokens) {
        requireConfigured();
        if (maxTokens < 1 || maxTokens > 131072) {
            throw new IllegalArgumentException("maxTokens must be between 1 and 131072");
        }
        try {
            var options = settings.options().mutate().maxTokens(maxTokens).build();
            ChatResponse response = model.call(prompt(text, systemMessage, options));
            if (response == null || response.getResult() == null) throw AiFailure.model(null);
            return response;
        } catch (AiFailure failure) { throw failure; }
        catch (RuntimeException exception) { throw AiFailure.model(exception); }
    }
    public Flux<ChatResponse> stream(String text) {
        return Flux.defer(() -> {
            requireConfigured();
            return model.stream(prompt(text));
        }).timeout(settings.httpTimeout()).onErrorMap(error -> error instanceof AiFailure ? error : AiFailure.model(error));
    }
}
