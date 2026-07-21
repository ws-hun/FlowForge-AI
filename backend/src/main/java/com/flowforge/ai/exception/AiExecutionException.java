package com.flowforge.ai.exception;

public class AiExecutionException extends IllegalStateException {

    private final String provider;
    private final String model;

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
}
