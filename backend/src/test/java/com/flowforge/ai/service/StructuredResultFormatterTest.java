package com.flowforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredResultFormatterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void formatsNestedApiObjectsAsAReadableDocument() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {
                  "base_url": "/api/v1",
                  "endpoints": [
                    {
                      "method": "post",
                      "path": "/workflows",
                      "description": "创建工作流",
                      "request_body": {
                        "name": "Launch Flow",
                        "active": true
                      }
                    }
                  ]
                }
                """);

        assertThat(StructuredResultFormatter.format(result))
                .isEqualTo("""
                        - **Base URL:** /api/v1

                        ## Endpoints

                        ### POST /workflows

                        - **Description:** 创建工作流

                        #### Request body

                        - **Name:** Launch Flow
                        - **Active:** true""");
    }

    @Test
    void formatsScalarAndNestedArraysWithoutReturningJsonSyntax() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {
                  "risks": ["Scope drift", "Missing owner"],
                  "groups": [["Design", "Engineering"]],
                  "empty": [],
                  "owner": null
                }
                """);

        String document = StructuredResultFormatter.format(result);

        assertThat(document)
                .contains("## Risks", "- Scope drift", "- Missing owner")
                .contains("## Groups", "### 条目 1", "- Design", "- Engineering")
                .contains("## Empty", "- 无", "- **Owner:** 未提供")
                .doesNotContain("{", "}", "[", "]");
    }

    @Test
    void usesStableLabelsForUnnamedAndNamedObjectItems() throws Exception {
        JsonNode result = objectMapper.readTree("""
                [
                  {"title": "First decision", "detail": "Keep the scope small"},
                  {"id": "risk-2", "severity": 3},
                  {"detail": "Unnamed item"}
                ]
                """);

        assertThat(StructuredResultFormatter.format(result))
                .contains("## First decision", "- **Detail:** Keep the scope small")
                .contains("## risk-2", "- **Severity:** 3")
                .contains("## 条目 3", "- **Detail:** Unnamed item");
    }

    @Test
    void keepsParentMetadataAheadOfNestedSections() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {
                  "details": {"status": "ready"},
                  "owner": "Platform team",
                  "notes": "First line\\nSecond line"
                }
                """);

        String document = StructuredResultFormatter.format(result);

        assertThat(document)
                .startsWith("- **Owner:** Platform team")
                .contains("## Details\n\n- **Status:** ready")
                .contains("## Notes\n\nFirst line\nSecond line");
    }
}
