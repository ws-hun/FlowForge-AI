export type ProviderReadiness = 'loading' | 'unavailable' | 'missing' | 'cached' | 'ready'

export function resolveProviderReadiness(options: {
  ready: boolean
  loadAttempted: boolean
  loading: boolean
  hasActiveProvider: boolean
}): ProviderReadiness {
  if (options.ready) {
    return options.hasActiveProvider ? 'ready' : 'missing'
  }
  if (options.hasActiveProvider) {
    return 'cached'
  }
  return options.loading || !options.loadAttempted ? 'loading' : 'unavailable'
}

export function providerCanExecute(readiness: ProviderReadiness) {
  return readiness === 'ready' || readiness === 'cached'
}
