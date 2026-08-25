import http from './client'
import type {
  AuthCredentials,
  AuthPasswordChangePayload,
  AuthProfileUpdatePayload,
  AuthSetupPayload,
  AuthStatus
} from '@/types'

export function getAuthStatus() {
  return http.get<AuthStatus>('/api/auth/status')
}

export function setupWorkspaceOwner(payload: AuthSetupPayload) {
  return http.post<AuthStatus>('/api/auth/setup', payload)
}

export function login(payload: AuthCredentials) {
  return http.post<AuthStatus>('/api/auth/login', payload)
}

export function logout() {
  return http.post<void>('/api/auth/logout')
}

export function updateProfile(payload: AuthProfileUpdatePayload) {
  return http.patch<AuthStatus>('/api/auth/me', payload)
}

export function changePassword(payload: AuthPasswordChangePayload) {
  return http.post<AuthStatus>('/api/auth/me/password', payload)
}
