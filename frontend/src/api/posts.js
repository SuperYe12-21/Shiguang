import http from './http'

export const fetchFeed = (cursor, limit = 10) =>
  http.get('/posts/feed', { params: { cursor, limit } })

export const fetchPostDetail = (id) => http.get(`/posts/${id}`)

export const likePost = (id) => http.post(`/posts/${id}/like`)

export const unlikePost = (id) => http.delete(`/posts/${id}/like`)