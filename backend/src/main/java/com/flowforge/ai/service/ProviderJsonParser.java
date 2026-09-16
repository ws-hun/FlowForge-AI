package com.flowforge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a JSON value from provider text without trusting markdown or prose
 * around the model response.
 */
final class ProviderJsonParser {

    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "```(?:json|javascript|js)?\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private ProviderJsonParser() {
    }

    static JsonNode parse(ObjectMapper objectMapper, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String normalized = removeBom(text.trim());
        JsonNode direct = read(objectMapper, normalized);
        if (direct != null) {
            return direct;
        }

        JsonNode fallback = null;
        Matcher fencedMatcher = FENCED_BLOCK.matcher(normalized);
        while (fencedMatcher.find()) {
            JsonNode fenced = read(objectMapper, removeBom(fencedMatcher.group(1).trim()));
            if (fenced != null) {
                fallback = preferTaskShape(fallback, fenced);
                if (hasTaskShape(fenced)) {
                    return fenced;
                }
            }
        }

        for (String candidate : balancedCandidates(normalized)) {
            JsonNode parsed = read(objectMapper, candidate);
            if (parsed == null) {
                continue;
            }
            fallback = preferTaskShape(fallback, parsed);
            if (hasTaskShape(parsed)) {
                return parsed;
            }
        }
        return fallback;
    }

    private static JsonNode read(ObjectMapper objectMapper, String candidate) {
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean hasTaskShape(JsonNode node) {
        return node != null && node.isObject() && (node.has("summary") || node.has("result"));
    }

    private static JsonNode preferTaskShape(JsonNode current, JsonNode candidate) {
        if (current == null || hasTaskShape(candidate)) {
            return candidate;
        }
        return current;
    }

    private static List<String> balancedCandidates(String text) {
        List<String> candidates = new ArrayList<>();
        for (int start = 0; start < text.length(); start++) {
            char opening = text.charAt(start);
            if (opening != '{' && opening != '[') {
                continue;
            }

            String candidate = scanBalancedValue(text, start, opening);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static String scanBalancedValue(String text, int start, char opening) {
        List<Character> stack = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;

        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }

            if (current == '"') {
                quoted = true;
                continue;
            }
            if (current == '{' || current == '[') {
                stack.add(current);
                continue;
            }
            if (current != '}' && current != ']') {
                continue;
            }
            if (stack.isEmpty() || !matches(stack.get(stack.size() - 1), current)) {
                return null;
            }
            stack.remove(stack.size() - 1);
            if (stack.isEmpty()) {
                return text.substring(start, index + 1);
            }
        }
        return null;
    }

    private static boolean matches(char opening, char closing) {
        return opening == '{' && closing == '}' || opening == '[' && closing == ']';
    }

    private static String removeBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1).trim() : text;
    }
}
