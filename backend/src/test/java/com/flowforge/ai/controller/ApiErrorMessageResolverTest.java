package com.flowforge.ai.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorMessageResolverTest {

    @Test
    void localizesCommonResourceAndValidationMessages() {
        assertThat(ApiErrorMessageResolver.resolve("Flow not found")).isEqualTo("找不到这个 Flow");
        assertThat(ApiErrorMessageResolver.resolve("Source Prompt not found")).isEqualTo("找不到来源 Prompt");
        assertThat(ApiErrorMessageResolver.resolve("Flow source must be either Prompt or Flow"))
                .isEqualTo("Flow 只能保留一种来源");
        assertThat(ApiErrorMessageResolver.resolve("Source Prompt must be included as a Prompt node"))
                .isEqualTo("来源 Prompt 必须作为 Prompt 节点加入 Flow");
        assertThat(ApiErrorMessageResolver.resolve("sourcePromptId is required when sourceTaskId is provided"))
                .isEqualTo("从运行创建 Flow 时必须提供来源 Prompt");
        assertThat(ApiErrorMessageResolver.resolve("Failed Task cannot be used as a Flow source"))
                .isEqualTo("失败运行不能用于创建 Flow");
        assertThat(ApiErrorMessageResolver.resolve("Source Prompt does not belong to the source Task"))
                .isEqualTo("来源 Prompt 与运行记录不匹配");
        assertThat(ApiErrorMessageResolver.resolve("revision is required")).isEqualTo("需要提供 revision");
        assertThat(ApiErrorMessageResolver.resolve("input is required")).isEqualTo("请输入任务内容");
    }

    @Test
    void localizesNodeLengthValidationWithoutChangingItsLimit() {
        assertThat(ApiErrorMessageResolver.resolve("node content must be less than 12000 characters"))
                .isEqualTo("节点内容不能超过 12000 个字符");
        assertThat(ApiErrorMessageResolver.resolve("title: title must be less than 120 characters"))
                .isEqualTo("标题不能超过 120 个字符");
    }

    @Test
    void localizesCommonBeanValidationMessages() {
        assertThat(ApiErrorMessageResolver.resolve("title: title is required")).isEqualTo("标题不能为空");
        assertThat(ApiErrorMessageResolver.resolve("email: email must be valid")).isEqualTo("邮箱格式不正确");
    }

    @Test
    void keepsChineseAndUnknownDomainMessagesIntact() {
        assertThat(ApiErrorMessageResolver.resolve("请先完善 Flow 节点")).isEqualTo("请先完善 Flow 节点");
        assertThat(ApiErrorMessageResolver.resolve("Provider returned a custom reason"))
                .isEqualTo("Provider returned a custom reason");
    }

    @Test
    void providesAStableFallbackForAnEmptyExceptionMessage() {
        assertThat(ApiErrorMessageResolver.resolve(null)).isEqualTo("请求处理失败，请稍后重试");
        assertThat(ApiErrorMessageResolver.resolve("  ")).isEqualTo("请求处理失败，请稍后重试");
    }
}
