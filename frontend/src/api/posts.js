import http from './http'

export const fetchFeed = (cursor, limit = 10) =>
  http.get('/posts/feed', { params: { cursor, limit } })

export const fetchPostDetail = (id) => http.get(`/posts/${id}`)

export const likePost = (id) => http.post(`/posts/${id}/like`)

export const unlikePost = (id) => http.delete(`/posts/${id}/like`)

export const favoritePost = (id) => http.post(`/posts/${id}/favorite`)

export const unfavoritePost = (id) => http.delete(`/posts/${id}/favorite`)
export const presignUpload = (type, contentType, extension) =>
  http.post('/upload/presign', { type, contentType, extension })

export const createPost = (payload) => http.post('/posts', payload)

export const markPostSeen = (id) => http.post(`/feed/seen/${id}`)

export const setPostVisibility = (id, visibility) => http.put(`/posts/${id}/visibility`, { visibility })

export const updatePost = (id, payload) => http.put(`/posts/${id}`, payload)

export const deletePost = (id) => http.delete(`/posts/${id}`)
