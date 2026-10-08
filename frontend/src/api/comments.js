import http from './http'

export const fetchComments = (postId, cursor, limit = 20) =>
  http.get('/posts/' + postId + '/comments', { params: { cursor, limit } })

export const createComment = (postId, content, images = []) =>
  http.post('/posts/' + postId + '/comments', { content, images })

export const deleteComment = (commentId) => http.delete('/comments/' + commentId)

export const fetchReplies = (commentId, cursor, limit = 10) =>
  http.get('/comments/' + commentId + '/replies', { params: { cursor, limit } })

export const createReply = (commentId, content, images = []) =>
  http.post('/comments/' + commentId + '/replies', { content, images })

export const likeComment = (commentId) => http.post('/comments/' + commentId + '/like')

export const unlikeComment = (commentId) => http.delete('/comments/' + commentId + '/like')
