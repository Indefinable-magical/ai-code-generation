import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'
import ACCESS_ENUM, { type AccessEnum } from '@/access/accessEnum'
import checkAccess from '@/access/checkAccess'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新，首次加载时，能够等后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  const needAccess = (to.meta?.access as AccessEnum) ?? ACCESS_ENUM.NOT_LOGIN
  if (needAccess !== ACCESS_ENUM.NOT_LOGIN && !loginUser?.id) {
    message.warning('请先登录')
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }
  if (!checkAccess(loginUser, needAccess)) {
    message.error('没有权限')
    next('/noAuth')
    return
  }
  next()
})
