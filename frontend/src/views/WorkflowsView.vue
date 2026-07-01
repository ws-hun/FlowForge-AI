<template>
  <section>
    <header class="quiet-header">
      <p class="page-kicker">Flow Space</p>
      <h1>在画布中组织 AI 工作流。</h1>
      <p>当前版本预留 Flow Canvas。未来可以拖拽节点、连接执行路径、配置输入输出。</p>
    </header>

    <div class="flow-space surface">
      <div class="flow-toolbar">
        <button class="secondary-button">添加节点</button>
        <button class="ghost-button">运行预览</button>
      </div>
      <div class="flow-canvas">
        <div class="flow-node">开始</div>
        <span></span>
        <div class="flow-node primary">AI 任务</div>
        <span></span>
        <div class="flow-node">输出</div>
      </div>
    </div>
  </section>
</template>
