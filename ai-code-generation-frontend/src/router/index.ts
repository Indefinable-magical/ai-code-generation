import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserProfilePage from '@/pages/user/UserProfilePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'
import ChatManagePage from '@/pages/admin/ChatManagePage.vue'
import NoAuthPage from '@/pages/NoAuthPage.vue'
import ACCESS_ENUM from '@/access/accessEnum'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
      },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
        hideInMenu: true,
      },
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
        hideInMenu: true,
      },
    },
    {
      path: '/user/profile',
      name: '用户资料',
      component: UserProfilePage,
      meta: {
        access: ACCESS_ENUM.USER,
        hideInMenu: true,
      },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/admin/chatManage',
      name: '对话管理',
      component: ChatManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/app/chat/:id',
      name: '应用对话',
      component: AppChatPage,
      meta: {
        access: ACCESS_ENUM.USER,
        hideInMenu: true,
      },
    },
    {
      path: '/app/edit/:id',
      name: '编辑应用',
      component: AppEditPage,
      meta: {
        access: ACCESS_ENUM.USER,
        hideInMenu: true,
      },
    },
    {
      path: '/noAuth',
      name: '无权限',
      component: NoAuthPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
        hideInMenu: true,
      },
    },
  ],
})

export default router
