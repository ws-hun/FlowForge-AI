package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowNodeDto;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FlowDefinitionValidator {

    private static final Map<String, Integer> NODE_ORDER = Map.of(
            "input", 0,
            "prompt", 1,
            "ai-task", 2,
            "output", 3
    );

    public void validate(List<FlowNodeDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Flow 至少需要一个节点");
        }

        Set<String> nodeIds = new HashSet<>();
        int previousOrder = -1;
        int inputCount = 0;
        int aiTaskCount = 0;
        int outputCount = 0;

        for (FlowNodeDto node : nodes) {
            String rawNodeId = node == null || node.id() == null ? "" : node.id();
            String nodeId = rawNodeId.trim();
            if (nodeId.isEmpty()) {
                throw new IllegalArgumentException("Flow 节点 ID 不能为空");
            }
            if (!nodeId.equals(rawNodeId)) {
                throw new IllegalArgumentException("Flow 节点 ID 不能包含首尾空格: " + rawNodeId);
            }
            if (!nodeIds.add(nodeId)) {
                throw new IllegalArgumentException("Flow 节点 ID 重复: " + nodeId);
            }

            String type = node.type() == null ? "" : node.type();
            Integer order = NODE_ORDER.get(type);
            if (order == null) {
                throw new IllegalArgumentException("Flow 节点类型不支持: " + type);
            }
            if (order < previousOrder) {
                throw new IllegalArgumentException("Flow 节点顺序应为 Input、Prompt、AI Task、Output");
            }
            previousOrder = order;

            switch (type) {
                case "input" -> inputCount++;
                case "ai-task" -> aiTaskCount++;
                case "output" -> outputCount++;
                default -> {
                    // Prompt nodes can be repeated and remain optional.
                }
            }
        }

        if (inputCount == 0) {
            throw new IllegalArgumentException("Flow 至少需要一个 Input 节点");
        }
        if (aiTaskCount != 1) {
            throw new IllegalArgumentException("当前 Flow 运行模型需要且只支持一个 AI Task 节点");
        }
        if (outputCount != 1) {
            throw new IllegalArgumentException("当前 Flow 运行模型需要且只支持一个 Output 节点");
        }
    }
}
