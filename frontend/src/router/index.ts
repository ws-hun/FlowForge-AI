import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'
import TasksView from '@/views/TasksView.vue'
import WorkflowsView from '@/views/WorkflowsView.vue'
import AgentsView from '@/views/AgentsView.vue'
import PromptLibraryView from '@/views/PromptLibraryView.vue'
import KnowledgeBaseView from '@/views/KnowledgeBaseView.vue'
import HistoryView from '@/views/HistoryView.vue'
import AnalyticsView from '@/views/AnalyticsView.vue'
import ApiKeysView from '@/views/ApiKeysView.vue'
import SettingsView from '@/views/SettingsView.vue'
import ProfileView from '@/views/ProfileView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'workspace', component: WorkspaceView },
        { path: 'tasks', name: 'tasks', component: TasksView },
        { path: 'workflows', name: 'workflows', component: WorkflowsView },
        { path: 'agents', name: 'agents', component: AgentsView },
        { path: 'prompts', name: 'prompts', component: PromptLibraryView },
        { path: 'knowledge', name: 'knowledge', component: KnowledgeBaseView },
        { path: 'history', name: 'history', component: HistoryView },
        { path: 'analytics', name: 'analytics', component: AnalyticsView },
        { path: 'api-keys', name: 'api-keys', component: ApiKeysView },
        { path: 'settings', name: 'settings', component: SettingsView },
        { path: 'profile', name: 'profile', component: ProfileView }
      ]
    }
  ]
})

export default router
