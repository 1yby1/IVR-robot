<template>
  <div class="not-found">
    <div class="nf-code">404</div>
    <h1 class="nf-title">页面走丢了</h1>
    <p class="nf-desc">你访问的页面不存在或已被移除</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  background: var(--color-bg-muted);
}
.nf-code {
  font-size: 96px;
  font-weight: var(--weight-semibold);
  line-height: 1;
  color: var(--color-secondary);
  letter-spacing: -2px;
  font-variant-numeric: tabular-nums;
}
.nf-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
  margin-top: var(--space-2);
}
.nf-desc {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin-bottom: var(--space-4);
}
</style>
