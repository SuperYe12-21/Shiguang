import http from './http'

export const followUser = (userId) => http.post(`/follow/${userId}`)

export const unfollowUser = (userId) => http.delete(`/follow/${userId}`)

export const fetchMe = () => http.get('/user/me')

export const fetchProfile = (userId) => http.get(`/user/${userId}`)

export const fetchUserPosts = (userId, cursor, limit) => http.get(`/user/${userId}/posts`, { params: { cursor: cursor || '', limit: limit || 12 } })

export const fetchUserLikes = (userId, cursor, limit) => http.get(`/user/${userId}/likes`, { params: { cursor: cursor || '', limit: limit || 12 } })

export const fetchUserFollowers = (userId, cursor, limit) => http.get(`/user/${userId}/followers`, { params: { cursor: cursor || '', limit: limit || 20 } })

export const fetchUserFollowing = (userId, cursor, limit) => http.get(`/user/${userId}/following`, { params: { cursor: cursor || '', limit: limit || 20 } })

export const updateMe = (payload) => http.put('/user/me', payload)
