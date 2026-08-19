package com.orbitworkbench.ai.application;

public record AiStreamEvent(
        String type,
        String text,
        Integer inputTokenCount,
        Integer outputTokenCount,
        String providerRequestId,
        boolean done,
        String errorCode,
        String errorSummary,
        AiUsage usage
) {
    public AiStreamEvent(String type,
                          String text,
                          Integer inputTokenCount,
                          Integer outputTokenCount,
                          String providerRequestId,
                          boolean done,
                          String errorCode,
                          String errorSummary) {
        this(type, text, inputTokenCount, outputTokenCount, providerRequestId, done,
                errorCode, errorSummary,
                inputTokenCount == null && outputTokenCount == null
                        ? null : new AiUsage(inputTokenCount, outputTokenCount, null));
    }

    public AiStreamEvent(String type,
                         String text,
                         AiUsage usage,
                         String providerRequestId,
                         boolean done) {
        this(type, text,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                providerRequestId, done, null, null, usage);
    }

    public static AiStreamEvent started() {
        return new AiStreamEvent("run.started", null, null, null, null, false, null, null, null);
    }

    public static AiStreamEvent delta(String text) {
        return new AiStreamEvent("output.text.delta", text, null, null, null, false, null, null, null);
    }

    public static AiStreamEvent completed() {
        return new AiStreamEvent("output.text.completed", null, null, null, null, true, null, null, null);
    }
}
