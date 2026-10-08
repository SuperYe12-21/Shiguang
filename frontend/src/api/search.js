import http from './http'

export const searchUsers = (keyword, cursor, limit) =>
  http.get('/search/users', { params: { keyword, cursor: cursor || '', limit: limit || 20 } })

export const searchPosts = (keyword, cursor, limit) =>
  http.get('/search/posts', { params: { keyword, cursor: cursor || '', limit: limit || 12 } })
