import { describe, expect, it } from 'vitest'
import { safeAuthRedirect } from '@/utils/authRedirect'

describe('authentication redirect', () => {
  it('keeps an internal workspace path', () => {
    expect(safeAuthRedirect('/workflows?flow=flow-1#node')).toBe('/workflows?flow=flow-1#node')
  })

  it('rejects absolute and protocol-relative destinations', () => {
    expect(safeAuthRedirect('https://example.com')).toBe('/')
    expect(safeAuthRedirect('//example.com')).toBe('/')
  })

  it('falls back when no string destination is available', () => {
    expect(safeAuthRedirect(undefined)).toBe('/')
    expect(safeAuthRedirect(['/history'])).toBe('/')
  })
})
