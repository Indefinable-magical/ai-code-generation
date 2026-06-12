<template>
  <div id="userProfilePage">
    <section class="profile-shell">
      <div class="profile-visual">
        <div class="avatar-ring">
          <a-avatar :size="88" :src="formState.userAvatar">
            {{ avatarText }}
          </a-avatar>
        </div>
        <div class="profile-meta">
          <h2>{{ formState.userName || '普通用户' }}</h2>
          <p>{{ formState.userAccount || '当前登录账号' }}</p>
        </div>
      </div>

      <a-form
        class="profile-form"
        layout="vertical"
        :model="formState"
        :rules="rules"
        @finish="handleSubmit"
      >
        <a-form-item label="账号">
          <a-input v-model:value="formState.userAccount" disabled />
        </a-form-item>
        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="formState.userName" placeholder="请输入昵称" :maxlength="40" />
        </a-form-item>
        <a-form-item label="头像地址" name="userAvatar">
          <a-input v-model:value="formState.userAvatar" placeholder="请输入头像图片 URL" />
        </a-form-item>
        <a-form-item label="个人简介" name="userProfile">
          <a-textarea
            v-model:value="formState.userProfile"
            placeholder="介绍一下自己"
            :rows="4"
            :maxlength="200"
            show-count
          />
        </a-form-item>
        <div class="form-actions">
          <a-button @click="resetForm">重置</a-button>
          <a-button type="primary" html-type="submit" :loading="submitting">保存资料</a-button>
        </div>
      </a-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { updateMyUser } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const loginUserStore = useLoginUserStore()
const submitting = ref(false)

const formState = reactive<API.LoginUserVO>({
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const rules: Record<string, Rule[]> = {
  userName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 40, message: '昵称不能超过 40 个字符', trigger: 'blur' },
  ],
  userAvatar: [{ type: 'url', message: '请输入有效的图片 URL', trigger: 'blur' }],
  userProfile: [{ max: 200, message: '个人简介不能超过 200 个字符', trigger: 'blur' }],
}

const avatarText = computed(() => formState.userName?.slice(0, 1) || '用')

const syncFormFromStore = () => {
  const loginUser = loginUserStore.loginUser
  formState.userAccount = loginUser.userAccount || ''
  formState.userName = loginUser.userName || ''
  formState.userAvatar = loginUser.userAvatar || ''
  formState.userProfile = loginUser.userProfile || ''
}

const resetForm = () => {
  syncFormFromStore()
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const res = await updateMyUser({
      userName: formState.userName,
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile,
    })
    if (res.data.code === 0) {
      await loginUserStore.fetchLoginUser()
      syncFormFromStore()
      message.success('资料已更新')
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  syncFormFromStore()
})
</script>

<style scoped>
#userProfilePage {
  min-height: calc(100vh - 64px);
  padding: 48px 20px 72px;
  background:
    radial-gradient(circle at 18% 20%, rgba(37, 99, 235, 0.14), transparent 28%),
    radial-gradient(circle at 82% 18%, rgba(124, 58, 237, 0.12), transparent 28%),
    linear-gradient(180deg, #f8fafc 0%, #eef4ff 100%);
}

.profile-shell {
  max-width: 880px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 28px;
  padding: 28px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.22);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.profile-visual {
  min-height: 100%;
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  border-radius: 8px;
  color: #ffffff;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(30, 64, 175, 0.92)),
    linear-gradient(90deg, rgba(34, 211, 238, 0.24) 1px, transparent 1px);
  background-size: auto, 18px 18px;
}

.avatar-ring {
  padding: 7px;
  border-radius: 999px;
  background: linear-gradient(135deg, #38bdf8, #8b5cf6, #22c55e);
  box-shadow: 0 0 32px rgba(59, 130, 246, 0.5);
}

.profile-meta h2 {
  margin: 18px 0 6px;
  font-size: 24px;
  color: #ffffff;
}

.profile-meta p {
  margin: 0;
  color: rgba(226, 232, 240, 0.82);
  word-break: break-all;
}

.profile-form {
  min-width: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  #userProfilePage {
    padding: 24px 12px 48px;
  }

  .profile-shell {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
