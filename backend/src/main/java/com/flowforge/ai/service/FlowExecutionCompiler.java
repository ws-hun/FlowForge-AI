package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowExecutionSectionResponse;
import com.flowforge.ai.dto.FlowNodeDto;
import com.flowforge.ai.dto.FlowRunSnapshotResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlowExecutionCompiler {

    static final String EXECUTION_MODE = "single-pass";
    static final int PROVIDER_CALL_COUNT = 1;
    static final String COMPILER_VERSION = "flow-compiler-v1";
    private static final Pattern FLOW_VARIABLE_PATTERN = Pattern.compile("\\{[a-zA-Z0-9_\\u4e00-\\u9fa5-]+}");

    Compilation compile(FlowRunSnapshotResponse snapshot) {
        List<FlowExecutionSectionResponse> inputSections = snapshot.nodes().stream()
                .filter(node -> "input".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .filter(node -> !node.content().trim().equals(snapshot.description()))
                .map(node -> compileNodeSection(node, "input-context", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> promptSections = snapshot.nodes().stream()
                .filter(node -> "prompt".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "prompt", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> executionGuidanceSections = snapshot.nodes().stream()
                .filter(node -> "ai-task".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "execution-guidance", snapshot.variableValues()))
                .toList();
        List<FlowExecutionSectionResponse> deliveryFocusSections = snapshot.nodes().stream()
                .filter(node -> "output".equals(node.type()))
                .filter(node -> StringUtils.hasText(node.content()))
                .map(node -> compileNodeSection(node, "delivery-focus", snapshot.variableValues()))
                .toList();

        List<FlowExecutionSectionResponse> sections = new ArrayList<>();
        sections.add(new FlowExecutionSectionResponse(
                "objective",
                null,
                snapshot.title(),
                snapshot.description()
        ));
        sections.addAll(inputSections);
        if (StringUtils.hasText(snapshot.runtimeContext())) {
            sections.add(new FlowExecutionSectionResponse(
                    "runtime-context",
                    null,
                    "本次运行说明",
                    snapshot.runtimeContext()
            ));
        }
        sections.addAll(promptSections);
        sections.addAll(executionGuidanceSections);
        sections.addAll(deliveryFocusSections);
        sections.add(new FlowExecutionSectionResponse(
                "response-contract",
                null,
                "结构化输出",
                "Summary · Key Points · Result · Next Actions"
        ));

        List<String> executionInput = new ArrayList<>();
        executionInput.add("请按下面的 Flow 目标执行 AI 工作流。");
        executionInput.add("");
        executionInput.add("Flow: " + snapshot.title());
        executionInput.add("目标: " + snapshot.description());
        if (!inputSections.isEmpty()) {
            executionInput.add("\n输入节点上下文:\n" + renderNodeSections(inputSections));
        }
        if (StringUtils.hasText(snapshot.runtimeContext())) {
            executionInput.add("\n本次运行上下文:\n" + snapshot.runtimeContext());
        }
        if (!promptSections.isEmpty()) {
            executionInput.add("\n可复用 Prompt 节点:\n" + renderNodeSections(promptSections));
        }
        if (!executionGuidanceSections.isEmpty()) {
            executionInput.add("\n执行指令:\n" + renderNodeSections(executionGuidanceSections));
        }
        if (!deliveryFocusSections.isEmpty()) {
            executionInput.add("\n交付重点:\n" + renderNodeSections(deliveryFocusSections));
        }
        executionInput.add("");
        executionInput.add("请输出：1. Summary 2. Key Points 3. Result 4. Next Actions");
        String compiledInput = String.join("\n", executionInput);
        return new Compilation(
                EXECUTION_MODE,
                PROVIDER_CALL_COUNT,
                COMPILER_VERSION,
                fingerprint(compiledInput),
                compiledInput,
                List.copyOf(sections)
        );
    }

    String fingerprint(String executionInput) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(executionInput.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    List<String> findMissingVariables(FlowRunSnapshotResponse snapshot) {
        Set<String> requiredVariables = new LinkedHashSet<>();
        snapshot.nodes().stream()
                .map(FlowNodeDto::content)
                .filter(StringUtils::hasText)
                .forEach(content -> {
                    Matcher matcher = FLOW_VARIABLE_PATTERN.matcher(content);
                    while (matcher.find()) {
                        requiredVariables.add(matcher.group().substring(1, matcher.group().length() - 1));
                    }
                });

        return requiredVariables.stream()
                .filter(variable -> !StringUtils.hasText(snapshot.variableValues().get(variable)))
                .toList();
    }

    String applyVariables(String content, Map<String, String> values) {
        if (!StringUtils.hasText(content) || values.isEmpty()) {
            return content;
        }

        Matcher matcher = FLOW_VARIABLE_PATTERN.matcher(content);
        StringBuffer compiled = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group().substring(1, matcher.group().length() - 1);
            String value = values.get(variable);
            String replacement = StringUtils.hasText(value) ? value : matcher.group();
            matcher.appendReplacement(compiled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(compiled);
        return compiled.toString();
    }

    private FlowExecutionSectionResponse compileNodeSection(
            FlowNodeDto node,
            String kind,
            Map<String, String> variableValues
    ) {
        return new FlowExecutionSectionResponse(
                kind,
                node.id(),
                node.title(),
                applyVariables(node.content(), variableValues)
        );
    }

    private String renderNodeSections(List<FlowExecutionSectionResponse> sections) {
        return sections.stream()
                .map(section -> "## " + section.title() + "\n" + section.content().trim())
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }

    record Compilation(
            String executionMode,
            int providerCallCount,
            String compilerVersion,
            String executionInputFingerprint,
            String executionInput,
            List<FlowExecutionSectionResponse> sections
    ) {
    }
}
