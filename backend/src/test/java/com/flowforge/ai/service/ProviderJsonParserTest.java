package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsTaskJsonFromProseAndMarkdownFence() {
        JsonNode parsed = ProviderJsonParser.parse(objectMapper, """
                Here is the structured result:
                ```JSON
                {"summary":"Ready {now}","result":{"steps":["one","two"]}}
                ```
                Hope this helps.
                """);

        assertThat(parsed).isNotNull();
        assertThat(parsed.path("summary").asText()).isEqualTo("Ready {now}");
        assertThat(parsed.path("result").path("steps")).hasSize(2);
    }

    @Test
    void findsTheTaskObjectWhenProseContainsOtherJsonLikeText() {
        JsonNode parsed = ProviderJsonParser.parse(objectMapper, """
                The example {not valid JSON} is omitted.
                Final payload: {"summary":"Done","result":"Use the saved flow."}.
                """);

        assertThat(parsed).isNotNull();
        assertThat(parsed.path("summary").asText()).isEqualTo("Done");
        assertThat(parsed.path("result").asText()).isEqualTo("Use the saved flow.");
    }

    @Test
    void removesBomAndAcceptsAJsonArrayWhenItIsTheRootValue() {
        JsonNode parsed = ProviderJsonParser.parse(objectMapper, "\uFEFF[\"first\", {\"second\": true}]");

        assertThat(parsed).isNotNull();
        assertThat(parsed.isArray()).isTrue();
        assertThat(parsed).hasSize(2);
    }

    @Test
    void returnsNullWhenNoBalancedJsonValueCanBeParsed() {
        assertThat(ProviderJsonParser.parse(objectMapper, "The model returned plain text with no JSON."))
                .isNull();
    }
}
