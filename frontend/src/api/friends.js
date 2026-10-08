import http from './http'

export const fetchFriends = (cursor, limit = 20) =>
  http.get('/follow/friends', { params: { cursor: cursor || '', limit } })

export const fetchFriendsUnread = () => http.get('/feed/friends/unread')

export const markFriendsSeen = () => http.post('/feed/friends/seen')
