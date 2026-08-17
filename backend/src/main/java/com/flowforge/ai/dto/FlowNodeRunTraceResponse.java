package com.flowforge.ai.dto;

/**
 * Immutable server-authored state for one node in a persisted Flow run trace.
 */
public record FlowNodeRunTraceResponse(
        String nodeId,
        String nodeType,
        String title,
        String status,
        String compiledContent,
        String outputSummary,
        String errorMessage,
        FlowNodeArtifactResponse outputArtifact
) {
    public FlowNodeRunTraceResponse(
            String nodeId,
            String nodeType,
            String title,
            String status,
            String compiledContent,
            String outputSummary,
            String errorMessage
    ) {
        this(nodeId, nodeType, title, status, compiledContent, outputSummary, errorMessage, null);
    }
}
