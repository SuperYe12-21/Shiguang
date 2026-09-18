import http from './http'

export const fetchUnread = () => http.get('/notifications/unread')

export const fetchNotifications = (category, cursor, limit = 20) =>
  http.get('/notifications', { params: { category, cursor: cursor || '', limit } })

export const markNotificationsRead = (category) =>
  http.post('/notifications/read', null, { params: { category } })
