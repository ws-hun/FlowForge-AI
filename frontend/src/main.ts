import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElCollapse, ElCollapseItem, ElDialog, ElDrawer, ElIcon } from 'element-plus'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/collapse/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'
import './styles/main.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElCollapse)
app.use(ElCollapseItem)
app.use(ElDialog)
app.use(ElDrawer)
app.use(ElIcon)
app.mount('#app')
