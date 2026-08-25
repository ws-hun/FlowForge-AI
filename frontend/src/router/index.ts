import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { safeAuthRedirect } from '@/utils/authRedirect'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth',
      name: 'auth',
      component: () => import('@/views/AuthView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'workspace', component: () => import('@/views/WorkspaceView.vue') },
        { path: 'tasks', name: 'tasks', component: () => import('@/views/TasksView.vue') },
        { path: 'workflows', name: 'workflows', component: () => import('@/views/WorkflowsView.vue') },
        { path: 'agents', name: 'agents', component: () => import('@/views/AgentsView.vue') },
        { path: 'prompts', name: 'prompts', component: () => import('@/views/PromptLibraryView.vue') },
        { path: 'knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeBaseView.vue') },
        { path: 'history', name: 'history', component: () => import('@/views/HistoryView.vue') },
        { path: 'analytics', name: 'analytics', component: () => import('@/views/AnalyticsView.vue') },
        { path: 'api-keys', name: 'api-keys', component: () => import('@/views/ApiKeysView.vue') },
        { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.bootstrap()

  if (to.meta.public) {
    if (auth.authenticated) {
      return safeAuthRedirect(to.query.redirect)
    }
    return true
  }

  if (!auth.authenticated) {
    return {
      name: 'auth',
      query: { redirect: to.fullPath }
    }
  }
  return true
})

export default router
