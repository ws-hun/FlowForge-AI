package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class StructuredResultFormatter {

    private static final Set<String> UPPERCASE_WORDS = Set.of("api", "url", "id", "json", "http", "https");

    private StructuredResultFormatter() {
    }

    static String format(JsonNode value) {
        MarkdownDocument document = new MarkdownDocument();
        appendValue(document, value, 2);
        return document.content();
    }

    private static void appendValue(MarkdownDocument document, JsonNode value, int headingLevel) {
        if (value.isObject()) {
            appendObject(document, value, headingLevel, Set.of());
        } else if (value.isArray()) {
            appendArray(document, value, headingLevel);
        } else {
            document.appendParagraph(scalarText(value));
        }
    }

    private static void appendObject(
            MarkdownDocument document,
            JsonNode object,
            int headingLevel,
            Set<String> omittedKeys
    ) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        object.fields().forEachRemaining(fields::add);
        boolean appended = false;

        for (Map.Entry<String, JsonNode> field : fields) {
            if (omittedKeys.contains(field.getKey())) {
                continue;
            }

            JsonNode fieldValue = field.getValue();
            if (fieldValue.isContainerNode() || isMultilineText(fieldValue)) {
                continue;
            }

            String label = humanizeKey(field.getKey());
            document.appendListItem("**" + label + ":** " + scalarText(fieldValue));
            appended = true;
        }

        for (Map.Entry<String, JsonNode> field : fields) {
            if (omittedKeys.contains(field.getKey())) {
                continue;
            }

            JsonNode fieldValue = field.getValue();
            String label = humanizeKey(field.getKey());
            if (fieldValue.isContainerNode()) {
                document.appendHeading(label, headingLevel);
                appendValue(document, fieldValue, nextHeadingLevel(headingLevel));
                appended = true;
            } else if (isMultilineText(fieldValue)) {
                document.appendHeading(label, headingLevel);
                document.appendParagraph(fieldValue.asText().trim());
                appended = true;
            }
        }

        if (!appended) {
            document.appendListItem("无");
        }
    }

    private static void appendArray(MarkdownDocument document, JsonNode array, int headingLevel) {
        if (array.isEmpty()) {
            document.appendListItem("无");
            return;
        }

        for (int index = 0; index < array.size(); index++) {
            JsonNode item = array.get(index);
            if (item.isObject()) {
                ItemHeading itemHeading = resolveItemHeading(item, index);
                document.appendHeading(itemHeading.text(), headingLevel);
                appendObject(document, item, nextHeadingLevel(headingLevel), itemHeading.omittedKeys());
            } else if (item.isArray()) {
                document.appendHeading("条目 " + (index + 1), headingLevel);
                appendArray(document, item, nextHeadingLevel(headingLevel));
            } else {
                document.appendListItem(scalarText(item));
            }
        }
    }

    private static boolean isMultilineText(JsonNode value) {
        return value.isTextual() && value.asText().contains("\n");
    }

    private static ItemHeading resolveItemHeading(JsonNode item, int index) {
        String method = textValue(item, "method");
        String path = textValue(item, "path");
        if (!method.isBlank() && !path.isBlank()) {
            return new ItemHeading(method.toUpperCase(Locale.ROOT) + " " + path, Set.of("method", "path"));
        }

        for (String key : new String[]{"title", "name", "id"}) {
            String value = textValue(item, key);
            if (!value.isBlank()) {
                return new ItemHeading(value, Set.of(key));
            }
        }
        return new ItemHeading("条目 " + (index + 1), Set.of());
    }

    private static String textValue(JsonNode object, String key) {
        JsonNode value = object.path(key);
        return value.isValueNode() && !value.isNull() ? value.asText().trim() : "";
    }

    private static String humanizeKey(String key) {
        String spaced = key
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (spaced.isBlank()) {
            return "字段";
        }

        String[] words = spaced.split("\\s+");
        for (int index = 0; index < words.length; index++) {
            String lower = words[index].toLowerCase(Locale.ROOT);
            if (UPPERCASE_WORDS.contains(lower)) {
                words[index] = lower.toUpperCase(Locale.ROOT);
            } else if (index == 0 && !words[index].isBlank()) {
                words[index] = Character.toUpperCase(words[index].charAt(0)) + words[index].substring(1);
            }
        }
        return String.join(" ", words);
    }

    private static String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "未提供";
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isBlank() ? "未提供" : text;
        }
        return value.asText();
    }

    private static int nextHeadingLevel(int currentLevel) {
        return Math.min(currentLevel + 1, 4);
    }

    private record ItemHeading(String text, Set<String> omittedKeys) {
        private ItemHeading {
            omittedKeys = Set.copyOf(omittedKeys);
        }
    }

    private static final class MarkdownDocument {

        private final StringBuilder content = new StringBuilder();
        private BlockType lastBlockType;

        private void appendHeading(String text, int level) {
            appendBlock("#".repeat(Math.max(2, Math.min(level, 4))) + " " + text, BlockType.HEADING);
        }

        private void appendParagraph(String text) {
            appendBlock(text, BlockType.PARAGRAPH);
        }

        private void appendListItem(String text) {
            appendSeparator(lastBlockType == BlockType.LIST ? "\n" : "\n\n");
            content.append("- ").append(text);
            lastBlockType = BlockType.LIST;
        }

        private void appendBlock(String text, BlockType blockType) {
            appendSeparator("\n\n");
            content.append(text);
            lastBlockType = blockType;
        }

        private void appendSeparator(String separator) {
            if (!content.isEmpty()) {
                content.append(separator);
            }
        }

        private String content() {
            return content.toString().trim();
        }
    }

    private enum BlockType {
        HEADING,
        PARAGRAPH,
        LIST
    }
}
