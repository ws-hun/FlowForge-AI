package com.flowforge.ai.controller;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ApiErrorMessageResolver {

    private static final Pattern NODE_SIZE = Pattern.compile("^node (id|type|title|description|content) must be less than (\\d+) characters$");
    private static final Pattern FIELD_REQUIRED = Pattern.compile("^(?:[A-Za-z][\\w.\\[\\]]*)?:?\\s*([A-Za-z][\\w]*) is required$");
    private static final Pattern FIELD_SIZE = Pattern.compile("^(?:[A-Za-z][\\w.\\[\\]]*)?:?\\s*([A-Za-z][\\w]*) must be less than (\\d+) characters$");
    private static final Pattern FIELD_EMAIL = Pattern.compile("^(?:[A-Za-z][\\w.\\[\\]]*)?:?\\s*([A-Za-z][\\w]*) must be valid$");
    private static final Map<String, String> EXACT_MESSAGES = Map.ofEntries(
            Map.entry("Invalid request", "请求内容无效"),
            Map.entry("Invalid request body", "请求体格式无效"),
            Map.entry("Internal server error", "服务暂时不可用，请稍后重试"),
            Map.entry("Resource not found", "找不到请求的资源"),
            Map.entry("Flow not found", "找不到这个 Flow"),
            Map.entry("Flow version not found", "找不到这个 Flow 修订"),
            Map.entry("Prompt not found", "找不到这个 Prompt"),
            Map.entry("Prompt version not found", "找不到这个 Prompt 修订"),
            Map.entry("Task run not found", "找不到这次运行记录"),
            Map.entry("Flow node artifact not found", "找不到这个 Flow 节点产物"),
            Map.entry("API key config not found", "找不到这个 Provider 配置"),
            Map.entry("Source Flow not found", "找不到来源 Flow"),
            Map.entry("Source Flow node not found", "找不到来源 Flow 节点"),
            Map.entry("Source Flow revision not found", "找不到来源 Flow 修订"),
            Map.entry("revision is required", "需要提供 revision"),
            Map.entry("revision must be zero or greater", "revision 必须大于等于 0"),
            Map.entry("input is required", "请输入任务内容"),
            Map.entry("continuation input is required", "请输入继续执行的任务内容"),
            Map.entry("Only failed task runs can be recovered", "只有失败的运行记录可以恢复"),
            Map.entry("A task run can only use one source", "一次运行只能使用一个来源")
    );

    private ApiErrorMessageResolver() {
    }

    static String resolve(String message) {
        if (message == null || message.isBlank()) {
            return "请求处理失败，请稍后重试";
        }

        String exactMessage = EXACT_MESSAGES.get(message);
        if (exactMessage != null) {
            return exactMessage;
        }

        Matcher nodeSize = NODE_SIZE.matcher(message);
        if (nodeSize.matches()) {
            String field = switch (nodeSize.group(1)) {
                case "id" -> "节点 ID";
                case "type" -> "节点类型";
                case "title" -> "节点标题";
                case "description" -> "节点说明";
                default -> "节点内容";
            };
            return field + "不能超过 " + nodeSize.group(2) + " 个字符";
        }

        Matcher fieldRequired = FIELD_REQUIRED.matcher(message);
        if (fieldRequired.matches()) {
            return fieldLabel(fieldRequired.group(1)) + "不能为空";
        }

        Matcher fieldSize = FIELD_SIZE.matcher(message);
        if (fieldSize.matches()) {
            return fieldLabel(fieldSize.group(1)) + "不能超过 " + fieldSize.group(2) + " 个字符";
        }

        Matcher fieldEmail = FIELD_EMAIL.matcher(message);
        if (fieldEmail.matches()) {
            return fieldLabel(fieldEmail.group(1)) + "格式不正确";
        }

        return message;
    }

    private static String fieldLabel(String field) {
        return switch (field) {
            case "title" -> "标题";
            case "description" -> "说明";
            case "content" -> "内容";
            case "category" -> "分类";
            case "input" -> "任务内容";
            case "email" -> "邮箱";
            case "password", "currentPassword", "newPassword" -> "密码";
            case "displayName" -> "显示名称";
            case "provider" -> "Provider";
            case "apiKey" -> "API Key";
            case "baseUrl" -> "Base URL";
            case "model" -> "模型";
            case "revision" -> "revision";
            default -> field;
        };
    }
}
