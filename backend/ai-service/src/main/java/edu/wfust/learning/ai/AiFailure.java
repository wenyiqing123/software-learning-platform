package edu.wfust.learning.ai;

import org.springframework.http.HttpStatus;

/** Only fixed, public messages belong here; upstream bodies must never be returned. */
public class AiFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean configured;

    public AiFailure(HttpStatus status, String code, String message, boolean configured) {
        this(status, code, message, configured, null);
    }
    public AiFailure(HttpStatus status, String code, String message, boolean configured, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.configured = configured;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public boolean configured() { return configured; }
    public static AiFailure model(Throwable cause) {
        return new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE",
                "模型服务暂不可用，请检查模型配置后重试。", true, cause);
    }
    public static AiFailure knowledge(Throwable cause) {
        return new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_UNAVAILABLE",
                "知识检索服务暂不可用，请稍后重试。", true, cause);
    }
    public static AiFailure comparisonTimeout() {
        return new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "COMPARISON_TIMEOUT",
                "模式对比已达到总时间上限，未完成的结果已取消。", true);
    }
    public static AiFailure ablationLimit() {
        return new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "ABLATION_UNSUPPORTED",
                "未截断的知识结果超过安全限制，当前请求不支持排序消融；未生成或截断实验依据。", true);
    }
    public static AiFailure interactiveMode() {
        return new AiFailure(HttpStatus.BAD_REQUEST, "EXPERIMENT_ONLY_MODE",
                "消融模式仅支持 compare 的 includeAblations=true。", true);
    }
}
