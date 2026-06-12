<template>
  <div class="no-auth-page">
    <a-result
      status="403"
      title="无权限"
      sub-title="当前账号没有访问该页面的权限"
    >
      <template #extra>
        <a-space>
          <a-button @click="router.back()">返回上一页</a-button>
          <a-button type="primary" @click="router.push('/')">回到主页</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.no-auth-page {
  min-height: calc(100vh - 168px);
  display: grid;
  place-items: center;
  padding: 48px 16px;
  background: #f7f9fc;
}
</style>
