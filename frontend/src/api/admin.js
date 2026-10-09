import http from './http'

/** 当前登录人是否管理员（非管理员返回 admin:false，不报错） */
export const fetchAdminMe = () => http.get('/admin/me')

/** 全量作品列表：status / visibility / authorKeyword / postId / cursor / limit */
export const fetchAdminPosts = (params) => http.get('/admin/posts', { params })

/** 下架作品（可填原因，作者可见） */
export const blockAdminPost = (id, reason) => http.post(`/admin/posts/${id}/block`, { reason: reason || '' })

/** 恢复被下架的作品 */
export const unblockAdminPost = (id) => http.post(`/admin/posts/${id}/unblock`)

/** 删除作品（不可逆） */
export const deleteAdminPost = (id) => http.delete(`/admin/posts/${id}`)
