import http from './http'

export const fetchConversations = (cursor, limit = 20) =>
  http.get('/conversations', { params: { cursor: cursor || '', limit } })

export const fetchChat = (peerId, cursor, limit = 30) =>
  http.get(`/conversations/${peerId}/messages`, { params: { cursor: cursor || '', limit } })

/** payload: { type: 'TEXT' | 'IMAGE' | 'POST_CARD', content, imageUrls, postId } */
export const sendMessage = (toUserId, payload) =>
  http.post('/messages', { toUserId, ...payload })

export const markConversationRead = (peerId) =>
  http.post(`/conversations/${peerId}/read`)

export const fetchMessageUnread = () =>
  http.get('/messages/unread')
