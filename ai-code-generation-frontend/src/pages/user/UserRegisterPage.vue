<template>
  <div id="userRegisterPage">
    <h2 class="title">AI 应用生成 - 用户注册</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 6, message: '密码不能小于 6 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 6, message: '密码不能小于 6 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  min-height: calc(100vh - 128px);
  width: 100%;
  display: grid;
  place-items: center;
  padding: 64px 20px;
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(120deg, rgba(20, 184, 166, 0.18), transparent 24%),
    linear-gradient(245deg, rgba(244, 114, 182, 0.12), transparent 28%),
    linear-gradient(180deg, #08111f 0%, #0b1020 48%, #101319 100%);
  isolation: isolate;
}

#userRegisterPage::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -2;
  background-image:
    linear-gradient(rgba(45, 212, 191, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(45, 212, 191, 0.08) 1px, transparent 1px),
    linear-gradient(115deg, transparent 0 44%, rgba(125, 211, 252, 0.1) 50%, transparent 56%);
  background-size:
    42px 42px,
    42px 42px,
    240px 240px;
  mask-image: linear-gradient(180deg, transparent 0%, #000 18%, #000 84%, transparent 100%);
}

#userRegisterPage::after {
  content: '';
  position: absolute;
  inset: -40% -20%;
  z-index: -1;
  background:
    conic-gradient(from 140deg, transparent 0 62%, rgba(34, 211, 238, 0.2), transparent 76%),
    linear-gradient(100deg, transparent 18%, rgba(132, 204, 22, 0.08), transparent 42%);
  filter: blur(18px);
  opacity: 0.9;
  animation: authScan 12s ease-in-out infinite alternate;
}

.title {
  text-align: center;
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.25;
  color: transparent;
  background: linear-gradient(120deg, #e0f2fe 0%, #22d3ee 38%, #a7f3d0 70%, #f9a8d4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  text-shadow: 0 0 28px rgba(34, 211, 238, 0.34);
}

.desc {
  text-align: center;
  color: rgba(203, 213, 225, 0.78);
  margin-bottom: 30px;
  font-size: 15px;
}

.tips {
  margin: -2px 0 18px;
  color: rgba(203, 213, 225, 0.68);
  font-size: 13px;
  text-align: right;
}

#userRegisterPage :deep(.ant-form) {
  width: min(100%, 460px);
  padding: 40px 38px 34px;
  position: relative;
  border: 1px solid rgba(125, 211, 252, 0.24);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.82), rgba(15, 23, 42, 0.66)),
    linear-gradient(135deg, rgba(34, 211, 238, 0.14), rgba(244, 114, 182, 0.08));
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.46),
    0 0 0 1px rgba(255, 255, 255, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(22px);
}

#userRegisterPage :deep(.ant-form)::before {
  content: '';
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, #22d3ee, #a7f3d0, transparent);
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.72);
}

#userRegisterPage :deep(.ant-form-item) {
  margin-bottom: 20px;
}

#userRegisterPage :deep(.ant-input),
#userRegisterPage :deep(.ant-input-affix-wrapper) {
  height: 46px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(2, 6, 23, 0.48) !important;
  color: #e2e8f0 !important;
  caret-color: #67e8f9;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

#userRegisterPage :deep(.ant-input-affix-wrapper .ant-input) {
  height: auto;
  border: none;
  background: transparent !important;
  color: inherit !important;
  box-shadow: none !important;
}

#userRegisterPage :deep(.ant-input::placeholder),
#userRegisterPage :deep(.ant-input-affix-wrapper .ant-input::placeholder) {
  color: rgba(148, 163, 184, 0.76);
}

#userRegisterPage :deep(.ant-input:-webkit-autofill),
#userRegisterPage :deep(.ant-input:-webkit-autofill:hover),
#userRegisterPage :deep(.ant-input:-webkit-autofill:focus),
#userRegisterPage :deep(.ant-input-affix-wrapper .ant-input:-webkit-autofill),
#userRegisterPage :deep(.ant-input-affix-wrapper .ant-input:-webkit-autofill:hover),
#userRegisterPage :deep(.ant-input-affix-wrapper .ant-input:-webkit-autofill:focus) {
  border-color: rgba(45, 212, 191, 0.56) !important;
  -webkit-text-fill-color: #e2e8f0 !important;
  caret-color: #67e8f9;
  box-shadow: 0 0 0 1000px rgba(2, 6, 23, 0.86) inset !important;
  transition: background-color 9999s ease-out;
}

#userRegisterPage :deep(.ant-input:hover),
#userRegisterPage :deep(.ant-input-affix-wrapper:hover) {
  border-color: rgba(45, 212, 191, 0.56);
  background: rgba(8, 13, 28, 0.74) !important;
}

#userRegisterPage :deep(.ant-input:focus),
#userRegisterPage :deep(.ant-input-focused),
#userRegisterPage :deep(.ant-input-affix-wrapper-focused) {
  border-color: #22d3ee;
  background: rgba(8, 13, 28, 0.82) !important;
  box-shadow:
    0 0 0 3px rgba(34, 211, 238, 0.12),
    0 0 26px rgba(34, 211, 238, 0.2);
}

#userRegisterPage :deep(.ant-input-password-icon) {
  color: rgba(203, 213, 225, 0.68);
}

#userRegisterPage :deep(.ant-form-item-explain-error) {
  color: #fda4af;
  font-size: 12px;
}

#userRegisterPage :deep(a) {
  color: #67e8f9;
  font-weight: 600;
  text-shadow: 0 0 14px rgba(103, 232, 249, 0.34);
}

#userRegisterPage :deep(a:hover) {
  color: #a7f3d0;
}

#userRegisterPage :deep(.ant-btn-primary) {
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: 12px;
  font-weight: 700;
  letter-spacing: 0;
  color: #04111f;
  background: linear-gradient(135deg, #67e8f9 0%, #22d3ee 42%, #a7f3d0 100%);
  box-shadow:
    0 14px 34px rgba(34, 211, 238, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

#userRegisterPage :deep(.ant-btn-primary:hover),
#userRegisterPage :deep(.ant-btn-primary:focus) {
  color: #020617;
  background: linear-gradient(135deg, #a7f3d0 0%, #67e8f9 54%, #f9a8d4 100%);
  box-shadow:
    0 18px 42px rgba(45, 212, 191, 0.34),
    0 0 26px rgba(34, 211, 238, 0.22);
  transform: translateY(-1px);
}

@keyframes authScan {
  0% {
    transform: translate3d(-3%, -1%, 0) rotate(0deg);
  }
  100% {
    transform: translate3d(3%, 1%, 0) rotate(4deg);
  }
}

@media (max-width: 576px) {
  #userRegisterPage {
    min-height: calc(100vh - 112px);
    padding: 40px 14px;
  }

  #userRegisterPage :deep(.ant-form) {
    padding: 34px 22px 28px;
    border-radius: 16px;
  }

  .title {
    font-size: 24px;
  }

  .desc {
    font-size: 14px;
  }
}
</style>
