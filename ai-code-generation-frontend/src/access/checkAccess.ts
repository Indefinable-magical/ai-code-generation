import ACCESS_ENUM, { type AccessEnum } from '@/access/accessEnum'

/**
 * 校验当前登录用户是否具备目标权限
 *
 * @param loginUser 当前登录用户
 * @param needAccess 需要的权限，默认未登录即可访问
 */
const checkAccess = (
  loginUser: API.LoginUserVO = {},
  needAccess: AccessEnum = ACCESS_ENUM.NOT_LOGIN,
) => {
  const loginUserRole = loginUser.userRole ?? ACCESS_ENUM.NOT_LOGIN

  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true
  }

  if (needAccess === ACCESS_ENUM.USER) {
    return loginUserRole !== ACCESS_ENUM.NOT_LOGIN
  }

  if (needAccess === ACCESS_ENUM.ADMIN) {
    return loginUserRole === ACCESS_ENUM.ADMIN
  }

  return true
}

export default checkAccess
