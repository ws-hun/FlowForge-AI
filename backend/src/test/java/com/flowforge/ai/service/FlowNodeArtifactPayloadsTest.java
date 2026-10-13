package com.flowforge.ai.service;

import com.flowforge.ai.dto.OpenAiTaskResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNodeArtifactPayloadsTest {

    @Test
    void separatesSummaryAndResultIntoStableMarkdownSections() {
        OpenAiTaskResult result = new OpenAiTaskResult(
                "# Not a document heading\nA second summary line",
                "## Detailed result\n\n- Keep the original result structure",
                "{}"
        );

        assertThat(FlowNodeArtifactPayloads.providerResult(result)).isEqualTo(
                "## 摘要\n\n> # Not a document heading\n> A second summary line"
                        + "\n\n## 结果\n\n## Detailed result\n\n- Keep the original result structure"
        );
    }

    @Test
    void returnsNoPayloadWithoutAProviderResult() {
        assertThat(FlowNodeArtifactPayloads.providerResult(null)).isNull();
    }
}
