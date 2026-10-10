/** HTTP CONNECT entries used by the Java crawler. */
export function parseProxyAddress(value: string) {
  let url: URL
  try { url = new URL(value.includes('://') ? value.trim() : `http://${value.trim()}`) }
  catch { throw new Error('请填写有效代理地址，例如 http://192.168.1.10:7890') }
  if (url.protocol !== 'http:' || !url.hostname || (url.pathname && url.pathname !== '/') || url.search || url.hash) {
    throw new Error('代理入口须为 HTTP 地址，不能包含路径、查询参数或片段')
  }
  let username: string, password: string
  try { username = decodeURIComponent(url.username); password = decodeURIComponent(url.password) }
  catch { throw new Error('代理认证信息编码无效') }
  return { host: url.hostname, port: Number(url.port || 80), username, password }
}
export function formatProxyAddress(host: string, port: number) {
  const safeHost = host.includes(':') && !host.startsWith('[') ? `[${host}]` : host
  return `http://${safeHost}:${port}`
}
