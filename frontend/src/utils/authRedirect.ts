export function safeAuthRedirect(candidate: unknown) {
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return '/'
  }
  return candidate
}
