export function safeReturnTo(value, router) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020\u007f]/.test(value)) {
    return '/feed'
  }
  let path
  try {
    path = decodeURIComponent(value.split(/[?#]/)[0])
  } catch {
    return '/feed'
  }
  if (path.startsWith('//') || /[\\\u0000-\u0020\u007f]/.test(path) || path.split('/').some((part) => part === '.' || part === '..')) {
    return '/feed'
  }
  const resolved = router.resolve(value)
  if (!resolved.matched.length || resolved.name === 'login' || resolved.name === 'not-found') return '/feed'
  return resolved.fullPath
}

export function loginLocation(fullPath, router) {
  return { name: 'login', query: { redirect: safeReturnTo(fullPath, router) } }
}
