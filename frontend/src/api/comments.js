import http from './http'

export const fetchComments = (postId, cursor, limit = 20) =>
  http.get('/posts/' + postId + '/comments', { params: { cursor, limit } })

export const createComment = (postId, content) =>
  http.post('/posts/' + postId + '/comments', { content })

export const deleteComment = (commentId) => http.delete('/comments/' + commentId)

export const likeComment = (commentId) => http.post('/comments/' + commentId + '/like')

export const unlikeComment = (commentId) => http.delete('/comments/' + commentId + '/like')
