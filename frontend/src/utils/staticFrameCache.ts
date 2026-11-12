// Bounded decoded-frame LRU (64 frames, each at most 400 x 400).
const frames = new Map<string, HTMLCanvasElement>()
export function getStaticFrame(key: string) {
  const frame = frames.get(key)
  if (frame) { frames.delete(key); frames.set(key, frame) }
  return frame
}
export function cacheStaticFrame(key: string, source: HTMLCanvasElement) {
  const frame = document.createElement('canvas')
  frame.width = source.width; frame.height = source.height
  frame.getContext('2d')?.drawImage(source, 0, 0)
  frames.delete(key); frames.set(key, frame)
  if (frames.size > 64) frames.delete(frames.keys().next().value!)
}
