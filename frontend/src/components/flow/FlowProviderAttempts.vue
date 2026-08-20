<template>
  <section class="flow-provider-attempts">
    <header>
      <div>
        <span>Provider Attempts</span>
        <strong>真实调用尝试</strong>
      </div>
      <small>{{ attempts.length }} 次已记录</small>
    </header>

    <ol>
      <li v-for="attempt in attempts" :key="attempt.id" :class="attempt.status">
        <div class="flow-provider-attempt-index">{{ attempt.attemptNumber }}</div>
        <div class="flow-provider-attempt-body">
          <header>
            <div>
              <small>{{ flowProviderAttemptTriggerLabel(attempt.triggerType) }}</small>
              <strong>{{ flowProviderCallSource(attempt) || 'Provider 来源未报告' }}</strong>
            </div>
            <em>{{ flowProviderCallStatusLabel(attempt) }}</em>
          </header>
          <p>
            <template v-if="flowProviderCallMetrics(attempt)">
              {{ flowProviderCallMetrics(attempt) }}
            </template>
            <template v-else>未报告 Token 与耗时</template>
          </p>
          <p v-if="attempt.errorMessage" class="flow-provider-attempt-error">
            {{ attempt.errorMessage }}
          </p>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { FlowProviderAttempt } from '@/types'
import {
  flowProviderAttemptTriggerLabel,
  flowProviderCallMetrics,
  flowProviderCallSource,
  flowProviderCallStatusLabel
} from '@/utils/flowProviderCall'

defineProps<{
  attempts: FlowProviderAttempt[]
}>()
</script>
