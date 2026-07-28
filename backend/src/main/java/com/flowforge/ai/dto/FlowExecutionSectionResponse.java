package com.flowforge.ai.dto;

/**
 * One ordered contribution to the single Provider request compiled for a Flow run.
 */
public record FlowExecutionSectionResponse(
        String kind,
        String nodeId,
        String title,
        String content
) {
}
