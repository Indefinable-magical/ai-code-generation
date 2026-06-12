<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <div class="ai-logo" aria-label="AI 应用生成平台">
              <div class="ai-logo-orbit ai-logo-orbit-one"></div>
              <div class="ai-logo-orbit ai-logo-orbit-two"></div>
              <div class="ai-logo-core">
                <ApiOutlined />
              </div>
            </div>
            <h1 class="site-title"></h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown :trigger="['click']">
              <a-space class="user-dropdown-trigger">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '普通用户' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="goUserProfile">
                    <UserOutlined />
                    用户资料
                  </a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import {
  ApiOutlined,
  AppstoreOutlined,
  HomeOutlined,
  LogoutOutlined,
  MessageOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import ACCESS_ENUM, { type AccessEnum } from '@/access/accessEnum.ts'
import checkAccess from '@/access/checkAccess.ts'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: '应用管理',
    title: '应用管理',
  },
  {
    key: '/admin/chatManage',
    icon: () => h(MessageOutlined),
    label: '对话管理',
    title: '对话管理',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (!menuKey) {
      return false
    }
    const matchedRoutes = router.resolve(menuKey).matched
    const routeMeta = matchedRoutes[matchedRoutes.length - 1]?.meta
    const needAccess = (routeMeta?.access as AccessEnum) ?? ACCESS_ENUM.NOT_LOGIN
    return !routeMeta?.hideInMenu && checkAccess(loginUserStore.loginUser, needAccess)
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 进入用户资料页
const goUserProfile = () => {
  router.push('/user/profile')
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
  height: 64px;
  line-height: 64px;
}

.header :deep(.ant-row) {
  height: 100%;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
}

.ai-logo {
  position: relative;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background:
    radial-gradient(circle at 34% 28%, rgba(255, 255, 255, 0.95), transparent 24%),
    linear-gradient(135deg, #0f172a 0%, #1d4ed8 44%, #7c3aed 100%);
  box-shadow:
    0 12px 28px rgba(37, 99, 235, 0.28),
    inset 0 0 18px rgba(255, 255, 255, 0.16);
  overflow: hidden;
}

.ai-logo::before {
  content: '';
  position: absolute;
  inset: 5px;
  border-radius: 11px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.16) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.12) 1px, transparent 1px);
  background-size: 9px 9px;
  opacity: 0.8;
}

.ai-logo::after {
  content: '';
  position: absolute;
  width: 62px;
  height: 14px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.72), transparent);
  transform: rotate(-28deg);
}

.ai-logo-core {
  position: relative;
  z-index: 2;
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #ffffff;
  font-size: 15px;
  background: rgba(15, 23, 42, 0.52);
  border: 1px solid rgba(125, 211, 252, 0.72);
  box-shadow:
    0 0 18px rgba(34, 211, 238, 0.62),
    inset 0 0 12px rgba(96, 165, 250, 0.38);
}

.ai-logo-orbit {
  position: absolute;
  border: 1px solid rgba(125, 211, 252, 0.72);
  border-radius: 999px;
  z-index: 1;
}

.ai-logo-orbit-one {
  width: 36px;
  height: 15px;
  transform: rotate(24deg);
}

.ai-logo-orbit-two {
  width: 36px;
  height: 15px;
  transform: rotate(-32deg);
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}

.user-dropdown-trigger {
  height: 64px;
  padding: 0 10px;
  cursor: pointer;
}
</style>
