import http from './http'

export const followUser = (userId) => http.post(`/follow/${userId}`)

export const unfollowUser = (userId) => http.delete(`/follow/${userId}`)

export const fetchMe = () => http.get('/user/me')

export const fetchProfile = (userId) => http.get(`/user/${userId}`)