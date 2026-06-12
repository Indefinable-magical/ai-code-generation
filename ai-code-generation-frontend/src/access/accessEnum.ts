/**
 * 权限定义
 */
export const ACCESS_ENUM = {
  NOT_LOGIN: 'notLogin',
  USER: 'user',
  ADMIN: 'admin',
} as const

export type AccessEnum = (typeof ACCESS_ENUM)[keyof typeof ACCESS_ENUM]

export default ACCESS_ENUM
