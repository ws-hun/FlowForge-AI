package com.flowforge.ai.exception;

import java.util.UUID;

public class AiExecutionException extends IllegalStateException {

    private final String provider;
    private final String model;
    private UUID runId;

    public AiExecutionException(String provider, String model, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public UUID getRunId() {
        return runId;
    }

    public AiExecutionException attachRunId(UUID runId) {
        this.runId = runId;
        return this;
    }
}
