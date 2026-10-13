package com.flowforge.ai.service;

import com.flowforge.ai.dto.OpenAiTaskResult;

import java.util.Arrays;
import java.util.stream.Collectors;

final class FlowNodeArtifactPayloads {

    private FlowNodeArtifactPayloads() {
    }

    static String providerResult(OpenAiTaskResult result) {
        if (result == null) {
            return null;
        }
        String summary = result.summary() == null ? "" : Arrays.stream(result.summary().split("\\R", -1))
                .map(line -> "> " + line)
                .collect(Collectors.joining("\n"));
        String body = result.result() == null ? "" : result.result();
        return "## 摘要\n\n" + summary + "\n\n## 结果\n\n" + body;
    }
}
