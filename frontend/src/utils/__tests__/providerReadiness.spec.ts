import { describe, expect, it } from 'vitest'
import { providerCanExecute, resolveProviderReadiness } from '@/utils/providerReadiness'

describe('provider readiness', () => {
  it('separates initial loading, unavailable, and missing states', () => {
    expect(resolveProviderReadiness({ ready: false, loadAttempted: false, loading: false, hasActiveProvider: false }))
      .toBe('loading')
    expect(resolveProviderReadiness({ ready: false, loadAttempted: true, loading: false, hasActiveProvider: false }))
      .toBe('unavailable')
    expect(resolveProviderReadiness({ ready: true, loadAttempted: true, loading: false, hasActiveProvider: false }))
      .toBe('missing')
  })

  it('keeps a previously loaded active Provider usable after refresh failure', () => {
    const readiness = resolveProviderReadiness({
      ready: false,
      loadAttempted: true,
      loading: false,
      hasActiveProvider: true
    })

    expect(readiness).toBe('cached')
    expect(providerCanExecute(readiness)).toBe(true)
    expect(providerCanExecute('unavailable')).toBe(false)
  })
})
