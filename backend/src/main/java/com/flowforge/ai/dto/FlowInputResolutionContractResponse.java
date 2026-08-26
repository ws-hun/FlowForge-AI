package com.flowforge.ai.dto;

import java.util.List;

/**
 * Versioned input resolution contract shared by preview and persisted Flow plans.
 */
public record FlowInputResolutionContractResponse(
        String version,
        String activeResolution,
        List<String> supportedResolutions,
        boolean persistedArtifactEnabled,
        String persistedArtifactActivation
) {
}
